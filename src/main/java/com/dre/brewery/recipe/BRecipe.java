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

import com.dre.brewery.*;
import com.dre.brewery.configuration.ConfigManager;
import com.dre.brewery.configuration.files.CustomItemsFile;
import com.dre.brewery.configuration.files.Lang;
import com.dre.brewery.configuration.sector.capsule.ConfigRecipe;
import com.dre.brewery.integration.PlaceholderAPIHook;
import com.dre.brewery.utility.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * A Recipe used to Brew a Brewery Potion.
 */
@Getter
@Setter
public final class BRecipe implements Cloneable {

    @Getter
    private static final List<BRecipe> recipes = new ArrayList<>();
    @Getter
    @Setter
    public static int numConfigRecipes; // The number of recipes in the list that are from config

    // info
    private String[] name;
    private boolean saveInData; // If this recipe should be saved in data and loaded again when the server restarts. Applicable to non-config recipes
    private String id; // ID that might be given by the config

    // brewing
    private List<RecipeItem> ingredients = new ArrayList<>(); // Items and amounts
    private int difficulty; // difficulty to brew the potion, how exact the instruction has to be followed
    private int cookingTime; // time to cook in cauldron
    private byte distillruns; // runs through the brewer
    private int distillTime; // time for one distill run in seconds
    private List<BarrelWoodType> barrelTypes = new ArrayList<>(); // barrel types the brew should be aged in
    private int age; // time in minecraft days for the potions to age in barrels

    // outcome
    private PotionColor color; // color of the distilled/finished potion
    private int alcohol; // Alcohol in perfect potion
    private List<Tuple<Integer, String>> lore; // Custom Lore on the Potion. The int is for Quality Lore, 0 = any, 1,2,3 = Bad,Middle,Good
    private int[] cmData; // Custom Model Data[3] for each quality
    private String[] itemModel; // item model Data[3] for each quality

    // drinking
    private List<BEffect> effects = new ArrayList<>(); // Special Effects when drinking
    private @Nullable List<Tuple<Integer, String>> playercmds; // Commands executed as the player when drinking
    private @Nullable List<Tuple<Integer, String>> servercmds; // Commands executed as the server when drinking
    private String drinkMsg; // Message when drinking
    private String drinkTitle; // Title to show when drinking
    private boolean glint; // If the potion should have a glint effect

    public BRecipe() {
    }

    /**
     * New BRecipe with Name.
     * <p>Use new BRecipe.Builder() for easier Recipe Creation
     *
     * @param name The name for all qualities
     */
    public BRecipe(final String name, @NotNull final PotionColor color) {
        this.name = new String[]{name};
        this.color = color;
        this.difficulty = 5;
    }

    /**
     * New BRecipe with Names.
     * <p>Use new BRecipe.Builder() for easier Recipe Creation
     *
     * @param names {name bad, name normal, name good}
     */
    public BRecipe(final String[] names, @NotNull final PotionColor color) {
        this.name = names;
        this.color = color;
        this.difficulty = 5;
    }

    @Nullable
    public static BRecipe fromConfig(final String recipeId, final ConfigRecipe configRecipe) {
        final var recipe = new BRecipe();
        recipe.id = recipeId;
        final var nameList = configRecipe.getName();
        if (nameList != null) {
            final var name = nameList.split("/");
            if (name.length > 2) {
                recipe.name = name;
            } else {
                recipe.name = new String[1];
                recipe.name[0] = name[0];
            }
        } else {
            Logging.errorLog(recipeId + ": Recipe Name missing or invalid!");
            return null;
        }
        if (recipe.getRecipeName() == null || recipe.getRecipeName().isEmpty()) {
            Logging.errorLog(recipeId + ": Recipe Name invalid");
            return null;
        }

        recipe.ingredients = loadIngredients(configRecipe.getIngredients(), recipeId);
        if (recipe.ingredients == null || recipe.ingredients.isEmpty()) {
            Logging.errorLog("No ingredients for: " + recipe.getRecipeName());
            return null;
        }
        recipe.cookingTime = configRecipe.getCookingTime() != null ? configRecipe.getCookingTime() : 0;
        final var dis = configRecipe.getDistillRuns() != null ? configRecipe.getDistillRuns() : 0;
        if (dis > Byte.MAX_VALUE) {
            recipe.distillruns = Byte.MAX_VALUE;
        } else {
            recipe.distillruns = (byte) dis;
        }
        recipe.distillTime = (configRecipe.getDistillTime() != null ? configRecipe.getDistillTime() : 0) * 20;
        recipe.setBarrelTypes(BarrelWoodType.listFromAny(configRecipe.getWood()));
        recipe.age = configRecipe.getAge() != null ? configRecipe.getAge() : 0;
        recipe.difficulty = configRecipe.getDifficulty() != null ? configRecipe.getDifficulty() : 0;
        recipe.alcohol = configRecipe.getAlcohol() != null ? configRecipe.getAlcohol() : 0;

        final var col = configRecipe.getColor() != null ? configRecipe.getColor() : "BLUE";
        recipe.color = PotionColor.fromString(col);
        if (recipe.color == PotionColor.WATER && !col.equals("WATER")) {
            Logging.errorLog("Invalid Color '" + col + "' in Recipe: " + recipe.getRecipeName());
            return null;
        }

        recipe.lore = loadQualityStringList(BUtil.getListSafely(configRecipe.getLore()), StringParser.ParseType.LORE);

        recipe.servercmds = loadQualityStringList(configRecipe.getServerCommands(), StringParser.ParseType.CMD);
        recipe.playercmds = loadQualityStringList(configRecipe.getPlayerCommands(), StringParser.ParseType.CMD);

        recipe.drinkMsg = BUtil.color(configRecipe.getDrinkMessage());
        recipe.drinkTitle = BUtil.color(configRecipe.getDrinkTitle());
        recipe.glint = configRecipe.getGlint() != null ? configRecipe.getGlint() : false;

        if (configRecipe.getCustomModelData() != null) {
            final var cmdParts = configRecipe.getCustomModelData().split("/");
            final var cmData = new int[3];
            for (var i = 0; i < 3; i++) {
                if (cmdParts.length > i) {
                    cmData[i] = BUtil.getRandomIntInRange(cmdParts[i]);
                } else {
                    cmData[i] = i == 0 ? 0 : cmData[i - 1];
                }
            }
            recipe.cmData = cmData;
        }

        if (configRecipe.getItemModel() != null) {
            final var cmdParts = configRecipe.getItemModel().split(";");
            final var cmData = new String[3];
            for (var i = 0; i < 3; i++) {
                if (cmdParts.length > i) {
                    cmData[i] = cmdParts[i];
                } else {
                    cmData[i] = i == 0 ? "" : cmData[i - 1];
                }
            }
            recipe.itemModel = cmData;
        }

        final List<String> effectStringList = configRecipe.getEffects() != null ? configRecipe.getEffects() : Collections.emptyList();
        for (final var effectString : effectStringList) {
            final var effect = new BEffect(effectString);
            if (effect.isValid()) {
                recipe.effects.add(effect);
            } else {
                Logging.errorLog("Error adding Effect to Recipe: " + recipe.getRecipeName());
            }
        }
        return recipe;
    }

    public static List<RecipeItem> loadIngredients(final ConfigurationSection cfg, final String recipeId) {
        final List<String> ingredientsList;
        if (cfg.isString(recipeId + ".ingredients")) {
            ingredientsList = new ArrayList<>(1);
            ingredientsList.add(cfg.getString(recipeId + ".ingredients", "x"));
        } else {
            ingredientsList = cfg.getStringList(recipeId + ".ingredients");
        }
        return loadIngredients(ingredientsList, recipeId);
    }

    public static List<RecipeItem> loadIngredients(List<String> stringList, final String recipeId) {
        if (stringList == null) {
            stringList = Collections.emptyList();
        }
        final List<RecipeItem> ingredients = new ArrayList<>();
        for (final var s : stringList) {
            final var result = loadIngredientVerbose(s);
            if (result instanceof IngredientResult.Success(final var ingredient)) {
                ingredients.add(ingredient);
            } else {
                final var error = (IngredientResult.Error) result;
                final var lang = ConfigManager.getConfig(Lang.class);
                final var errorMessage = lang.getEntry(error.error().getTranslationKey(), error.invalidPart());
                Logging.errorLog(recipeId + ": " + errorMessage);
                return null;
            }
        }
        return ingredients;
    }

    public static IngredientResult loadIngredientVerbose(final String item) {
        final var ingredParts = item.split("/");
        var amount = 1;
        if (ingredParts.length == 2) {
            amount = BUtil.getRandomIntInRange(ingredParts[1]);
            if (amount < 1) {
                return new IngredientResult.Error(IngredientError.INVALID_AMOUNT, ingredParts[1]);
            }
        }
        final String[] matParts;
        if (ingredParts[0].contains(",")) {
            matParts = ingredParts[0].split(",");
        } else if (ingredParts[0].contains(";")) {
            matParts = ingredParts[0].split(";");
        } else {
            matParts = ingredParts[0].split("\\.");
        }


        // Check if this is a Plugin Item
        final var pluginItem = matParts[0].split(":", 2);
        if (pluginItem.length > 1) {
            final RecipeItem custom = PluginItem.fromConfig(pluginItem[0], pluginItem[1]);
            if (custom != null) {
                custom.setAmount(amount);
                custom.makeImmutable();
                BCauldronRecipe.acceptedCustom.add(custom);
                return new IngredientResult.Success(custom);
            } else {
                // TODO Maybe load later ie on first use of recipe?
                return new IngredientResult.Error(IngredientError.INVALID_PLUGIN_ITEM, item);
            }
        }

        // Try to find this Ingredient as Custom Item
        for (var custom : ConfigManager.getConfig(CustomItemsFile.class).getRecipeItems().stream().filter(Objects::nonNull).toList()) {
            if (custom.getConfigId().equalsIgnoreCase(matParts[0])) {
                custom = custom.getMutableCopy();
                custom.setAmount(amount);
                custom.makeImmutable();
                if (custom.hasMaterials()) {
                    BCauldronRecipe.acceptedMaterials.addAll(custom.getMaterials());
                }
                // Add it as acceptedCustom
                if (!BCauldronRecipe.acceptedCustom.contains(custom)) {
                    BCauldronRecipe.acceptedCustom.add(custom);
                }
                return new IngredientResult.Success(custom);
            }
        }

        final var mat = MaterialUtil.getMaterialSafely(matParts[0]);
        short durability = -1;
        if (matParts.length == 2) {
            durability = (short) BUtil.getRandomIntInRange(matParts[1]);
        }
        if (mat != null) {
            final RecipeItem rItem;
            if (durability > -1) {
                rItem = new SimpleItem(mat, durability);
            } else {
                rItem = new SimpleItem(mat);
            }
            rItem.setAmount(amount);
            rItem.makeImmutable();
            BCauldronRecipe.acceptedMaterials.add(mat);
            BCauldronRecipe.acceptedSimple.add(mat);
            return new IngredientResult.Success(rItem);
        } else {
            return new IngredientResult.Error(IngredientError.INVALID_MATERIAL, ingredParts[0]);
        }
    }

    /**
     * Load a list of strings from a ConfigurationSection and parse the quality
     */
    @Nullable
    public static List<Tuple<Integer, String>> loadQualityStringList(final ConfigurationSection cfg, final String path, final StringParser.ParseType parseType) {
        final var load = BUtil.loadCfgStringList(cfg, path);
        if (load != null) {
            return loadQualityStringList(load, parseType);
        }
        return null;
    }

    public static List<Tuple<Integer, String>> loadQualityStringList(final List<String> stringList, final StringParser.ParseType parseType) {
        final List<Tuple<Integer, String>> result = new ArrayList<>();
        if (stringList == null) {
            return result;
        }
        for (final var line : stringList) {
            result.add(StringParser.parseQuality(line, parseType));
        }
        return result;
    }

    /**
     * Gets a Modifiable Sublist of the Recipes that are loaded by config.
     * <p>Changes are directly reflected by the main list of all recipes
     * <br>Changes to the main List of all recipes will make the reference to this sublist invalid
     *
     * <p>After adding or removing elements, BRecipe.numConfigRecipes MUST be updated!
     */
    public static List<BRecipe> getConfigRecipes() {
        return recipes.subList(0, numConfigRecipes);
    }

    /**
     * Gets a Modifiable Sublist of the Recipes that are added by plugins.
     * <p>Changes are directly reflected by the main list of all recipes
     * <br>Changes to the main List of all recipes will make the reference to this sublist invalid
     */
    public static List<BRecipe> getAddedRecipes() {
        return recipes.subList(numConfigRecipes, recipes.size());
    }

    /**
     * Gets the main List of all recipes.
     */
    public static List<BRecipe> getAllRecipes() {
        return recipes;
    }

    /**
     * Get the BRecipe that has the given name as one of its quality names.
     */
    @Nullable
    public static BRecipe getMatching(final String name) {
        final var mainNameRecipe = get(name);
        if (mainNameRecipe != null) {
            return mainNameRecipe;
        }
        for (final var recipe : recipes) {
            if (recipe.getName(1).equalsIgnoreCase(name)) {
                return recipe;
            } else if (recipe.getName(10).equalsIgnoreCase(name)) {
                return recipe;
            }
        }
        for (final var recipe : recipes) {
            if (name.equalsIgnoreCase(recipe.getId())) {
                return recipe;
            }
        }
        return null;
    }

    @Nullable
    public static BRecipe getById(final String id) {
        for (final var recipe : recipes) {
            if (id.equals(recipe.getId())) {
                return recipe;
            }
        }
        return null;
    }

    /**
     * Get the BRecipe that has that name as its name
     */
    @Nullable
    public static BRecipe get(final String name) {
        for (final var recipe : recipes) {
            if (recipe.getRecipeName().equalsIgnoreCase(name)) {
                return recipe;
            }
        }
        return null;
    }

    public final boolean isAlcoholic() {
        return this.alcohol > 0;
    }

    /**
     * check every part of the recipe for validity.
     */
    public final boolean isValid() {
        if (this.ingredients == null || this.ingredients.isEmpty()) {
            Logging.errorLog("No ingredients could be loaded for Recipe: " + this.getRecipeName());
            return false;
        }
        if (this.cookingTime < 1) {
            Logging.errorLog("Invalid cooking time '" + this.cookingTime + "' in Recipe: " + this.getRecipeName());
            return false;
        }
        if (this.distillruns < 0) {
            Logging.errorLog("Invalid distillruns '" + this.distillruns + "' in Recipe: " + this.getRecipeName());
            return false;
        }
        if (this.distillTime < 0) {
            Logging.errorLog("Invalid distilltime '" + this.distillTime + "' in Recipe: " + this.getRecipeName());
            return false;
        }
        if (this.age < 0) {
            Logging.errorLog("Invalid age time '" + this.age + "' in Recipe: " + this.getRecipeName());
            return false;
        }
        if (this.difficulty < 0 || this.difficulty > 10) {
            Logging.errorLog("Invalid difficulty '" + this.difficulty + "' in Recipe: " + this.getRecipeName());
            return false;
        }
        return true;
    }

    /**
     * allowed deviation to the recipes count of ingredients at the given difficulty
     */
    public final int allowedCountDiff(int count) {
        if (count < 8) {
            count = 8;
        }
        final var allowedCountDiff = Math.round((float) ((11.0 - this.difficulty) * (count / 10.0)));

        if (allowedCountDiff == 0) {
            return 1;
        }
        return allowedCountDiff;
    }

    /**
     * allowed deviation to the recipes cooking-time at the given difficulty
     */
    public final int allowedTimeDiff(int time) {
        if (time < 8) {
            time = 8;
        }
        final var allowedTimeDiff = Math.round((float) ((11.0 - this.difficulty) * (time / 10.0)));

        if (allowedTimeDiff == 0) {
            return 1;
        }
        return allowedTimeDiff;
    }

    /**
     * Gets the <strong>primary</strong> barrel type out of all supported.
     *
     * @return the barrel type
     * @see #getBarrelTypes() for the full list
     */
    public final BarrelWoodType getWood() {
        return this.barrelTypes.isEmpty() ? BarrelWoodType.ANY : this.barrelTypes.getFirst();
    }

    public final void setWood(final BarrelWoodType wood) {
        this.barrelTypes = Collections.singletonList(wood);
    }

    public final boolean usesAnyWood() {
        return this.getWood() == BarrelWoodType.ANY;
    }

    public final void setBarrelTypes(final List<BarrelWoodType> barrelTypes) {
        if (barrelTypes.stream().anyMatch(b -> b == BarrelWoodType.ANY)) {
            this.setWood(BarrelWoodType.ANY);
        } else {
            this.barrelTypes = barrelTypes;
        }
    }

    /**
     * difference between given and recipe-wanted woodtype
     */
    public final float getWoodDiff(final float wood) {
        return (float) this.barrelTypes.stream()
                .mapToDouble(w -> Math.abs(wood - w.getIndex()))
                .min()
                .orElse(0.0);
    }

    public final boolean isCookingOnly() {
        return this.age == 0 && this.distillruns == 0;
    }

    public final boolean needsDistilling() {
        return this.distillruns != 0;
    }

    public final boolean needsToAge() {
        return this.age != 0;
    }

    /**
     * true if given list misses an ingredient
     */
    public boolean isMissingIngredients(final List<Ingredient> list) {
        if (list.size() < this.ingredients.size()) {
            return true;
        }
        for (final var rItem : this.ingredients) {
            var matches = false;
            for (final var used : list) {
                if (rItem.matches(used)) {
                    matches = true;
                    break;
                }
            }
            if (!matches) {
                return true;
            }
        }
        return false;
    }

    public final List<RecipeItem> getMissingIngredients(final List<Ingredient> list) {
        final List<RecipeItem> missing = new ArrayList<>();
        for (final var rItem : this.ingredients) {
            var matches = false;
            for (final var used : list) {
                if (rItem.matches(used)) {
                    matches = true;
                    break;
                }
            }
            if (!matches) {
                missing.add(rItem);
            }
        }
        return missing;
    }

    public final void applyDrinkFeatures(final Player player, final int quality) {
        final var playerCmdsForQuality = this.getPlayercmdsForQuality(quality);
        if (playerCmdsForQuality != null) {
            for (final var cmd : playerCmdsForQuality) {
                this.scheduleCommand(player, cmd, player.getName(), quality, false);
            }
        }
        final var serverCmdsForQuality = this.getServercmdsForQuality(quality);
        if (serverCmdsForQuality != null) {
            for (final var cmd : serverCmdsForQuality) {
                this.scheduleCommand(player, cmd, player.getName(), quality, true);
            }
        }
        if (this.drinkMsg != null) {
            player.sendMessage(BUtil.applyPlaceholders(this.drinkMsg, player.getName(), quality));
        }
        if (this.drinkTitle != null) {
            player.sendTitle("", BUtil.applyPlaceholders(this.drinkTitle, player.getName(), quality), 10, 90, 30);
        }
    }

    private void scheduleCommand(final Player player, String cmd, final String playerName, final int quality, final boolean isServerCommand) {
        if (cmd.startsWith("/")) cmd = cmd.substring(1);
        if (cmd.contains("/")) {
            final var parts = cmd.split("/");
            final var command = parts[0].trim(); // Needs to be effectively final for scheduling
            cmd = parts[0].trim();
            final var delay = parts[1].trim();
            final var delayTicks = this.parseDelayToTicks(delay);
            if (delayTicks > 0) {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        BRecipe.this.executeCommand(player, command, playerName, quality, isServerCommand);
                    }
                }.runTaskLater(BreweryPlugin.getInstance(), delayTicks);
                return;
            }
        }
        // Execute command immediately if no delay is specified
        this.executeCommand(player, cmd, playerName, quality, isServerCommand);
    }

    private long parseDelayToTicks(final String delay) {
        try {
            if (delay.endsWith("s")) {
                final var seconds = Integer.parseInt(delay.substring(0, delay.length() - 1));
                return seconds * 20L; // 20 ticks per second
            } else if (delay.endsWith("m")) {
                final var minutes = Integer.parseInt(delay.substring(0, delay.length() - 1));
                return minutes * 1200L; // 1200 ticks per minute
            }
        } catch (final NumberFormatException e) {
            // Invalid format: Default to 0
        }
        return 0; // Immediately execute command
    }

    private void executeCommand(final Player player, final String cmd, final String playerName, final int quality, final boolean isServerCommand) {
        final var finalCommand = PlaceholderAPIHook.PLACEHOLDERAPI.setPlaceholders(player, BUtil.applyPlaceholders(cmd, playerName, quality));
        BreweryPlugin.getScheduler().runLater(() -> {
                    if (isServerCommand) {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand);
                    } else {
                        Bukkit.dispatchCommand(player, finalCommand);
                    }
                }, 0
        );
    }


    // Getter

    /**
     * Create a Potion from this Recipe with best values.
     * Quality can be set, but will reset to 10 if unset immutable and put in a barrel
     *
     * @param quality The Quality of the Brew
     * @return The Created Item
     */
    public final ItemStack create(final int quality, final Player player) {
        return this.createBrew(quality).createItem(this, player);
    }

    public final ItemStack create(final int quality) {
        return this.createBrew(quality).createItem(this);
    }

    /**
     * Create a Brew from this Recipe with best values.
     * Quality can be set, but will reset to 10 if unset immutable and put in a barrel
     *
     * @param quality The Quality of the Brew
     * @return The created Brew
     */
    public final Brew createBrew(final int quality) {
        final List<Ingredient> list = new ArrayList<>(this.ingredients.size());
        for (final var rItem : this.ingredients) {
            final var ing = rItem.toIngredientGeneric();
            ing.setAmount(rItem.getAmount());
            list.add(ing);
        }

        final var bIngredients = new BIngredients(list, this.cookingTime);

        return new Brew(bIngredients, quality, 0, this.distillruns, this.getAge(), this.getWood(), this.getRecipeName(), false, true, 0);
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

    /**
     * how many of a specific ingredient in the recipe
     */
    public final int amountOf(final Ingredient ing) {
        for (final var rItem : this.ingredients) {
            if (rItem.matches(ing)) {
                return rItem.getAmount();
            }
        }
        return 0;
    }

    /**
     * how many of a specific ingredient in the recipe
     */
    public int amountOf(final ItemStack item) {
        for (final var rItem : this.ingredients) {
            if (rItem.matches(item)) {
                return rItem.getAmount();
            }
        }
        return 0;
    }

    /**
     * Same as getName(5)
     */
    public final String getRecipeName() {
        return this.getName(5);
    }

    /**
     * name that fits the quality
     */
    public final String getName(final int quality) {
        if (this.name.length > 2) {
            if (quality <= 3) {
                return this.name[0];
            } else if (quality <= 7) {
                return this.name[1];
            } else {
                return this.name[2];
            }
        } else {
            return this.name[0];
        }
    }

    /**
     * If one of the quality names equalIgnoreCase given name
     */
    public boolean hasName(final String name) {
        for (final var test : this.name) {
            if (test.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    @NotNull
    public final PotionColor getColor() {
        return this.color;
    }

    public void setColor(@NotNull final PotionColor color) {
        this.color = color;
    }

    public final boolean hasLore() {
        return this.lore != null && !this.lore.isEmpty();
    }

    @Nullable
    public List<Tuple<Integer, String>> getLore() {
        return this.lore;
    }

    @Nullable
    public final List<String> getLoreForQuality(final int quality) {
        return this.getStringsForQuality(quality, this.lore);
    }

    @Nullable
    public final List<String> getPlayercmdsForQuality(final int quality) {
        return this.getStringsForQuality(quality, this.playercmds);
    }

    @Nullable
    public final List<String> getServercmdsForQuality(final int quality) {
        return this.getStringsForQuality(quality, this.servercmds);
    }

    /**
     * Get a quality filtered list of supported attributes
     */
    @Nullable
    public final List<String> getStringsForQuality(final int quality, final List<Tuple<Integer, String>> source) {
        if (source == null) return null;
        final int plus;
        if (quality <= 3) {
            plus = 1;
        } else if (quality <= 7) {
            plus = 2;
        } else {
            plus = 3;
        }
        final List<String> list = new ArrayList<>(source.size());
        for (final var line : source) {
            if (line.first() == 0 || line.first() == plus) {
                list.add(line.second());
            }
        }
        return list;
    }

    @Override
    public final String toString() {
        return "BRecipe{" +
                "name=" + Arrays.toString(this.name) +
                ", ingredients=" + this.ingredients +
                ", difficulty=" + this.difficulty +
                ", cookingTime=" + this.cookingTime +
                ", distillruns=" + this.distillruns +
                ", distillTime=" + this.distillTime +
                ", barrelTypes=" + this.barrelTypes +
                ", age=" + this.age +
                ", color=" + this.color +
                ", alcohol=" + this.alcohol +
                ", lore=" + this.lore +
                ", cmData=" + Arrays.toString(this.cmData) +
                ", effects=" + this.effects +
                ", playercmds=" + this.playercmds +
                ", servercmds=" + this.servercmds +
                ", drinkMsg='" + this.drinkMsg + '\'' +
                ", drinkTitle='" + this.drinkTitle + '\'' +
                ", glint=" + this.glint +
                '}';
    }

    @Override
    public final BRecipe clone() {
        try {
            final var clone = (BRecipe) super.clone();
            clone.name = this.name.clone();
            clone.ingredients = new ArrayList<>(this.ingredients.size());
            for (final var item : this.ingredients) {
                clone.ingredients.add(item.getMutableCopy());
            }
            clone.lore = (this.lore != null) ? new ArrayList<>(this.lore) : null;
            clone.playercmds = (this.playercmds != null) ? new ArrayList<>(this.playercmds) : null;
            clone.servercmds = (this.servercmds != null) ? new ArrayList<>(this.servercmds) : null;
            clone.effects = new ArrayList<>(this.effects.size());
            for (final var effect : this.effects) {
                clone.effects.add(effect.clone());
            }
            clone.cmData = (this.cmData != null) ? this.cmData.clone() : null;
            clone.drinkMsg = this.drinkMsg;
            clone.drinkTitle = this.drinkTitle;
            clone.glint = this.glint;
            clone.saveInData = this.saveInData;
            clone.id = this.id;
            clone.difficulty = this.difficulty;
            clone.cookingTime = this.cookingTime;
            clone.distillruns = this.distillruns;
            clone.distillTime = this.distillTime;
            clone.barrelTypes = this.barrelTypes;
            clone.age = this.age;
            clone.color = this.color;
            clone.alcohol = this.alcohol;
            return clone;
        } catch (final CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }


    @AllArgsConstructor
    @Getter
    public enum IngredientError implements Translatable {
        INVALID_AMOUNT("Error_InvalidAmount"),
        INVALID_PLUGIN_ITEM("Error_InvalidPluginItem"),
        INVALID_MATERIAL("Error_InvalidMaterial");

        private final String translationKey;
    }

    public sealed interface IngredientResult {
        record Success(RecipeItem ingredient) implements IngredientResult {
        }

        record Error(IngredientError error, String invalidPart) implements IngredientResult {
        }
    }

    /**
     * Builder to easily create Recipes
     */
    public static final class Builder {
        private final BRecipe recipe;

        public Builder(final String name) {
            this.recipe = new BRecipe(name, PotionColor.WATER);
        }

        public Builder(final String... names) {
            this.recipe = new BRecipe(names, PotionColor.WATER);
        }


        public Builder addIngredient(final RecipeItem... item) {
            Collections.addAll(this.recipe.ingredients, item);
            return this;
        }

        public Builder addIngredient(final ItemStack... item) {
            for (final var i : item) {
                final var customItem = new CustomItem(i);
                customItem.setAmount(i.getAmount());
                this.recipe.ingredients.add(customItem);
            }
            return this;
        }

        public Builder difficulty(final int difficulty) {
            this.recipe.difficulty = difficulty;
            return this;
        }

        public Builder color(final String colorString) {
            this.recipe.color = PotionColor.fromString(colorString);
            return this;
        }

        public Builder color(final PotionColor color) {
            this.recipe.color = color;
            return this;
        }

        public Builder color(final Color color) {
            this.recipe.color = PotionColor.fromColor(color);
            return this;
        }

        public Builder cook(final int cookTime) {
            this.recipe.cookingTime = cookTime;
            return this;
        }

        public Builder distill(final byte distillRuns, final int distillTime) {
            this.recipe.distillruns = distillRuns;
            this.recipe.distillTime = distillTime;
            return this;
        }

        public Builder age(final int age, final BarrelWoodType wood) {
            this.recipe.age = age;
            this.recipe.setWood(wood);
            return this;
        }

        public Builder age(final int age, final BarrelWoodType... barrelTypes) {
            this.recipe.age = age;
            this.recipe.setBarrelTypes(List.of(barrelTypes));
            return this;
        }

        public Builder alcohol(final int alcohol) {
            this.recipe.alcohol = alcohol;
            return this;
        }

        public Builder addLore(final String line) {
            return this.addLore(0, line);
        }

        /**
         * Add a Line of Lore
         *
         * @param quality 0 for any quality, 1: bad, 2: normal, 3: good
         * @param line    The Line for custom lore to add
         * @return this
         */
        public final Builder addLore(final int quality, final String line) {
            if (quality < 0 || quality > 3) {
                throw new IllegalArgumentException("Lore Quality must be 0 - 3");
            }
            if (this.recipe.lore == null) {
                this.recipe.lore = new ArrayList<>();
            }
            this.recipe.lore.add(new Tuple<>(quality, line));
            return this;
        }

        /**
         * Add Commands that are executed by the player on drinking
         */
        public Builder addPlayerCmds(final String... cmds) {
            final List<Tuple<Integer, String>> playercmds = new ArrayList<>(cmds.length);

            for (final var cmd : cmds) {
                playercmds.add(StringParser.parseQuality(cmd, StringParser.ParseType.CMD));
            }
            if (this.recipe.playercmds == null) {
                this.recipe.playercmds = playercmds;
            } else {
                this.recipe.playercmds.addAll(playercmds);
            }
            return this;
        }

        /**
         * Add Commands that are executed by the server on drinking
         */
        public Builder addServerCmds(final String... cmds) {
            final List<Tuple<Integer, String>> servercmds = new ArrayList<>(cmds.length);

            for (final var cmd : cmds) {
                servercmds.add(StringParser.parseQuality(cmd, StringParser.ParseType.CMD));
            }
            if (this.recipe.servercmds == null) {
                this.recipe.servercmds = servercmds;
            } else {
                this.recipe.servercmds.addAll(servercmds);
            }
            return this;
        }

        /**
         * Add Message that is sent to the player in chat when he drinks the brew
         */
        public Builder drinkMsg(final String msg) {
            this.recipe.drinkMsg = msg;
            return this;
        }

        /**
         * Add Message that is sent to the player as a small title when he drinks the brew
         */
        public Builder drinkTitle(final String title) {
            this.recipe.drinkTitle = title;
            return this;
        }

        /**
         * Add a Glint to the Potion
         */
        public Builder glint(final boolean glint) {
            this.recipe.glint = glint;
            return this;
        }

        /**
         * Set the Optional ID of this recipe
         */
        public Builder setID(final String id) {
            this.recipe.id = id;
            return this;
        }

        /**
         * Add Custom Model Data for each Quality
         */
        public Builder addCustomModelData(final int bad, final int normal, final int good) {
            this.recipe.cmData = new int[]{bad, normal, good};
            return this;
        }

        public Builder addEffects(final BEffect... effects) {
            Collections.addAll(this.recipe.effects, effects);
            return this;
        }

        public BRecipe get() {
            if (this.recipe.name == null) {
                throw new IllegalArgumentException("Recipe name is null");
            }
            if (this.recipe.name.length != 1 && this.recipe.name.length != 3) {
                throw new IllegalArgumentException("Recipe name neither 1 nor 3");
            }
            if (this.recipe.color == null) {
                throw new IllegalArgumentException("Recipe has no color");
            }
            if (this.recipe.ingredients == null || this.recipe.ingredients.isEmpty()) {
                throw new IllegalArgumentException("Recipe has no ingredients");
            }
            if (!this.recipe.isValid()) {
                throw new IllegalArgumentException("Recipe has not valid");
            }
            for (final var ingredient : this.recipe.ingredients) {
                ingredient.makeImmutable();
            }
            return this.recipe;
        }
    }
}
