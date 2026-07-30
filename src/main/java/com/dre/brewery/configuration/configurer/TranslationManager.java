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

package com.dre.brewery.configuration.configurer;

import com.dre.brewery.BreweryPlugin;
import com.dre.brewery.configuration.ConfigHead;
import com.dre.brewery.configuration.ConfigManager;
import com.dre.brewery.configuration.files.Config;
import com.dre.brewery.configuration.files.Lang;
import com.dre.brewery.utility.Logging;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Getter
public final class TranslationManager {

    @Getter
    private static final Translation fallbackTranslation = Translation.EN;
    private static TranslationManager singleton;
    private final File dataFolder;
    private final ConfigTranslations translations;
    private final ConfigTranslations fallbackTranslations;
    private Translation activeTranslation;

    private TranslationManager(final File dataFolder) {
        final var yaml = new Yaml();

        // Ok so,
        // Our config depends on our TranslationManager in order to load properly.
        // The problem is, the TranslationManager needs to be provided a translation in order to load,
        // in order to preserve the configurability of the language using the 'config.yml' instead of a separate file,
        // we have to grab the language from the config.yml before Okaeri loads it, so that's what we're doing right here.
        // Annoying race condition, but so be it.
        this.dataFolder = dataFolder;
        this.activeTranslation = Translation.EN;

        try (final var inputStream = Files.newInputStream(ConfigManager.getFilePath(Config.class))) {
            final Map<String, String> data = yaml.loadAs(inputStream, Map.class);
            if (data != null) {
                this.activeTranslation = Translation.getTranslation(data.get("language"));
            }
        } catch (final IOException e) {
            Logging.debugLog("Error reading YAML file: " + e.getMessage());
        }
        final var languageFile = new File(dataFolder, "languages/" + this.activeTranslation.fileName());
        if (TranslationManager.class.getResource("/languages/" + this.activeTranslation.fileName()) == null && !languageFile.exists()) {
            Logging.errorLog("Translation could not be found internally or as a file externally: " + this.activeTranslation.fileName());
            Logging.errorLog("You need to either change translation or provide one at: " + languageFile);
            throw new IllegalStateException("Language file not found: languages/" + this.activeTranslation.fileName());
        }


        this.translations = new ConfigTranslations(this.activeTranslation, yaml);
        this.fallbackTranslations = new ConfigTranslations(fallbackTranslation, yaml);

        // Create lang files from /resources/languages
        for (final var translation : Translation.getDefaultTranslations()) {
            this.createLanguageFile(translation);
        }
    }

    public static void newInstance(final File dataFolder) {
        newInstance(dataFolder, false);
    }

    public static void newInstance(final File dataFolder, final boolean respectAlreadyExisting) {
        if (singleton != null && respectAlreadyExisting) {
            return;
        }
        singleton = new TranslationManager(dataFolder);
    }

    // Okaeri would do this normally, but since default values in Lang changes based on language,
    // we have to manually go through each file.

    public static TranslationManager getInstance() {
        if (singleton == null) {
            singleton = new TranslationManager(BreweryPlugin.getInstance().getDataFolder());
        }
        return singleton;
    }

    /**
     * Attempts to retrieve a translation for the given key with fallback logic.
     */
    @Nullable
    public final String getTranslationWithFallback(final String key) {
        final var activeTranslationString = this.translations.getTranslation(key);
        if (activeTranslationString != null) {
            return activeTranslationString;
        }

        // Fallback to the english translation
        final var fallbackTranslationString = this.fallbackTranslations.getTranslation(key);
        if (fallbackTranslationString == null) {
            Logging.warningLog("No translation found for key: " + key);
        }

        return fallbackTranslationString;
    }

    public final void createLanguageFile(final Translation translation) {
        final var languageFile = this.dataFolder.toPath().resolve("languages").resolve(translation.fileName());
        if (!Files.exists(languageFile) && TranslationManager.class.getResource("/languages/" + translation.fileName()) == null) {
            throw new IllegalStateException("Translation could not be found internally or as a file externally: " + translation);
        }
        ConfigManager.createFileFromResources("languages/" + translation.fileName(), languageFile);
    }

    /**
     * Updates all translation files by adding all missing keys.
     * If a key hasn't been translated yet, english is used as a fallback.
     */
    public final void updateTranslationFiles() {
        final var tempHead = new ConfigHead(); // Prevent polluting ConfigManager global state

        final var fallback = this.loadFromResources(tempHead, Translation.EN);
        for (final var trans : Translation.getDefaultTranslations()) {
            final var langFilePathStr = "languages/" + trans.fileName();
            final var langFilePath = this.dataFolder.toPath().resolve(langFilePathStr);

            final var langFromFile = tempHead.createConfig(Lang.class, langFilePath);
            final var langFromResources = this.loadFromResources(tempHead, trans);

            langFromFile.updateMissingValuesFrom(langFromResources);
            if (trans != Translation.EN) {
                langFromFile.updateMissingValuesFrom(fallback);
            }
            langFromFile.save();
        }

    }

    // Loads a lang from resources... by loading from file then overwriting with resources InputStream
    // A bit of a hack, but avoids having to modify Okaeri
    @Nullable
    private Lang loadFromResources(final ConfigHead tempHead, final Translation translation) {
        final var langFilePathStr = "languages/" + translation.fileName();
        final var langFilePath = this.dataFolder.toPath().resolve(langFilePathStr);

        try (final var inputStream = BreweryPlugin.class.getResourceAsStream("/" + langFilePathStr)) {
            if (inputStream == null || !Files.exists(langFilePath)) {
                throw new IOException("Lang file not found: " + langFilePathStr);
            }
            final var langFromResources = tempHead.createConfig(Lang.class, langFilePath);
            langFromResources.load(inputStream);
            return langFromResources;
        } catch (final IOException e) {
            Logging.errorLog("Failed to load " + langFilePathStr + " from resources", e);
            return null;
        }
    }
}
