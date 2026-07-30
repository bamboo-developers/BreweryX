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

package com.dre.brewery.recipe;

import com.dre.brewery.configuration.sector.capsule.ConfigCauldronIngredient;
import com.dre.brewery.utility.BUtil;
import com.dre.brewery.utility.Logging;
import com.dre.brewery.utility.StringParser;
import com.dre.brewery.utility.Tuple;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Color;
import org.bukkit.Material;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

/**
 * A Recipe for the Base Potion coming out of the Cauldron.
 */
@Getter
@Setter
public final class BCauldronRecipe {
    @Getter
    public static List<BCauldronRecipe> recipes = new ArrayList<>();
    @Getter
    @Setter
    public static int numConfigRecipes;
    public static final List<RecipeItem> acceptedCustom = new ArrayList<>(); // All accepted custom and other items
    @Getter
    public static final Set<Material> acceptedSimple = new HashSet<>(); // All accepted simple items
    @Getter
    public static final Set<Material> acceptedMaterials = new HashSet<>(); // Fast cache for all accepted Materials

    private final String id;
    private String name;
    private List<RecipeItem> ingredients;
    private PotionColor color;
    private List<Tuple<Integer, Color>> particleColor = new ArrayList<>();
    private List<String> lore;
    private int cmData; // Custom Model Data
    private boolean saveInData; // If this recipe should be saved in data and loaded again when the server restarts. Applicable to non-config recipes


    /**
     * A New Cauldron Recipe with the given name.
     * <p>Use new BCauldronRecipe.Builder() for easier Cauldron Recipe Creation
     *
     * @param id   ID of the Cauldron Recipe
     * @param name Name of the Cauldron Recipe
     */
    public BCauldronRecipe(final String id, final String name) {
        this.id = id;
        this.name = name;
        this.color = PotionColor.CYAN;
    }

    @Nullable
    public static BCauldronRecipe fromConfig(final String id, final ConfigCauldronIngredient cfgCauldronIngredient) {

        var name = cfgCauldronIngredient.getName();
        if (name != null) {
            name = BUtil.color(name);
        } else {
            Logging.errorLog("Missing name for Cauldron-Recipe: " + id);
            return null;
        }

        final var recipe = new BCauldronRecipe(id, name);

        recipe.ingredients = BRecipe.loadIngredients(BUtil.getListSafely(cfgCauldronIngredient.getIngredients()), id);
        if (recipe.ingredients == null || recipe.ingredients.isEmpty()) {
            Logging.errorLog("No ingredients for Cauldron-Recipe: " + recipe.name);
            return null;
        }

        final var col = cfgCauldronIngredient.getColor();
        if (col != null) {
            recipe.color = PotionColor.fromString(col);
        } else {
            recipe.color = PotionColor.CYAN;
        }
        if (recipe.color == PotionColor.WATER && !col.equals("WATER")) {
            recipe.color = PotionColor.CYAN;
            // Don't throw error here as old mc versions will not know even the default colors
        }

        final List<String> cookParticles = cfgCauldronIngredient.getCookParticles() != null ? cfgCauldronIngredient.getCookParticles() : new ArrayList<>();
        for (final var entry : cookParticles) {
            final var split = entry.split("/");
            final int minute;
            if (split.length == 1) {
                minute = 10;
            } else if (split.length == 2) {
                minute = BUtil.parseIntOrZero(split[1]);
            } else {
                Logging.errorLog("cookParticle: '" + entry + "' in: " + recipe.name);
                return null;
            }
            if (minute < 1) {
                Logging.errorLog("cookParticle: '" + entry + "' in: " + recipe.name);
                return null;
            }
            final var partCol = PotionColor.fromString(split[0]);
            if (partCol == PotionColor.WATER && !split[0].equals("WATER")) {
                Logging.errorLog("Color of cookParticle: '" + entry + "' in: " + recipe.name);
                return null;
            }
            recipe.particleColor.add(new Tuple<>(minute, partCol.getColor()));
        }
        if (!recipe.particleColor.isEmpty()) {
            // Sort by minute
            recipe.particleColor.sort(Comparator.comparing(Tuple::first));
        }


        final var lore = BRecipe.loadQualityStringList(cfgCauldronIngredient.getLore(), StringParser.ParseType.LORE);
        if (!lore.isEmpty()) {
            recipe.lore = lore.stream().map(Tuple::second).collect(Collectors.toList());
        }

        recipe.cmData = cfgCauldronIngredient.getCustomModelData() != null ? cfgCauldronIngredient.getCustomModelData() : 0;

        return recipe;
    }

    @Nullable
    public static BCauldronRecipe get(final String name) {
        for (final var recipe : recipes) {
            if (recipe.name.equalsIgnoreCase(name)) {
                return recipe;
            }
        }
        return null;
    }

    /**
     * Gets a Modifiable Sublist of the CauldronRecipes that are loaded by config.
     * <p>Changes are directly reflected by the main list of all recipes
     * <br>Changes to the main List of all CauldronRecipes will make the reference to this sublist invalid
     *
     * <p>After adding or removing elements, CauldronRecipes.numConfigRecipes MUST be updated!
     */
    public static List<BCauldronRecipe> getConfigRecipes() {
        return recipes.subList(0, numConfigRecipes);
    }

    /**
     * Gets a Modifiable Sublist of the CauldronRecipes that are added by plugins.
     * <p>Changes are directly reflected by the main list of all recipes
     * <br>Changes to the main List of all CauldronRecipes will make the reference to this sublist invalid
     */
    public static List<BCauldronRecipe> getAddedRecipes() {
        return recipes.subList(numConfigRecipes, recipes.size());
    }

    /**
     * Gets the main List of all CauldronRecipes.
     */
    public static List<BCauldronRecipe> getAllRecipes() {
        return recipes;
    }

    /**
     * Find how much these ingredients match the given ones from 0-10.
     * <p>If any ingredient is missing, returns 0
     * <br>Any included item that is not in the recipe, will drive the number down most heavily.
     * <br>More Amount of any item, will logarithmically raise the number
     * <br>Difference in Amount to what the recipe expects will make a tiny difference on the number
     * <p>So apart from unexpected items, more amount of the correct item will make the number go up,
     * with a little dip for difference in expected amount.
     *
     * <p>The thought behind this is, that a given list of ingredients matches this recipe most, when:
     * <br>1. It is not missing ingredients,
     * <br>2. It has no unexpected ingredients
     * <br>3. It has a lot of the matching ingredients, so that for two recipes, both having the same
     * amount of unexpected ingredients, the one matching the item with the highest amounts wins.
     * <br> For Example | Recipe_1: (Wheat*1), Recipe_2: (Sugar*1) | Ingredients: (Wheat*10, Sugar*5), Recipe_1 should win,
     * even though the difference in expected amount (1) is lower for Recipe_2
     * <br>4. It has the least difference in expected ingredient amount.
     */
    public final float getIngredientMatch(final List<Ingredient> items) {
        if (items.size() < this.ingredients.size()) {
            return 0;
        }
        float match = 10;
        search:
        for (final var recipeIng : this.ingredients) {
            for (final var ing : items) {
                if (recipeIng.matches(ing)) {
                    final double difference = Math.abs(recipeIng.getAmount() - ing.getAmount());
                    if (difference >= 1000) {
                        return 0;
                    }
                    // The Item Amount is the determining part here, the higher the better.
                    // But let the difference in amount to what the recipe expects have a tiny factor as well.
                    // This way for the same amount, the recipe with the lower difference wins.
                    final var factor = ing.getAmount() * (1.0 - (difference / 1000.0));
                    //double mod = 0.1 + (0.9 * Math.exp(-0.03 * difference)); // logarithmic curve from 1 to 0.1
                    final var mod = 1 + (0.9 * -Math.exp(-0.03 * factor)); // logarithmic curve from 0.1 to 1, small for a low factor

                    match *= mod;
                    continue search;
                }
            }
            return 0;
        }
        if (items.size() > this.ingredients.size()) {
            // If there are too many items in the List, multiply the match by 0.1 per Item thats too much
            // So that even if every other ingredient is perfect, a recipe that expects all these items will fare better
            final float tooMuch = items.size() - this.ingredients.size();
            final var mod = Math.pow(0.1, tooMuch);
            match *= mod;
        }
        Logging.debugLog("Match for Cauldron Recipe " + this.name + ": " + match);
        return match;
    }

    public final void updateAcceptedLists() {
        for (final var ingredient : this.getIngredients()) {
            if (ingredient.hasMaterials()) {
                BCauldronRecipe.acceptedMaterials.addAll(ingredient.getMaterials());
            }
            if (ingredient instanceof SimpleItem) {
                BCauldronRecipe.acceptedSimple.add(((SimpleItem) ingredient).getMaterial());
            } else {
                // Add it as acceptedCustom
                if (!BCauldronRecipe.acceptedCustom.contains(ingredient)) {
                    BCauldronRecipe.acceptedCustom.add(ingredient);
                }
            }
        }
    }

    @Override
    public final String toString() {
        return "BCauldronRecipe{" + this.name + '}';
    }

    public static final class Builder {
        private final String id;
        private final String name;
        private final List<RecipeItem> ingredients = new ArrayList<>();
        private final List<Tuple<Integer, Color>> particleColor = new ArrayList<>();
        private final List<String> lore = new ArrayList<>();
        private PotionColor color = PotionColor.CYAN;
        private int cmData = 0;
        private boolean saveInData = false;


        public Builder(final String id, final String name) {
            this.id = id;
            this.name = name;
        }

        public Builder ingredient(final RecipeItem ingredient) {
            this.ingredients.add(ingredient);
            return this;
        }

        public Builder ingredients(final List<RecipeItem> ingredients) {
            this.ingredients.addAll(ingredients);
            return this;
        }

        public Builder color(final PotionColor color) {
            this.color = color;
            return this;
        }

        public Builder particleColor(final int minute, final Color color) {
            this.particleColor.add(new Tuple<>(minute, color));
            return this;
        }

        public Builder lore(final String lore) {
            this.lore.add(lore);
            return this;
        }

        public Builder lore(final List<String> lore) {
            this.lore.addAll(lore);
            return this;
        }

        public Builder cmData(final int cmData) {
            this.cmData = cmData;
            return this;
        }

        public Builder saveInData(final boolean saveInData) {
            this.saveInData = saveInData;
            return this;
        }

        public BCauldronRecipe build() {
            final var recipe = new BCauldronRecipe(this.id, this.name);
            recipe.ingredients = this.ingredients;
            recipe.color = this.color;
            recipe.particleColor = this.particleColor;
            recipe.lore = this.lore;
            recipe.cmData = this.cmData;
            recipe.saveInData = this.saveInData;
            return recipe;
        }
    }
}
