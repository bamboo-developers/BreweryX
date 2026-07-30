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

import com.dre.brewery.api.events.brew.BrewModifyEvent;
import com.dre.brewery.configuration.ConfigManager;
import com.dre.brewery.configuration.files.Config;
import com.dre.brewery.configuration.files.Lang;
import com.dre.brewery.lore.Base91DecoderStream;
import com.dre.brewery.lore.Base91EncoderStream;
import com.dre.brewery.lore.BrewLore;
import com.dre.brewery.recipe.*;
import com.dre.brewery.utility.BUtil;
import com.dre.brewery.utility.Logging;
import com.dre.brewery.utility.MinecraftVersion;
import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Collectors;

/**
 * Represents ingredients in Cauldron, Brew
 */
@Getter
public final class BIngredients {

    private static final MinecraftVersion VERSION = BreweryPlugin.getMCVersion();
    private static final BreweryPlugin plugin = BreweryPlugin.getInstance();
    private static final Config config = ConfigManager.getConfig(Config.class);
    private static final Lang lang = ConfigManager.getConfig(Lang.class);
    private static int lastId = 0; // Legacy

    private int id; // Legacy
    private List<Ingredient> ingredients = new ArrayList<>();
    private int cookedTime;

    /**
     * Init a new BIngredients
     */
    public BIngredients() {
    }

    /**
     * Load from File
     */
    public BIngredients(final List<Ingredient> ingredients, final int cookedTime) {
        this.ingredients = ingredients;
        this.cookedTime = cookedTime;
    }

    /**
     * Load from legacy Brew section
     */
    public BIngredients(final List<Ingredient> ingredients, final int cookedTime, final boolean legacy) {
        this(ingredients, cookedTime);
        if (legacy) {
            this.id = lastId;
            lastId++;
        }
    }

    public static BIngredients load(final DataInputStream in, final short dataVersion) throws IOException {
        final var cookedTime = in.readInt();
        var size = in.readByte();
        final List<Ingredient> ing = new ArrayList<>(size);
        for (; size > 0; size--) {
            final var itemLoader = new ItemLoader(dataVersion, in, in.readUTF());
            if (!plugin.getIngredientLoaders().containsKey(itemLoader.getSaveID())) {
                Logging.errorLog("Ingredient Loader not found: " + itemLoader.getSaveID());
                break;
            }
            final var loaded = plugin.getIngredientLoaders().get(itemLoader.getSaveID()).apply(itemLoader);
            final int amount = in.readShort();
            if (loaded != null) {
                loaded.setAmount(amount);
                ing.add(loaded);
            }
        }
        return new BIngredients(ing, cookedTime);
    }

    public static BIngredients deserializeIngredients(final String mat) {
        try (final var in = new DataInputStream(new Base91DecoderStream(new ByteArrayInputStream(mat.getBytes())))) {
            final var ver = in.readByte();
            return BIngredients.load(in, ver);
        } catch (final IOException e) {
            Logging.errorLog("Failed to deserialize Ingredients", e);
            return new BIngredients();
        }
    }

    /**
     * Force add an ingredient to this.
     * <p>Will not check if item is acceptable
     *
     * @param ingredient the item to add
     */
    public void add(final ItemStack ingredient) {
        for (final var existing : this.ingredients) {
            if (existing.matches(ingredient)) {
                existing.setAmount(existing.getAmount() + 1);
                return;
            }
        }

        final var ing = RecipeItem.getMatchingRecipeItem(ingredient, true).toIngredient(ingredient);
        ing.setAmount(1);
        this.ingredients.add(ing);
    }

    /**
     * Add an ingredient to this with corresponding RecipeItem
     *
     * @param ingredient the item to add
     * @param rItem      the RecipeItem that matches the ingredient
     */
    public final void add(final ItemStack ingredient, final RecipeItem rItem) {
        this.add(rItem.toIngredient(ingredient));
    }

    /**
     * Add an ingredient to this based on the given RecipeItem
     *
     * @param rItem the RecipeItem that matches the ingredient
     */
    public final void addGeneric(final RecipeItem rItem) {
        this.add(rItem.toIngredientGeneric());
    }

    private void add(final Ingredient ingredient) {
        for (final var existing : this.ingredients) {
            if (existing.isSimilar(ingredient)) {
                existing.setAmount(existing.getAmount() + 1);
                return;
            }
        }
        ingredient.setAmount(1);
        this.ingredients.add(ingredient);
    }

    /**
     * returns an Potion item with cooked ingredients
     */
    public final ItemStack cook(final int state, final Player brewer) {

        final var potion = new ItemStack(Material.POTION);
        final var potionMeta = (PotionMeta) potion.getItemMeta();
        assert potionMeta != null;

        // cookedTime is always time in minutes, state may differ with number of ticks
        this.cookedTime = state;
        String cookedName = null;
        final var cookRecipe = this.getCookRecipe();
        final Brew brew;

        //int uid = Brew.generateUID();

        if (cookRecipe != null) {
            // Potion is best with cooking only
            final var quality = (int) Math.round((this.getIngredientQuality(cookRecipe) + this.getCookingQuality(cookRecipe, false)) / 2.0);
            final var alc = Math.round(cookRecipe.getAlcohol() * ((float) quality / 10.0f));
            Logging.debugLog("cooked potion has Quality: " + quality + ", Alc: " + alc);
            brew = new Brew(quality, alc, cookRecipe, this);
            final var lore = new BrewLore(brew, potionMeta);
            lore.updateQualityStars(false);
            lore.updateCustomLore();
            lore.updateAlc(false);
            lore.updateBrewer(brewer == null ? null : brewer.getDisplayName());
            lore.addOrReplaceEffects(brew.getEffects(), brew.getQuality());
            lore.write();

            cookedName = cookRecipe.getName(quality);
            cookRecipe.getColor().colorBrew(potionMeta, potion, false);
            brew.updateCustomModelData(potionMeta);

            if (cookRecipe.isGlint()) {
                potionMeta.addEnchant(Enchantment.MENDING, 1, true);
                potionMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
        } else {
            // new base potion
            brew = new Brew(this);

            if (state <= 0) {
                cookedName = lang.getEntry("Brew_ThickBrew");
                PotionColor.BLUE.colorBrew(potionMeta, potion, false);
            } else {
                final var cauldronRecipe = this.getCauldronRecipe();
                if (cauldronRecipe != null) {
                    Logging.debugLog("Found Cauldron Recipe: " + cauldronRecipe.getName());
                    cookedName = cauldronRecipe.getName();
                    if (cauldronRecipe.getLore() != null) {
                        final var lore = new BrewLore(brew, potionMeta);
                        lore.addCauldronLore(cauldronRecipe.getLore());
                        lore.write();
                    }
                    cauldronRecipe.getColor().colorBrew(potionMeta, potion, true);
                    if (VERSION.isOrLater(MinecraftVersion.V1_14) && cauldronRecipe.getCmData() != 0) {
                        potionMeta.setCustomModelData(cauldronRecipe.getCmData());
                    }
                }
            }
        }
        if (cookedName == null) {
            // if no name could be found
            cookedName = lang.getEntry("Brew_Undefined");
            PotionColor.CYAN.colorBrew(potionMeta, potion, true);
        }

        potionMeta.setDisplayName(BUtil.color("&f" + cookedName));
        //if (!P.use1_14) {
        // Before 1.14 the effects duration would strangely be only a quarter of what we tell it to be
        // This is due to the Duration Modifier, that is removed in 1.14
        //	uid *= 4;
        //}
        // This effect stores the UID in its Duration
        //potionMeta.addCustomEffect((PotionEffectType.REGENERATION).createEffect((uid * 4), 0), true);

        brew.touch();
        final var modifyEvent = new BrewModifyEvent(brew, potionMeta, BrewModifyEvent.Type.FILL, brewer);
        plugin.getServer().getPluginManager().callEvent(modifyEvent);
        if (modifyEvent.isCancelled()) {
            return null;
        }
        brew.save(potionMeta);
        potion.setItemMeta(potionMeta);
        plugin.getBreweryStats().metricsForCreate(false);

        return potion;
    }

    /**
     * returns amount of ingredients
     */
    public final int getIngredientsCount() {
        var count = 0;
        for (final var ing : this.ingredients) {
            count += ing.getAmount();
        }
        return count;
    }

    public final List<Ingredient> getIngredientList() {
        return this.ingredients;
    }

    /**
     * best recipe for current state of potion, STILL not always returns the correct one...
     */
    public final @Nullable BRecipe getBestRecipe(final BarrelWoodType wood, final float time, final boolean distilled) {
        return this.getBestRecipeFull(wood, time, distilled).getSuccessRecipe();
    }

    /**
     * best recipe for current state of potion, STILL not always returns the correct one...
     */
    public final BestRecipeResult getBestRecipeFull(final BarrelWoodType wood, final float time, final boolean distilled) {
        if (BRecipe.getAllRecipes().isEmpty()) {
            return new BestRecipeResult.NoRecipesRegistered();
        }

        // tracks the highest quality recipe using exact numbers, no rounding or clamping
        // if no legacy recipe can be found, this is the plugin's best guess at what the player is trying to make
        BRecipe bestRecipe = null;
        RecipeEvaluation bestEval = null;
        // the original Brewery plugin uses a different algorithm that rounds and clamps ingredient/cook/age/wood
        // qualities before adding them, so we have to do the same here to avoid breaking backward compatibility
        float quality = 0;
        BRecipe bestRecipeLegacy = null;
        RecipeEvaluation bestEvalLegacy = null;

        // FIXME: This should include BCauldronRecipes too. (Proper parent class needed!)
        for (final var recipe : BRecipe.getAllRecipes()) {
            final RecipeEvaluation completeRecipeEval;

            final var ingredientEval = this.getIngredientQualityFull(recipe);
            final var ingredientQuality = ingredientEval.getQuality();

            final var cookingEval = this.getCookingQualityFull(recipe, distilled);
            final var cookingQuality = cookingEval.getQuality();

            // age and wood quality cannot be fatal, only need to check ingredient and cooking
            final var isFatal = ingredientEval.isFatal() || cookingEval.isFatal();

            if (recipe.needsToAge() || time > 0.5) {
                // needs riping in barrel
                final var ageEval = this.getAgeQualityFull(recipe, time);
                final var ageQuality = ageEval.getQuality();

                final var woodEval = this.getWoodQualityFull(recipe, wood);
                final var woodQuality = woodEval.getQuality();

                // is this recipe better than the previous best?
                Logging.debugLog("Ingredient Quality: " + ingredientQuality + " Cooking Quality: " + cookingQuality +
                        " Wood Quality: " + woodQuality + " age Quality: " + ageQuality + " for " + recipe.getName(5));
                completeRecipeEval = RecipeEvaluation.combine(ingredientEval, cookingEval, ageEval, woodEval);

                final var averageQuality = (ingredientQuality + cookingQuality + woodQuality + ageQuality) / 4;
                if (!isFatal && averageQuality > quality) {
                    quality = (ingredientQuality + cookingQuality + woodQuality + ageQuality) / 4;
                    bestRecipeLegacy = recipe;
                    bestEvalLegacy = completeRecipeEval;
                }

            } else {
                // calculate quality without age and barrel
                Logging.debugLog("Ingredient Quality: " + ingredientQuality + " Cooking Quality: " + cookingQuality + " for " + recipe.getName(5));
                completeRecipeEval = RecipeEvaluation.combine(ingredientEval, cookingEval);

                final var averageQuality = (ingredientQuality + cookingQuality) / 2;
                if (!isFatal && averageQuality > quality) {
                    quality = averageQuality;
                    bestRecipeLegacy = recipe;
                    bestEvalLegacy = completeRecipeEval;
                }
            }

            if (bestEval == null || completeRecipeEval.compareMostToLeastComplexity(bestEval) > 0) {
                bestRecipe = recipe;
                bestEval = completeRecipeEval;
            }
        }

        if (bestRecipeLegacy != null) {
            Logging.debugLog(String.format("best recipe: %s has Quality=%.3f",
                    bestRecipeLegacy.getName(5), quality));
            return new BestRecipeResult.Found(bestRecipeLegacy, bestEvalLegacy);
        } else {
            Logging.debugLog(String.format("guess recipe: %s has Quality=%.3f",
                    bestRecipe.getName(5), bestEval.getTrueQuality()));
            return new BestRecipeResult.Error(bestRecipe, bestEval);
        }
    }

    /**
     * returns recipe that is cooking only and matches the ingredients and cooking time
     */
    public final @Nullable BRecipe getCookRecipe() {
        return this.getCookRecipeFull().getSuccessRecipe();
    }

    public final BestRecipeResult getCookRecipeFull() {
        final var result = this.getBestRecipeFull(BarrelWoodType.ANY, 0, false);

        // Check if best recipe is cooking only
        if (result instanceof BestRecipeResult.Found(final var recipe, final var eval)) {
            if (recipe.isCookingOnly()) {
                return result;
            } else {
                eval.fatal(new BrewDefect.CookingNotNeeded());
                return new BestRecipeResult.Error(recipe, eval);
            }
        }
        return result;
    }

    /**
     * Get Cauldron Recipe that matches the contents of the cauldron
     */
    @Nullable
    public final BCauldronRecipe getCauldronRecipe() {
        BCauldronRecipe best = null;
        float bestMatch = 0;
        float match;
        for (final var recipe : BCauldronRecipe.getAllRecipes()) {
            match = recipe.getIngredientMatch(this.ingredients);
            if (match >= 10) {
                return recipe;
            }
            if (match > bestMatch) {
                best = recipe;
                bestMatch = match;
            }
        }
        return best;
    }

    /**
     * returns the currently best matching recipe for distilling for the ingredients and cooking time
     */
    public final @Nullable BRecipe getDistillRecipe(final BarrelWoodType wood, final float time) {
        return this.getDistillRecipeFull(wood, time).getSuccessRecipe();
    }

    public final BestRecipeResult getDistillRecipeFull(final BarrelWoodType wood, final float time) {
        final var result = this.getBestRecipeFull(wood, time, true);

        // Check if best recipe needs to be distilled
        if (result instanceof BestRecipeResult.Found(final var recipe, final var eval)) {
            if (recipe.needsDistilling()) {
                return result;
            } else {
                eval.fatal(new BrewDefect.DistillMismatch(true, false, recipe.isAlcoholic()));
                return new BestRecipeResult.Error(recipe, eval);
            }
        }
        return result;
    }

    /**
     * returns currently best matching recipe for ingredients, cooking- and ageingtime
     */
    public @Nullable BRecipe getAgeRecipe(final BarrelWoodType wood, final float time, final boolean distilled) {
        return this.getAgeRecipeFull(wood, time, distilled).getSuccessRecipe();
    }

    public final BestRecipeResult getAgeRecipeFull(final BarrelWoodType wood, final float time, final boolean distilled) {
        final var result = this.getBestRecipeFull(wood, time, distilled);

        if (result instanceof BestRecipeResult.Found(final var recipe, final var eval)) {
            if (recipe.needsToAge()) {
                return result;
            } else {
                eval.fatal(new BrewDefect.AgeMismatch(time, recipe.getAge(), recipe.isAlcoholic()));
                return new BestRecipeResult.Error(recipe, eval);
            }
        }
        return result;
    }

    /**
     * returns the quality of the ingredients conditioning given recipe, -1 if no recipe is near them
     */
    public final int getIngredientQuality(final BRecipe recipe) {
        return Math.round(this.getIngredientQualityFull(recipe).getQuality());
    }

    public final RecipeEvaluation getIngredientQualityFull(final BRecipe recipe) {
        final var eval = new RecipeEvaluation();

        final var missingIngredients = recipe.getMissingIngredients(this.ingredients);
        if (!missingIngredients.isEmpty()) {
            // when ingredients are not complete
            for (final var missing : missingIngredients) {
                eval.fatal(new BrewDefect.MissingIngredient(missing, missing.getAmount()));
            }
        }

        var badStuff = 0;
        for (final var ingredient : this.ingredients) {
            final var amountInRecipe = recipe.amountOf(ingredient);
            final var count = ingredient.getAmount();
            if (amountInRecipe == 0) {
                // this ingredient doesn't belong into the recipe
                badStuff++;
                if (count > (this.getIngredientsCount() / 2)) {
                    // when more than half of the ingredients don't fit into the recipe
                    eval.fatal(new BrewDefect.WrongIngredient(ingredient));
                } else if (badStuff < this.ingredients.size()) {
                    // when there are other ingredients
                    final var badIngredientDeduction = count * (recipe.getDifficulty() / 2.0f);
                    eval.deduct(new BrewDefect.WrongIngredient(ingredient), badIngredientDeduction);
                } else {
                    // ingredients don't fit at all
                    eval.fatal(new BrewDefect.WrongIngredient(ingredient));
                }
            } else if (count != amountInRecipe) {
                // calculate the quality
                final var ingredientCountDeduction = ((float) Math.abs(count - amountInRecipe) / recipe.allowedCountDiff(amountInRecipe)) * 10.0f;
                eval.deduct(new BrewDefect.WrongCount(ingredient, amountInRecipe), ingredientCountDeduction);
            }
        }
        return eval;
    }

    /**
     * returns the quality regarding the cooking-time conditioning given Recipe
     */
    public final int getCookingQuality(final BRecipe recipe, final boolean distilled) {
        return Math.round(this.getCookingQualityFull(recipe, distilled).getQuality());
    }

    public final RecipeEvaluation getCookingQualityFull(final BRecipe recipe, final boolean distilled) {
        final var eval = new RecipeEvaluation();
        if (recipe.needsDistilling() != distilled) {
            eval.fatal(new BrewDefect.DistillMismatch(distilled, recipe.needsDistilling(), recipe.isAlcoholic()));
        }

        if (this.cookedTime < 1) {
            eval.deduct(new BrewDefect.CookTimeMismatch(0, recipe.getCookingTime()), 10);
        } else if (this.cookedTime != recipe.getCookingTime()) {
            final var cookTimeDeduction = ((float) Math.abs(this.cookedTime - recipe.getCookingTime()) / recipe.allowedTimeDiff(recipe.getCookingTime())) * 10.0f;
            eval.deduct(new BrewDefect.CookTimeMismatch(this.cookedTime, recipe.getCookingTime()), cookTimeDeduction);
        }
        return eval;
    }

    /**
     * returns pseudo quality of distilling. 0 if doesn't match the need of the recipes distilling
     */
    public final int getDistillQuality(final BRecipe recipe, final byte distillRuns) {
        if (recipe.needsDistilling() != distillRuns > 0) {
            return 0;
        }
        return 10 - Math.abs(recipe.getDistillruns() - distillRuns);
    }

    /**
     * returns the quality regarding the barrel wood conditioning given Recipe
     */
    public final int getWoodQuality(final BRecipe recipe, final BarrelWoodType wood) {
        return Math.round(this.getWoodQualityFull(recipe, wood).getQuality());
    }

    public final RecipeEvaluation getWoodQualityFull(final BRecipe recipe, final BarrelWoodType wood) {
        final var eval = new RecipeEvaluation();
        if (recipe.usesAnyWood()) {
            // type of wood doesnt matter
            return eval;
        }

        if (wood != recipe.getWood()) {
            final float woodDeduction;
            if (config.isNewBarrelTypeAlgorithm()) {
                woodDeduction = this.getWoodQualityNew(recipe, wood);
            } else {
                woodDeduction = recipe.getWoodDiff(wood.getIndex()) * recipe.getDifficulty();
            }
            eval.deduct(new BrewDefect.WrongWood(wood, recipe.getWood()), woodDeduction);
        }
        return eval;
    }

    // At difficulty 1, distances 0-5 have quality 10, 10, 9, 8, 7, 6
    // At difficulty 5, distances 0-5 have quality 10, 8, 4, 1, 0, 0
    // At difficulty 10, distances 0-5 have quality 10, 5, 0, 0, 0, 0
    // See: https://www.desmos.com/calculator/aaoixs2qo7
    private int getWoodQualityNew(final BRecipe recipe, final BarrelWoodType wood) {
        final var recipeWood = recipe.getWood();
        final var baseQuality = switch (recipeWood.getDistance(wood)) {
            case 0 -> 10.0f;
            case 1 -> 9.0f;
            case 2 -> 7.75f;
            case 3 -> 6.25f;
            case 4 -> 4.5f;
            case 5 -> 2.5f;
            default -> 0.0f;
        };
        if (baseQuality == 0.0f) {
            return 0;
        }
        final var quality = 10f - (10f - baseQuality) * 0.5f * recipe.getDifficulty();
        return Math.max(Math.round(quality), 0);
    }

    /**
     * returns the quality regarding the ageing time conditioning given Recipe
     */
    public final int getAgeQuality(final BRecipe recipe, final float time) {
        return Math.round(this.getAgeQualityFull(recipe, time).getQuality());
    }

    public final RecipeEvaluation getAgeQualityFull(final BRecipe recipe, final float time) {
        final var eval = new RecipeEvaluation();
        if (!BUtil.isClose(time, recipe.getAge())) {
            final var ageDeduction = Math.abs(time - recipe.getAge()) * ((float) recipe.getDifficulty() / 2);
            eval.deduct(new BrewDefect.AgeMismatch(time, recipe.getAge(), recipe.isAlcoholic()), ageDeduction);
        }
        return eval;
    }

    @Override
    public final boolean equals(final Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof final BIngredients other)) return false;
        return this.cookedTime == other.cookedTime &&
                this.ingredients.equals(other.ingredients);
    }

    // Creates a copy ingredients
    public final BIngredients copy() {
        final var copy = new BIngredients();
        copy.ingredients.addAll(this.ingredients);
        copy.cookedTime = this.cookedTime;
        return copy;
    }

    @Override
    public final String toString() {
        final var ingredientsStr = this.ingredients.stream()
                .map(DebuggableItem::debug)
                .collect(Collectors.joining(", ", "[", "]"));
        return new StringJoiner(", ", "BIngredients{", "}")
                .add("cookedTime=" + this.cookedTime)
                .add("ingredients=" + ingredientsStr)
                .toString();
    }

    public final void save(final DataOutputStream out) throws IOException {
        out.writeInt(this.cookedTime);
        out.writeByte(this.ingredients.size());
        for (final var ing : this.ingredients) {
            ing.saveTo(out);
            out.writeShort(Math.min(ing.getAmount(), Short.MAX_VALUE));
        }
    }

    // saves data into main Ingredient section. Returns the save id
    // Only needed for legacy potions
    public final int saveLegacy(final ConfigurationSection config) {
        final var path = "Ingredients." + this.id;
        if (this.cookedTime != 0) {
            config.set(path + ".cookedTime", this.cookedTime);
        }
        config.set(path + ".mats", this.serializeIngredients());
        return this.id;
    }

    // Serialize Ingredients to String for storing in yml, ie for Cauldrons
    public final String serializeIngredients() {
        final var byteStream = new ByteArrayOutputStream();
        try (final var out = new DataOutputStream(new Base91EncoderStream(byteStream))) {
            out.writeByte(Brew.SAVE_VER);
            this.save(out);
        } catch (final IOException e) {
            Logging.errorLog("Failed to serialize Ingredients", e);
            return "";
        }
        return byteStream.toString();
    }

}
