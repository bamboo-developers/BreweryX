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

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.Lightable;
import org.jetbrains.annotations.Nullable;

public final class MaterialUtil {

    // Cauldron fill levels
    public static final byte EMPTY = 0;
    public static final byte SOME = 1;
    public static final byte FULL = 2;

    private MaterialUtil() {
    }

    @Nullable
    public static Material getMaterialSafely(final String name) {
        if (name == null) {
            return null;
        }
        return Material.matchMaterial(name);
    }

    public static boolean isCauldronHeatSource(final Block block) {
        final var type = block.getType();
        return type == Material.FIRE || type == Material.SOUL_FIRE || type == Material.MAGMA_BLOCK
                || type == Material.LAVA || litCampfire(block);
    }

    public static boolean litCampfire(final Block block) {
        if (block.getType() == Material.CAMPFIRE || block.getType() == Material.SOUL_CAMPFIRE) {
            return block.getBlockData() instanceof final Lightable lightable && lightable.isLit();
        }
        return false;
    }

    public static boolean isBottle(final Material type) {
        return type == Material.POTION
                || type == Material.LINGERING_POTION
                || type == Material.SPLASH_POTION
                || type == Material.EXPERIENCE_BOTTLE
                || type == Material.DRAGON_BREATH
                || type == Material.HONEY_BOTTLE;
    }

    /**
     * Test if this Material Type is a Cauldron filled with water
     */
    public static boolean isWaterCauldron(final Material type) {
        return type == Material.WATER_CAULDRON;
    }

    /**
     * Get The Fill Level of a Cauldron Block, 0 = empty, 1 = something in, 2 = full
     *
     * @return 0 = empty, 1 = something in, 2 = full
     */
    public static byte getFillLevel(final Block block) {
        if (!isWaterCauldron(block.getType())) {
            return EMPTY;
        }

        final var cauldron = (Levelled) block.getBlockData();
        if (cauldron.getLevel() == 0) {
            return EMPTY;
        } else if (cauldron.getLevel() == cauldron.getMaximumLevel()) {
            return FULL;
        }
        return SOME;
    }
}
