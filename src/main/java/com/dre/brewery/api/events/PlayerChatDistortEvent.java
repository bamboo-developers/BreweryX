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

package com.dre.brewery.api.events;

import com.dre.brewery.BPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * The Player writes something in Chat or on a Sign and his words are distorted.
 *
 * <p>This Event may be Async if the Chat Event is Async!
 */
public final class PlayerChatDistortEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();

    private final Player player;
    private final BPlayer bPlayer;
    private final String prevMsg;
    private String distortMsg;
    private boolean cancelled;

    public PlayerChatDistortEvent(final boolean async, final Player player, final BPlayer bPlayer, final String prevMsg, final String distortMsg) {
        super(async);
        this.player = player;
        this.bPlayer = bPlayer;
        this.prevMsg = prevMsg;
        this.distortMsg = distortMsg;
    }

    // Required by Bukkit
    public static HandlerList getHandlerList() {
        return handlers;
    }

    @NotNull
    public Player getPlayer() {
        return this.player;
    }

    @NotNull
    public BPlayer getbPlayer() {
        return this.bPlayer;
    }

    /**
     * @return The Message the Player had actually written
     */
    @NotNull
    public String getWrittenMessage() {
        return this.prevMsg;
    }

    /**
     * @return The message after it was distorted
     */
    @NotNull
    public final String getDistortedMessage() {
        return this.distortMsg;
    }

    /**
     * Set the Message that the player will say instead of what he wrote
     */
    public void setDistortedMessage(final String distortMsg) {
        this.distortMsg = Objects.requireNonNull(distortMsg);
    }

    /**
     * @return The drunkenness of the player that is writing the message
     */
    public int getDrunkeness() {
        return this.bPlayer.getDrunkeness();
    }

    @Override
    public final boolean isCancelled() {
        return this.cancelled;
    }

    @Override
    public final void setCancelled(final boolean cancelled) {
        this.cancelled = cancelled;
    }

    @NotNull
    @Override
    public final HandlerList getHandlers() {
        return handlers;
    }
}
