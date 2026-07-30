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

package com.dre.brewery;

import com.dre.brewery.utility.BUtil;
import com.dre.brewery.utility.BoundingBox;
import com.dre.brewery.utility.MinecraftVersion;
import com.google.common.collect.ImmutableMap;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.util.BlockVector;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * The Blocks that make up a Barrel in the World
 */
@Getter
@Setter
public abstract class BarrelBody {

    private static final Map<BlockVector, BarrelPart> UNTRANSFORMED_SMALL_BARREL_PART_MAP = Map.of(
            new BlockVector(1, 0, 0), BarrelPart.BOTTOM_RIGHT,
            new BlockVector(1, 0, 1), BarrelPart.BOTTOM_LEFT,
            new BlockVector(1, 1, 0), BarrelPart.TOP_RIGHT,
            new BlockVector(1, 1, 1), BarrelPart.TOP_LEFT,
            new BlockVector(2, 0, 0), BarrelPart.BOTTOM_RIGHT,
            new BlockVector(2, 0, 1), BarrelPart.BOTTOM_LEFT,
            new BlockVector(2, 1, 0), BarrelPart.TOP_RIGHT,
            new BlockVector(2, 1, 1), BarrelPart.TOP_LEFT
    );
    private static final Map<BlockVector, BarrelPart> UNTRANSFORMED_LARGE_BARREL_PART_MAP = new ImmutableMap.Builder<BlockVector, BarrelPart>()
            .put(new BlockVector(1, 0, -1), BarrelPart.BOTTOM_RIGHT)
            .put(new BlockVector(1, 0, 1), BarrelPart.BOTTOM_LEFT)
            .put(new BlockVector(1, 0, 0), BarrelPart.BLOCK)
            .put(new BlockVector(1, 1, 1), BarrelPart.BLOCK)
            .put(new BlockVector(1, 1, -1), BarrelPart.BLOCK)
            .put(new BlockVector(1, 2, -1), BarrelPart.TOP_RIGHT)
            .put(new BlockVector(1, 2, 1), BarrelPart.TOP_LEFT)
            .put(new BlockVector(1, 2, 0), BarrelPart.BLOCK)
            .put(new BlockVector(1, 1, 0), BarrelPart.BLOCK)
            .put(new BlockVector(2, 0, -1), BarrelPart.BOTTOM_RIGHT)
            .put(new BlockVector(2, 0, 1), BarrelPart.BOTTOM_LEFT)
            .put(new BlockVector(2, 0, 0), BarrelPart.BLOCK)
            .put(new BlockVector(2, 1, 1), BarrelPart.BLOCK)
            .put(new BlockVector(2, 1, -1), BarrelPart.BLOCK)
            .put(new BlockVector(2, 2, -1), BarrelPart.TOP_RIGHT)
            .put(new BlockVector(2, 2, 1), BarrelPart.TOP_LEFT)
            .put(new BlockVector(2, 2, 0), BarrelPart.BLOCK)
            .put(new BlockVector(3, 0, -1), BarrelPart.BOTTOM_RIGHT)
            .put(new BlockVector(3, 0, 1), BarrelPart.BOTTOM_LEFT)
            .put(new BlockVector(3, 0, 0), BarrelPart.BLOCK)
            .put(new BlockVector(3, 1, 1), BarrelPart.BLOCK)
            .put(new BlockVector(3, 1, -1), BarrelPart.BLOCK)
            .put(new BlockVector(3, 2, -1), BarrelPart.TOP_RIGHT)
            .put(new BlockVector(3, 2, 1), BarrelPart.TOP_LEFT)
            .put(new BlockVector(3, 2, 0), BarrelPart.BLOCK)
            .put(new BlockVector(4, 0, -1), BarrelPart.BOTTOM_RIGHT)
            .put(new BlockVector(4, 0, 1), BarrelPart.BOTTOM_LEFT)
            .put(new BlockVector(4, 0, 0), BarrelPart.BLOCK)
            .put(new BlockVector(4, 1, 1), BarrelPart.BLOCK)
            .put(new BlockVector(4, 1, -1), BarrelPart.BLOCK)
            .put(new BlockVector(4, 2, -1), BarrelPart.TOP_RIGHT)
            .put(new BlockVector(4, 2, 1), BarrelPart.TOP_LEFT)
            .put(new BlockVector(4, 2, 0), BarrelPart.BLOCK)
            .put(new BlockVector(4, 1, 0), BarrelPart.BLOCK)
            .build();
    protected final BoundingBox bounds;
    protected Block spigot;
    protected byte signoffset;

    public BarrelBody(final Block spigot, final byte signoffset) {
        this.spigot = spigot;
        this.signoffset = signoffset;
        this.bounds = new BoundingBox(0, 0, 0, 0, 0, 0);

        if (MinecraftVersion.isFolia()) { // Issues#70
            BreweryPlugin.getScheduler().runAtLocationLater(spigot.getLocation(), () -> {
                final var broken = this.getBrokenBlock(true);
                if (broken != null) {
                    this.remove(broken, null, true);
                }
            }, 0);
        }
    }

    /**
     * Loading from file
     */
    public BarrelBody(final Block spigot, final byte signoffset, final BoundingBox bounds) {
        this.spigot = spigot;
        this.signoffset = signoffset;
        this.bounds = bounds;
        if (this.bounds == null || this.bounds.isBad()) {
            // Never load chunks on startup, therefore always scheduled
            BreweryPlugin.getScheduler().runAtLocationLater(spigot.getLocation(), this::regenerateBounds, 0);
        }
    }

    /**
     * direction of the barrel from the spigot
     */
    public static @Nullable BarrelFacing getDirection(final Block spigot) {
        BarrelFacing direction = null;// 1=x+ 2=x- 3=z+ 4=z-
        var type = spigot.getRelative(0, 0, 1).getType();
        if (BarrelAsset.isBarrelAsset(BarrelAsset.PLANKS, type) || BarrelAsset.isBarrelAsset(BarrelAsset.STAIRS, type)) {
            direction = BarrelFacing.SOUTH;
        }
        type = spigot.getRelative(0, 0, -1).getType();
        if (BarrelAsset.isBarrelAsset(BarrelAsset.PLANKS, type) || BarrelAsset.isBarrelAsset(BarrelAsset.STAIRS, type)) {
            if (direction == null) {
                direction = BarrelFacing.NORTH;
            } else {
                return null;
            }
        }
        type = spigot.getRelative(1, 0, 0).getType();
        if (BarrelAsset.isBarrelAsset(BarrelAsset.PLANKS, type) || BarrelAsset.isBarrelAsset(BarrelAsset.STAIRS, type)) {
            if (direction == null) {
                direction = BarrelFacing.EAST;
            } else {
                return null;
            }
        }
        type = spigot.getRelative(-1, 0, 0).getType();
        if (BarrelAsset.isBarrelAsset(BarrelAsset.PLANKS, type) || BarrelAsset.isBarrelAsset(BarrelAsset.STAIRS, type)) {
            if (direction == null) {
                direction = BarrelFacing.WEST;
            } else {
                return null;
            }
        }
        return direction;
    }

    /**
     * returns the fence above/below a block, itself if there is none
     */
    public static Block getSpigotOfSign(final Block block) {

        var y = -2;
        while (y <= 1) {
            // Fence and Netherfence
            final var relative = block.getRelative(0, y, 0);
            if (BarrelAsset.isBarrelAsset(BarrelAsset.FENCE, relative.getType())) {
                return relative;
            }
            y++;
        }
        return block;
    }

    /**
     * If the Sign of a Large Barrel gets destroyed, set signOffset to 0
     */
    public final void destroySign() {
        this.signoffset = 0;
    }

    /**
     * woodtype of the block the spigot is attached to
     */
    public final BarrelWoodType getWood() {
        final var direction = getDirection(this.spigot);
        if (direction == null) {
            return BarrelWoodType.ANY;
        }
        final var wood = this.spigot.getRelative(direction.getFace());
        return BarrelWoodType.fromMaterial(wood.getType());
    }

    /**
     * Returns true if this Block is part of this Barrel
     *
     * @param block the block to check
     * @return true if the given block is part of this Barrel
     */
    public final boolean hasBlock(final Block block) {
        if (block != null) {
            if (this.spigot.equals(block)) {
                return true;
            }
            if (this.spigot.getWorld().equals(block.getWorld())) {
                return this.bounds != null && this.bounds.contains(block.getX(), block.getY(), block.getZ());
            }
        }
        return false;
    }

    /**
     * Returns true if the Offset of the clicked Sign matches the Barrel.
     * <p>This prevents adding another sign to the barrel and clicking that.
     */
    public final boolean isSignOfBarrel(final byte offset) {
        return offset == 0 || this.signoffset == 0 || this.signoffset == offset;
    }

    /**
     * returns the Sign of a large barrel, the spigot if there is none
     */
    public Block getSignOfSpigot() {
        if (this.signoffset != 0) {
            if (BarrelAsset.isBarrelAsset(BarrelAsset.SIGN, this.spigot.getType())) {
                return this.spigot;
            }

            final var relative = this.spigot.getRelative(0, this.signoffset, 0);
            if (BarrelAsset.isBarrelAsset(BarrelAsset.SIGN, relative.getType())) {
                return relative;
            } else {
                this.signoffset = 0;
            }
        }
        return this.spigot;
    }

    public abstract void remove(@Nullable Block broken, @Nullable Player breaker, boolean dropItems);

    /**
     * Regenerate the Barrel Bounds.
     *
     * @return true if successful, false if Barrel was broken and should be removed.
     */
    public abstract boolean regenerateBounds();

    /**
     * returns null if Barrel is correctly placed; the block that is missing when not.
     * <p>the barrel needs to be formed correctly
     *
     * @param force to also check even if chunk is not loaded
     */
    public final Block getBrokenBlock(final boolean force) {
        if (force || BUtil.isChunkLoaded(this.spigot)) {
            //spigot = getSpigotOfSign(spigot);
            if (BarrelAsset.isBarrelAsset(BarrelAsset.SIGN, this.spigot.getType())) {
                return this.checkSBarrel();
            } else {
                return this.checkLBarrel();
            }
        }
        return null;
    }

    public final Block checkSBarrel() {
        final var direction = getDirection(this.spigot);// 1=x+ 2=x- 3=z+ 4=z-
        if (direction == null) {
            return this.spigot;
        }
        final var orthogonal = direction.rotate90degrees();
        final var dx1 = direction.getDx();
        final var dx2 = orthogonal.getDx();
        final var dz1 = direction.getDz();
        final var dz2 = orthogonal.getDz();

        final var brokenBlock = this.validateStructure(direction, dx1, dx2, dz1, dz2, UNTRANSFORMED_SMALL_BARREL_PART_MAP);
        if (brokenBlock != null) {
            return brokenBlock;
        }

        final var spigotPos = this.spigot.getLocation().toVector().toBlockVector();
        final var minBarrel = (BlockVector) new BlockVector(dx1, 0, dz1).add(spigotPos);
        final var maxBarrel = (BlockVector) new BlockVector(2 * dx1 + dx2, 1, 2 * dz1 + dz2).add(spigotPos);
        this.bounds.resize(minBarrel.getBlockX(), minBarrel.getBlockY(), minBarrel.getBlockZ(), maxBarrel.getBlockX(), maxBarrel.getBlockY(), maxBarrel.getBlockZ());
        return null;
    }

    public final Block checkLBarrel() {
        final var direction = getDirection(this.spigot);
        if (direction == null) {
            return this.spigot;
        }
        final var orthogonal = direction.rotate90degrees();
        final var dx1 = direction.getDx();
        final var dx2 = orthogonal.getDx();
        final var dz1 = direction.getDz();
        final var dz2 = orthogonal.getDz();

        final var brokenBlock = this.validateStructure(direction, dx1, dx2, dz1, dz2, UNTRANSFORMED_LARGE_BARREL_PART_MAP);
        if (brokenBlock != null) {
            return brokenBlock;
        }
        final var spigotPos = this.spigot.getLocation().toVector().toBlockVector();
        final var minBarrel = (BlockVector) new BlockVector(dx1 - dx2, 0, dz1 - dz2).add(spigotPos);
        final var maxBarrel = (BlockVector) new BlockVector(4 * dx1 + dx2, 2, 4 * dz1 + dz2).add(spigotPos);
        this.bounds.resize(minBarrel.getBlockX(), minBarrel.getBlockY(), minBarrel.getBlockZ(), maxBarrel.getBlockX(), maxBarrel.getBlockY(), maxBarrel.getBlockZ());
        return null;
    }

    @Nullable
    private Block validateStructure(final BarrelFacing direction, final int dx1, final int dx2, final int dz1, final int dz2, final Map<BlockVector, BarrelPart> untransformedBarrelPartMap) {
        final var woodType = this.getWood();
        for (final var entry : untransformedBarrelPartMap.entrySet()) {
            final var relativeX = dx1 * entry.getKey().getBlockX() + dx2 * entry.getKey().getBlockZ();
            final var relativeZ = dz1 * entry.getKey().getBlockX() + dz2 * entry.getKey().getBlockZ();
            final var relativeY = entry.getKey().getBlockY();
            final var block = this.spigot.getRelative(relativeX, relativeY, relativeZ);
            final var blockData = block.getBlockData();
            if (!entry.getValue().matches(woodType, blockData, direction)) {
                return block;
            }
        }
        return null;
    }
}
