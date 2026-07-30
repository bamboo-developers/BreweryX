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

package com.dre.brewery.listeners;

import com.dre.brewery.BCauldron;
import com.dre.brewery.Barrel;
import com.dre.brewery.BreweryPlugin;
import com.dre.brewery.api.events.barrel.BarrelDestroyEvent;
import com.dre.brewery.utility.BUtil;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

public final class EntityListener implements Listener {

    //  --- Barrel Breaking ---

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(final EntityExplodeEvent event) {
        if (this.causedByWindCharge(event)) return; // Fixes barrels being destroyed when hit by a WindCharge
        final var iter = event.blockList().listIterator();
        if (!iter.hasNext()) return;
        final List<BarrelDestroyEvent> breakEvents = new ArrayList<>(6);
        Block block;
        blocks:
        while (iter.hasNext()) {
            block = iter.next();
            final var cauldron = BCauldron.get(block);
            if (cauldron != null) {
                BUtil.blockDestroy(block, null, BarrelDestroyEvent.Reason.EXPLODED);
                continue;
            }
            if (!breakEvents.isEmpty()) {
                for (final var breakEvent : breakEvents) {
                    if (breakEvent.getBarrel().hasBlock(block)) {
                        if (breakEvent.isCancelled()) {
                            iter.remove();
                        }
                        continue blocks;
                    }
                }
            }
            final var barrel = Barrel.get(block);
            if (barrel != null) {
                final var breakEvent = new BarrelDestroyEvent(barrel, block, BarrelDestroyEvent.Reason.EXPLODED, null);
                BreweryPlugin.getInstance().getServer().getPluginManager().callEvent(breakEvent);
                breakEvents.add(breakEvent);
                if (breakEvent.isCancelled()) {
                    iter.remove();
                } else {
                    barrel.remove(block, null, true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockChange(final EntityChangeBlockEvent event) {
        if (event.getBlock().getType().name().toUpperCase().contains("CUT_COPPER")) return;
        if (Barrel.get(event.getBlock()) == null) return;
        event.setCancelled(true);
    }

    /**
     * Utility method to determine if the given event was caused by a wind charge
     *
     * @param event An instance of EntityExplodeEvent that should be analyzed
     * @return A boolean representing if the given event was caused by a wind charge
     */
    private boolean causedByWindCharge(final EntityExplodeEvent event) {
        final var type = event.getEntityType();
        return type == EntityType.BREEZE_WIND_CHARGE || type == EntityType.WIND_CHARGE;

        /*
         * Note that, since WindCharges have the ability to modify BlockStates (e.g. flip trapdoors they hit), we sadly
         * can't just check for event.blockList(), as they provide one too and checking if those blocks are destroyed
         * could only be done after the event has been successfully executed. Life isn't that easy at times :/
         */

    }

}
