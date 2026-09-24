package com.foxaria.core.service;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.profile.PlayerProfile;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Фейковый игрок для системы слипперов: пакетный ServerPlayer в позе SWIMMING
 * (+ флаг sprinting) — клиент рисует тело лежащим, как спящее тело в Rust.
 *
 * Ключевые инварианты (именно их нарушала старая версия):
 *  - entity id тела ОДИН на владельца и для всех зрителей — иначе при удалении
 *    у поздно зашедших игроков остаются «призраки»;
 *  - NMS-сущность нужна только чтобы получить свободный entity id и скин;
 *    все пакеты (spawn/meta/remove) строятся по снапшоту, поэтому поздно
 *    зашедшие получают то же тело с тем же id и тем же скином;
 *  - поза только SWIMMING: SLEEPING без кровати клиент рендерит с глитчами;
 *  - никакого логирования пакетов: спам раздувает логи и грузит диск.
 */
public final class FakePlayerSleeper {

    private final JavaPlugin plugin;
    private final Map<UUID, SleeperVisual> visuals = new ConcurrentHashMap<>();
    private volatile boolean debug;

    private record SkinProp(String name, String value, String signature) {}

    private record SleeperVisual(
        int entityId,
        UUID uuid,
        String name,
        List<SkinProp> skin,
        double x, double y, double z,
        float yaw
    ) {}

    public FakePlayerSleeper(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public boolean spawn(Location loc, UUID ownerUuid, String playerName, PlayerProfile profile) {
        SleeperVisual v = register(loc, ownerUuid, playerName, profile);
        if (v == null) return false;
        try {
            sendSpawn(v, new ArrayList<>(Bukkit.getOnlinePlayers()));
            return true;
        } catch (Exception e) {
            dbg("spawn broadcast failed: " + e.getMessage());
            return false;
        }
    }

    public void sendToPlayer(Player viewer, UUID ownerUuid, Location loc, String playerName, PlayerProfile profile) {
        if (viewer == null || !viewer.isOnline()) return;
        try {
            SleeperVisual v = visuals.get(ownerUuid);
            if (v == null && loc != null) {
                v = register(loc, ownerUuid, playerName, profile);
            }
            if (v == null) return;
            sendSpawn(v, List.of(viewer));
        } catch (Exception ignored) {
        }
    }

    private SleeperVisual register(Location loc, UUID ownerUuid, String playerName, PlayerProfile profile) {
        if (loc == null || loc.getWorld() == null) return null;
        SleeperVisual existing = visuals.get(ownerUuid);
        if (existing != null) return existing;
        try {
            Object sp = buildServerPlayer(loc, ownerUuid, playerName, profile);
            int eid = nmsEntityId(sp);
            if (eid < 0) return null;
            SleeperVisual v = new SleeperVisual(
                eid, ownerUuid, playerName, extractSkin(sp),
                loc.getX(), loc.getY() - 0.35, loc.getZ(), loc.getYaw());
            visuals.put(ownerUuid, v);
            return v;
        } catch (Exception e) {
            dbg("register failed: " + e.getMessage());
            return null;
        }
    }

    public void remove(UUID ownerUuid) {
        SleeperVisual v = visuals.remove(ownerUuid);
        if (v == null) return;
        try {
            Object pkt = removePacket(v.entityId());
            for (Player p : Bukkit.getOnlinePlayers()) {
                send(p, pkt);
            }
        } catch (Exception ignored) {
        }
    }

    public void removeAll() {
        new ArrayList<>(visuals.keySet()).forEach(this::remove);
    }

    // ── Пакеты из снапшота (без живой NMS-сущности) ───────────────────────────

    private void sendSpawn(SleeperVisual v, Collection<? extends Player> viewers) throws Exception {
        Object infoAdd = infoAddPacket(v);
        Object addEntity = addEntityPacket(v);
        Object meta = metaPacket(v.entityId());
        Object infoRemove = infoRemovePacket(v.uuid());

        for (Player viewer : viewers) {
            if (!viewer.isOnline()) continue;
            send(viewer, infoAdd);
            send(viewer, addEntity);
            send(viewer, meta);
        }
        // Метаданные (поза) повторно через 2 тика — клиент успевает обработать спавн.
        List<Player> targets = viewers.stream().filter(Player::isOnline).toList();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            for (Player viewer : targets) {
                if (viewer.isOnline()) send(viewer, meta);
            }
        }, 2L);
        // Убираем из таблицы — скин у клиента уже закэширован.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            for (Player viewer : targets) {
                if (viewer.isOnline()) send(viewer, infoRemove);
            }
        }, 60L);
    }

    private Object addEntityPacket(SleeperVisual v) throws Exception {
        Class<?> cls = nms("net.minecraft.network.protocol.game.ClientboundAddEntityPacket");
        Class<?> etClass = nms("net.minecraft.world.entity.EntityType");
        Object playerType = etClass.getField("PLAYER").get(null);
        Class<?> vecClass = nms("net.minecraft.world.phys.Vec3");
        Object zeroVec = vecClass.getField("ZERO").get(null);
        Constructor<?> c = cls.getDeclaredConstructor(
            int.class, UUID.class,
            double.class, double.class, double.class,
            float.class, float.class,
            etClass, int.class, vecClass, double.class);
        c.setAccessible(true);
        return c.newInstance(v.entityId(), v.uuid(), v.x(), v.y(), v.z(), 0.0f, v.yaw(),
            playerType, 0, zeroVec, (double) v.yaw());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object infoAddPacket(SleeperVisual v) throws Exception {
        Class<?> pkCls = nms("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket");
        Class<?> actCls = nms("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket$Action");
        Class<?> entCls = nms("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket$Entry");
        Class<?> gpCls = nms("com.mojang.authlib.GameProfile");
        Class<?> gtCls = nms("net.minecraft.world.level.GameType");

        java.util.EnumSet actions = java.util.EnumSet.of((Enum) findEnum(actCls, "ADD_PLAYER"));
        Object profile = buildGameProfile(v.uuid(), v.name(), v.skin());

        Constructor<?> entryCtor = entCls.getDeclaredConstructor(
            UUID.class, gpCls,
            boolean.class, int.class, gtCls,
            nms("net.minecraft.network.chat.Component"),
            boolean.class, int.class,
            nms("net.minecraft.network.chat.RemoteChatSession$Data"));
        entryCtor.setAccessible(true);
        Object entry = entryCtor.newInstance(v.uuid(), profile, false, 0,
            findEnum(gtCls, "CREATIVE"), null, true, 0, null);

        List<Object> entries = List.of(entry);
        for (Constructor<?> c : pkCls.getDeclaredConstructors()) {
            Class<?>[] p = c.getParameterTypes();
            if (p.length == 2 && java.util.EnumSet.class.isAssignableFrom(p[0])
                && java.util.Collection.class.isAssignableFrom(p[1])) {
                c.setAccessible(true);
                return c.newInstance(actions, entries);
            }
        }
        throw new NoSuchMethodException("PlayerInfoUpdatePacket(EnumSet, Collection) not found");
    }

    private Object metaPacket(int entityId) throws Exception {
        Class<?> entityClass = nms("net.minecraft.world.entity.Entity");
        Class<?> poseClass = nms("net.minecraft.world.entity.Pose");
        Class<?> dvClass = nms("net.minecraft.network.syncher.SynchedEntityData$DataValue");

        Method createDv = dvClass.getMethod("create",
            nms("net.minecraft.network.syncher.EntityDataAccessor"), Object.class);

        Field flagsField = entityClass.getDeclaredField("DATA_SHARED_FLAGS_ID");
        flagsField.setAccessible(true);
        Object flagsValue = createDv.invoke(null, flagsField.get(null), (byte) 0x08); // sprinting = «crawl» на земле

        Field poseField = entityClass.getDeclaredField("DATA_POSE");
        poseField.setAccessible(true);
        Object poseValue = createDv.invoke(null, poseField.get(null), findEnum(poseClass, "SWIMMING"));

        Class<?> pktClass = nms("net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket");
        Constructor<?> c = pktClass.getDeclaredConstructor(int.class, List.class);
        c.setAccessible(true);
        return c.newInstance(entityId, List.of(flagsValue, poseValue));
    }

    private Object infoRemovePacket(UUID uuid) throws Exception {
        Class<?> cls = nms("net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket");
        for (Constructor<?> c : cls.getDeclaredConstructors()) {
            c.setAccessible(true);
            Class<?>[] p = c.getParameterTypes();
            if (p.length == 1 && java.util.Collection.class.isAssignableFrom(p[0])) {
                return c.newInstance(List.of(uuid));
            }
        }
        Constructor<?> c = cls.getDeclaredConstructors()[0];
        c.setAccessible(true);
        return c.newInstance(List.of(uuid));
    }

    private Object removePacket(int entityId) throws Exception {
        Class<?> cls = nms("net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket");
        return cls.getDeclaredConstructor(int[].class).newInstance((Object) new int[]{entityId});
    }

    // ── NMS ServerPlayer: только ради свободного entity id и скина ────────────

    private Object buildServerPlayer(Location loc, UUID uuid, String playerName, PlayerProfile profile) throws Exception {
        Object mcServer = Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
        Object serverLevel = loc.getWorld().getClass().getMethod("getHandle").invoke(loc.getWorld());

        Object gp = buildGameProfile(uuid, playerName, profile == null ? List.of() : skinFromBukkitProfile(profile));

        Object ci = buildClientInfo();
        Class<?> spClass = nms("net.minecraft.server.level.ServerPlayer");
        for (Constructor<?> c : spClass.getDeclaredConstructors()) {
            Class<?>[] p = c.getParameterTypes();
            if (p.length == 4
                && p[0].isAssignableFrom(mcServer.getClass())
                && p[1].isAssignableFrom(serverLevel.getClass())
                && p[2].getName().contains("GameProfile")
                && p[3].getName().contains("ClientInformation")) {
                c.setAccessible(true);
                return c.newInstance(mcServer, serverLevel, gp, ci);
            }
        }
        throw new NoSuchMethodException("ServerPlayer ctor(MinecraftServer, ServerLevel, GameProfile, ClientInformation)");
    }

    private Object buildClientInfo() throws Exception {
        Class<?> cls;
        try {
            cls = nms("net.minecraft.server.level.ClientInformation");
        } catch (ClassNotFoundException e) {
            cls = nms("net.minecraft.server.network.ClientInformation");
        }
        try {
            return cls.getMethod("createDefault").invoke(null);
        } catch (NoSuchMethodException ignored) {
        }
        Constructor<?> c = cls.getDeclaredConstructors()[0];
        c.setAccessible(true);
        Class<?>[] pt = c.getParameterTypes();
        Object[] args = new Object[pt.length];
        for (int i = 0; i < pt.length; i++) {
            if (pt[i] == String.class) args[i] = "en_us";
            else if (pt[i] == int.class) args[i] = 2;
            else if (pt[i] == boolean.class) args[i] = true;
            else if (pt[i].isEnum()) args[i] = pt[i].getEnumConstants()[0];
        }
        return c.newInstance(args);
    }

    private int nmsEntityId(Object sp) {
        try {
            return (int) sp.getClass().getMethod("getId").invoke(sp);
        } catch (Exception e) {
            return -1;
        }
    }

    // ── Скин: снапшот properties, чтобы поздно зашедшие видели тот же скин ───

    private List<SkinProp> extractSkin(Object sp) {
        try {
            Object gp = sp.getClass().getMethod("getGameProfile").invoke(sp);
            return skinFromGameProfile(gp);
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<SkinProp> skinFromBukkitProfile(PlayerProfile profile) {
        try {
            Object gp = profile.getClass().getMethod("getGameProfile").invoke(profile);
            return skinFromGameProfile(gp);
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<SkinProp> skinFromGameProfile(Object gp) {
        List<SkinProp> out = new ArrayList<>();
        try {
            Object props = gp.getClass().getMethod("getProperties").invoke(gp);
            Iterable<?> values = (Iterable<?>) props.getClass().getMethod("get", String.class).invoke(props, "textures");
            if (values == null) values = (Iterable<?>) props.getClass().getMethod("values").invoke(props);
            for (Object prop : values) {
                String name = readProp(prop, "name", "getName");
                if (!"textures".equals(name)) continue;
                out.add(new SkinProp(name, readProp(prop, "value", "getValue"), readProp(prop, "signature", "getSignature")));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private String readProp(Object prop, String recordAccessor, String legacyGetter) {
        try {
            Object r = prop.getClass().getMethod(recordAccessor).invoke(prop);
            return r == null ? null : r.toString();
        } catch (Exception e) {
            try {
                Object r = prop.getClass().getMethod(legacyGetter).invoke(prop);
                return r == null ? null : r.toString();
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    private Object buildGameProfile(UUID uuid, String name, List<SkinProp> skin) throws Exception {
        Class<?> gpClass = nms("com.mojang.authlib.GameProfile");
        Object gp = gpClass.getConstructor(UUID.class, String.class).newInstance(uuid, name);
        if (skin.isEmpty()) return gp;
        Object props = gp.getClass().getMethod("getProperties").invoke(gp);
        for (SkinProp sp : skin) {
            Object prop = buildProperty(sp);
            if (prop != null) {
                props.getClass().getMethod("put", Object.class, Object.class).invoke(props, sp.name(), prop);
            }
        }
        return gp;
    }

    private Object buildProperty(SkinProp sp) {
        try {
            Class<?> propClass = nms("com.mojang.authlib.properties.Property");
            for (Constructor<?> c : propClass.getDeclaredConstructors()) {
                if (c.getParameterCount() == 3
                    && c.getParameterTypes()[0] == String.class
                    && c.getParameterTypes()[1] == String.class
                    && c.getParameterTypes()[2] == String.class) {
                    c.setAccessible(true);
                    return c.newInstance(sp.name(), sp.value(), sp.signature());
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    // ── Транспорт ─────────────────────────────────────────────────────────────

    private void send(Player player, Object packet) {
        if (packet == null || player == null || !player.isOnline()) return;
        try {
            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            Object connection = handle.getClass().getField("connection").get(handle);
            if (connection == null) return;
            connection.getClass().getMethod("send", nms("net.minecraft.network.protocol.Packet")).invoke(connection, packet);
        } catch (Exception e) {
            dbg("send failed: " + e.getMessage());
        }
    }

    private void dbg(String msg) {
        if (debug) plugin.getLogger().info("[FakePlayerSleeper] " + msg);
    }

    // ── Утилиты ───────────────────────────────────────────────────────────────

    private static Class<?> nms(String name) throws ClassNotFoundException {
        ClassLoader cl = Bukkit.getServer().getClass().getClassLoader();
        try {
            return Class.forName(name, true, cl);
        } catch (ClassNotFoundException e) {
            return Class.forName(name);
        }
    }

    private static Object findEnum(Class<?> cls, String name) {
        for (Object c : cls.getEnumConstants()) {
            if (((Enum<?>) c).name().equals(name)) return c;
        }
        throw new IllegalStateException("enum " + cls.getSimpleName() + "." + name + " not found");
    }
}
