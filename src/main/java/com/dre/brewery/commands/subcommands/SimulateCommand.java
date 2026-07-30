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

package com.dre.brewery.commands.subcommands;

import com.dre.brewery.*;
import com.dre.brewery.commands.SubCommand;
import com.dre.brewery.configuration.ConfigManager;
import com.dre.brewery.configuration.files.Lang;
import com.dre.brewery.recipe.BCauldronRecipe;
import com.dre.brewery.recipe.BRecipe;
import com.dre.brewery.recipe.RecipeItem;
import com.dre.brewery.utility.BUtil;
import com.dre.brewery.utility.Logging;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Stream;

public final class SimulateCommand implements SubCommand {

    private static void sendUsage(final Lang lang, final CommandSender sender) {
        lang.sendEntry(sender, "Etc_Usage");
        lang.sendEntry(sender, "Help_Simulate");
        lang.sendEntry(sender, "Help_Simulate_Options");
        lang.sendEntry(sender, "Help_Simulate_Recipe");
        lang.sendEntry(sender, "Help_Simulate_Cook");
        lang.sendEntry(sender, "Help_Simulate_Distill");
        lang.sendEntry(sender, "Help_Simulate_Age");
        lang.sendEntry(sender, "Help_Simulate_Brewer");
        lang.sendEntry(sender, "Help_Simulate_Player");
    }

    private static void simulate(final Lang lang, final CommandSender sender, final SimulationParameters simulation) {
        final var ingredients = new BIngredients();
        for (final var item : simulation.ingredients()) {
            for (var i = 0; i < item.getAmount(); i++) {
                ingredients.addGeneric(item);
            }
        }
        Logging.debugLog(String.format("simulate: ingredients=%s", ingredients));

        final var item = ingredients.cook(simulation.cookedTime(), simulation.brewer());
        final var brew = new Brew(ingredients);
        Logging.debugLog(String.format("simulate: cooked for %d minutes: %s",
                simulation.cookedTime(), ChatColor.stripColor(brew.toString())));

        if (simulation.distillRuns().isPresent()) {
            if (!(item.getItemMeta() instanceof final PotionMeta meta)) {
                lang.sendEntry(sender, "CMD_Cannot_Distill");
                return;
            }

            final var distillRuns = simulation.distillRuns().getAsInt();
            for (var i = 0; i < distillRuns; i++) {
                brew.distillSlot(item, meta);
            }
            Logging.debugLog(String.format("simulate: distilled for %d runs: %s",
                    distillRuns, ChatColor.stripColor(brew.toString())));

            if (!brew.hasRecipe()) {
                lang.sendEntry(sender, "CMD_Distill_Ruined");
                giveBrew(lang, sender, item, simulation.player());
                return;
            }
        }

        final var age = simulation.age();
        if (age != null) {
            final var barrelType = age.barrelType();
            if (barrelType == null) {
                lang.sendEntry(sender, "Error_MissingBarrelType");
                return;
            }
            brew.age(item, age.ageTime(), age.barrelType());
            Logging.debugLog(String.format("simulate: aged for %.3f years in %s barrel: %s",
                    age.ageTime(), age.barrelType().getFormattedName(), ChatColor.stripColor(brew.toString())));

            if (!brew.hasRecipe()) {
                lang.sendEntry(sender, "CMD_Age_Ruined");
            }
        }

        giveBrew(lang, sender, item, simulation.player());
    }

    private static void giveBrew(final Lang lang, final CommandSender sender, final ItemStack item, @Nullable final Player player) {
        if (player != null) {
            player.getInventory().addItem(item);
        } else if (sender instanceof final Player self) {
            self.getInventory().addItem(item);
        } else {
            final var fromItem = Brew.get(item);
            if (fromItem == null) {
                // this message should never appear since simulation was successful
                sender.sendMessage("&cCould not get brew from item");
                return;
            }
            sender.sendMessage(fromItem.toString());
        }
        lang.sendEntry(sender, "CMD_Simulated");
    }

    private static @Nullable List<String> tabComplete(final SimulationParser parser, final String arg) {
        final var completions = parser.getTabCompletions();
        return completions == null ? null : StringUtil.copyPartialMatches(arg, completions, new ArrayList<>());
    }

    private static List<String> getRecipeCompletions() {
        return Stream.concat(
                        BCauldronRecipe.getAllRecipes().stream()
                                .map(BCauldronRecipe::getName),
                        BRecipe.getAllRecipes().stream()
                                .mapMulti((recipe, consumer) -> {
                                    consumer.accept(recipe.getRecipeName());
                                    consumer.accept(recipe.getId());
                                })
                ).sorted()
                .distinct()
                .map(BUtil::quote)
                .toList();
    }

    private static List<String> getIngredientCompletions() {
        return Stream.concat(
                        BCauldronRecipe.getAllRecipes().stream()
                                .map(BCauldronRecipe::getIngredients),
                        BRecipe.getAllRecipes().stream()
                                .map(BRecipe::getIngredients)
                ).flatMap(List::stream)
                .map(RecipeItem::toConfigStringNoAmount)
                .sorted()
                .distinct()
                .map(BUtil::quote)
                .toList();
    }

    @Override
    public void execute(final BreweryPlugin breweryPlugin, final Lang lang, final CommandSender sender, final String label, final String[] args) {
        final var arguments = BUtil.splitStringKeepingQuotes(String.join(" ", args));

        final var parser = new SimulationParser();
        for (var i = 1; i <= arguments.size(); i++) {
            final Status status;
            if (i < arguments.size()) {
                final var arg = arguments.get(i);
                status = parser.parse(arg);
            } else {
                status = parser.finish();
            }

            if (status instanceof Status.Help) {
                sendUsage(lang, sender);
                return;
            } else if (status instanceof Status.Finished(final var simulation)) {
                simulate(lang, sender, simulation);
                return;
            } else if (status instanceof Status.Error(final var error1, final var args1)) {
                lang.sendEntry(sender, error1.getTranslationKey(), args1);
                return;
            }
        }
        throw new AssertionError("parser.finish() must not return Status.Updated()");
    }

    @Override
    public List<String> tabComplete(final BreweryPlugin breweryPlugin, final CommandSender sender, final String label, final String[] args) {
        final var arguments = BUtil.splitStringKeepingQuotes(String.join(" ", args));

        final var parser = new SimulationParser();
        for (var i = 1; i <= arguments.size(); i++) {
            final var arg = arguments.size() == 1 ? args[1] : arguments.get(i);

            if (i >= arguments.size() - 1) {
                final var rawLastArg = args[args.length - 1];

                // supporting tab complete mid-quote is too complicated
                if (rawLastArg.equals("\"")) {
                    return List.of();
                }

                // If player inputs `/brew simulate --age ` (notice the trailing space), rawLastArg will be blank.
                // Since splitStringKeepingQuotes() will remove the trailing space,
                // we need to first parse `--age` then tab complete on `` (blank).
                if (rawLastArg.isBlank()) {
                    final var status = parser.parse(arg);
                    if (status instanceof Status.Help || status instanceof Status.Error) {
                        return List.of();
                    }
                    return tabComplete(parser, rawLastArg);
                }

                return tabComplete(parser, arg);
            }

            final var status = parser.parse(arg);
            if (status instanceof Status.Help || status instanceof Status.Error) {
                return List.of();
            }
        }
        throw new AssertionError("unreachable");
    }

    @Override
    public String permission() {
        return "brewery.cmd.create";
    }

    @Override
    public boolean playerOnly() {
        return false;
    }

    @AllArgsConstructor
    @Getter
    private enum ErrorType implements Translatable {
        INVALID_OPTION("CMD_Invalid_Option"),
        DUPLICATE_OPTION("CMD_Duplicate_Option"),
        RECIPE("Error_NoBrewName"),
        COOK("CMD_Invalid_Cook_Time"),
        DISTILL_RUNS("CMD_Invalid_Distill_Runs"),
        WOOD_TYPE("CMD_Invalid_Wood_Type"),
        AGE_TIME("CMD_Invalid_Age_Time"),
        PLAYER("Error_NoPlayer"),
        /**
         * Takes 2 parameters, [arg, prevArg]
         */
        INVALID_INGREDIENT("CMD_Invalid_Ingredient"),
        /**
         * Takes 0 parameters
         */
        MISSING_COOK("CMD_Missing_Cook_Time"),
        /**
         * Takes 0 parameters
         */
        MISSING_INGREDIENTS("CMD_Missing_Ingredients");

        private final String translationKey;
    }

    private sealed interface Status {
        /**
         * The parser was updated with the latest argument, and parsing should continue
         */
        record Updated() implements Status {
        }

        /**
         * Need to display command usage
         */
        record Help() implements Status {
        }

        /**
         * Parsing finished, next arguments are ingredients
         */
        record Finished(SimulationParameters simulation) implements Status {
        }

        /**
         * User error
         */
        record Error(Translatable error, Object... args) implements Status {
        }
    }

    @ToString
    private static final class SimulationParser {

        private static final List<String> helpStrings = List.of("help", "-h", "--help");
        private final List<RecipeItem> ingredients = new ArrayList<>();
        private final EnumSet<Option> options = EnumSet.noneOf(Option.class);
        @Nullable
        BarrelWoodType woodType = null;
        @Nullable
        private BRecipe recipe = null;
        private int cookedTime = -1;
        private int distillRuns = -1;
        private float ageTime = Float.NaN;
        @Nullable
        private Player brewer = null;
        @Nullable
        private Player player = null;
        private State state = State.OPTIONS;

        @Nullable
        private String prevArg = null;

        public final Status parse(final String arg) {
            if (arg.isBlank()) {
                return new Status.Updated();
            }

            switch (this.state) {

                case OPTIONS -> {
                    if (this.prevArg == null && helpStrings.contains(arg.toLowerCase(Locale.ROOT))) {
                        return new Status.Help();
                    }
                    if (!arg.startsWith("-")) {
                        if (this.prevArg == null) {
                            return new Status.Error(ErrorType.INVALID_OPTION, arg);
                        } else {
                            this.state = State.INGREDIENTS;
                            return this.parseIngredient(arg);
                        }
                    }

                    final var option = Option.get(arg);
                    if (option == null) {
                        return new Status.Error(ErrorType.INVALID_OPTION, arg);
                    }
                    if (!this.options.add(option)) {
                        return new Status.Error(ErrorType.DUPLICATE_OPTION, arg);
                    }
                    this.state = option.getState();
                }

                case RECIPE -> {
                    final var recipe = BRecipe.getMatching(arg);
                    if (recipe == null) {
                        return new Status.Error(ErrorType.RECIPE, arg);
                    }
                    this.recipe = recipe;
                    this.state = State.OPTIONS;
                }

                case COOK -> {
                    final var cookedTime = BUtil.parseInt(arg).orElse(-1);
                    if (cookedTime < 0) {
                        return new Status.Error(ErrorType.COOK, arg);
                    }
                    this.cookedTime = cookedTime;
                    this.state = State.OPTIONS;
                }

                case DISTILL -> {
                    final var distillRuns = BUtil.parseInt(arg).orElse(-1);
                    if (distillRuns <= 0) {
                        return new Status.Error(ErrorType.DISTILL_RUNS, arg);
                    }
                    this.distillRuns = distillRuns;
                    this.state = State.OPTIONS;
                }

                case WOOD -> {
                    final var woodType = BarrelWoodType.fromName(arg);
                    if (woodType == null || !woodType.isSpecific()) {
                        return new Status.Error(ErrorType.WOOD_TYPE, arg);
                    }
                    this.woodType = woodType;
                    this.state = State.AGE;
                }
                case AGE -> {
                    final var ageTime = BUtil.parseFloat(arg).orElse(-1);
                    if (ageTime <= 0) {
                        return new Status.Error(ErrorType.AGE_TIME, arg);
                    }
                    this.ageTime = ageTime;
                    this.state = State.OPTIONS;
                }

                case BREWER -> {
                    final var brewer = BUtil.getPlayerfromString(arg);
                    if (brewer == null) {
                        return new Status.Error(ErrorType.PLAYER, arg);
                    }
                    this.brewer = brewer;
                    this.state = State.OPTIONS;
                }

                case PLAYER -> {
                    final var player = BUtil.getPlayerfromString(arg);
                    if (player == null) {
                        return new Status.Error(ErrorType.PLAYER, arg);
                    }
                    this.player = player;
                    this.state = State.OPTIONS;
                }

                case INGREDIENTS -> {
                    return this.parseIngredient(arg);
                }

            }
            return this.update(arg);
        }

        private Status parseIngredient(final String arg) {
            // user probably meant "ingredient/#" instead of "ingredient #"
            if (BUtil.isInt(arg)) {
                final var prevIngredient = this.prevArg != null ? this.prevArg : ConfigManager.getConfig(Lang.class).getEntry("CMD_Ingredient");
                return new Status.Error(ErrorType.INVALID_INGREDIENT, arg, prevIngredient);
            }

            final var result = BRecipe.loadIngredientVerbose(arg);
            if (result instanceof BRecipe.IngredientResult.Error(final var error1, final var invalidPart)) {
                return new Status.Error(error1, invalidPart);
            }
            this.ingredients.add(((BRecipe.IngredientResult.Success) result).ingredient());

            return this.update(arg);
        }

        private Status update(final String arg) {
            this.prevArg = arg;
            return new Status.Updated();
        }

        public final Status finish() {
            final int cookedTime;
            if (this.options.contains(Option.COOK)) {
                cookedTime = this.cookedTime;
            } else if (this.recipe != null) {
                cookedTime = this.recipe.getCookingTime();
            } else {
                return new Status.Error(ErrorType.MISSING_COOK);
            }

            final OptionalInt distill;
            if (this.options.contains(Option.DISTILL)) {
                distill = OptionalInt.of(this.distillRuns);
            } else if (this.recipe != null && this.recipe.needsDistilling()) {
                distill = OptionalInt.of(this.recipe.getDistillruns());
            } else {
                distill = OptionalInt.empty();
            }

            final Age age;
            if (this.options.contains(Option.AGE)) {
                age = new Age(this.woodType, this.ageTime);
            } else if (this.recipe != null) {
                age = Age.of(this.recipe);
            } else {
                age = null;
            }

            final List<RecipeItem> ingredients = new ArrayList<>();
            if (this.recipe != null && this.ingredients.isEmpty()) {
                ingredients.addAll(this.recipe.getIngredients());
            } else if (!this.ingredients.isEmpty()) {
                ingredients.addAll(this.ingredients);
            } else {
                return new Status.Error(ErrorType.MISSING_INGREDIENTS);
            }

            return new Status.Finished(new SimulationParameters(cookedTime, distill, age, ingredients, this.brewer, this.player));
        }

        @Nullable
        public final List<String> getTabCompletions() {
            return switch (this.state) {

                case OPTIONS -> {
                    final List<String> completions = new ArrayList<>();
                    if (this.prevArg == null) {
                        completions.addAll(helpStrings);
                    }
                    if (this.options.contains(Option.RECIPE) || this.options.contains(Option.COOK)) {
                        completions.addAll(getIngredientCompletions());
                    }
                    completions.addAll(this.getOptionCompletions());
                    yield completions;
                }

                case RECIPE -> getRecipeCompletions();
                case COOK -> BUtil.numberRange(1, 30);
                case DISTILL -> BUtil.numberRange(1, 10);
                case WOOD -> BarrelWoodType.TAB_COMPLETIONS;
                case AGE -> BUtil.numberRange(1, 50);
                case BREWER, PLAYER -> null;
                case INGREDIENTS -> getIngredientCompletions();

            };
        }

        private List<String> getOptionCompletions() {
            return EnumSet.complementOf(this.options).stream()
                    .map(Option::getOptions)
                    .flatMap(List::stream)
                    .toList();
        }

        @Getter
        private enum Option {
            RECIPE(State.RECIPE, "-r", "--recipe"),
            COOK(State.COOK, "-c", "--cook"),
            DISTILL(State.DISTILL, "-d", "--distill"),
            AGE(State.WOOD, "-a", "--age"),
            BREWER(State.BREWER, "-b", "--brewer"),
            PLAYER(State.PLAYER, "-p", "--player");

            private final State state;
            private final List<String> options;

            Option(final State state, final String... options) {
                this.state = state;
                this.options = List.of(options);
            }

            public static @Nullable Option get(final String arg) {
                for (final var option : values()) {
                    if (option.matches(arg)) {
                        return option;
                    }
                }
                return null;
            }

            public boolean matches(final String arg) {
                return this.options.contains(arg.toLowerCase(Locale.ROOT));
            }
        }

        private enum State {
            OPTIONS, RECIPE, COOK, DISTILL, WOOD, AGE, BREWER, PLAYER, INGREDIENTS
        }

    }

    private record SimulationParameters(
            int cookedTime,
            OptionalInt distillRuns,
            @Nullable Age age,
            List<RecipeItem> ingredients,
            @Nullable Player brewer,
            @Nullable Player player
    ) {
    }

    private record Age(BarrelWoodType barrelType, float ageTime) {
        public static @Nullable Age of(final BRecipe recipe) {
            if (recipe.needsToAge()) {
                final var barrelType = recipe.getWood();
                return new Age(barrelType.isSpecific() ? barrelType : BarrelWoodType.OAK, recipe.getAge());
            }
            return null;
        }
    }

}
