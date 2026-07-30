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

import com.dre.brewery.*;
import com.dre.brewery.configuration.ConfigManager;
import com.dre.brewery.configuration.files.Config;
import com.dre.brewery.lore.BrewLore;
import com.dre.brewery.utility.Logging;
import io.papermc.lib.PaperLib;
import org.bukkit.Material;
import org.bukkit.block.BrewingStand;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.PotionMeta;

import java.util.*;
import java.util.stream.Stream;

public final class InventoryListener implements Listener {

    private static final Set<InventoryAction> CLICKED_INVENTORY_ITEM_MOVE = Set.of(InventoryAction.PLACE_SOME,
            InventoryAction.PLACE_ONE, InventoryAction.PLACE_ALL, InventoryAction.PICKUP_ALL, InventoryAction.PICKUP_HALF,
            InventoryAction.PICKUP_SOME, InventoryAction.PICKUP_ONE);
    private static final Set<String> BANNED_ACTIONS = Set.of("PICKUP_ALL_INTO_BUNDLE", "PICKUP_FROM_BUNDLE",
            "PICKUP_SOME_INTO_BUNDLE", "PLACE_ALL_INTO_BUNDLE", "PLACE_SOME_INTO_BUNDLE");
    private final Config config = ConfigManager.getConfig(Config.class);
    /* === Recreating manually the prior BrewEvent behavior. === */
    private final HashSet<UUID> trackedBrewmen = new HashSet<>();


    // Helper: checks if an item is a valid Brewery brew
    private boolean isBrewItem(final ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        if (item.getItemMeta() instanceof final PotionMeta potionMeta) {
            return Brew.get(potionMeta) != null;
        }
        return false;
    }

    /**
     * Start tracking distillation for a person when they open the brewer window.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBrewerOpen(final InventoryOpenEvent event) {
        final var player = event.getPlayer();
        final var inv = event.getInventory();
        if (player == null || !(inv instanceof BrewerInventory)) return;

        Logging.debugLog("Starting brew inventory tracking");
        this.trackedBrewmen.add(player.getUniqueId());
    }

    /**
     * Stop tracking distillation for a person when they close the brewer window.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBrewerClose(final InventoryCloseEvent event) {
        final var player = event.getPlayer();
        final var inv = event.getInventory();
        if (player == null || !(inv instanceof BrewerInventory)) return;

        Logging.debugLog("Stopping brew inventory tracking");
        this.trackedBrewmen.remove(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBrewerDrag(final InventoryDragEvent event) {
        // Workaround the Drag event when only clicking a slot
        if (event.getInventory() instanceof BrewerInventory) {
            this.onBrewerClick(new InventoryClickEvent(event.getView(), InventoryType.SlotType.CONTAINER, 0, ClickType.LEFT, InventoryAction.PLACE_ALL));
        }
    }

    /**
     * Clicking can either start or stop the new brew distillation tracking.
     * <p>Note that server restart will halt any ongoing brewing processes and
     * they will _not_ restart until a new click event.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBrewerClick(final InventoryClickEvent event) {

        final var player = event.getWhoClicked();
        final var inv = event.getInventory();
        if (!(inv instanceof BrewerInventory)) return;

        final var puid = player.getUniqueId();
        if (!this.trackedBrewmen.contains(puid)) return;

        if (InventoryType.BREWING != inv.getType()) return;
        if (event.getAction() == InventoryAction.NOTHING) return; // Ignore clicks that do nothing

        BDistiller.distillerClick(event);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBrew(final BrewEvent event) {
        if (BDistiller.hasBrew(event.getContents(), BDistiller.getDistillContents(event.getContents())) != 0) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(final InventoryClickEvent event) {
        final var holder = PaperLib.getHolder(event.getInventory(), true).getHolder();
        final var isVanillaBarrel = holder instanceof org.bukkit.block.Barrel;
        if (isVanillaBarrel && this.config.isExemptVanillaBarrels()) {
            return;
        }
        if (!(holder instanceof Barrel) && !isVanillaBarrel) {
            return;
        }
        final var action = event.getAction();
        if (action == InventoryAction.NOTHING) {
            return;
        }
        final var upperInventoryIsClicked = event.getClickedInventory() == event.getInventory();
        if (!upperInventoryIsClicked && CLICKED_INVENTORY_ITEM_MOVE.contains(action)) {
            return;
        }
        final var hoveredItem = event.getCurrentItem();
        final Stream<ItemStack> relatedItems;
        if (upperInventoryIsClicked && hoveredItem != null) {
            if (hoveredItem.getItemMeta() instanceof final PotionMeta potionMeta) {
                final var brew = Brew.get(potionMeta);
                if (brew != null) {
                    final var lore = new BrewLore(brew, potionMeta);
                    if (BrewLore.hasColorLore(potionMeta)) {
                        lore.convertLore(false);
                        lore.write();
                    } else if (!this.config.isAlwaysShowAlc() && event.getInventory().getType() == InventoryType.BREWING) {
                        lore.updateAlc(false);
                        lore.write();
                    }
                    hoveredItem.setItemMeta(potionMeta);
                }
            }
        }
        if (!this.config.isOnlyAllowBrewsInBarrels()) {
            return;
        }
        if (BANNED_ACTIONS.contains(action.name())) {
            event.setResult(Event.Result.DENY);
            return;
        }
        final var view = event.getView();
        // getHotbarButton also returns -1 for offhand clicks
        final var hotbarItem = event.getHotbarButton() == -1 ?
                (event.getClick() == ClickType.SWAP_OFFHAND
                 ? event.getWhoClicked().getInventory().getItemInOffHand()
                 : null)
                : view.getBottomInventory().getItem(event.getHotbarButton());
        if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            // player takes something out
            if (upperInventoryIsClicked && hotbarItem == null) {
                return;
            }
            relatedItems = Stream.of(hotbarItem, hoveredItem);
        } else if (action == InventoryAction.HOTBAR_SWAP) {
            // barrel not involved
            if (!upperInventoryIsClicked) {
                return;
            }
            relatedItems = Stream.of(hotbarItem, hoveredItem);
        } else {
            final var cursor = event.getCursor();
            relatedItems = Stream.of(cursor);
        }
        final var itemsToCheck = relatedItems
                .filter(Objects::nonNull)
                .filter(item -> !item.getType().isAir());
        if (itemsToCheck.anyMatch(item -> !this.isBrewItem(item))) {
            event.setResult(Event.Result.DENY);
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(final InventoryDragEvent event) {
        final var view = event.getView();
        final var topInventory = view.getTopInventory();
        final var holder = PaperLib.getHolder(topInventory, true).getHolder();
        final var isVanillaBarrel = holder instanceof org.bukkit.block.Barrel;
        if (isVanillaBarrel && this.config.isExemptVanillaBarrels()) {
            return;
        }
        if (!(holder instanceof Barrel) && !isVanillaBarrel) {
            return;
        }

        final var topSize = topInventory.getSize();
        for (final var entry : event.getNewItems().entrySet()) {
            final int rawSlot = entry.getKey();
            if (rawSlot < topSize) {
                final var item = entry.getValue();
                if (item == null || item.getType().isAir()) {
                    continue;
                }
                if (!this.isBrewItem(item)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }


    // Check if the player tries to add more than the allowed amount of brews into an mc-barrel
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClickMCBarrel(final InventoryClickEvent event) {
        if (event.getInventory().getType() != InventoryType.BARREL) return;
        if (!this.config.isAgeInMCBarrels()) return;

        final var inv = event.getInventory();
        final var barrel = MCBarrel.openBarrels.computeIfAbsent(inv, MCBarrel::new);
        barrel.clickInv(event);
    }

    // Handle the Brew Sealer Inventory
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClickBSealer(final InventoryClickEvent event) {
        final var holder = PaperLib.getHolder(event.getInventory(), true).getHolder();
        if (!(holder instanceof BSealer)) {
            return;
        }
        ((BSealer) holder).clickInv();
    }

    //public static boolean opening = false;

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(final InventoryOpenEvent event) {
        if (!this.config.isAgeInMCBarrels()) return;

        // Check for MC Barrel
        if (event.getInventory().getType() == InventoryType.BARREL) {
            final var inv = event.getInventory();
            final var barrel = MCBarrel.openBarrels.computeIfAbsent(inv, MCBarrel::new);
            barrel.open();
        }
    }

    // block the pickup of items where getPickupDelay is > 1000 (puke)
    @EventHandler(ignoreCancelled = true)
    public void onHopperPickupPuke(final InventoryPickupItemEvent event) {
        if (event.getItem().getPickupDelay() > 1000 && this.config.getPukeItem().contains(event.getItem().getItemStack().getType())) {
            event.setCancelled(true);
        }
    }

    // Block taking out items from running distillers,
    // Convert Color Lore from MC Barrels back into normal color on taking out
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onHopperMove(final InventoryMoveItemEvent event) {
        if (event.getSource() instanceof final BrewerInventory inv && PaperLib.getHolder(inv, true).getHolder() instanceof final BrewingStand holder) {
            if (BDistiller.isTrackingDistiller(holder.getBlock())) {
                event.setCancelled(true);
            }
            return;
        }


        if (event.getSource().getType() == InventoryType.BARREL) {
            final var item = event.getItem();
            if (item.getType() == Material.POTION && Brew.isBrew(item)) {
                final var meta = (PotionMeta) item.getItemMeta();
                assert meta != null;
                if (BrewLore.hasColorLore(meta)) {
                    // has color lore, convert lore back to normal
                    final var brew = Brew.get(meta);
                    if (brew != null) {
                        final var lore = new BrewLore(brew, meta);
                        lore.convertLore(false);
                        lore.write();
                        item.setItemMeta(meta);
                        event.setItem(item);
                    }
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClose(final InventoryCloseEvent event) {
        if (PaperLib.getHolder(event.getInventory(), true).getHolder() instanceof final BSealer holder) {
            holder.closeInv();
        }


        // Barrel Closing Sound
        if (PaperLib.getHolder(event.getInventory(), true).getHolder() instanceof final Barrel barrel) {
            barrel.playClosingSound();
        }

        // Check for MC Barrel
        if (this.config.isAgeInMCBarrels() && event.getInventory().getType() == InventoryType.BARREL) {
            final var inv = event.getInventory();
            final var barrel = MCBarrel.openBarrels.get(inv);
            if (barrel != null) {
                barrel.close();
                if (inv.getViewers().size() == 1) {
                    // Last viewer, remove Barrel from open Barrel tracking
                    MCBarrel.openBarrels.remove(inv, barrel);
                }
                return;
            }
            new MCBarrel(inv).close();
        }
    }
}
