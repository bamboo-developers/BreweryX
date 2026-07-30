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

package com.dre.brewery.commands.subcommands;

import com.dre.brewery.Brew;
import com.dre.brewery.BreweryPlugin;
import com.dre.brewery.commands.SubCommand;
import com.dre.brewery.configuration.files.Lang;
import com.dre.brewery.utility.BUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public final class CopyCommand implements SubCommand {

    @Override
    public void execute(final BreweryPlugin breweryPlugin, final Lang lang, final CommandSender sender, final String label, final String[] args) {
        if (args.length > 1) {
            this.cmdCopy(sender, BUtil.getRandomIntInRange(args[1]), lang);
        } else {
            this.cmdCopy(sender, 1, lang);
        }
    }

    @Override
    public List<String> tabComplete(final BreweryPlugin breweryPlugin, final CommandSender sender, final String label, final String[] args) {
        return null;
    }

    @Override
    public String permission() {
        return "brewery.cmd.copy";
    }

    @Override
    public boolean playerOnly() {
        return true;
    }

    //@Deprecated but still used?
    public final void cmdCopy(final CommandSender sender, int count, final Lang lang) {
        if (count < 1 || count > 36) {
            lang.sendEntry(sender, "Etc_Usage");
            lang.sendEntry(sender, "Help_Copy");
            return;
        }
        final var player = (Player) sender;
        final var hand = player.getItemInHand();
        if (hand != null) {
            if (Brew.isBrew(hand)) {
                while (count > 0) {
                    final var item = hand.clone();
                    if (!(player.getInventory().addItem(item)).isEmpty()) {
                        lang.sendEntry(sender, "CMD_Copy_Error", "" + count);
                        return;
                    }
                    count--;
                }
                return;
            }
        }

        lang.sendEntry(sender, "Error_ItemNotPotion");

    }
}
