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

import com.dre.brewery.lore.BrewLore;
import com.dre.brewery.utility.Logging;
import com.dre.brewery.utility.MinecraftVersion;
import com.tcoded.folialib.wrapper.task.WrappedTask;
import io.papermc.lib.PaperLib;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BrewingStand;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Updated for 1.9 to replicate the "Brewing" process for distilling.
 * Because of how metadata has changed, the brewer no longer triggers as previously described.
 * So, I've added some event tracking and manual forcing of the brewing "animation" if the
 * set of ingredients in the brewer can be distilled.
 * Nothing here should interfere with vanilla brewing.
 *
 * @author ProgrammerDan (1.9 distillation update only)
 */
public final class BDistiller {

    private static final MinecraftVersion VERSION = BreweryPlugin.getMCVersion();

    private static final int DISTILLTIME = 400;
    private static final Map<Block, BDistiller> trackedDistillers = new ConcurrentHashMap<>();
    private final Block standBlock;
    private final int fuel;
    private WrappedTask task;
    private int runTime = -1;
    private int brewTime = -1;

    public BDistiller(final Block standBlock, final int fuel) {
        this.standBlock = standBlock;
        this.fuel = fuel;
    }

    public static void distillerClick(final InventoryClickEvent event) {
        final var standInv = (BrewingStand) PaperLib.getHolder(event.getInventory(), true).getHolder();
        final var standBlock = standInv.getBlock();

        // If we were already tracking the brewer, cancel any ongoing event due to the click.
        var distiller = trackedDistillers.get(standBlock);
        if (distiller != null) {
            distiller.cancelDistill();
            standInv.setBrewingTime(0); // Fixes brewing continuing without fuel for normal potions
            standInv.update();
        }
        final var fuel = standInv.getFuelLevel();

        // Now check if we should bother to track it.
        distiller = new BDistiller(standBlock, fuel);
        trackedDistillers.put(standBlock, distiller);
        distiller.start();
    }

    public static boolean isTrackingDistiller(final Block block) {
        return trackedDistillers.containsKey(block);
    }

    // Returns a Brew or null for every Slot in the BrewerInventory
    public static Brew[] getDistillContents(final BrewerInventory inv) {
        ItemStack item;
        final var contents = new Brew[3];
        for (var slot = 0; slot < 3; slot++) {
            item = inv.getItem(slot);
            if (item != null) {
                contents[slot] = Brew.get(item);
            }
        }
        return contents;
    }

    public static void checkContents(final BrewerInventory inv, final Brew[] contents) {
        ItemStack item;
        for (var slot = 0; slot < 3; slot++) {
            if (contents[slot] != null) {
                item = inv.getItem(slot);
                if (!Brew.isBrew(item)) {
                    contents[slot] = null;
                }
            }
        }
    }

    public static byte hasBrew(final BrewerInventory brewer, final Brew[] contents) {
        final var item = brewer.getItem(3); // ingredient
        final var glowstone = (item != null && Material.GLOWSTONE_DUST == item.getType()); // need dust in the top slot.
        byte customFound = 0;
        for (final var brew : contents) {
            if (brew != null) {
                if (!glowstone) {
                    return 1;
                }
                if (brew.canDistill()) {
                    return 2;
                } else {
                    customFound = 1;
                }
            }
        }
        return customFound;
    }

    public static boolean runDistill(final BrewerInventory inv, final Brew[] contents) {
        var custom = false;
        for (var slot = 0; slot < 3; slot++) {
            if (contents[slot] == null) continue;
            if (contents[slot].canDistill()) {
                // is further distillable
                custom = true;
            } else {
                contents[slot] = null;
            }
        }
        if (custom) {
            Brew.distillAll(inv, contents);
            return true;
        }
        return false;
    }

    public static int getLongestDistillTime(final Brew[] contents) {
        var bestTime = 0;
        int time;
        for (var slot = 0; slot < 3; slot++) {
            if (contents[slot] == null) continue;
            time = contents[slot].getDistillTimeNextRun();
            if (time == 0) {
                // Undefined Potion needs 40 seconds
                time = 800;
            }
            if (time > bestTime) {
                bestTime = time;
            }
        }
        if (bestTime > 0) {
            return bestTime;
        }
        return 800;
    }

    public static void showAlc(final BrewerInventory inv, final Brew[] contents) {
        for (var slot = 0; slot < 3; slot++) {
            if (contents[slot] != null) {
                // Show Alc in lore
                final var item = inv.getItem(slot);
                final var meta = (PotionMeta) item.getItemMeta();
                final var brewLore = new BrewLore(contents[slot], meta);
                brewLore.updateAlc(true);
                brewLore.write();
                item.setItemMeta(meta);
            }
        }
    }

    public final void cancelDistill() {
        this.task.cancel(); // cancel prior
    }

    public final void start() {
        this.task = BreweryPlugin.getScheduler().runTimer(new DistillRunnable(), 2L, 1L);
    }

    public final class DistillRunnable implements Runnable {
        private Brew[] contents = null;

        @Override
        public final void run() {
            final var standLocation = BDistiller.this.standBlock.getLocation();
            BreweryPlugin.getScheduler().runAtLocationLater(standLocation, () -> {
                if (!standLocation.getWorld().isChunkLoaded(standLocation.getChunk())) {
                    return;
                }
                if (BDistiller.this.standBlock.getType() != Material.BREWING_STAND) {
                    BDistiller.this.task.cancel();
                    trackedDistillers.remove(BDistiller.this.standBlock);
                    Logging.debugLog("The block was replaced; not a brewing stand.");
                    return;
                }
                final var stand = (BrewingStand) PaperLib.getBlockState(BDistiller.this.standBlock, false).getState();
                if (BDistiller.this.brewTime == -1 && !this.prepareForDistillables(stand)) { // check at the beginning for distillables
                    return;
                }

                BDistiller.this.brewTime--; // count down.
                if (BDistiller.this.brewTime > 1) {
                    stand.setBrewingTime((int) ((float) BDistiller.this.brewTime / ((float) BDistiller.this.runTime / (float) DISTILLTIME)) + 1);
                    stand.update();
                    return;
                }

                this.contents = getDistillContents(stand.getInventory()); // Get the contents again at the end just in case
                stand.setBrewingTime(0);
                stand.update();
                if (!runDistill(stand.getInventory(), this.contents)) {
                    BDistiller.this.task.cancel();
                    trackedDistillers.remove(BDistiller.this.standBlock);
                    Logging.debugLog("All done distilling");
                } else {
                    BDistiller.this.brewTime = -1; // go again.
                    Logging.debugLog("Can distill more! Continuing.");
                }
            }, 0);
        }

        private boolean prepareForDistillables(final BrewingStand stand) {
            final var inventory = stand.getInventory();
            if (this.contents == null) {
                this.contents = getDistillContents(inventory);
            } else {
                checkContents(inventory, this.contents);
            }
            switch (hasBrew(inventory, this.contents)) {
                case 1:
                    // Custom potion but not for distilling. Stop any brewing and cancel this task
                    if (stand.getBrewingTime() > 0) {
                        if (VERSION.isOrLater(MinecraftVersion.V1_11)) {
                            // The trick below doesn't work in 1.11, but we don't need it anymore
                            // This should only happen with older Brews that have been made with the old Potion Color System
                            // This causes standard potions to not brew in the brewing stand if put together with Brews, but the bubble animation will play
                            stand.setBrewingTime(Short.MAX_VALUE);
                        } else {
                            // Brewing time is sent and stored as short
                            // This sends a negative short value to the Client
                            // In the client the Brewer will look like it is not doing anything
                            stand.setBrewingTime(Short.MAX_VALUE << 1);
                        }
                        stand.setFuelLevel(BDistiller.this.fuel);
                    }
                case 0:
                    // No custom potion, cancel and ignore
                    BDistiller.this.task.cancel();
                    trackedDistillers.remove(BDistiller.this.standBlock);
                    showAlc(inventory, this.contents);
                    Logging.debugLog("nothing to distill");
                    return false;
                default:
                    BDistiller.this.runTime = getLongestDistillTime(this.contents);
                    BDistiller.this.brewTime = BDistiller.this.runTime;
                    Logging.debugLog("using brewtime: " + BDistiller.this.runTime);

            }
            return true;
        }
    }
}
