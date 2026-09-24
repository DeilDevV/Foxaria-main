package com.foxaria.core.util;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.Base64;

public final class ItemStackSerializer {

    private ItemStackSerializer() {
    }

    public static String serialize(ItemStack itemStack) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            try (BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {
                dataOutput.writeObject(itemStack);
            }
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize item stack", exception);
        }
    }

    public static ItemStack deserialize(String base64) {
        try {
            byte[] data = Base64.getDecoder().decode(base64);
            try (BukkitObjectInputStream dataInput = new BukkitObjectInputStream(new ByteArrayInputStream(data))) {
                return (ItemStack) dataInput.readObject();
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to deserialize item stack", exception);
        }
    }

    public static String fingerprint(ItemStack itemStack) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(serialize(itemStack.asOne()).getBytes());
            StringBuilder builder = new StringBuilder();
            for (byte part : hash) {
                builder.append(String.format("%02x", part));
            }
            return builder.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to fingerprint item stack", exception);
        }
    }
}
