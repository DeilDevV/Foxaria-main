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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Spawns a fake player entity (NMS ServerPlayer) in SWIMMING pose — lying flat like RustMe.
 * Uses exact Paper 1.21.11 constructor signatures discovered via javap.
 */
public final class FakePlayerSleeper {

    private final JavaPlugin plugin;
    private final ConcurrentHashMap<UUID, Integer> entityIds = new ConcurrentHashMap<>();

    public FakePlayerSleeper(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public boolean spawn(Location loc, UUID ownerUuid, String playerName, PlayerProfile profile) {
        try {
            Object sp = buildServerPlayer(loc, ownerUuid, playerName, profile);
            if (sp == null) return false;
            int eid = getId(sp);
            entityIds.put(ownerUuid, eid);
            sendSpawnToAll(sp, loc, ownerUuid, Bukkit.getOnlinePlayers());
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[FakePlayerSleeper] spawn failed: " + e.getMessage(), e);
            return false;
        }
    }

    public void sendToPlayer(Player viewer, UUID ownerUuid, Location loc, String playerName, PlayerProfile profile) {
        try {
            Object sp = buildServerPlayer(loc, ownerUuid, playerName, profile);
            if (sp == null) return;
            sendSpawnToAll(sp, loc, ownerUuid, List.of(viewer));
        } catch (Exception ignored) {}
    }

    public void remove(UUID ownerUuid) {
        Integer eid = entityIds.remove(ownerUuid);
        if (eid == null) return;
        try {
            Object pkt = buildRemovePacket(eid);
            for (Player p : Bukkit.getOnlinePlayers()) sendPkt(p, pkt);
        } catch (Exception ignored) {}
    }

    public void removeAll() {
        new ArrayList<>(entityIds.keySet()).forEach(this::remove);
    }

    // ── NMS classloader ───────────────────────────────────────────────────────

    private static Class<?> nms(String name) throws ClassNotFoundException {
        ClassLoader cl = Bukkit.getServer().getClass().getClassLoader();
        try { return Class.forName(name, true, cl); }
        catch (ClassNotFoundException e) { return Class.forName(name); }
    }

    // ── Build fake ServerPlayer ───────────────────────────────────────────────

    private Object buildServerPlayer(Location loc, UUID uuid, String playerName, PlayerProfile profile) throws Exception {
        Object mcServer    = Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
        Object serverLevel = loc.getWorld().getClass().getMethod("getHandle").invoke(loc.getWorld());

        // GameProfile + copy skin textures
        Class<?> gpClass = nms("com.mojang.authlib.GameProfile");
        Object gp = gpClass.getConstructor(UUID.class, String.class).newInstance(uuid, playerName);
        copySkin(gp, profile);

        // ClientInformation — in server.level in Paper 1.21.11
        Object ci = buildClientInfo();
        Class<?> ciClass = ci.getClass();

        // ServerPlayer(MinecraftServer, ServerLevel, GameProfile, ClientInformation)
        Class<?> spClass = nms("net.minecraft.server.level.ServerPlayer");
        Class<?> msClass = nms("net.minecraft.server.MinecraftServer");
        Class<?> slClass = nms("net.minecraft.server.level.ServerLevel");

        // Find the correct constructor
        Constructor<?> ctor = null;
        for (Constructor<?> c : spClass.getDeclaredConstructors()) {
            Class<?>[] p = c.getParameterTypes();
            if (p.length == 4
                && p[0].isAssignableFrom(mcServer.getClass())
                && p[1].isAssignableFrom(serverLevel.getClass())
                && p[2].getName().contains("GameProfile")
                && p[3].getName().contains("ClientInformation")) {
                ctor = c;
                break;
            }
        }
        if (ctor == null) throw new NoSuchMethodException("ServerPlayer ctor(MS,SL,GP,CI) not found");
        ctor.setAccessible(true);
        Object sp = ctor.newInstance(mcServer, serverLevel, gp, ci);

        // Set position (Y-0.35 so lying body is flush with ground in SWIMMING pose)
        setPos(sp, loc.getX(), loc.getY() - 0.35, loc.getZ(), loc.getYaw());

        // Try SLEEPING first (lying on side), then SWIMMING (horizontal on land = crawling)
        Class<?> poseClass = nms("net.minecraft.world.entity.Pose");
        Object sleeping = findEnum(poseClass, "SLEEPING");
        Object swimming = findEnum(poseClass, "SWIMMING");
        Object targetPose = sleeping != null ? sleeping : swimming;
        if (targetPose != null) callMethod(sp, "setPose", poseClass, targetPose);
        // Sprint flag enables crawling visual on land
        setSharedFlag(sp, 3, true);

        // Set sprinting flag (bit 3) to lock the swimming animation on land
        setSharedFlag(sp, 3, true);

        return sp;
    }

    private Object buildClientInfo() throws Exception {
        // Paper 1.21.11: net.minecraft.server.level.ClientInformation
        Class<?> cls;
        try { cls = nms("net.minecraft.server.level.ClientInformation"); }
        catch (ClassNotFoundException e) { cls = nms("net.minecraft.server.network.ClientInformation"); }

        // Try static createDefault()
        try { return cls.getMethod("createDefault").invoke(null); } catch (NoSuchMethodException ignored) {}

        // Use first constructor with sensible defaults
        Constructor<?> c = cls.getDeclaredConstructors()[0];
        c.setAccessible(true);
        Class<?>[] pt = c.getParameterTypes();
        Object[] args = new Object[pt.length];
        for (int i = 0; i < pt.length; i++) {
            if (pt[i] == String.class)       args[i] = "en_us";
            else if (pt[i] == int.class)     args[i] = 2;
            else if (pt[i] == boolean.class) args[i] = true;
            else if (pt[i].isEnum())         args[i] = pt[i].getEnumConstants()[0];
            else args[i] = null;
        }
        return c.newInstance(args);
    }

    // ── Send packets ──────────────────────────────────────────────────────────

    private void sendSpawnToAll(Object sp, Location loc, UUID uuid, Collection<? extends Player> viewers) throws Exception {
        // 1. Register in tablist so client loads the skin
        Object infoAdd = buildInfoPacket(sp);
        // 2. Spawn the entity
        Object addEnt  = buildAddEntityPacket(sp, loc, uuid);
        // 3. Push entity metadata (pose = SLEEPING/SWIMMING = lying flat)
        Object meta    = buildMetaPacket(sp);
        plugin.getLogger().warning("[FakePlayerSleeper] meta=" + (meta == null ? "NULL!" : meta.getClass().getSimpleName()));
        // 4. Schedule tablist removal
        Object infoRem = buildInfoRemovePacket(uuid);

        for (Player v : viewers) {
            sendPktLogged(v, infoAdd, "InfoAdd");
            sendPktLogged(v, addEnt, "AddEntity");
            if (meta != null) sendPktLogged(v, meta, "MetaPose");
        }
        // Send meta again after 2 ticks to ensure client has processed spawn first
        final Object metaFinal = meta;
        final Object rem = infoRem;
        final Collection<? extends Player> vws = new ArrayList<>(viewers);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            for (Player v : vws) {
                if (!v.isOnline()) continue;
                if (metaFinal != null) sendPktLogged(v, metaFinal, "MetaPose[2t]");
            }
        }, 2L);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            for (Player v : vws) if (v.isOnline()) try { sendPkt(v, rem); } catch (Exception ignored) {}
        }, 60L);
    }

    /**
     * ClientboundPlayerInfoUpdatePacket — built with manual Entry record.
     * Using Entry(UUID, GameProfile, listed, latency, gameMode, displayName, showHat, listOrder, chatSession)
     * avoids touching ServerPlayer.connection (which is null for our fake entity).
     */
    @SuppressWarnings({"unchecked","rawtypes"})
    private Object buildInfoPacket(Object sp) throws Exception {
        Class<?> pkCls  = nms("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket");
        Class<?> actCls = nms("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket$Action");
        Class<?> entCls = nms("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket$Entry");
        Class<?> gpCls  = nms("com.mojang.authlib.GameProfile");
        Class<?> gtCls  = nms("net.minecraft.world.level.GameType");

        Object addPlayer = findEnum(actCls, "ADD_PLAYER");
        java.util.EnumSet actions = java.util.EnumSet.of((Enum) addPlayer);

        // Get UUID + GameProfile from the fake ServerPlayer
        UUID uuid = (UUID) sp.getClass().getMethod("getUUID").invoke(sp);
        Object profile = gpCls.cast(sp.getClass().getMethod("getGameProfile").invoke(sp));
        Object gameModeCreative = findEnum(gtCls, "CREATIVE");

        // public Entry(UUID, GameProfile, boolean listed, int latency, GameType, Component, boolean showHat, int listOrder, RemoteChatSession.Data)
        Constructor<?> entryCtor = entCls.getDeclaredConstructor(
            UUID.class, gpCls,
            boolean.class, int.class, gtCls,
            nms("net.minecraft.network.chat.Component"),
            boolean.class, int.class,
            nms("net.minecraft.network.chat.RemoteChatSession$Data")
        );
        entryCtor.setAccessible(true);
        Object entry = entryCtor.newInstance(
            uuid, profile,
            false, 0, gameModeCreative,
            null,          // displayName = null
            true, 0,
            null           // chatSession = null
        );

        // ClientboundPlayerInfoUpdatePacket(EnumSet<Action>, Entry)
        for (Constructor<?> c : pkCls.getDeclaredConstructors()) {
            Class<?>[] p = c.getParameterTypes();
            if (p.length == 2 && p[0] == java.util.EnumSet.class && entCls.isAssignableFrom(p[1])) {
                c.setAccessible(true);
                return c.newInstance(actions, entry);
            }
        }
        // Fallback: (EnumSet<Action>, List<Entry>)
        for (Constructor<?> c : pkCls.getDeclaredConstructors()) {
            Class<?>[] p = c.getParameterTypes();
            if (p.length == 2 && p[0] == java.util.EnumSet.class && p[1] == List.class) {
                c.setAccessible(true);
                return c.newInstance(actions, List.of(entry));
            }
        }
        throw new NoSuchMethodException("PlayerInfoUpdatePacket(EnumSet, Entry) not found");
    }

    private Object buildInfoRemovePacket(UUID uuid) throws Exception {
        Class<?> cls = nms("net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket");
        // Find public constructor or make accessible
        for (Constructor<?> c : cls.getDeclaredConstructors()) {
            c.setAccessible(true);
            Class<?>[] p = c.getParameterTypes();
            if (p.length == 1 && (p[0] == List.class || p[0] == java.util.Collection.class)) {
                return c.newInstance(List.of(uuid));
            }
        }
        // Fallback: first constructor
        Constructor<?> c = cls.getDeclaredConstructors()[0];
        c.setAccessible(true);
        return c.newInstance(List.of(uuid));
    }

    /**
     * ClientboundAddEntityPacket(int id, UUID, double x, y, z, float xRot, yRot, EntityType, int data, Vec3, double yHeadRot)
     * Or the (Entity, int, BlockPos) shorthand.
     */
    private Object buildAddEntityPacket(Object sp, Location loc, UUID uuid) throws Exception {
        Class<?> cls     = nms("net.minecraft.network.protocol.game.ClientboundAddEntityPacket");
        Class<?> etClass = nms("net.minecraft.world.entity.EntityType");
        Object playerEt  = etClass.getField("PLAYER").get(null);
        Class<?> v3Class = nms("net.minecraft.world.phys.Vec3");
        Object zeroVec   = v3Class.getField("ZERO").get(null);

        int eid = getId(sp);
        float yaw = loc.getYaw();
        double x = loc.getX(), y = loc.getY() - 0.35, z = loc.getZ();

        // Always use full raw constructor — guarantees EntityType.PLAYER
        Constructor<?> c = cls.getDeclaredConstructor(
            int.class, UUID.class,
            double.class, double.class, double.class,
            float.class, float.class,
            etClass, int.class, v3Class, double.class);
        c.setAccessible(true);
        plugin.getLogger().warning("[FakePlayerSleeper] AddEntity eid=" + eid + " uuid=" + uuid + " pos=" + x + "," + y + "," + z);
        return c.newInstance(eid, uuid, x, y, z, 0.0f, yaw, playerEt, 0, zeroVec, (double) yaw);
    }

    /**
     * Build ClientboundSetEntityDataPacket with SWIMMING pose + sprinting flag.
     * Uses DataValue.create(EntityDataAccessor, value) — exact API from Paper 1.21.11 javap.
     */
    private Object buildMetaPacket(Object sp) {
        try {
            int eid = getId(sp);
            Class<?> entityClass = nms("net.minecraft.world.entity.Entity");
            Class<?> poseClass   = nms("net.minecraft.world.entity.Pose");
            Class<?> edsClass    = nms("net.minecraft.network.syncher.EntityDataSerializers");
            Class<?> dvClass     = nms("net.minecraft.network.syncher.SynchedEntityData$DataValue");

            // DataValue.create(EntityDataAccessor<T>, T value)
            Method createDv = dvClass.getMethod("create",
                nms("net.minecraft.network.syncher.EntityDataAccessor"), Object.class);

            // 1) Pose — try SLEEPING first (lying on side), fallback SWIMMING (crawling)
            Field dataPoseField = entityClass.getDeclaredField("DATA_POSE");
            dataPoseField.setAccessible(true);
            Object dataPoseAccessor = dataPoseField.get(null);
            Object targetPose = findEnum(poseClass, "SLEEPING");
            if (targetPose == null) targetPose = findEnum(poseClass, "SWIMMING");
            plugin.getLogger().warning("[FakePlayerSleeper] targetPose=" + targetPose + " accessor=" + dataPoseAccessor);
            Object poseDataValue = createDv.invoke(null, dataPoseAccessor, targetPose);

            // 2) Flags byte bit 3 = SPRINTING  →  Entity.DATA_SHARED_FLAGS_ID accessor
            Field flagsField = entityClass.getDeclaredField("DATA_SHARED_FLAGS_ID");
            flagsField.setAccessible(true);
            Object flagsAccessor = flagsField.get(null);
            // Get current flags from entity, OR with 0x08
            Object entityData = sp.getClass().getMethod("getEntityData").invoke(sp);
            byte currentFlags = 0;
            try {
                Object val = entityData.getClass()
                    .getMethod("get", nms("net.minecraft.network.syncher.EntityDataAccessor"))
                    .invoke(entityData, flagsAccessor);
                currentFlags = (byte) val;
            } catch (Exception ignored) {}
            Object flagsDataValue = createDv.invoke(null, flagsAccessor, (byte)(currentFlags | 0x08));

            List<Object> values = new ArrayList<>();
            values.add(flagsDataValue);
            values.add(poseDataValue);

            Class<?> pktClass = nms("net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket");
            Constructor<?> c = pktClass.getDeclaredConstructor(int.class, List.class);
            c.setAccessible(true);
            return c.newInstance(eid, values);
        } catch (Exception e) {
            plugin.getLogger().warning("[FakePlayerSleeper] buildMetaPacket failed: " + e.getMessage());
            return null;
        }
    }

    private Object buildRemovePacket(int eid) throws Exception {
        Class<?> cls = nms("net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket");
        return cls.getDeclaredConstructor(int[].class).newInstance((Object) new int[]{eid});
    }

    // ── Send a packet to a player via their connection ────────────────────────

    private void sendPktLogged(Player player, Object packet, String label) {
        if (packet == null || !player.isOnline()) return;
        try {
            sendPkt(player, packet);
        } catch (Exception e) {
            plugin.getLogger().warning("[FakePlayerSleeper] sendPkt[" + label + "] failed: " + e.getMessage());
        }
    }

    private void sendPkt(Player player, Object packet) {
        if (packet == null || !player.isOnline()) return;
        try {
            Object ep  = player.getClass().getMethod("getHandle").invoke(player);
            // Use public 'connection' field (ServerGamePacketListenerImpl)
            Object con = ep.getClass().getField("connection").get(ep);
            if (con == null) {
                plugin.getLogger().warning("[FakePlayerSleeper] connection field null for " + player.getName());
                return;
            }
            // send(Packet<?>) is in ServerCommonPacketListenerImpl
            Class<?> pktClass = nms("net.minecraft.network.protocol.Packet");
            con.getClass().getMethod("send", pktClass).invoke(con, packet);
        } catch (Exception e) {
            plugin.getLogger().warning("[FakePlayerSleeper] sendPkt error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    // ── Position / pose helpers ───────────────────────────────────────────────

    private void setPos(Object sp, double x, double y, double z, float yaw) {
        try { callMethod(sp, "moveTo", x, y, z, yaw, 0.0f); }
        catch (Exception e) {
            try { callMethod(sp, "setPos", x, y, z); } catch (Exception ignored) {}
        }
    }

    private void setSharedFlag(Object sp, int bit, boolean val) {
        for (Method m : allMethods(sp.getClass())) {
            if (m.getName().equals("setSharedFlag") && m.getParameterCount() == 2) {
                m.setAccessible(true);
                try { m.invoke(sp, bit, val); return; } catch (Exception ignored) {}
            }
        }
    }

    private int getId(Object sp) {
        try { return (int) sp.getClass().getMethod("getId").invoke(sp); } catch (Exception e) { return -1; }
    }

    // ── Skin ──────────────────────────────────────────────────────────────────

    private void copySkin(Object gp, PlayerProfile profile) {
        if (profile == null) return;
        try {
            Object craftGp = profile.getClass().getMethod("getGameProfile").invoke(profile);
            if (craftGp == null) return;
            Object src = craftGp.getClass().getMethod("getProperties").invoke(craftGp);
            Object dst = gp.getClass().getMethod("getProperties").invoke(gp);
            for (Object prop : (Iterable<?>) src.getClass().getMethod("values").invoke(src)) {
                String n = (String) prop.getClass().getMethod("name").invoke(prop);
                dst.getClass().getMethod("put", Object.class, Object.class).invoke(dst, n, prop);
            }
        } catch (Exception ignored) {}
    }

    // ── Reflection utilities ──────────────────────────────────────────────────

    private static Object findEnum(Class<?> cls, String name) {
        for (Object c : cls.getEnumConstants()) if (((Enum<?>) c).name().equals(name)) return c;
        return null;
    }

    private static void callMethod(Object obj, String name, Class<?> paramType, Object arg) throws Exception {
        for (Method m : allMethods(obj.getClass())) {
            if (m.getName().equals(name) && m.getParameterCount() == 1 &&
                m.getParameterTypes()[0].isAssignableFrom(paramType)) {
                m.setAccessible(true); m.invoke(obj, arg); return;
            }
        }
        throw new NoSuchMethodException(name + "(" + paramType.getSimpleName() + ")");
    }

    private static void callMethod(Object obj, String name, Object... args) {
        for (Method m : allMethods(obj.getClass())) {
            if (m.getName().equals(name) && m.getParameterCount() == args.length) {
                m.setAccessible(true);
                try { m.invoke(obj, args); return; } catch (Exception ignored) {}
            }
        }
    }

    private static Object getField(Object obj, String name) {
        for (Field f : allFields(obj.getClass())) {
            if (f.getName().equals(name)) {
                f.setAccessible(true);
                try { return f.get(obj); } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private static Field[] allFields(Class<?> cls) {
        List<Field> l = new ArrayList<>();
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) l.addAll(Arrays.asList(c.getDeclaredFields()));
        return l.toArray(new Field[0]);
    }

    private static Method[] allMethods(Class<?> cls) {
        List<Method> l = new ArrayList<>();
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) l.addAll(Arrays.asList(c.getDeclaredMethods()));
        return l.toArray(new Method[0]);
    }
}
