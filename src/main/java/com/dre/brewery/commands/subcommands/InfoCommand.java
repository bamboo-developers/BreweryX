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

import com.dre.brewery.BPlayer;
import com.dre.brewery.BreweryPlugin;
import com.dre.brewery.commands.SubCommand;
import com.dre.brewery.configuration.files.Lang;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public final class InfoCommand implements SubCommand {


    @Override
    public void execute(final BreweryPlugin breweryPlugin, final Lang lang, final CommandSender sender, final String label, final String[] args) {
        if (args.length > 1) {
            if (sender.hasPermission("brewery.cmd.infoOther")) {
                this.cmdInfo(sender, args[1], lang);
            } else {
                lang.sendEntry(sender, "Error_NoPermissions");
            }
        } else {
            if (sender.hasPermission("brewery.cmd.info")) {
                this.cmdInfo(sender, null, lang);
            } else {
                lang.sendEntry(sender, "Error_NoPermissions");
            }
        }
    }

    @Override
    public List<String> tabComplete(final BreweryPlugin breweryPlugin, final CommandSender sender, final String label, final String[] args) {
        return null;
    }

    @Override
    public String permission() {
        return "brewery.cmd.info";
    }

    @Override
    public boolean playerOnly() {
        return false;
    }

    public final void cmdInfo(final CommandSender sender, String playerName, final Lang lang) {

        final var selfInfo = playerName == null;
        if (selfInfo) {
            if (sender instanceof final Player player) {
                playerName = player.getName();
            } else {
                lang.sendEntry(sender, "Error_PlayerCommand");
                return;
            }
        }

        final var player = BreweryPlugin.getInstance().getServer().getPlayerExact(playerName);
        final BPlayer bPlayer;
        if (player == null) {
            bPlayer = BPlayer.getByName(playerName);
        } else {
            bPlayer = BPlayer.get(player);
        }
        if (bPlayer == null) {
            lang.sendEntry(sender, "CMD_Info_NotDrunk", playerName);
        } else {
            if (selfInfo) {
                bPlayer.showDrunkeness(player);
            } else {
                lang.sendEntry(sender, "CMD_Info_Drunk", playerName, "" + bPlayer.getDrunkeness(), "" + bPlayer.getQuality());
            }
        }

    }
}
