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

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.block.Block;

import java.util.List;

@Getter
@Setter
public final class BoundingBox {

    private BlockPos min;
    private BlockPos max;

    public BoundingBox(final BlockPos a, final BlockPos b) {
        this(a.x, a.y, a.z, b.x, b.y, b.z);
    }

    public BoundingBox(final int x1, final int y1, final int z1, final int x2, final int y2, final int z2) {
        final int minX;
        final int minY;
        final int minZ;
        final int maxX;
        final int maxY;
        final int maxZ;
        minX = Math.min(x1, x2);
        minY = Math.min(y1, y2);
        minZ = Math.min(z1, z2);
        this.min = new BlockPos(minX, minY, minZ);
        maxX = Math.max(x2, x1);
        maxY = Math.max(y2, y1);
        maxZ = Math.max(z2, z1);
        this.max = new BlockPos(maxX, maxY, maxZ);
    }

    public static BoundingBox fromPoints(final int[] locations) {
        if (locations.length % 3 != 0) throw new IllegalArgumentException("Locations has to be pairs of three");

        final var length = locations.length - 2;

        int minx = Integer.MAX_VALUE;
        int miny = Integer.MAX_VALUE;
        int minz = Integer.MAX_VALUE;
        int maxx = Integer.MIN_VALUE;
        int maxy = Integer.MIN_VALUE;
        int maxz = Integer.MIN_VALUE;
        for (var i = 0; i < length; i += 3) {
            minx = Math.min(locations[i], minx);
            miny = Math.min(locations[i + 1], miny);
            minz = Math.min(locations[i + 2], minz);
            maxx = Math.max(locations[i], maxx);
            maxy = Math.max(locations[i + 1], maxy);
            maxz = Math.max(locations[i + 2], maxz);
        }
        return new BoundingBox(minx, miny, minz, maxx, maxy, maxz);
    }

    public static BoundingBox fromPoints(final List<Integer> locations) {
        if (locations.size() % 3 != 0) throw new IllegalArgumentException("Locations has to be pairs of three");

        final var length = locations.size() - 2;

        int minx = Integer.MAX_VALUE;
        int miny = Integer.MAX_VALUE;
        int minz = Integer.MAX_VALUE;
        int maxx = Integer.MIN_VALUE;
        int maxy = Integer.MIN_VALUE;
        int maxz = Integer.MIN_VALUE;
        for (var i = 0; i < length; i += 3) {
            minx = Math.min(locations.get(i), minx);
            miny = Math.min(locations.get(i + 1), miny);
            minz = Math.min(locations.get(i + 2), minz);
            maxx = Math.max(locations.get(i), maxx);
            maxy = Math.max(locations.get(i + 1), maxy);
            maxz = Math.max(locations.get(i + 2), maxz);
        }
        return new BoundingBox(minx, miny, minz, maxx, maxy, maxz);
    }

    public final boolean contains(final int x, final int y, final int z) {
        return (x >= this.min.x && x <= this.max.x) && (y >= this.min.y && y <= this.max.y) && (z >= this.min.z && z <= this.max.z);
    }

    public boolean contains(final Location loc) {
        return this.contains(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    public final boolean contains(final Block block) {
        return this.contains(block.getX(), block.getY(), block.getZ());
    }

    public final boolean intersects(final BoundingBox other) {
        if (other == null) {
            return false;
        }

        return this.max.x >= other.min.x && this.min.x <= other.max.x
                && this.max.y >= other.min.y && this.min.y <= other.max.y
                && this.max.z >= other.min.z && this.min.z <= other.max.z;
    }

    public final long volume() {
        return ((long) (this.max.z - this.min.z + 1)) * ((long) (this.max.y - this.min.y + 1)) * ((long) (this.max.z - this.min.z + 1));
    }

    // Quick check if the bounds are valid or seem corrupt
    public final boolean isBad() {
        final var volume = this.volume();
        return volume != 8 && volume != 36;
    }

    public final void resize(final int x1, final int y1, final int z1, final int x2, final int y2, final int z2) {
        final var box = new BoundingBox(x1, y1, z1, x2, y2, z2);
        this.min = box.min;
        this.max = box.max;
    }

    public final String serialize() {
        return this.min.x + "," + this.min.y + "," + this.min.z + "," + this.max.x + "," + this.max.y + "," + this.max.z;
    }

    public final List<Integer> serializeToIntList() {
        return List.of(this.min.x, this.min.y, this.min.z, this.max.x, this.max.y, this.max.z);
    }

    public record BlockPos(int x, int y, int z) {
    }
}
