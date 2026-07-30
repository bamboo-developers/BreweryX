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

package com.dre.brewery.commands.subcommands;

import com.dre.brewery.Brew;
import com.dre.brewery.BreweryPlugin;
import com.dre.brewery.commands.SubCommand;
import com.dre.brewery.configuration.files.Lang;
import com.dre.brewery.utility.BUtil;
import com.dre.brewery.utility.Logging;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;

import java.util.List;

public final class DistillCommand implements SubCommand {

    private static void cmdDistill(final Lang lang, final Player player, final int distillRuns) {
        final var item = player.getInventory().getItemInMainHand();
        final var brew = Brew.get(item);
        if (brew == null) {
            lang.sendEntry(player, "Error_ItemNotPotion");
            return;
        }
        final var meta = (PotionMeta) item.getItemMeta();

        for (var i = 0; i < distillRuns; i++) {
            brew.distillSlot(item, meta);
        }
        Logging.debugLog(String.format("distill: distilled for %d runs: %s",
                distillRuns, ChatColor.stripColor(brew.toString())));
        player.getInventory().setItemInMainHand(item);
        lang.sendEntry(player, "CMD_Distilled", distillRuns);
    }

    @Override
    public void execute(final BreweryPlugin breweryPlugin, final Lang lang, final CommandSender sender, final String label, final String[] args) {
        if (args.length < 2) {
            cmdDistill(lang, (Player) sender, 1);
        } else {
            final var distillRuns = BUtil.parseInt(args[1]).orElse(0);
            if (distillRuns <= 0) {
                lang.sendEntry(sender, "CMD_Invalid_Distill_Runs", args[1]);
                return;
            }
            cmdDistill(lang, (Player) sender, distillRuns);
        }
    }

    @Override
    public List<String> tabComplete(final BreweryPlugin breweryPlugin, final CommandSender sender, final String label, final String[] args) {
        return List.of();
    }

    @Override
    public String permission() {
        return "brewery.cmd.create";
    }

    @Override
    public boolean playerOnly() {
        return true;
    }

}
