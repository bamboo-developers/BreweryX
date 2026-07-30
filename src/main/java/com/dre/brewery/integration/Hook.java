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

package com.dre.brewery.integration;

import com.dre.brewery.configuration.ConfigManager;
import com.dre.brewery.configuration.files.Config;
import com.dre.brewery.utility.Logging;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

@Getter
@Setter
public class Hook {

    public static final Hook NEXO = new Hook("Nexo");
    protected static final Config config = ConfigManager.getConfig(Config.class);
    public static final Hook GAMEMODEINVENTORIES = new Hook("GameModeInventories", config.isUseGMInventories());
    private final String name;
    @ApiStatus.Internal
    protected boolean checked;
    @ApiStatus.Internal
    private boolean enabled;

    public Hook(final String name) {
        this.name = name;
        this.enabled = true;
    }

    public Hook(final String name, final boolean enabled) {
        this.name = name;
        this.enabled = enabled;
    }

    public final boolean isEnabled() {
        if (!this.checked) { // Have we checked with Bukkit to see if the plugin is enabled yet?
            this.checked = true;
            if (this.enabled) { // If it's 'enabled' in the config, check if it's actually enabled through Bukkit
                this.enabled = Bukkit.getPluginManager().isPluginEnabled(this.name);
            }
        }
        return this.enabled;
    }

    @Contract
    public final @Nullable Plugin getPlugin() {
        if (this.isEnabled()) {
            final var plugin = Bukkit.getPluginManager().getPlugin(this.name);
            if (plugin == null) {
                Logging.errorLog("Plugin " + this.name + " is marked enabled but not found!");
            }
            return plugin;
        } else {
            return null;
        }
    }

}
