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

package com.dre.brewery.integration.listeners;

import com.dre.brewery.BreweryPlugin;
import com.dre.brewery.api.events.barrel.BarrelAccessEvent;
import com.dre.brewery.configuration.ConfigManager;
import com.dre.brewery.configuration.files.Config;
import com.dre.brewery.configuration.files.Lang;
import com.dre.brewery.integration.Hook;
import com.dre.brewery.utility.Logging;
import io.papermc.lib.PaperLib;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;

public final class IntegrationListener implements Listener {

    private final Config config = ConfigManager.getConfig(Config.class);
    private final Lang lang = ConfigManager.getConfig(Lang.class);

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBarrelAccess(final BarrelAccessEvent event) {
        final var hook = Hook.GAMEMODEINVENTORIES;
        if (hook.isEnabled()) {
            final var pl = hook.getPlugin();
            if (pl != null && pl.isEnabled()) {
                try {
                    if (pl.getConfig().getBoolean("restrict_creative")) {
                        final var player = event.getPlayer();
                        if (player.getGameMode() == GameMode.CREATIVE) {
                            if (!pl.getConfig().getBoolean("bypass.inventories") || (!player.hasPermission("gamemodeinventories.bypass") && !player.isOp())) {
                                event.setCancelled(true);
                                if (!pl.getConfig().getBoolean("dont_spam_chat")) {
                                    this.lang.sendEntry(event.getPlayer(), "Error_NoBarrelAccess");
                                }
                                return;
                            }
                        }
                    }
                } catch (final Throwable e) {
                    Logging.errorLog("Failed to Check GameModeInventories for Barrel Open Permissions!", e);
                    Logging.errorLog("Players will be able to open Barrel with GameMode Creative");
                    hook.setEnabled(false);
                }
            } else {
                hook.setEnabled(false);
            }
        }

        if (this.config.isUseVirtualChestPerms()) {
            final var player = event.getPlayer();
            final var originalBlockState = PaperLib.getBlockState(event.getClickedBlock(), true).getState();

            event.getClickedBlock().setType(Material.CHEST, false);
            final var simulatedEvent = new PlayerInteractEvent(
                    player,
                    Action.RIGHT_CLICK_BLOCK,
                    player.getInventory().getItemInMainHand(),
                    event.getClickedBlock(),
                    event.getClickedBlockFace(),
                    EquipmentSlot.HAND);

            try {
                BreweryPlugin.getInstance().getServer().getPluginManager().callEvent(simulatedEvent);
            } catch (final Throwable e) {
                Logging.errorLog("Failed to simulate a Chest for Barrel Open Permissions!", e);
                Logging.errorLog("Disable useVirtualChestPerms in the config and do /brew reload");

                if (player.hasPermission("brewery.admin") || player.hasPermission("brewery.mod")) {
                    Logging.msg(player, "&cVirtual Chest Error");
                    Logging.msg(player, "&cSet &7useVirtualChestPerms: false &cin the config and /brew reload");
                } else {
                    Logging.msg(player, "&cError opening Barrel, please report to an Admin!");
                }
            } finally {
                event.getClickedBlock().setType(Material.AIR, false);
                originalBlockState.update(true);
            }

            if (simulatedEvent.useInteractedBlock() == Event.Result.DENY) {
                event.setCancelled(true);
                this.lang.sendEntry(event.getPlayer(), "Error_NoBarrelAccess");
                //return;
            }
        }
    }
}
