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

package com.dre.brewery.commands.subcommands;

import com.dre.brewery.BIngredients;
import com.dre.brewery.Brew;
import com.dre.brewery.BreweryPlugin;
import com.dre.brewery.commands.SubCommand;
import com.dre.brewery.configuration.files.Lang;
import com.dre.brewery.recipe.*;
import com.dre.brewery.utility.Logging;
import com.dre.brewery.utility.MinecraftVersion;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public final class DebugInfoCommand implements SubCommand {


    private static void logAllRecipes(final BIngredients ingredients, final Brew brew) {
        Logging.log("&lIngredients:");
        for (final var ing : ingredients.getIngredientList()) {
            Logging.log(ing.toString());
        }
        Logging.log("&lTesting Recipes");
        for (final var recipe : BRecipe.getAllRecipes()) {
            logRecipe(recipe, brew);
        }
        final var distill = ingredients.getBestRecipeFull(brew.getWood(), brew.getAgeTime(), true);
        Logging.log("&lDistill-Recipe: &r" + ChatColor.stripColor(distill.toString()));
        final var nonDistill = ingredients.getBestRecipeFull(brew.getWood(), brew.getAgeTime(), false);
        Logging.log("&lRecipe: &r" + ChatColor.stripColor(nonDistill.toString()));
    }

    private static void logSpecificRecipe(final Player player, final BIngredients ingredients, final Brew brew, final String recipeName) {
        final var recipe = BRecipe.getMatching(recipeName);
        if (recipe == null) {
            Logging.msg(player, "Could not find Recipe " + recipeName);
            return;
        }
        Logging.log("&lIngredients in Recipe " + recipe.getRecipeName() + "&r&l:&r");
        for (final var ri : recipe.getIngredients()) {
            Logging.log(ri.toString());
        }
        Logging.log("&lIngredients in Brew:");
        for (final var ingredient : ingredients.getIngredientList()) {
            final var amountInRecipe = recipe.amountOf(ingredient);
            Logging.log(ingredient.toString() + ": " + amountInRecipe + " of this are in the Recipe");
        }
        logRecipe(recipe, brew);
    }

    private static void logRecipe(final BRecipe recipe, final Brew brew) {
        final var ingredients = brew.getIngredients();
        final var ingQ = ingredients.getIngredientQualityFull(recipe);
        Logging.log(String.format("%s&r ingQlty: %s", recipe.getRecipeName(), ingQ));
        final var cookQ = ingredients.getCookingQualityFull(recipe, false);
        Logging.log(String.format("%s&r cookQlty: %s", recipe.getRecipeName(), cookQ));
        final var cookDistQ = ingredients.getCookingQualityFull(recipe, true);
        Logging.log(String.format("%s&r cook+DistQlty: %s", recipe.getRecipeName(), cookDistQ));
        final var ageQ = ingredients.getAgeQualityFull(recipe, brew.getAgeTime());
        Logging.log(String.format("%s&r ageQlty: %s", recipe.getRecipeName(), ageQ));
        final var woodQ = ingredients.getWoodQualityFull(recipe, brew.getWood());
        Logging.log(String.format("%s&r woodQlty: %s", recipe.getRecipeName(), woodQ));
    }

    @Override
    public void execute(final BreweryPlugin breweryPlugin, final Lang lang, final CommandSender sender, final String label, final String[] args) {
        this.debugInfo(sender, args.length > 1 ? args[1] : null);
    }

    @Override
    public List<String> tabComplete(final BreweryPlugin breweryPlugin, final CommandSender sender, final String label, final String[] args) {
        return null;
    }

    @Override
    public String permission() {
        return "brewery.cmd.debuginfo";
    }

    @Override
    public boolean playerOnly() {
        return true;
    }

    public final void debugInfo(final CommandSender sender, final String recipeName) {
        if (BreweryPlugin.getMCVersion().isOrEarlier(MinecraftVersion.V1_9)) return;

        final var player = (Player) sender;
        final var hand = player.getInventory().getItemInMainHand();
        final var brew = Brew.get(hand);

        if (brew == null) return;

        Logging.log(brew.toString());
        final var ingredients = brew.getIngredients();

        if (recipeName == null) {
            logAllRecipes(ingredients, brew);
        } else {
            logSpecificRecipe(player, ingredients, brew, recipeName);
        }

        Logging.msg(player, "Debug Info for item written into Log");
    }

}
