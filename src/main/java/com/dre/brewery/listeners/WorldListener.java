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

package com.dre.brewery.listeners;

import com.dre.brewery.Barrel;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.World;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

public final class WorldListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(final WorldLoadEvent event) {
        // Restore barrels that were set aside in memory when this world was previously unloaded.
        // Does not re-read storage: Barrels for worlds loaded at plugin startup were already loaded then.
        Barrel.onLoad(event.getWorld());
    }

    /**
     * Only Barrels are set aside here. {@link com.dre.brewery.BCauldron#onUnload(World)} and
     * {@link com.dre.brewery.Wakeup#onUnload(World)} drop their entries outright, and since the
     * DataManager saves with a full rewrite that would delete them from storage on the next
     * autosave. They stay unwired until they keep the data around like Barrel does.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldUnload(final WorldUnloadEvent event) {
        Barrel.onUnload(event.getWorld());
    }
}
