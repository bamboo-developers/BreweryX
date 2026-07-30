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
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

/**
 * A class to manage files for an addon.
 * Deprecated, use {@link AddonConfigFile} and {@link AddonConfigManager} instead.
 */
@Deprecated(since = "3.4.3-SNAPSHOT")
public class AddonFileManager {
    private static final BreweryPlugin plugin = BreweryPlugin.getInstance();

    private final File addonFolder;
    private final AddonLogger logger;
    private final File configFile;
    private final File jarFile;
    private YamlConfiguration addonConfig;

    public AddonFileManager(final BreweryAddon addon, final File jarFile) {
        this.jarFile = jarFile;
        final var addonName = addon.getClass().getSimpleName();
        this.addonFolder = new File(plugin.getDataFolder().getAbsolutePath() + File.separator + "addons" + File.separator + addonName);
        this.logger = addon.getAddonLogger();
        this.configFile = new File(this.addonFolder, addonName + ".yml");
        this.addonConfig = this.configFile.exists() ? YamlConfiguration.loadConfiguration(this.configFile) : null;
    }


    public void generateFile(final String fileName) {
        this.generateFile(new File(this.addonFolder, fileName));
    }

    public void generateFileAbsPath(final String absolutePath) {
        this.generateFile(new File(absolutePath));
    }

    public void generateFile(final File parent, final String fileName) {
        this.generateFile(new File(parent, fileName));
    }

    public final void generateFile(final File file) {
        this.createAddonFolder();
        try {
            if (!file.exists()) {
                file.createNewFile();
                try (final var jarInputStream = new JarInputStream(new FileInputStream(this.jarFile))) {
                    JarEntry jarEntry;
                    while ((jarEntry = jarInputStream.getNextJarEntry()) != null) {
                        if (jarEntry.isDirectory() || !jarEntry.getName().equals(file.getName())) {
                            continue;
                        }
                        final var outputStream = Files.newOutputStream(file.toPath());
                        final var buffer = new byte[1024];
                        int bytesRead;
                        while ((bytesRead = jarInputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, bytesRead);
                        }
                        outputStream.flush();
                        outputStream.close();
                        break;
                    }
                }
            }
        } catch (final IOException ex) {
            this.logger.severe("Failed to generate file " + file.getName(), ex);
        }
    }

    public File getFile(final String fileName) {
        this.createAddonFolder();
        return new File(this.addonFolder, fileName);
    }

    public YamlConfiguration getYamlConfiguration(final String fileName) {
        this.createAddonFolder();
        return YamlConfiguration.loadConfiguration(new File(this.addonFolder, fileName));
    }

    public File getAddonFolder() {
        return this.addonFolder;
    }


    public YamlConfiguration getAddonConfig() {
        this.generateAddonConfig();
        return this.addonConfig;
    }

    public void saveAddonConfig() {
        this.generateAddonConfig();
        try {
            this.addonConfig.save(this.configFile);
        } catch (final IOException ex) {
            this.logger.severe("Failed to save addon config", ex);
        }
    }

    private void generateAddonConfig() {
        if (this.addonConfig == null) {
            this.generateFile(this.configFile);
            this.addonConfig = YamlConfiguration.loadConfiguration(this.configFile);
        }
    }

    private void createAddonFolder() {
        if (!this.addonFolder.exists()) {
            this.addonFolder.mkdirs();
        }
    }
}
