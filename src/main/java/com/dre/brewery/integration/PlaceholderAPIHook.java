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

import com.dre.brewery.integration.papi.PlaceholderAPIManager;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

public final class PlaceholderAPIHook extends Hook {

    public static final PlaceholderAPIHook PLACEHOLDERAPI = new PlaceholderAPIHook("PlaceholderAPI");

    private PlaceholderAPIManager instance;

    public PlaceholderAPIHook(final String name) {
        super(name);
    }

    public final PlaceholderAPIManager getInstance() {
        if (this.instance == null && this.isEnabled()) {
            this.instance = new PlaceholderAPIManager();
        }
        return this.instance;
    }

    public final String setPlaceholders(@Nullable final Player player, final String text) {
        if (!this.isEnabled()) {
            return text;
        }

        return PlaceholderAPI.setPlaceholders(player, text);
    }

}
