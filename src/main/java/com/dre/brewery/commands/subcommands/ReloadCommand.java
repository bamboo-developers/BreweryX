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

import com.dre.brewery.BCauldron;
import com.dre.brewery.BSealer;
import com.dre.brewery.Barrel;
import com.dre.brewery.BreweryPlugin;
import com.dre.brewery.commands.CommandUtil;
import com.dre.brewery.commands.SubCommand;
import com.dre.brewery.configuration.ConfigManager;
import com.dre.brewery.configuration.configurer.TranslationManager;
import com.dre.brewery.configuration.files.Lang;
import com.dre.brewery.utility.BUtil;
import com.dre.brewery.utility.Logging;
import com.dre.brewery.utility.releases.ReleaseChecker;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;

import java.util.List;

public final class ReloadCommand implements SubCommand {

    @Getter
    private static CommandSender reloader;

    @Override
    public void execute(final BreweryPlugin breweryPlugin, final Lang lang, final CommandSender sender, final String label, final String[] args) {
        if (!sender.equals(Bukkit.getConsoleSender())) {
            reloader = sender;
        }


        try {
            // Reload translation manager
            TranslationManager.newInstance(breweryPlugin.getDataFolder());
            TranslationManager.getInstance().updateTranslationFiles();

            // Reload each config
            for (final var file : ConfigManager.LOADED_CONFIGS.values()) {
                try {
                    file.reload();
                } catch (final Throwable e) {
                    Logging.errorLog("Something went wrong trying to load " + file.getBindFile().getFileName() + "!", e);
                }
            }

            // Reload Cauldron Ingredients
            ConfigManager.loadCauldronIngredients();
            // Reload Recipes
            ConfigManager.loadRecipes();
            // Reload Seed
            ConfigManager.loadSeed();

            // Reload Cauldron Particle Recipes
            BCauldron.reload();

            // Drop caches derived from the config that have no reload hook of their own
            Barrel.invalidateEtcBarrelTitleCache();
            BUtil.invalidateDrainItemMapCache();

            // Clear Recipe completions
            CommandUtil.reloadTabCompleter();

            // Sealing table recipe
            BSealer.registerRecipe();

            // Let addons know this command was executed
            BreweryPlugin.getAddonManager().reloadAddons();

            lang.sendEntry(sender, "CMD_Reload");

            final var releaseChecker = ReleaseChecker.getInstance(true);
            releaseChecker.checkForUpdate().thenAccept(updateAvailable -> {
                if (!(sender instanceof final ConsoleCommandSender consoleSender)) {
                    releaseChecker.notify(sender);
                } else {
                    releaseChecker.notify(consoleSender);
                }
            });
        } catch (final Throwable e) {
            Logging.errorLog("Something went wrong trying to reload Brewery!", e);
        }
        // Make sure this reloader is set to null after
        reloader = null;
    }

    @Override
    public List<String> tabComplete(final BreweryPlugin breweryPlugin, final CommandSender sender, final String label, final String[] args) {
        return null;
    }

    @Override
    public String permission() {
        return "brewery.cmd.reload";
    }

    @Override
    public boolean playerOnly() {
        return false;
    }
}
