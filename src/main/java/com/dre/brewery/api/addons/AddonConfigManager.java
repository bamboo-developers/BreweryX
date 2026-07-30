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

package com.dre.brewery.api.addons;

import com.dre.brewery.BreweryPlugin;
import com.dre.brewery.configuration.ConfigHead;
import com.dre.brewery.configuration.annotation.OkaeriConfigFileOptions;
import eu.okaeri.configs.configurer.Configurer;
import eu.okaeri.configs.serdes.BidirectionalTransformer;
import eu.okaeri.configs.serdes.OkaeriSerdesPack;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Collection;

/**
 * Management of addon configuration files.
 *
 * @see AddonConfigFile
 * @see OkaeriConfigFileOptions
 */
public final class AddonConfigManager {

    private final ConfigHead INSTANCE;

    public AddonConfigManager(final BreweryAddon addon) {
        this.INSTANCE = new ConfigHead(BreweryPlugin.getInstance().getDataFolder().toPath().resolve("addons").resolve(addon.getAddonInfo().name()));
    }

    public AddonConfigManager(final Path addonDataFolder) {
        this.INSTANCE = new ConfigHead(addonDataFolder);
    }

    /**
     * Get a config instance from the LOADED_CONFIGS map, or create a new instance if it doesn't exist
     *
     * @param configClass The class of the config to get
     * @param <T>         The type of the config
     * @return The config instance
     */
    public <T extends AddonConfigFile> T getConfig(final Class<T> configClass) {
        return this.INSTANCE.getConfig(configClass);
    }

    /**
     * Replaces a config instance in the LOADED_CONFIGS map with a new instance of the same class
     *
     * @param configClass The class of the config to replace
     * @param <T>         The type of the config
     */
    public <T extends AddonConfigFile> void newInstance(final Class<T> configClass, final boolean overwrite) {
        this.INSTANCE.newInstance(configClass, overwrite);
    }


    /**
     * Get the file path of a config class
     *
     * @param configClass The class of the config to get the file name of
     * @param <T>         The type of the config
     * @return The file name
     */
    public <T extends AddonConfigFile> Path getFilePath(final Class<T> configClass) {
        return this.INSTANCE.getFilePath(configClass);
    }


    public Collection<AddonConfigFile> getLoadedConfigs() {
        return this.INSTANCE.LOADED_CONFIGS.values().stream().map(AddonConfigFile.class::cast).toList();
    }


    /**
     * Create a new config instance with a custom file name, configurer, serdes pack, and puts it in the LOADED_CONFIGS map
     *
     * @param configClass The class of the config to create
     * @param file        The file to use
     * @param configurer  The configurer to use
     * @param serdesPack  The serdes pack to use
     * @param <T>         The type of the config
     * @return The new config instance
     */
    private <T extends AddonConfigFile> T createConfig(final Class<T> configClass, final Path file, final Configurer configurer, final OkaeriSerdesPack serdesPack, final boolean update, final boolean removeOrphans) {
        return this.INSTANCE.createConfig(configClass, file, configurer, serdesPack, update, removeOrphans);
    }

    /**
     * Create a new config instance using a config class' annotation
     *
     * @param configClass The class of the config to create
     * @param <T>         The type of the config
     * @return The new config instance
     */
    public <T extends AddonConfigFile> T createConfig(final Class<T> configClass) {
        return this.INSTANCE.createConfig(configClass);
    }

    @Nullable
    public <T extends AddonConfigFile> T createBlankConfigInstance(final Class<T> configClass) {
        return this.INSTANCE.createBlankConfigInstance(configClass);
    }


    public void addSerdesPacks(final OkaeriSerdesPack... serdesPacks) {
        this.INSTANCE.addSerdesPacks(serdesPacks);
    }

    public void addBiDirectionalTransformers(final BidirectionalTransformer<?, ?>... transformers) {
        this.INSTANCE.addBidirectionalTransformers(transformers);
    }

    public void addConfigurer(final Configurer configurer) {
        this.INSTANCE.addConfigurer(configurer);
    }

    // Util

    public void createFileFromResources(final String resourcesPath, final Path destination) {
        this.INSTANCE.createFileFromResources(resourcesPath, destination);
    }


    public OkaeriConfigFileOptions getOkaeriConfigFileOptions(final Class<? extends AddonConfigFile> configClass) {
        return this.INSTANCE.getOkaeriConfigFileOptions(configClass);
    }

    public Path getDataFolder() {
        return this.INSTANCE.DATA_FOLDER;
    }
}
