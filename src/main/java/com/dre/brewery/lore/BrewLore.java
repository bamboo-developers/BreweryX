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

package com.dre.brewery.lore;

import com.dre.brewery.BIngredients;
import com.dre.brewery.Brew;
import com.dre.brewery.configuration.ConfigManager;
import com.dre.brewery.configuration.files.Config;
import com.dre.brewery.configuration.files.Lang;
import com.dre.brewery.recipe.BEffect;
import com.dre.brewery.recipe.BRecipe;
import com.dre.brewery.utility.BUtil;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the Lore on a Brew under Modification.
 * <p>Can efficiently replace certain lines of lore, to update brew information on an item.
 */
public final class BrewLore {

    private static final Config config = ConfigManager.getConfig(Config.class);
    private static final Lang lang = ConfigManager.getConfig(Lang.class);

    private final Brew brew;
    private final PotionMeta meta;
    private final List<String> lore;
    private boolean lineAddedOrRem = false;

    public BrewLore(final Brew brew, final PotionMeta meta) {
        this.brew = brew;
        this.meta = meta;
        this.lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
    }

    /**
     * True if the PotionMeta has Lore in quality color
     */
    public static boolean hasColorLore(final PotionMeta meta) {
        if (meta == null) return false;
        if (!meta.hasLore()) return false;
        final var lore = meta.getLore();
        if (lore.size() < 2) {
            return false;
        }
        // Ingredient lore present, must be quality colored
        return Type.INGR.findInLore(lore) != -1;
        //!meta.getLore().get(1).startsWith("§7");
    }

    /**
     * gets the Color that represents a quality in Lore
     *
     * @param quality The Quality for which to find the color code
     * @return Color Code for given Quality
     */
    public static String getQualityColor(final int quality) {
        final String color;
        if (quality > 8) {
            color = "&a";
        } else if (quality > 6) {
            color = "&e";
        } else if (quality > 4) {
            color = "&6";
        } else if (quality > 2) {
            color = "&c";
        } else {
            color = "&4";
        }
        return BUtil.color(color);
    }

    /**
     * Gets the icon representing a quality for use in lore
     *
     * @param quality The quality used for the icon
     * @return The icon for the given quality
     */
    public static char getQualityIcon(final int quality) {
        final char icon;
        if (quality > 8) {
            icon = '\u2605';
        } else if (quality > 6) {
            icon = '\u2BEA';
        } else if (quality > 4) {
            icon = '\u2606';
        } else if (quality > 2) {
            icon = '\u2718';
        } else {
            icon = '\u2620';
        }
        return icon;
    }

    /**
     * Write the new lore into the Meta.
     * <p>Should be called at the end of operation on this Brew Lore
     */
    public final PotionMeta write() {
        if (this.lineAddedOrRem) {
            this.updateSpacer();
        }

        this.meta.setLore(this.lore);
        return this.meta;
    }

    /**
     * adds or removes an empty line in lore to space out the text a bit
     */
    public final void updateSpacer() {
        var hasSpace = false;
        for (var i = 0; i < this.lore.size(); i++) {
            final var t = Type.get(this.lore.get(i));
            if (t == Type.CUSTOM) {
                // Custom lore, keep looking for the spacer position
            } else if (t == Type.SPACE) {
                hasSpace = true;
            } else if (t != null && t.isAfter(Type.SPACE)) {
                if (hasSpace) return;

                // We want to add the spacer if we have Custom Lore, to have a space between custom and brew lore.
                this.lore.add(i, Type.SPACE.id);
                return;
            }
        }
        if (hasSpace) {
            // There was a space but nothing after the space
            this.removeLore(Type.SPACE);
        }
    }

    /**
     * Add the list of strings as custom lore for the base potion coming out of the cauldron
     */
    public final void addCauldronLore(final List<String> l) {
        var index = -1;
        for (final var line : l) {
            if (index == -1) {
                index = this.addLore(Type.CUSTOM, "", line);
                index++;
            } else {
                this.lore.add(index, Type.CUSTOM.id + line);
                index++;
            }
        }
    }

    /**
     * updates the IngredientLore
     *
     * @param qualityColor If the lore should have colors according to quality
     */
    public final void updateIngredientLore(final boolean qualityColor) {
        if (qualityColor && this.brew.hasRecipe() && !this.brew.isStripped()) {
            final var quality = this.brew.getIngredients().getIngredientQuality(this.brew.getCurrentRecipe());
            final var prefix = getQualityColor(quality);
            final var icon = getQualityIcon(quality);
            this.addOrReplaceLore(Type.INGR, prefix, lang.getEntry("Brew_Ingredients"), " " + icon);
        } else {
            this.removeLore(Type.INGR, lang.getEntry("Brew_Ingredients"));
        }
    }

    /**
     * updates the CookLore
     *
     * @param qualityColor If the lore should have colors according to quality
     */
    public final void updateCookLore(final boolean qualityColor) {
        if (qualityColor && this.brew.hasRecipe() && this.brew.getDistillRuns() > 0 == this.brew.getCurrentRecipe().needsDistilling() && !this.brew.isStripped()) {
            final var ingredients = this.brew.getIngredients();
            final var quality = ingredients.getCookingQuality(this.brew.getCurrentRecipe(), this.brew.getDistillRuns() > 0);
            var prefix = getQualityColor(quality) + ingredients.getCookedTime() + " " + lang.getEntry("Brew_minute");
            if (ingredients.getCookedTime() > 1) {
                prefix = prefix + lang.getEntry("Brew_MinutePluralPostfix");
            }
            this.addOrReplaceLore(Type.COOK, prefix, " " + lang.getEntry("Brew_fermented"), " " + getQualityIcon(quality));
        } else {
            this.removeLore(Type.COOK, lang.getEntry("Brew_fermented"));
        }
    }

    /**
     * updates the DistillLore
     *
     * @param qualityColor If the lore should have colors according to quality
     */
    public final void updateDistillLore(final boolean qualityColor) {
        if (this.brew.getDistillRuns() <= 0) return;
        String prefix;
        var suffix = "";
        final var distillRuns = this.brew.getDistillRuns();
        if (qualityColor && !this.brew.isUnlabeled() && this.brew.hasRecipe()) {
            final var quality = this.brew.getIngredients().getDistillQuality(this.brew.getCurrentRecipe(), distillRuns);
            prefix = getQualityColor(quality);
            suffix = " " + getQualityIcon(quality);
        } else {
            prefix = "§7";
        }
        if (!this.brew.isUnlabeled()) {
            if (distillRuns > 1) {
                prefix = prefix + distillRuns + " " + lang.getEntry("Brew_times") + " ";
            }
        }
        if (this.brew.isUnlabeled() && this.brew.hasRecipe() && distillRuns < this.brew.getCurrentRecipe().getDistillruns()) {
            this.addOrReplaceLore(Type.DISTILL, prefix, lang.getEntry("Brew_LessDistilled"), suffix);
        } else {
            this.addOrReplaceLore(Type.DISTILL, prefix, lang.getEntry("Brew_Distilled"), suffix);
        }
    }

    /**
     * updates the AgeLore
     *
     * @param qualityColor If the lore should have colors according to quality
     */
    public final void updateAgeLore(final boolean qualityColor) {
        if (this.brew.isStripped()) return;
        String prefix;
        var suffix = "";
        final var age = this.brew.getAgeTime();
        if (qualityColor && !this.brew.isUnlabeled() && this.brew.hasRecipe()) {
            final var quality = this.brew.getIngredients().getAgeQuality(this.brew.getCurrentRecipe(), age);
            prefix = getQualityColor(quality);
            suffix = " " + getQualityIcon(quality);
        } else {
            prefix = "§7";
        }
        if (!this.brew.isUnlabeled()) {
            if (age >= 1 && age < 2) {
                prefix = prefix + lang.getEntry("Brew_OneYear") + " ";
            } else if (age < 201) {
                prefix = prefix + (int) Math.floor(age) + " " + lang.getEntry("Brew_Years") + " ";
            } else {
                prefix = prefix + lang.getEntry("Brew_HundredsOfYears") + " ";
            }
        }
        if (age > 0) {
            this.addOrReplaceLore(Type.AGE, prefix, lang.getEntry("Brew_BarrelRiped"), suffix);
        }
    }

    /**
     * updates the WoodLore
     *
     * @param qualityColor If the lore should have colors according to quality
     */
    public final void updateWoodLore(final boolean qualityColor) {
        if (qualityColor && this.brew.hasRecipe() && !this.brew.isUnlabeled()) {
            final var quality = this.brew.getIngredients().getWoodQuality(this.brew.getCurrentRecipe(), this.brew.getWood());
            this.addOrReplaceLore(Type.WOOD, getQualityColor(quality), lang.getEntry("Brew_Woodtype"), " " + getQualityIcon(quality));
        } else {
            this.removeLore(Type.WOOD, lang.getEntry("Brew_Woodtype"));
        }
    }

    /**
     * updates the Custom Lore
     */
    public final void updateCustomLore() {
        this.removeLore(Type.CUSTOM);

        final var recipe = this.brew.getCurrentRecipe();
        if (recipe != null && recipe.hasLore()) {
            var index = -1;
            for (final var line : recipe.getLoreForQuality(this.brew.getQuality())) {
                if (index == -1) {
                    index = this.addLore(Type.CUSTOM, "", line);
                } else {
                    this.lore.add(index, Type.CUSTOM.id + line);
                }
                index++;
            }
        }
    }

    public final void updateQualityStars(final boolean qualityColor) {
        this.updateQualityStars(qualityColor, false);
    }

    public final void updateQualityStars(final boolean qualityColor, final boolean withBars) {
        if (this.brew.isStripped()) return;
        if (this.brew.hasRecipe() && this.brew.getCurrentRecipe().needsToAge() && this.brew.getAgeTime() < 0.5) {
            return;
        }
        final var quality = this.brew.getQuality();
        if (quality > 0 && (qualityColor || config.isAlwaysShowQuality())) {
            var stars = quality / 2;
            final var half = quality % 2 > 0;
            var noStars = 5 - stars - (half ? 1 : 0);
            final var b = new StringBuilder(24);
            String color;
            if (qualityColor) {
                color = getQualityColor(quality);
            } else {
                color = "§7";
            }
            if (withBars) {
                color = "§8[" + color;
            }
            for (; stars > 0; stars--) {
                b.append("⭑");
            }
            if (half) {
                if (!qualityColor) {
                    b.append("§8");
                }
                b.append("⭒");
            }
            if (withBars) {
                if (noStars > 0) {
                    b.append("§0");
                    for (; noStars > 0; noStars--) {
                        b.append("⭑");
                    }
                }
                b.append("§8]");
            }
            this.addOrReplaceLore(Type.STARS, color, b.toString());
        } else {
            this.removeLore(Type.STARS);
        }
    }

    public final void updateAlc(final boolean inDistiller) {
        final var alc = this.brew.getOrCalcAlc();
        if (!this.brew.isUnlabeled() && (inDistiller || config.isAlwaysShowAlc()) && alc != 0) {
            this.addOrReplaceLore(Type.ALC, "§8", lang.getEntry("Brew_Alc", alc + ""));
        } else if (config.isAlwaysShowAlcIndicator() && alc > 0) {
            this.addOrReplaceLore(Type.ALC, "§8", lang.getEntry("Brew_Alcoholic"));
        } else {
            this.removeLore(Type.ALC);
        }
    }

    public final void updateDefect(@Nullable final String defectMessage) {
        if (defectMessage != null) {
            if (Type.DEFECT.findInLore(this.lore) == -1) {
                this.addOrReplaceLore(Type.DEFECT, "§c", defectMessage);
            }
        } else {
            this.removeLore(Type.DEFECT);
        }
    }

    public final void updateBrewer(final String name) {
        if (name != null && config.isShowBrewer()) {
            this.addOrReplaceLore(Type.BREWER, "§8", lang.getEntry("Brew_Brewer", name));
        } else {
            this.removeLore(Type.BREWER);
        }
    }

    /**
     * Converts to/from qualitycolored Lore
     */
    public final void convertLore(final boolean toQuality) {
        if (!this.brew.hasRecipe()) {
            return;
        }

        this.updateCustomLore();
        if (toQuality && this.brew.isUnlabeled()) {
            return;
        }
        this.updateQualityStars(toQuality);

        // Ingredients
        this.updateIngredientLore(toQuality);

        // Cooking
        this.updateCookLore(toQuality);

        // Distilling
        this.updateDistillLore(toQuality);

        // Ageing
        if (this.brew.getAgeTime() >= 1) {
            this.updateAgeLore(toQuality);
        }

        // WoodType
        if (this.brew.getAgeTime() > 0.5) {
            this.updateWoodLore(toQuality);
        }

        this.updateAlc(false);
    }

    /**
     * Adds or replaces a line of Lore.
     * <p>Searches for type and if not found for Substring lore and replaces it
     *
     * @param type   The Type of BrewLore to replace
     * @param prefix The Prefix to add to the line of lore
     * @param line   The Line of Lore to add or replace
     */
    public final int addOrReplaceLore(final Type type, final String prefix, final String line) {
        return this.addOrReplaceLore(type, prefix, line, "");
    }

    /**
     * Adds or replaces a line of Lore.
     * <p>Searches for type and if not found for Substring lore and replaces it
     *
     * @param type   The Type of BrewLore to replace
     * @param prefix The Prefix to add to the line of lore
     * @param line   The Line of Lore to add or replace
     * @param suffix The Suffix to add to the line of lore
     */
    public final int addOrReplaceLore(final Type type, final String prefix, final String line, final String suffix) {
        var index = type.findInLore(this.lore);
        if (index > -1) {
            this.lore.set(index, type.id + prefix + line + suffix);
            return index;
        }

        // Could not find Lore by type, find and replace by substring
        index = BUtil.indexOfSubstring(this.lore, line);
        if (index > -1) {
            this.lore.remove(index);
        }
        return this.addLore(type, prefix, line, suffix);
    }

    /**
     * Adds a line of Lore in the correct ordering
     *
     * @param type   The Type of BrewLore to add
     * @param prefix The Prefix to add to the line of lore
     * @param line   The Line of Lore to add or add
     */
    public final int addLore(final Type type, final String prefix, final String line) {
        return this.addLore(type, prefix, line, "");
    }

    /**
     * Adds a line of Lore in the correct ordering
     *
     * @param type   The Type of BrewLore to add
     * @param prefix The Prefix to add to the line of lore
     * @param line   The Line of Lore to add or add
     * @param suffix The Suffix to add to the line of lore
     */
    public final int addLore(final Type type, final String prefix, final String line, final String suffix) {
        this.lineAddedOrRem = true;
        for (var i = 0; i < this.lore.size(); i++) {
            final var existing = Type.get(this.lore.get(i));
            if (existing != null && existing.isAfter(type)) {
                this.lore.add(i, type.id + prefix + line + suffix);
                return i;
            }
        }
        this.lore.add(type.id + prefix + BUtil.color(line) + suffix); // TODO: Color
        return this.lore.size() - 1;
    }

    /**
     * Searches for type and if not found for Substring lore and removes it
     */
    public final void removeLore(final Type type, final String line) {
        var index = type.findInLore(this.lore);
        if (index == -1) {
            index = BUtil.indexOfSubstring(this.lore, line);
        }
        if (index > -1) {
            this.lineAddedOrRem = true;
            this.lore.remove(index);
        }
    }

    /**
     * Searches for type and removes it
     */
    public final void removeLore(final Type type) {
        if (type != Type.CUSTOM) {
            final var index = type.findInLore(this.lore);
            if (index > -1) {
                this.lineAddedOrRem = true;
                this.lore.remove(index);
            }
        } else {
            // Lore could have multiple lines of this type
            for (var i = this.lore.size() - 1; i >= 0; i--) {
                if (Type.get(this.lore.get(i)) == type) {
                    this.lore.remove(i);
                    this.lineAddedOrRem = true;
                }
            }
        }
    }

    /**
     * Removes all Brew Lore lines
     */
    public void removeAll() {
        for (var i = this.lore.size() - 1; i >= 0; i--) {
            if (Type.get(this.lore.get(i)) != null) {
                this.lore.remove(i);
                this.lineAddedOrRem = true;
            }
        }
    }

    /**
     * Adds the Effect names to the Items description
     */
    public final void addOrReplaceEffects(final List<BEffect> effects, final int quality) {
        // Effects are shown by the client itself since 1.9, nothing to write into the lore
    }

    /**
     * If the Lore Line at index is a Brew Lore line
     *
     * @param index the index in lore to check
     * @return true if the line at index is of any Brew Lore type
     */
    public boolean isBrewLore(final int index) {
        return index < this.lore.size() && Type.get(this.lore.get(index)) != null;
    }

    /**
     * Removes all effects
     */
    public final void removeEffects() {
        if (this.meta.hasCustomEffects()) {
            for (final var effect : new ArrayList<>(this.meta.getCustomEffects())) {
                final var type = effect.getType();
                //if (!type.equals(PotionEffectType.REGENERATION)) {
                this.meta.removeCustomEffect(type);
                //}
            }
        }
    }

    /**
     * Type of Lore Line
     */
    public enum Type {
        CUSTOM("§t"),
        SPACE("§u"),

        STARS("§s"),
        INGR("§v"),
        COOK("§w"),
        DISTILL("§p"),
        AGE("§y"),
        WOOD("§z"),
        ALC("§q"),
        DEFECT("§i"),
        BREWER("§g");
        // Available: j, non-letters (server deletes §x)

        public final String id;

        /**
         * @param id Identifier as Prefix of the Loreline
         */
        Type(final String id) {
            this.id = id;
        }

        /**
         * Get the Type of the given line of Lore
         */
        @Nullable
        public static Type get(final String loreLine) {
            if (loreLine.length() >= 2) {
                return getById(loreLine.substring(0, 2));
            } else {
                return null;
            }
        }

        /**
         * Get the Type of the given Identifier, prefix of a line of lore
         */
        @Nullable
        public static Type getById(final String id) {
            for (final var t : values()) {
                if (t.id.equals(id)) {
                    return t;
                }
            }
            return null;
        }

        /**
         * Find this type in the Lore
         *
         * @param lore The lore to search in
         * @return index of this type in the lore, -1 if not found
         */
        public int findInLore(final List<String> lore) {
            return BUtil.indexOfStart(lore, this.id);
        }

        /**
         * Is this type after the other in lore
         *
         * @param other the other type
         * @return true if this type should be after the other type in lore
         */
        public boolean isAfter(final Type other) {
            return other.ordinal() <= this.ordinal();
        }

    }
}
