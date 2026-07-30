/*
 * BreweryX Bukkit-Plugin for an alternate brewing process
 * Copyright (C) 2024 The Brewery Team
 *
 * This file is part of BreweryX.
 *
 * BreweryX is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BreweryX is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BreweryX. If not, see <http://www.gnu.org/licenses/gpl-3.0.html>.
 */

package com.dre.brewery.utility;

import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Compatibility wrapper around CustomModelData access for different 1.21.x APIs.
 */
public final class ItemMetaCompat {

    private static final Method HAS_COMPONENT_METHOD = findMethod(ItemMeta.class, "hasCustomModelDataComponent");
    private static final Method GET_COMPONENT_METHOD = findMethod(ItemMeta.class, "getCustomModelDataComponent");
    private static final Method HAS_LEGACY_METHOD = findMethod(ItemMeta.class, "hasCustomModelData");
    private static final Method GET_LEGACY_METHOD = findMethod(ItemMeta.class, "getCustomModelData");

    private ItemMetaCompat() {
    }

    public static boolean hasCustomModelData(@NotNull final ItemMeta meta) {
        if (HAS_COMPONENT_METHOD != null && GET_COMPONENT_METHOD != null) {
            return getCustomModelData(meta) != null;
        }
        if (HAS_LEGACY_METHOD != null) {
            try {
                return (boolean) HAS_LEGACY_METHOD.invoke(meta);
            } catch (final IllegalAccessException | InvocationTargetException ignored) {
                return false;
            }
        }
        return false;
    }

    public static @Nullable Integer getCustomModelData(@NotNull final ItemMeta meta) {
        if (HAS_COMPONENT_METHOD != null && GET_COMPONENT_METHOD != null) {
            final var componentValue = readComponentValue(meta);
            if (componentValue != null) {
                return componentValue;
            }
        }
        if (HAS_LEGACY_METHOD != null && GET_LEGACY_METHOD != null) {
            try {
                if ((boolean) HAS_LEGACY_METHOD.invoke(meta)) {
                    return (Integer) GET_LEGACY_METHOD.invoke(meta);
                }
            } catch (final IllegalAccessException | InvocationTargetException ignored) {
                return null;
            }
        }
        return null;
    }

    private static @Nullable Integer readComponentValue(@NotNull final ItemMeta meta) {
        try {
            final var component = GET_COMPONENT_METHOD.invoke(meta);
            if (component == null) {
                return null;
            }

            final var getFloats = findMethod(component.getClass(), "getFloats");
            if (getFloats == null) {
                return null;
            }

            final var rawFloats = getFloats.invoke(component);
            if (!(rawFloats instanceof final List<?> floats) || floats.isEmpty()) {
                return null;
            }

            final var first = floats.getFirst();
            if (first instanceof final Number number) {
                return number.intValue();
            }
        } catch (final IllegalAccessException | InvocationTargetException ignored) {
            return null;
        }
        return null;
    }

    private static @Nullable Method findMethod(final Class<?> type, final String name) {
        try {
            return type.getMethod(name);
        } catch (final NoSuchMethodException ignored) {
            return null;
        }
    }
}
