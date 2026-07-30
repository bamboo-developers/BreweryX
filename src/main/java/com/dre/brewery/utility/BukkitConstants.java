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

package com.dre.brewery.utility;

import com.dre.brewery.BreweryPlugin;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.bukkit.*;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

@SuppressWarnings("unchecked")
public final class BukkitConstants {

    private static final Map<String, Keyed> MAPPED_VALUES = new HashMap<>();
    // More constants can be added as required, these are just the ones Brewery currently uses
    public static final PotionEffectType HUNGER = potionEffectType("hunger");
    public static final PotionEffectType NAUSEA = potionEffectType("nausea");
    public static final PotionEffectType BLINDNESS = potionEffectType("blindness");
    public static final PotionEffectType SLOWNESS = potionEffectType("slowness");
    public static final PotionEffectType REGENERATION = potionEffectType("regeneration");
    public static final PotionEffectType POISON = potionEffectType("poison");
    public static final PotionEffectType WEAKNESS = potionEffectType("weakness");
    public static final PotionEffectType FIRE_RESISTANCE = potionEffectType("fire_resistance");
    public static final PotionEffectType INSTANT_HEALTH = potionEffectType("instant_health");
    public static final PotionEffectType INSTANT_DAMAGE = potionEffectType("instant_damage");
    public static final PotionEffectType WATER_BREATHING = potionEffectType("water_breathing");
    public static final PotionEffectType NIGHT_VISION = potionEffectType("night_vision");
    public static final PotionEffectType SPEED = potionEffectType("speed");
    public static final PotionEffectType HASTE = potionEffectType("haste");

    public static final Particle INSTANT_EFFECT = particle("instant_effect");
    public static final Particle SPLASH = particle("splash");
    public static final Particle ENTITY_EFFECT = particle("entity_effect");
    public static final Particle LARGE_SMOKE = particle("large_smoke");
    public static final Particle DUST = particle("dust");

    public static final PotionType POTION_REGENERATION = potionType("regeneration");
    public static final PotionType POTION_SWIFTNESS = potionType("swiftness");
    public static final PotionType POTION_FIRE_RESISTANCE = potionType("fire_resistance");
    public static final PotionType POTION_POISON = potionType("poison");
    public static final PotionType POTION_HEALING = potionType("healing");
    public static final PotionType POTION_NIGHT_VISION = potionType("night_vision");
    public static final PotionType POTION_WEAKNESS = potionType("weakness");
    public static final PotionType POTION_STRENGTH = potionType("strength");
    public static final PotionType POTION_SLOWNESS = potionType("slowness");
    public static final PotionType POTION_WATER_BREATHING = potionType("water_breathing");
    public static final PotionType POTION_HARMING = potionType("harming");
    public static final PotionType POTION_INVISIBILITY = potionType("invisibility");

    public static final Material SHORT_GRASS = renamedMaterial("grass", new Tuple<>(MinecraftVersion.V1_20_4, "short_grass"));

    static {
        try {
            for (final var field : BukkitConstants.class.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) || !Keyed.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                final var obj = (Keyed) field.get(null);
                MAPPED_VALUES.put(obj.getKey().getKey(), obj);
            }
        } catch (final IllegalAccessException e) {
            Logging.errorLog("BukkitConstants failed to initialize mapped values", e);
        }
    }

    private BukkitConstants() {
    }

    public static Particle particle(final String key) {
        return Registry.PARTICLE_TYPE.get(NamespacedKey.minecraft(key));
    }

    public static PotionEffectType potionEffectType(final String key) {
        if (BreweryPlugin.getMCVersion().isOrLater(MinecraftVersion.V1_21)) {
            return getOrThrow(Registry.EFFECT, key);
        }
        return throwIfNull(key, k -> PotionEffectType.getByKey(NamespacedKey.minecraft(k)));
    }

    public static PotionType potionType(final String key) {
        return getOrThrow(Registry.POTION, key);
    }

    @Nullable
    public static PotionEffectType nullablePotionEffectType(final String key) {
        if (BreweryPlugin.getMCVersion().isOrLater(MinecraftVersion.V1_21)) {
            return Registry.EFFECT.get(NamespacedKey.minecraft(key));
        }
        return PotionEffectType.getByKey(NamespacedKey.minecraft(key));
    }

    public static Material renamedMaterial(final String defaultValue, final Tuple<MinecraftVersion, String>... names) {
        final var activeVersion = BreweryPlugin.getMCVersion();
        for (final var pair : names) {
            final var version = pair.a();
            final var name = pair.b();
            if (activeVersion.isOrLater(version)) {
                return Material.getMaterial(name.toUpperCase());
            }
        }
        return Material.getMaterial(defaultValue.toUpperCase());
    }

    private static <T extends Keyed> T getOrThrow(final Registry<T> registry, final String key) {
        final var value = registry.get(NamespacedKey.minecraft(key));
        if (value == null) {
            throw new IllegalArgumentException("No value found in registry for key: " + key);
        }
        return value;
    }

    private static <T> T throwIfNull(final String key, final KeyLookup<T> function) {
        final var value = function.throwIfNull(key);
        if (value == null) {
            throw new IllegalArgumentException("No value found for key: " + key);
        }
        return value;
    }

    @Nullable
    public static <T extends Keyed> T getMappedValue(final String key, final Class<T> type) {
        final var keyed = MAPPED_VALUES.get(key);
        if (keyed == null) {
            return null;
        }
        if (!type.isAssignableFrom(keyed.getClass())) {
            return null;
        }
        return type.cast(keyed);
    }

    // I don't really like this but whatever

    public static <T extends Keyed> Collection<T> getMappedValues(final Class<T> type) {
        final List<T> list = new ArrayList<>();
        for (final var keyed : MAPPED_VALUES.values()) {
            if (type.isAssignableFrom(keyed.getClass())) {
                list.add(type.cast(keyed));
            }
        }
        return list;
    }

    @Nullable
    public static Keyed getMappedValue(final String key) {
        return MAPPED_VALUES.get(key);
    }

    public static Collection<Keyed> getMappedValues() {
        return MAPPED_VALUES.values();
    }


    private interface KeyLookup<T> {
        T throwIfNull(String key);
    }

    @Getter
    @AllArgsConstructor
    public abstract static class BukkitConstantWrapper {
        protected static final MinecraftVersion SERVER_VERSION = BreweryPlugin.getMCVersion();
        protected final MinecraftVersion requiredVersion;
    }

    public static final class ParticleSpellWrapper extends BukkitConstantWrapper {

        public ParticleSpellWrapper() {
            super(MinecraftVersion.V1_21_10);
        }

        @SuppressWarnings("UnstableApiUsage")
        @Nullable
        public final Object toInstance(@NotNull final Color color, final float size) {
            if (!SERVER_VERSION.isOrLater(this.requiredVersion)) {
                return null;
            }

            return new Particle.Spell(color, size);
        }
    }
}
