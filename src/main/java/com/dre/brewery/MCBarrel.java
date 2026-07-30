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

import com.dre.brewery.configuration.ConfigManager;
import com.dre.brewery.configuration.files.Config;
import com.dre.brewery.configuration.files.Lang;
import com.dre.brewery.utility.Logging;
import io.papermc.lib.PaperLib;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Barrel;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class MCBarrel {

    public static final String TAG = "Btime";
    private static final NamespacedKey TAG_KEY = new NamespacedKey(BreweryPlugin.getInstance(), TAG);
    public static final ConcurrentMap<Inventory, MCBarrel> openBarrels = new ConcurrentHashMap<>();
    private static final Config config = ConfigManager.getConfig(Config.class);
    private static final Lang lang = ConfigManager.getConfig(Lang.class);
    public static long mcBarrelTime; // Globally stored Barrel time. Difference between this and the time stored on each mc-barrel will give the barrel age time
    private final Inventory inv;
    private final int invSize;
    private byte brews = -1; // How many Brewery Brews are in this Barrel


    public MCBarrel(final Inventory inv) {
        this.inv = inv;
        this.invSize = inv.getSize();
    }

    public static void onUpdate() {
        if (config.isAgeInMCBarrels()) {
            mcBarrelTime++;
        }
    }

    // Now Opening this Barrel for a player
    public final void open() {
        // if nobody had the inventory opened
        if (this.inv.getViewers().size() == 1 && PaperLib.getHolder(this.inv, true).getHolder() instanceof final Barrel barrel) {
            final var data = barrel.getPersistentDataContainer();
            if (!data.has(TAG_KEY, PersistentDataType.LONG)) {
                return;
            }

            // Get the difference between the time that is stored on the Barrel and the current stored global mcBarrelTime
            final var time = mcBarrelTime - data.getOrDefault(TAG_KEY, PersistentDataType.LONG, mcBarrelTime);
            data.remove(TAG_KEY);
            barrel.update();
            Logging.debugLog("Barrel Time since last open: " + time);

            if (time > 0) {
                this.brews = 0;
                // if inventory contains potions
                if (this.inv.contains(Material.POTION)) {
                    var loadTime = System.nanoTime();
                    for (final var item : this.inv.getContents()) {
                        if (item != null) {
                            final var brew = Brew.get(item);
                            if (brew != null && !brew.isStatic()) {
                                if (this.brews < config.getMaxBrewsInMCBarrels() || config.getMaxBrewsInMCBarrels() < 0) {
                                    // The time is in minutes, but brew.age() expects time in mc-days
                                    brew.age(item, ((float) time) / 20f, BarrelWoodType.OAK);
                                }
                                this.brews++;
                            }
                        }
                    }
                    if (config.isDebug()) {
                        loadTime = System.nanoTime() - loadTime;
                        final var ftime = (float) (loadTime / 1000000.0);
                        Logging.debugLog("opening MC Barrel with potions (" + ftime + "ms)");
                    }
                }
            }
        }
    }

    // Closing Inventory. Check if we need to set a time on the Barrel
    public final void close() {
        if (this.inv.getViewers().size() == 1) {
            // This is the last viewer
            for (final var item : this.inv.getContents()) {
                if (item != null) {
                    if (Brew.isBrew(item)) {
                        // We found a brew, so set time on this Barrel
                        if (PaperLib.getHolder(this.inv, true).getHolder() instanceof final org.bukkit.block.Barrel barrel) {
                            final var data = barrel.getPersistentDataContainer();
                            data.set(TAG_KEY, PersistentDataType.LONG, mcBarrelTime);
                            barrel.update();
                        }
                        return;
                    }
                }
            }
            // No Brew found, ignore this Barrel
        }
    }

    public final void countBrews() {
        this.brews = 0;
        for (final var item : this.inv.getContents()) {
            if (item != null) {
                if (Brew.isBrew(item)) {
                    this.brews++;
                }
            }
        }
    }

    public Inventory getInventory() {
        return this.inv;
    }

    // Used to visually stop Players from placing more than 6 (configurable) brews in the MC Barrels.
    // There are still methods to place more Brews in that would be too tedious to catch.
    // This is only for direct visual Notification, the age routine above will never age more than 6 brews in any case.
    public final void clickInv(final InventoryClickEvent event) {
        if (config.getMaxBrewsInMCBarrels() >= this.invSize || config.getMaxBrewsInMCBarrels() < 0) {
            // There are enough brews allowed to fill the inventory, we don't need to keep track
            return;
        }
        var adding = false;
        switch (event.getAction()) {
            case PLACE_ALL:
            case PLACE_ONE:
            case PLACE_SOME:
            case SWAP_WITH_CURSOR:
                // Placing Brew in MC Barrel
                if (event.getCursor() != null && event.getClickedInventory() != null && event.getClickedInventory().getType() == InventoryType.BARREL && event.getCursor().getType() == Material.POTION) {
                    if (Brew.isBrew(event.getCursor())) {
                        if (event.getAction() == InventoryAction.SWAP_WITH_CURSOR && event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.POTION) {
                            if (Brew.isBrew(event.getCurrentItem())) {
                                // The item we are swapping with is also a brew, dont change the count and allow
                                break;
                            }
                        }
                        adding = true;
                    }
                }
                break;
            case MOVE_TO_OTHER_INVENTORY:
                if (event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.POTION && event.getClickedInventory() != null) {
                    if (event.getClickedInventory().getType() == InventoryType.BARREL) {
                        // Moving Brew out of MC Barrel
                        if (Brew.isBrew(event.getCurrentItem())) {
                            if (this.brews == -1) {
                                this.countBrews();
                            }
                            this.brews--;
                        }
                        break;
                    } else if (event.getClickedInventory().getType() == InventoryType.PLAYER) {
                        // Moving Brew into MC Barrel
                        if (Brew.isBrew(event.getCurrentItem())) {
                            adding = true;
                        }
                    }
                }
                break;

            case PICKUP_ALL:
            case PICKUP_ONE:
            case PICKUP_HALF:
            case PICKUP_SOME:
            case COLLECT_TO_CURSOR:
                // Pickup Brew from MC Barrel
                if (event.getCurrentItem() != null && event.getClickedInventory() != null && event.getClickedInventory().getType() == InventoryType.BARREL && event.getCurrentItem().getType() == Material.POTION) {
                    if (Brew.isBrew(event.getCurrentItem())) {
                        if (this.brews == -1) {
                            this.countBrews();
                        }
                        this.brews--;
                    }
                }
                break;
            case HOTBAR_MOVE_AND_READD:
            case HOTBAR_SWAP:
                this.brews = -1;
                break;
            default:
                return;
        }
        if (adding) {
            if (this.brews == -1) {
                this.countBrews();
            }
            if (this.brews >= config.getMaxBrewsInMCBarrels()) {
                event.setCancelled(true);
                lang.sendEntry(event.getWhoClicked(), "Player_BarrelFull");
            } else {
                this.brews++;
            }
        }
    }

}
