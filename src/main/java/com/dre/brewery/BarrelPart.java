/*
 * BreweryX Bukkit-Plugin for an alternate brewing process
 * Copyright (C) 2024-2025 The Brewery Team
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

package com.dre.brewery;

import lombok.AllArgsConstructor;
import org.bukkit.Material;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.jetbrains.annotations.NotNull;

@AllArgsConstructor
public enum BarrelPart {
    BOTTOM_RIGHT(BarrelAsset.STAIRS, Bisected.Half.TOP, BarrelFacing.EAST),
    BOTTOM_LEFT(BarrelAsset.STAIRS, Bisected.Half.TOP, BarrelFacing.WEST),
    TOP_RIGHT(BarrelAsset.STAIRS, Bisected.Half.BOTTOM, BarrelFacing.EAST),
    TOP_LEFT(BarrelAsset.STAIRS, Bisected.Half.BOTTOM, BarrelFacing.WEST),
    BLOCK(BarrelAsset.PLANKS);

    private final BarrelAsset barrelAsset;
    private final Bisected.Half half = null;
    private final BarrelFacing untransformedFacing = null;

    BarrelPart(final BarrelAsset barrelAsset) {
        this.barrelAsset = barrelAsset;
    }

    public boolean matches(final BarrelWoodType type, @NotNull final BlockData actual, final BarrelFacing facing) {
        final var actualType = actual.getMaterial();
        if (!BarrelAsset.isBarrelAsset(this.barrelAsset, actualType) || BarrelWoodType.fromMaterial(actualType) != type) {
            return false;
        }
        if (this.half != null && (!(actual instanceof final Bisected bisected) || bisected.getHalf() != this.half)) {
            return false;
        }
        if (this.untransformedFacing == null) {
            return true;
        }
        if (!(actual instanceof final Directional directional)) {
            return false;
        }
        return switch (facing) {
            case SOUTH -> this.untransformedFacing.getFace().equals(directional.getFacing());
            case EAST -> this.untransformedFacing.rotate90degrees().getFace().equals(directional.getFacing());
            case NORTH -> this.untransformedFacing.rotate180degrees().getFace().equals(directional.getFacing());
            case WEST -> this.untransformedFacing.rotate270degrees().getFace().equals(directional.getFacing());
        };
    }
}
