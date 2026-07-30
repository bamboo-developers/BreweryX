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

import com.dre.brewery.api.addons.AddonManager;
import com.dre.brewery.commands.CommandManager;
import com.dre.brewery.configuration.ConfigManager;
import com.dre.brewery.configuration.configurer.TranslationManager;
import com.dre.brewery.configuration.files.Config;
import com.dre.brewery.configuration.files.Lang;
import com.dre.brewery.integration.PlaceholderAPIHook;
import com.dre.brewery.integration.bstats.BreweryStats;
import com.dre.brewery.integration.bstats.BreweryXStats;
import com.dre.brewery.integration.listeners.IntegrationListener;
import com.dre.brewery.listeners.*;
import com.dre.brewery.recipe.*;
import com.dre.brewery.storage.DataManager;
import com.dre.brewery.storage.StorageInitException;
import com.dre.brewery.utility.Logging;
import com.dre.brewery.utility.MinecraftVersion;
import com.dre.brewery.utility.releases.ReleaseChecker;
import com.tcoded.folialib.FoliaLib;
import com.tcoded.folialib.impl.PlatformScheduler;
import io.papermc.lib.PaperLib;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter
public final class BreweryPlugin extends JavaPlugin {

    @Getter
    private static AddonManager addonManager;
    @Getter
    private static PlatformScheduler scheduler;
    @Getter
    private static BreweryPlugin instance;
    @Getter
    private static MinecraftVersion MCVersion;
    @Getter
    @Setter
    private static DataManager dataManager;


    private final Map<String, Function<ItemLoader, Ingredient>> ingredientLoaders = new HashMap<>(); // Registrations
    private BreweryStats breweryStats; // Metrics

    {
        // Basically just racing to be the first code to execute.
        // Okaeri configs are used in static fields, so we need this code to execute before Okaeri
        // can start loading.
        instance = this;
        this.migrateBreweryDataFolder();
        MCVersion = MinecraftVersion.getIt();
        FoliaLib foliaLib = new FoliaLib(this);
        scheduler = foliaLib.getScheduler();
        TranslationManager.newInstance(this.getDataFolder());
    }

    @Override
    public void onLoad() {

        // Campfires are weird. Initialize once now, so it doesn't lag later when we check for campfires under Cauldrons
        this.getServer().createBlockData(Material.CAMPFIRE);
    }

    @Override
    public void onEnable() {

        // Register Item Loaders
        CustomItem.registerItemLoader(this);
        SimpleItem.registerItemLoader(this);
        PluginItem.registerItemLoader(this);

        // Load config
        final var config = ConfigManager.getConfig(Config.class);
        if (config.isFirstCreation()) {
            config.onFirstCreation();
        }

        // Load lang
        TranslationManager.getInstance().updateTranslationFiles();
        ConfigManager.newInstance(Lang.class, false);

        BSealer.registerRecipe(); // Sealing table recipe
        ConfigManager.registerDefaultPluginItems(); // Register plugin items

        // Load Addons
        addonManager = new AddonManager(this);
        addonManager.loadAddons();

        ConfigManager.loadCauldronIngredients();
        ConfigManager.loadRecipes();
        ConfigManager.loadDistortWords();
        ConfigManager.loadSeed();
        this.breweryStats = new BreweryStats(); // Load metrics


        Logging.log("Minecraft version&7:&a " + MCVersion.getVersion());
        if (MinecraftVersion.isBelowMinimum()) {
            Logging.errorLog("BreweryX requires Minecraft 1.21.5 or newer. Disabling.");
            this.getServer().getPluginManager().disablePlugin(this);
            return;
        }
        if (MCVersion == MinecraftVersion.UNKNOWN) {
            Logging.warningLog("This version of Minecraft is not known to Brewery! Please be wary of bugs or other issues that may occur in this version.");
        }


        // Load DataManager
        try {
            dataManager = DataManager.createDataManager(config.getStorage());
        } catch (final StorageInitException e) {
            Logging.errorLog("Failed to initialize DataManager!", e);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // Load objects
        DataManager.loadMiscData(dataManager.getBreweryMiscData());
        dataManager.getAllBarrels().thenAcceptAsync(barrels -> barrels.stream()
                .filter(Objects::nonNull)
                .forEach(Barrel::registerBarrel)
        );
        BCauldron.getBcauldrons().putAll(dataManager.getAllCauldrons().stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(
                        BCauldron::getBlock, Function.identity(),
                        (existing, replacement) -> replacement // Issues#68
                )));
        BCauldron.startAllFoliaParticleTasks();
        BPlayer.getPlayers().putAll(dataManager.getAllPlayers()
                .stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(
                        BPlayer::getUuid,
                        Function.identity()
                )));
        Wakeup.getWakeups().addAll(dataManager.getAllWakeups()
                .stream()
                .filter(Objects::nonNull)
                .toList());

        addonManager.enableAddons();
        // Setup Metrics
        this.breweryStats.setupBStats();
        new BreweryXStats().setupBStats();

        // Register command and aliases
        final var defaultCommand = this.getCommand("breweryx");
        defaultCommand.setExecutor(new CommandManager());
        try {
            // This has to be done reflectively because Spigot doesn't expose the CommandMap through the API
            final var bukkitCommandMap = this.getServer().getClass().getDeclaredField("commandMap");
            bukkitCommandMap.setAccessible(true);

            final var commandMap = (CommandMap) bukkitCommandMap.get(this.getServer());

            for (final var alias : config.getCommandAliases()) {
                commandMap.register(alias, "breweryx", defaultCommand);
            }
        } catch (final Exception e) {
            Logging.errorLog("Failed to register command aliases!", e);
        }

        // Register Listeners
        final var pluginManager = this.getServer().getPluginManager();
        pluginManager.registerEvents(new BlockListener(), this);
        pluginManager.registerEvents(new PlayerListener(), this);
        pluginManager.registerEvents(new EntityListener(), this);
        pluginManager.registerEvents(new InventoryListener(), this);
        pluginManager.registerEvents(new IntegrationListener(), this);
        pluginManager.registerEvents(new CauldronListener(), this);
        pluginManager.registerEvents(new WorldListener(), this);

        // Heartbeat
        BreweryPlugin.getScheduler().runTimer(new BreweryRunnable(), 650, 1200);
        BreweryPlugin.getScheduler().runTimer(new DrunkRunnable(), 120, 120);
        if (!MinecraftVersion.isFolia())
            BreweryPlugin.getScheduler().runTimer(new CauldronParticles(), 1, 1);


        // Register PlaceholderAPI Placeholders
        final var placeholderAPIHook = PlaceholderAPIHook.PLACEHOLDERAPI;
        if (placeholderAPIHook.isEnabled()) {
            placeholderAPIHook.getInstance().register();
        }

        Logging.log("Using scheduler&7: &a" + scheduler.getClass().getSimpleName());
        Logging.log("Environment&7: &a" + Logging.getEnvironmentAsString());
        if (!PaperLib.isPaper()) {
            Logging.log("&aBreweryX performs best on Paper-based servers. Please consider switching to Paper for the best experience. &7https://papermc.io");
        }
        Logging.log("BreweryX enabled!");

        final var releaseChecker = ReleaseChecker.getInstance();
        releaseChecker.checkForUpdate().thenAccept(updateAvailable -> releaseChecker.notify(Bukkit.getConsoleSender()));
    }

    @Override
    public void onDisable() {
        if (addonManager != null) addonManager.unloadAddons();

        // Disable listeners
        HandlerList.unregisterAll(this);

        BCauldron.stopAllFoliaParticleTasks();

        // Stop schedulers
        BreweryPlugin.getScheduler().cancelAllTasks();

        // save Data to Disk
        if (dataManager != null) dataManager.exit(true, false);

        final var placeholderAPIHook = PlaceholderAPIHook.PLACEHOLDERAPI;
        if (placeholderAPIHook.isEnabled()) {
            placeholderAPIHook.getInstance().unregister();
        }

        Logging.log("BreweryX disabled!");
    }


    /**
     * For loading ingredients from ItemMeta.
     * <p>Register a Static function that takes an ItemLoader, containing a DataInputStream.
     * <p>Using the Stream it constructs a corresponding Ingredient for the chosen SaveID
     *
     * @param saveID  The SaveID should be a small identifier like "AB"
     * @param loadFct The Static Function that loads the Item, i.e.
     *                public static AItem loadFrom(ItemLoader loader)
     */
    public void registerForItemLoader(final String saveID, final Function<ItemLoader, Ingredient> loadFct) {
        this.ingredientLoaders.put(saveID, loadFct);
    }

    /**
     * Unregister the ItemLoader
     *
     * @param saveID the chosen SaveID
     */
    public void unRegisterItemLoader(final String saveID) {
        this.ingredientLoaders.remove(saveID);
    }


    // Runnables

    // Lots of users migrate from the original Brewery. Because of this,
    // we need to rename our 'Brewery' folder to 'BreweryX' ASAP. Before Okaeri loads.
    public void migrateBreweryDataFolder() {
        final var pluginsFolder = this.getDataFolder().getParentFile().getPath();

        final var breweryFolder = new File(pluginsFolder + File.separator + "Brewery");
        final var breweryXFolder = new File(pluginsFolder + File.separator + "BreweryX");

        if (breweryFolder.exists() && !breweryXFolder.exists()) {
            if (!breweryXFolder.exists()) {
                breweryXFolder.mkdirs();
            }

            final var files = breweryFolder.listFiles();
            if (files != null) {
                for (final var file : files) {
                    try {
                        Files.copy(file.toPath(), new File(breweryXFolder, file.getName()).toPath());
                    } catch (final IOException e) {
                        Logging.errorLog("Failed to move file: " + file.getName(), e);
                    }
                }
                Logging.log("&5Moved files from Brewery to BreweryX's data folder");
            }
        }
    }

    public static final class DrunkRunnable implements Runnable {
        @Override
        public final void run() {
            if (!BPlayer.isEmpty()) {
                BPlayer.drunkenness();
            }
        }
    }

    public static final class BreweryRunnable implements Runnable {
        @Override
        public final void run() {
            final var start = System.currentTimeMillis();

            // runs every min to update cooking time

            for (final var bCauldron : BCauldron.bcauldrons.values()) {
                BreweryPlugin.getScheduler().runAtLocationLater(bCauldron.getBlock().getLocation(), () -> {
                    if (!bCauldron.onUpdate()) {
                        BCauldron.remove(bCauldron.getBlock());
                    }
                }, 0);
            }


            Barrel.onUpdate();// runs every min to check and update ageing time

            MCBarrel.onUpdate();

            BPlayer.onUpdate();// updates players drunkenness


            //DataSave.autoSave();
            dataManager.tryAutoSave();

            Logging.debugLog(() -> "BreweryRunnable: " + (System.currentTimeMillis() - start) + "ms");
        }

    }

    public static final class CauldronParticles implements Runnable {

        private static final Config config = ConfigManager.getConfig(Config.class);

        @Override
        public final void run() {
            if (!config.isEnableCauldronParticles()) return;
            if (config.isMinimalParticles() && ThreadLocalRandom.current().nextFloat() > 0.5f) {
                return;
            }
            BCauldron.processCookEffects();
        }
    }
}
