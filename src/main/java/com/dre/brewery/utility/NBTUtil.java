package com.dre.brewery.utility;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class NBTUtil {

    private NBTUtil() {
    }

    public static void writeBytesItem(final byte[] bytes, final ItemMeta meta, final NamespacedKey key) {
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE_ARRAY, bytes);
    }

    public static byte[] readBytesItem(final ItemMeta meta, final NamespacedKey key) {
        return meta.getPersistentDataContainer().get(key, PersistentDataType.BYTE_ARRAY);
    }

    public static boolean hasBytesItem(final ItemMeta meta, final NamespacedKey key) {
        return meta.getPersistentDataContainer().has(key, PersistentDataType.BYTE_ARRAY);
    }
}
