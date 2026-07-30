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
import com.tcoded.folialib.wrapper.task.WrappedTask;
import io.papermc.lib.PaperLib;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

/**
 * The Sealing Inventory that is being checked for Brews and seals them after a second.
 */
public final class BSealer implements InventoryHolder {
    public static final NamespacedKey TAG_KEY = new NamespacedKey(BreweryPlugin.getInstance(), "SealingTable");
    private static final Config config = ConfigManager.getConfig(Config.class);
    private static final Lang lang = ConfigManager.getConfig(Lang.class);
    public static boolean inventoryHolderWorking = true;
    private final Inventory inventory;
    private final Player player;
    private final short[] slotTime = new short[9];
    private ItemStack[] contents = null;
    private WrappedTask task;

    public BSealer(final Player player) {
        this.player = player;
        if (inventoryHolderWorking) {
            final var inv = Bukkit.createInventory(this, InventoryType.DISPENSER, lang.getEntry("Etc_SealingTable"));
            // Inventory Holder (for DISPENSER, ...) is only passed in Paper, not in Spigot. Doing inventory.getHolder() will return null in spigot :/
            if (PaperLib.getHolder(inv, true).getHolder() == this) {
                this.inventory = inv;
                return;
            } else {
                inventoryHolderWorking = false;
            }
        }
        this.inventory = Bukkit.createInventory(this, 9, lang.getEntry("Etc_SealingTable"));
    }

    public static boolean isBSealer(final Block block) {
        if (block.getType() == config.getSealingTableBlock()) {
            final var container = (Container) PaperLib.getBlockState(block, true).getState();
            if (container.getCustomName() != null) {
                if (container.getCustomName().equals("§e" + lang.getEntry("Etc_SealingTable"))) {
                    return true;
                } else {
                    return container.getPersistentDataContainer().has(TAG_KEY, PersistentDataType.BYTE);
                }
            }
        }
        return false;
    }

    public static void blockPlace(final ItemStack item, final Block block) {
        if (item.getType() == config.getSealingTableBlock() && item.hasItemMeta()) {
            final var itemMeta = item.getItemMeta();
            assert itemMeta != null;
            if ((itemMeta.hasDisplayName() && itemMeta.getDisplayName().equals("§e" + lang.getEntry("Etc_SealingTable"))) ||
                    itemMeta.getPersistentDataContainer().has(BSealer.TAG_KEY, PersistentDataType.BYTE)) {
                final var container = (Container) PaperLib.getBlockState(block, true).getState();
                // Rotate the Block 180° so it looks different
                if (container.getBlockData() instanceof final Directional dir) {
                    dir.setFacing(dir.getFacing().getOppositeFace());
                    container.setBlockData(dir);
                }
                container.getPersistentDataContainer().set(BSealer.TAG_KEY, PersistentDataType.BYTE, (byte) 1);
                container.update();
            }
        }
    }

    public static void registerRecipe() {
        // Register Sealing Table Recipe
        if (!config.isCraftSealingTable() && recipeExists()) {
            unregisterRecipe();
            return;
        } else if (!config.isCraftSealingTable() || recipeExists()) {
            return;
        }

        final var sealingTableItem = new ItemStack(config.getSealingTableBlock());
        final var meta = BreweryPlugin.getInstance().getServer().getItemFactory().getItemMeta(config.getSealingTableBlock());
        if (meta == null) return;
        meta.setDisplayName("§e" + lang.getEntry("Etc_SealingTable"));
        meta.getPersistentDataContainer().set(TAG_KEY, PersistentDataType.BYTE, (byte) 1);
        sealingTableItem.setItemMeta(meta);

        final var recipe = new ShapedRecipe(new NamespacedKey(BreweryPlugin.getInstance(), "SealingTable"), sealingTableItem);
        recipe.shape("bb ",
                "ww ",
                "ww ");
        recipe.setIngredient('b', Material.GLASS_BOTTLE);
        recipe.setIngredient('w', new RecipeChoice.MaterialChoice(Tag.PLANKS));

        Bukkit.getServer().addRecipe(recipe);
    }

    public static boolean recipeExists() {
        final var recipe = Bukkit.getRecipe(TAG_KEY);
        return recipe != null;
    }

    public static void unregisterRecipe() {
        final var recipe = Bukkit.getRecipe(TAG_KEY);
        if (recipe != null) {
            Bukkit.removeRecipe(TAG_KEY);
        }
    }

    @Override
    public final @NotNull Inventory getInventory() {
        return this.inventory;
    }

    public final void clickInv() {
        this.contents = null;
        if (this.task == null) {
            this.task = BreweryPlugin.getScheduler().runTimer(this::itemChecking, 1, 1);
        }
    }

    public final void closeInv() {
        if (this.task != null) {
            this.task.cancel();
            this.task = null;
        }
        this.contents = this.inventory.getContents();
        for (final var item : this.contents) {
            if (item != null && item.getType() != Material.AIR) {
                this.player.getWorld().dropItemNaturally(this.player.getLocation(), item);
            }
        }
        this.contents = null;
        this.inventory.clear();
    }

    private void itemChecking() {
        if (this.contents == null) {
            this.contents = this.inventory.getContents();
            for (var i = 0; i < this.slotTime.length; i++) {
                if (this.contents[i] == null || this.contents[i].getType() != Material.POTION) {
                    this.slotTime[i] = -1;
                } else if (this.slotTime[i] < 0) {
                    this.slotTime[i] = 0;
                }
            }
        }
        final var playerValid = this.player.isValid() && !this.player.isDead();
        for (var i = 0; i < this.slotTime.length; i++) {
            if (this.slotTime[i] > 20) {
                this.slotTime[i] = -1;
                final var brew = Brew.get(this.contents[i]);
                if (brew != null && !brew.isStripped()) {
                    brew.seal(this.contents[i], this.player);
                    if (playerValid) {
                        this.player.playSound(this.player.getLocation(), Sound.ITEM_BOTTLE_FILL_DRAGONBREATH, 1, 1.5f + (float) (Math.random() * 0.2));
                    }
                }
            } else if (this.slotTime[i] >= 0) {
                this.slotTime[i]++;
            }
        }
    }
}
