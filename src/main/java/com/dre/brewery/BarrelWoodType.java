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
import com.dre.brewery.utility.MaterialUtil;
import lombok.Getter;
import org.bukkit.Material;
import org.jetbrains.annotations.Nullable;

import java.util.*;


@Getter
public enum BarrelWoodType {

    ANY("Any", 0, true),
    BIRCH("Birch", 1),
    OAK("Oak", 2),
    JUNGLE("Jungle", 3),
    SPRUCE("Spruce", 4),
    ACACIA("Acacia", 5),
    DARK_OAK("Dark Oak", 6),
    CRIMSON("Crimson", 7),
    WARPED("Warped", 8),
    MANGROVE("Mangrove", 9),
    CHERRY("Cherry", 10),
    BAMBOO("Bamboo", 11),
    CUT_COPPER("Cut Copper", 12,
            Material.CUT_COPPER, Material.CUT_COPPER_STAIRS,
            Material.WAXED_CUT_COPPER, Material.WAXED_CUT_COPPER_STAIRS,
            Material.EXPOSED_CUT_COPPER, Material.EXPOSED_CUT_COPPER_STAIRS,
            Material.WAXED_EXPOSED_CUT_COPPER, Material.WAXED_EXPOSED_CUT_COPPER_STAIRS,
            Material.WEATHERED_CUT_COPPER, Material.WEATHERED_CUT_COPPER_STAIRS,
            Material.WAXED_WEATHERED_CUT_COPPER, Material.WAXED_WEATHERED_CUT_COPPER_STAIRS,
            Material.OXIDIZED_CUT_COPPER, Material.OXIDIZED_CUT_COPPER_STAIRS,
            Material.WAXED_OXIDIZED_CUT_COPPER, Material.WAXED_OXIDIZED_CUT_COPPER_STAIRS
    ),
    PALE_OAK("Pale Oak", 13),
    // If you're adding more wood types, add them above 'NONE'
    // Remember to also add the wood type to the Group enum
    NONE("None", -1, true);


    public static final int MAX_DISTANCE = Group.MAX_DISTANCE + 1;
    public static final int COPPER_DISTANCE = Group.SPECIAL_DISTANCE + 1;

    public static final List<String> TAB_COMPLETIONS = Arrays.stream(values())
            .filter(BarrelWoodType::isSpecific)
            .map(BarrelWoodType::getFormattedName)
            .map(s -> s.replace(' ', '_'))
            .toList();

    // Precomputed set of every Cut Copper related Material, for cheap membership checks on hot paths (block change events)
    public static final Set<Material> CUT_COPPER_MATERIALS = Collections.unmodifiableSet(BarrelAsset.getMaterialsOf(CUT_COPPER));

    private final String formattedName;
    private final int index;

    BarrelWoodType(final String formattedName, final int index) {
        this(formattedName, index, false);
    }

    BarrelWoodType(final String formattedName, final int index, final boolean exclude) {
        this.formattedName = formattedName;
        this.index = index;
        if (!exclude) {
            BarrelAsset.addBarrelAsset(this, BarrelAsset.PLANKS, this.getStandardBarrelAssetMaterial(BarrelAsset.PLANKS));
            BarrelAsset.addBarrelAsset(this, BarrelAsset.STAIRS, this.getStandardBarrelAssetMaterial(BarrelAsset.STAIRS));
            BarrelAsset.addBarrelAsset(this, BarrelAsset.SIGN, this.getStandardBarrelAssetMaterial(BarrelAsset.SIGN));
            BarrelAsset.addBarrelAsset(this, BarrelAsset.FENCE, this.getStandardBarrelAssetMaterial(BarrelAsset.FENCE));
        }
    }

    BarrelWoodType(final String formattedName, final int index, final Material planks, final Material stairs, final Material sign, final Material fence) {
        this.formattedName = formattedName;
        this.index = index;

        BarrelAsset.addBarrelAsset(this, BarrelAsset.PLANKS, planks);
        BarrelAsset.addBarrelAsset(this, BarrelAsset.STAIRS, stairs);
        BarrelAsset.addBarrelAsset(this, BarrelAsset.SIGN, sign);
        BarrelAsset.addBarrelAsset(this, BarrelAsset.FENCE, fence);
    }

    BarrelWoodType(final String formattedName, final int index, final Material planks, final Material stairs) {
        this.formattedName = formattedName;
        this.index = index;

        BarrelAsset.addBarrelAsset(this, BarrelAsset.PLANKS, planks);
        BarrelAsset.addBarrelAsset(this, BarrelAsset.STAIRS, stairs);
    }

    BarrelWoodType(final String formattedName, final int index, final Material... materials) {
        this.formattedName = formattedName;
        this.index = index;

        if (materials == null) return;
        for (final var material : materials) {
            final var isStairs = material.name().contains("STAIRS");
            BarrelAsset.addBarrelAsset(this, isStairs ? BarrelAsset.STAIRS : BarrelAsset.PLANKS, material);
        }
    }

    public static BarrelWoodType fromName(final String name) {
        for (final var type : values()) {
            if (type.name().equalsIgnoreCase(name) || type.formattedName.equalsIgnoreCase(name)) {
                return type;
            }
        }
        return ANY;
    }

    public static BarrelWoodType fromIndex(final int index) {
        for (final var type : values()) {
            if (type.index == index) {
                return type;
            }
        }
        return ANY;
    }

    private static final Map<Material, BarrelWoodType> MATERIAL_TO_TYPE = new EnumMap<>(Material.class);

    static {
        for (final var material : Material.values()) {
            MATERIAL_TO_TYPE.put(material, computeFromMaterial(material));
        }
    }

    // Original, order-dependent lookup logic. Used once per Material to precompute MATERIAL_TO_TYPE.
    private static BarrelWoodType computeFromMaterial(final Material material) {
        for (final var type : values()) {
            if (material.name().toUpperCase().startsWith(type.name().toUpperCase())) {
                return type;
            }
            if (
                    type == CUT_COPPER && !material.name().toUpperCase().contains("SLAB")
                            && material.name().toUpperCase().contains(type.name().toUpperCase())
                // ^ Needed because of the waxed/exposed/weathered/oxidized prefix
            ) {
                return type;
            }
        }
        return ANY;
    }

    public static BarrelWoodType fromMaterial(final Material material) {
        return MATERIAL_TO_TYPE.getOrDefault(material, ANY);
    }

    public static BarrelWoodType fromAny(final Object intOrString) {
        if (intOrString instanceof final Integer integer) {
            return fromIndex(integer);
        } else if (intOrString instanceof final String s) {
            return fromName(s);
        } else if (intOrString instanceof Float || intOrString instanceof Double) {
            return fromIndex((int) (float) intOrString);
        } else if (intOrString instanceof final Material m) {
            return fromMaterial(m);
        }
        return ANY;
    }

    public static List<BarrelWoodType> listFromAny(final Object intOrStringOrList) {
        if (intOrStringOrList instanceof final List<?> list) {
            return list.stream()
                    .map(BarrelWoodType::fromAny)
                    .toList();
        }
        return Collections.singletonList(fromAny(intOrStringOrList));
    }

    /**
     * Parses a string to determine the corresponding BarrelWoodType.
     *
     * @param string The string to parse, which can be an integer index,
     *               a material name, or a formatted name.
     * @return The matching BarrelWoodType based on the provided string.
     * Returns BarrelWoodType.ANY if no match is found.
     */
    public static BarrelWoodType parse(final String string) {
        final var indexOpt = BUtil.parseInt(string);
        if (indexOpt.isPresent()) {
            final var index = indexOpt.getAsInt();
            for (final var type : values()) {
                if (type.index == index) {
                    return type;
                }
            }
        }
        final var material = MaterialUtil.getMaterialSafely(string);
        if (material != null) {
            return fromMaterial(material);
        }
        return fromName(string);
    }

    @Nullable
    private Material[] getStandardBarrelAssetMaterial(final BarrelAsset assetType) {
        try {
            // TODO: I dont like this... Change it later
            if (assetType == BarrelAsset.SIGN) {
                return new Material[]{Material.valueOf(this.name() + "_" + assetType.name()), Material.valueOf(this.name() + "_WALL_SIGN")};
            }
            return new Material[]{Material.valueOf(this.name() + "_" + assetType.name())};
        } catch (final IllegalArgumentException e) {
            // Just assume they're running some older version.
            return null;
        }
    }

    public boolean isSpecific() {
        return this != ANY && this != NONE;
    }

    /**
     * Computes the distance 0 to {@link #MAX_DISTANCE} between two barrel types.
     * Similar barrel types, such as oak and dark oak, have a distance of 1.
     * Copper has a distance of {@link #COPPER_DISTANCE} with every other barrel type.
     *
     * @param other the other barrel type
     * @return The distance, or -1 if this or the other barrel type is ANY or NONE
     */
    public int getDistance(final BarrelWoodType other) {
        final var group = Group.of(this);
        final var otherGroup = Group.of(other);
        if (group == null || otherGroup == null) {
            return -1;
        }

        // Checking if barrel types are the same *after* checking for ANY or NONE
        if (this == other) {
            return 0;
        }
        // Group distance is 0-3, add 1 to get 1-4 where distance 1 is two barrel types in the same group
        return group.getDistance(otherGroup) + 1;
    }

    /**
     * Advances this barrel type towards another barrel type by a specified number of steps.
     * Each step moves the barrel type by one group, or to another barrel type within the same group.
     * Copper is a special case, requiring at least 3 steps to reach it, otherwise nothing changes.
     * Barrel types are a maximum of 4 distance apart, so taking 4 or more steps always reaches the destination.
     *
     * @param other The target barrel type to step towards
     * @param steps The number of steps to take towards the target type
     * @return The resulting barrel type after taking the steps
     */
    public BarrelWoodType stepTowards(final BarrelWoodType other, final int steps) {
        if (this == other || steps >= Group.MAX_DISTANCE + 1) {
            return other;
        }

        final var group = Group.of(this);
        final var otherGroup = Group.of(other);
        if (group == null || otherGroup == null) {
            return this;
        }

        if (group == otherGroup) {
            return other;
        }
        return group.stepTowards(otherGroup, steps).members[0];
    }


    private enum Group {
        /*
                      OAK    HOT_DRY --- NETHER
                       ^    /   |
          COLD --- TEMPERATE    |
                            \   |
                             HOT_HUMID

          temperature x-axis, humidity y-axis, oak z-axis
        */
        COLD(new Properties.Normal(Temperature.COLD, Humidity.MODERATE), SPRUCE),
        TEMPERATE(new Properties.Normal(Temperature.WARM, Humidity.MODERATE), BIRCH, CHERRY),
        OAK(new Properties.Normal(Temperature.WARM, Humidity.MODERATE, true), BarrelWoodType.OAK, DARK_OAK, PALE_OAK),
        HOT_HUMID(new Properties.Normal(Temperature.HOT, Humidity.HUMID), JUNGLE, MANGROVE, BAMBOO),
        HOT_DRY(new Properties.Normal(Temperature.HOT, Humidity.DRY), ACACIA),
        NETHER(new Properties.Normal(Temperature.NETHER, Humidity.DRY), CRIMSON, WARPED),
        SPECIAL(new Properties.Special(), CUT_COPPER);

        public static final int MAX_DISTANCE = 4;
        public static final int SPECIAL_DISTANCE = 3;

        private final Properties properties;
        private final BarrelWoodType[] members;

        Group(final Properties properties, final BarrelWoodType... members) {
            this.properties = properties;
            this.members = members;
        }

        public static Group of(final BarrelWoodType type) {
            for (final var group : Group.values()) {
                if (group.contains(type)) {
                    return group;
                }
            }
            return null;
        }

        private boolean contains(final BarrelWoodType type) {
            for (final var member : this.members) {
                if (member == type) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Computes the distance 0-3 between two groups.
         * The SPECIAL group is a special case, and always has {@link #SPECIAL_DISTANCE} from other groups.
         *
         * @param other The other group
         * @return The distance
         */
        public int getDistance(final Group other) {
            assert other != null;
            if (this == other) {
                return 0;
            }
            if (!(this.properties instanceof Properties.Normal(final var temperature1, final var humidity1, final var oak1)) || !(other.properties instanceof Properties.Normal(
                    final var temperature, final var humidity, final var oak
            ))) {
                return SPECIAL_DISTANCE;
            }

            final var temperatureDistance = Math.abs(temperature1.ordinal() - temperature.ordinal());
            final var humidityDistance = Math.abs(humidity1.ordinal() - humidity.ordinal());
            final var oakDistance = oak1 != oak ? 1 : 0;
            return temperatureDistance + humidityDistance + oakDistance;
        }

        /**
         * Advances this group towards another group by a specified number of steps.
         * Each step moves from one group to another.
         * The SPECIAL is a special case, requiring at least {@link #SPECIAL_DISTANCE} steps to reach it,
         * otherwise nothing changes.
         *
         * @param to    The group to move towards
         * @param steps The number of steps to take
         * @return The resulting group
         */
        public Group stepTowards(final Group to, final int steps) {
            assert to != null;
            final Comparator<Group> comparatorInitial = Comparator.comparing(g -> g.getDistance(to));
            // Tiebreaker comparator, should never be used unless we add a group
            final var comparator = comparatorInitial.thenComparing(Enum::ordinal);
            return Arrays.stream(values())
                    .filter(g -> this.getDistance(g) <= steps) // Out of all groups within `steps` distance...
                    .min(comparator) // ...get the group closest to the target...
                    .filter(g -> g.getDistance(to) < this.getDistance(to)) // ...as long as that group is actually closer
                    .orElse(this); // If no groups are in range, make no movement
        }
    }

    private enum Temperature {
        COLD, WARM, HOT, NETHER
    }

    private enum Humidity {
        DRY, MODERATE, HUMID
    }

    private sealed interface Properties {
        record Normal(Temperature temperature, Humidity humidity, boolean oak) implements Properties {
            public Normal(final Temperature temperature, final Humidity humidity) {
                this(temperature, humidity, false);
            }
        }

        record Special() implements Properties {
        }
    }

}
