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
import org.bukkit.Bukkit;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enum for major Minecraft versions where Brewery needs
 * to handle things differently.
 * <p>
 * BreweryX requires {@link #V1_21_5} or later. Anything older resolves to {@link #UNKNOWN}
 * and is rejected on startup.
 */
@Getter
public enum MinecraftVersion {

    // Minimum supported version. 1.21.5 - 1.21.8 are handled the same way in BreweryX.
    V1_21_5("1.21.5", "1.21.6", "1.21.7", "1.21.8"),
    V1_21_10("1.21.10", "1.21.9"), // 1.21.10 & 1.21.9 are one and the same
    V1_21_11("1.21.11"),
    V26_1("26.1"),
    UNKNOWN("Unknown");

    private static final Pattern VERSION_PATTERN = Pattern.compile("^([0-9]+)\\.([0-9]+)(?:\\.([0-9]+))?");
    private static final int[] MINIMUM_VERSION = {1, 21, 5};

    @Getter
    private static final boolean isFolia = ClassUtil.exists("io.papermc.paper.threadedregions.RegionizedServer");
    @Getter
    private static final boolean isCanvas = ClassUtil.exists("io.canvasmc.canvas.Config"); // Popular Folia fork
    /**
     * Whether the running server is older than {@link #V1_21_5}, the minimum supported version.
     */
    @Getter
    private static boolean belowMinimum;

    private final String[] versions;

    MinecraftVersion(final String... version) {
        this.versions = version;
    }

    public static MinecraftVersion get(final String major, final String minor, @Nullable final String patch) {
        final var withPatch = major + "." + minor + "." + patch;
        final var withoutPatch = major + "." + minor;

        // We do two passes to prefer exact matches with a patch version.
        for (final var minecraftVersion : values()) {
            for (final var versionString : minecraftVersion.versions) {
                if (versionString.equals(withPatch)) {
                    return minecraftVersion;
                }
            }
        }

        for (final var minecraftVersion : values()) {
            for (final var versionString : minecraftVersion.versions) {
                if (versionString.equals(withoutPatch)) {
                    return minecraftVersion;
                }
            }
        }
        return UNKNOWN;
    }

    public static MinecraftVersion getIt() {
        final var rawVersion = Bukkit.getVersion();
        final var rawVersionParsed = rawVersion.substring(rawVersion.indexOf("(MC: ") + 5, rawVersion.indexOf(")"));

        final var matcher = VERSION_PATTERN.matcher(rawVersionParsed);
        if (!matcher.find()) {
            throw new IllegalStateException("Could not parse Minecraft version from: " + rawVersion);
        }

        belowMinimum = isBelowMinimum(matcher.group(1), matcher.group(2), matcher.group(3));
        return get(matcher.group(1), matcher.group(2), matcher.group(3));
    }

    /**
     * Both unsupported old versions and versions newer than this build knows about resolve to
     * {@link #UNKNOWN}, so the numeric comparison is what tells the two cases apart.
     */
    private static boolean isBelowMinimum(final String major, final String minor, @Nullable final String patch) {
        final int[] running = {parseOrZero(major), parseOrZero(minor), parseOrZero(patch)};
        for (var i = 0; i < MINIMUM_VERSION.length; i++) {
            if (running[i] != MINIMUM_VERSION[i]) {
                return running[i] < MINIMUM_VERSION[i];
            }
        }
        return false;
    }

    private static int parseOrZero(@Nullable final String value) {
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (final NumberFormatException e) {
            return 0;
        }
    }

    public boolean isOrLater(final MinecraftVersion version) {
        return this.ordinal() >= version.ordinal();
    }

    public String getVersion() {
        return this.versions[0];
    }
}
