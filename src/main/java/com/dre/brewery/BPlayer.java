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

import com.dre.brewery.api.events.PlayerEffectEvent;
import com.dre.brewery.api.events.PlayerPukeEvent;
import com.dre.brewery.api.events.PlayerPushEvent;
import com.dre.brewery.api.events.brew.BrewDrinkEvent;
import com.dre.brewery.configuration.ConfigManager;
import com.dre.brewery.configuration.files.Config;
import com.dre.brewery.configuration.files.Lang;
import com.dre.brewery.lore.BrewLore;
import com.dre.brewery.recipe.BEffect;
import com.dre.brewery.utility.*;
import com.tcoded.folialib.wrapper.task.WrappedTask;
import io.papermc.lib.PaperLib;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@ToString
@Getter
@Setter
public final class BPlayer {

    private static final MinecraftVersion VERSION = BreweryPlugin.getMCVersion();
    private static final Config config = ConfigManager.getConfig(Config.class);
    private static final Lang lang = ConfigManager.getConfig(Lang.class);

    private static final ConcurrentHashMap<String, BPlayer> players = new ConcurrentHashMap<>();// Players uuid and BPlayer
    private static final ConcurrentHashMap<Player, Integer> pTasks = new ConcurrentHashMap<>();// Player and count
    private static WrappedTask task;
    private static Random pukeRand;

    private final String uuid;
    private int quality = 0;// = quality of drunkenness * drunkenness
    private int drunkenness = 0;// = amount of drunkenness
    private int offlineDrunk = 0;// drunkenness when gone offline
    private int alcRecovery = -1; // Drunkeness reduce per minute
    private Vector push = new Vector(0, 0, 0);
    private int time = 20;

    public BPlayer(final String uuid) {
        this.uuid = uuid;
    }

    // reading from file
    public BPlayer(final String uuid, final int quality, final int drunkenness, final int offlineDrunk) {
        this.quality = quality;
        this.drunkenness = drunkenness;
        this.offlineDrunk = offlineDrunk;
        this.uuid = uuid;
    }

    public BPlayer(final UUID uuid, final int quality, final int drunkenness, final int offlineDrunk) {
        this(uuid.toString(), quality, drunkenness, offlineDrunk);
    }

    public BPlayer(final UUID uuid) {
        this(uuid.toString());
    }

    @Nullable
    public static BPlayer get(final OfflinePlayer player) {
        if (!players.isEmpty()) {
            return players.get(player.getUniqueId().toString());
        }
        return null;
    }

    // This method may be slow and should not be used if not needed
    @Nullable
    public static BPlayer getByName(final String playerName) {
        for (final var entry : players.entrySet()) {
            final var p = BreweryPlugin.getInstance().getServer().getOfflinePlayer(UUID.fromString(entry.getKey()));
            final var name = p.getName();
            if (name != null) {
                if (name.equalsIgnoreCase(playerName)) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    // This method may be slow and should not be used if not needed
    public static boolean hasPlayerbyName(final String playerName) {
        for (final var entry : players.entrySet()) {
            final var p = BreweryPlugin.getInstance().getServer().getOfflinePlayer(UUID.fromString(entry.getKey()));
            if (p != null) {
                final var name = p.getName();
                if (name != null) {
                    if (name.equalsIgnoreCase(playerName)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static ConcurrentHashMap<String, BPlayer> getPlayers() {
        return players;
    }

    public static boolean isEmpty() {
        return players.isEmpty();
    }

    public static boolean hasPlayer(final OfflinePlayer player) {
        return players.containsKey(player.getUniqueId().toString());
    }

    // Create a new BPlayer and add it to the list
    public static BPlayer addPlayer(final OfflinePlayer player) {
        final var bPlayer = new BPlayer(player.getUniqueId());
        players.put(player.getUniqueId().toString(), bPlayer);
        return bPlayer;
    }

    public static void remove(final OfflinePlayer player) {
        players.remove(player.getUniqueId().toString());
    }


    public static int numDrunkPlayers() {
        return players.size();
    }

    public static void clear() {
        players.clear();
    }

    // Drink a brew and apply effects, etc.
    public static boolean drink(Brew brew, final Player player, @Nullable final ItemMeta meta, @Nullable final PlayerItemConsumeEvent event) {
        var bPlayer = get(player);
        if (bPlayer == null) {
            bPlayer = addPlayer(player);
        }
        // In this event the added alcohol amount is calculated, based on the sensitivity permission
        final var drinkEvent = new BrewDrinkEvent(brew, meta, player, bPlayer, event);
        if (meta != null) {
            BreweryPlugin.getInstance().getServer().getPluginManager().callEvent(drinkEvent);
            if (brew != drinkEvent.getBrew()) brew = drinkEvent.getBrew();
            if (drinkEvent.isCancelled()) {
                if (bPlayer.drunkenness <= 0) {
                    bPlayer.remove();
                }
                return false;
            }
        }

        if (brew.hasRecipe()) {
            brew.getCurrentRecipe().applyDrinkFeatures(player, brew.getQuality());
        }
        BreweryPlugin.getInstance().getBreweryStats().forDrink(brew);

        final var brewAlc = drinkEvent.getAddedAlcohol();
        final var quality = drinkEvent.getQuality();
        final var effects = getBrewEffects(brew.getEffects(), quality);

        applyEffects(effects, player, PlayerEffectEvent.EffectType.DRINK);
        if (brewAlc < 0) {
            // If the Drink has negative alcohol, drain some alcohol
            bPlayer.drain(player, -brewAlc);
        } else if (brewAlc > 0) {
            bPlayer.drunkenness += brewAlc;
            if (quality > 0) {
                bPlayer.quality += quality * brewAlc;
            } else {
                bPlayer.quality += brewAlc;
            }

            applyEffects(getQualityEffects(quality, brewAlc), player, PlayerEffectEvent.EffectType.QUALITY);
        }

        if (bPlayer.drunkenness > 100) {
            bPlayer.drinkCap(player);
        }

        if (config.isShowStatusOnDrink()) {
            // Only show the Player his drunkenness if he is already drunk, or this drink changed his drunkenness
            if (brewAlc != 0 || bPlayer.drunkenness > 0) {
                bPlayer.showDrunkeness(player);
            }
        }

        if (bPlayer.drunkenness <= 0) {
            bPlayer.remove();
        }
        return true;
    }

    // push the player around if he moves
    public static void playerMove(final PlayerMoveEvent event) {
        final var bPlayer = get(event.getPlayer());
        if (bPlayer != null) {
            bPlayer.move(event);
        }
    }

    // make a Player puke "count" items
    public static void addPuke(final Player player, final int count) {
        if (!config.isEnablePuke()) {
            return;
        }

        final var event = new PlayerPukeEvent(player, count);
        BreweryPlugin.getInstance().getServer().getPluginManager().callEvent(event);
        if (event.isCancelled() || event.getCount() < 1) {
            return;
        }
        BUtil.reapplyPotionEffect(player, BukkitConstants.HUNGER.createEffect(80, 4), true);

        if (pTasks.isEmpty()) {
            task = BreweryPlugin.getScheduler().runAtEntityTimer(player, BPlayer::pukeTask, 1L, 1L);
        }
        pTasks.put(player, event.getCount());
    }

    public static void pukeTask() {
        for (final var iter = pTasks.entrySet().iterator(); iter.hasNext(); ) {
            final var entry = iter.next();
            final var player = entry.getKey();
            final int count = entry.getValue();
            if (!player.isValid() || !player.isOnline()) {
                iter.remove();
                continue;
            }
            puke(player);
            if (count <= 1) {
                iter.remove();
            } else {
                entry.setValue(count - 1);
            }
        }
        if (pTasks.isEmpty()) {
            task.cancel();
        }
    }

    public static void puke(final Player player) {
        if (pukeRand == null) {
            pukeRand = new Random();
        }
        if (config.getPukeItem() == null || config.getPukeItem().isEmpty()) {
            config.setPukeItem(List.of(Material.SOUL_SAND));
        }
        final var loc = player.getLocation();
        loc.setY(loc.getY() + 1.1);
        loc.setPitch(loc.getPitch() - 10 + pukeRand.nextInt(20));
        loc.setYaw(loc.getYaw() - 10 + pukeRand.nextInt(20));
        final var direction = loc.getDirection();
        direction.multiply(0.5);
        loc.add(direction);

        final var item = player.getWorld().dropItem(loc, new ItemStack(config.getPukeItem().get(new Random().nextInt(config.getPukeItem().size()))));
        item.setVelocity(direction);
        item.setPickupDelay(32767); // Item can never be picked up when pickup delay is 32767
        item.setMetadata("brewery_puke", new FixedMetadataValue(BreweryPlugin.getInstance(), true));
        if (VERSION.isOrLater(MinecraftVersion.V1_14)) item.setPersistent(false); // No need to save Puke items

        final var pukeDespawntime = config.getPukeDespawntime();
        final var despawnRate = BUtil.getItemDespawnRate(player.getWorld());
        if (pukeDespawntime >= (despawnRate - 200)) {
            return;
        }

        // Setting the age determines when an item is despawned. At age 6000 it is removed.
        if (pukeDespawntime <= 0) {
            // Just show the item for a few ticks
            item.setTicksLived(despawnRate - 4);
        } else if (pukeDespawntime <= 120) {
            // it should despawn in less than 6 sec. Add up to half of that randomly
            item.setTicksLived(despawnRate - pukeDespawntime + pukeRand.nextInt((int) (pukeDespawntime / 2F)));
        } else {
            // Add up to 5 sec randomly
            item.setTicksLived(despawnRate - pukeDespawntime + pukeRand.nextInt(100));
        }
    }

    public static void applyEffects(List<PotionEffect> effects, final Player player, final PlayerEffectEvent.EffectType effectType) {
        final var event = new PlayerEffectEvent(player, effectType, effects);
        BreweryPlugin.getInstance().getServer().getPluginManager().callEvent(event);
        effects = event.getEffects();
        if (event.isCancelled() || effects == null) {
            return;
        }
        for (final var effect : effects) {
            BUtil.reapplyPotionEffect(player, effect, true);
        }
    }

    public static List<PotionEffect> getQualityEffects(final int quality, final int brewAlc) {
        final List<PotionEffect> out = new ArrayList<>(2);
        var duration = 7 - quality;
        if (quality == 0) {
            duration *= 125;
        } else if (quality <= 5) {
            duration *= 62;
        } else {
            duration = 25;
            if (brewAlc <= 10) {
                duration = 0;
            }
        }
        if (VERSION.isOrEarlier(MinecraftVersion.V1_14)) {
            duration *= 4;
        }
        if (duration > 0) {
            out.add(BukkitConstants.POISON.createEffect(duration, 0));
        }

        if (brewAlc > 10) {
            if (quality <= 5) {
                duration = 10 - quality;
                duration += brewAlc;
                duration *= 15;
            } else {
                duration = 30;
            }
            if (VERSION.isOrEarlier(MinecraftVersion.V1_14)) {
                duration *= 4;
            }
            out.add(BukkitConstants.BLINDNESS.createEffect(duration, 0));
        }
        return out;
    }

    public static void addQualityEffects(final int quality, final int brewAlc, final Player player) {
        var list = getQualityEffects(quality, brewAlc);
        final var event = new PlayerEffectEvent(player, PlayerEffectEvent.EffectType.QUALITY, list);
        BreweryPlugin.getInstance().getServer().getPluginManager().callEvent(event);
        list = event.getEffects();
        if (event.isCancelled() || list == null) {
            return;
        }
        for (final var effect : list) {
            BUtil.reapplyPotionEffect(player, effect, true);
        }
    }

    public static List<PotionEffect> getBrewEffects(final List<BEffect> effects, final int quality) {
        final List<PotionEffect> out = new ArrayList<>();
        if (effects != null) {
            for (final var effect : effects) {
                final var e = effect.generateEffect(quality);
                if (e != null) {
                    out.add(e);
                }
            }
        }
        return out;
    }

    public static void addBrewEffects(final Brew brew, final Player player) {
        final var effects = brew.getEffects();
        if (effects != null) {
            for (final var effect : effects) {
                effect.apply(brew.getQuality(), player);
            }
        }
    }

    public static void drunkenness() {
        for (final var entry : players.entrySet()) {
            final var name = entry.getKey();
            final var bplayer = entry.getValue();

            if (bplayer.drunkenness > 30) {
                if (bplayer.offlineDrunk == 0) {
                    final var player = BUtil.getPlayerfromString(name);
                    if (player != null) {

                        bplayer.drunkEffects(player);

                        if (config.isEnablePuke()) {
                            bplayer.drunkPuke(player);
                        }

                    }
                }
            }
        }
    }

    // decreasing drunkenness over time
    public static void onUpdate() {
        if (!players.isEmpty()) {
            final var iter = players.entrySet().iterator();
            while (iter.hasNext()) {
                final var entry = iter.next();
                final var uuid = entry.getKey();
                final var bplayer = entry.getValue();
                final var playerIfOnline = BUtil.getPlayerfromString(uuid);

                if (bplayer.getAlcRecovery() == -1) {
                    bplayer.recalculateAlcRecovery(playerIfOnline);
                }

                if (bplayer.drain(playerIfOnline, bplayer.getAlcRecovery())) {
                    iter.remove();
                }
            }
        }
    }

    // save all data
    public static void save(final ConfigurationSection config) {
        for (final var entry : players.entrySet()) {
            final var section = config.createSection(entry.getKey());
            final var bPlayer = entry.getValue();
            section.set("quality", bPlayer.quality);
            section.set("drunk", bPlayer.drunkenness);
            if (bPlayer.offlineDrunk != 0) {
                section.set("offDrunk", bPlayer.offlineDrunk);
            }
        }
    }

    public final void remove() {
        for (final var iterator = players.entrySet().iterator(); iterator.hasNext(); ) {
            final var entry = iterator.next();
            if (entry.getValue() == this) {
                iterator.remove();
                return;
            }
        }
    }


    // #### Login ####

    /**
     * Show the Player his current drunkenness and quality as an Actionbar graphic or when unsupported, in chat
     */
    public final void showDrunkeness(final Player player) {
        try {
            // It this returns false, then the Action Bar is not supported. Do not repeat the message as it was sent into chat
            if (this.sendDrunkenessMessage(player)) {
                BreweryPlugin.getScheduler().runLater(() -> this.sendDrunkenessMessage(player), 40);
                BreweryPlugin.getScheduler().runLater(() -> this.sendDrunkenessMessage(player), 80);
            }
        } catch (final Exception e) {
            Logging.errorLog("Failed to show drunkenness to " + player.getName(), e);
        }
    }

    /**
     * Send one Message to the player, showing his drunkenness or hangover
     *
     * @param player The Player to send the message to
     * @return false if the message should not be repeated.
     */
    public final boolean sendDrunkenessMessage(final Player player) {
        final var b = new StringBuilder(100);

        var strength = this.drunkenness;
        var hangover = false;
        if (this.offlineDrunk > 0) {
            strength = this.offlineDrunk;
            hangover = true;
        }

        b.append(lang.getEntry(hangover ? "Player_Hangover" : "Player_Drunkeness"));

        // Drunkenness or Hangover Strength Bars
        b.append("§7[");
        b.append(this.generateBars(strength, hangover));
        b.append("§7] ");

        final int quality;
        if (hangover) {
            quality = 11 - this.getHangoverQuality();
        } else {
            quality = strength > 0 ? this.getQuality() : 0;
        }

        // Quality Stars
        b.append("§7[");
        b.append(this.generateStars(quality));
        b.append("§7]");

        final var text = b.toString();
        if (hangover && VERSION.isOrLater(MinecraftVersion.V1_11)) {
            BreweryPlugin.getScheduler().runLater(() -> player.sendTitle("", text, 30, 100, 90), 160);
            return false;
        }
        try {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(text));
            return true;
        } catch (final UnsupportedOperationException | NoSuchMethodError e) {
            player.sendMessage(text);
            return false;
        }
    }

    private String generateBars(final int strength, final boolean hangover) {
        // Generate 25 Bars, color one per 4 drunkenness
        final var b = new StringBuilder();
        final int bars;
        if (strength <= 0) {
            bars = 0;
        } else if (strength == 1) {
            bars = 1;
        } else {
            bars = Math.round(strength / 4.0f);
        }
        var noBars = 25 - bars;
        if (bars > 0) {
            b.append(hangover ? "§c" : "§6");
        }
        for (var addedBars = 0; addedBars < bars; addedBars++) {
            b.append("|");
            if (addedBars == 20) {
                // color the last 4 bars red
                b.append("§c");
            }
        }
        if (noBars > 0) {
            b.append("§0");
            for (; noBars > 0; noBars--) {
                b.append("|");
            }
        }
        return b.toString();
    }

    public final String generateBars() {
        return this.generateBars(this.offlineDrunk > 0 ? this.offlineDrunk : this.drunkenness, this.offlineDrunk > 0);
    }

    private String generateStars(final int quality) {
        // Generate stars representing the quality
        final var b = new StringBuilder();
        var stars = quality / 2;
        final var half = quality % 2 > 0;
        var noStars = 5 - stars - (half ? 1 : 0);

        b.append(BrewLore.getQualityColor(quality));
        for (; stars > 0; stars--) {
            b.append("⭑");
        }
        if (half) {
            b.append("⭒");
        }
        if (noStars > 0) {
            b.append("§0");
            for (; noStars > 0; noStars--) {
                b.append("⭑");
            }
        }

        return b.toString();
    }


    // #### Puking ####

    public final String generateStars() {
        return this.generateStars(this.offlineDrunk > 0 ? 11 - this.getHangoverQuality() : this.drunkenness > 0 ? this.getQuality() : 0);
    }

    // Player has drunken too much
    public final void drinkCap(final Player player) {
        this.quality = this.getQuality() * 100;
        this.drunkenness = 100;
        if (config.isEnableKickOnOverdrink() && !player.hasPermission("brewery.bypass.overdrink")) {
            BreweryPlugin.getScheduler().runLater(() -> this.passOut(player), 1);
        } else {
            addPuke(player, 60 + (int) (Math.random() * 60.0));
            lang.sendEntry(player, "Player_CantDrink");
        }
    }

    // Eat something to drain the drunkenness
    public final void drainByItem(final Player player, final Material mat) {
        final int strength = BUtil.getMaterialMap(config.getDrainItems()).get(mat);
        if (this.drain(player, strength)) {
            remove(player);
        }
    }

    // drain the drunkenness by amount, returns true when player has to be removed
    public final boolean drain(@Nullable final Player player, final int amount) {
        if (this.drunkenness > 0) {
            this.quality -= this.getQuality() * amount;
        }
        this.drunkenness -= amount;
        if (this.drunkenness > 0) {
            if (this.offlineDrunk == 0) {
                if (player == null) {
                    this.offlineDrunk = this.drunkenness;
                }
            }
        } else {
            if (this.offlineDrunk == 0) {
                return true;
            }
            if (this.drunkenness == 0) {
                this.drunkenness--;
            }
            this.quality = this.getQuality();
            if (this.drunkenness <= -this.offlineDrunk) {
                return this.drunkenness <= -config.getHangoverDays();
            }
        }
        return false;
    }


    // #### Effects ####

    // player is drunk
    public final void move(final PlayerMoveEvent event) {
        // has player more alc than 10
        if (this.drunkenness >= 10 && config.getStumblePercent() > 0.001f) {
            if (this.drunkenness <= 100) {
                if (this.time > 1) {
                    this.time--;
                } else {
                    // Is he moving
                    if (event.getFrom().getX() != event.getTo().getX() || event.getFrom().getZ() != event.getTo().getZ()) {
                        final var player = event.getPlayer();
                        // We have to cast here because it had issues otherwise on previous versions of Minecraft
                        // Don't know if that's still the case, but we better leave it
                        // not in midair
                        if (((Entity) player).isOnGround()) {
                            this.time--;
                            if (this.time == 0) {
                                // push him only to the side? or any direction
                                // like now
                                if (VERSION.isOrLater(MinecraftVersion.V1_9)) { // Pushing is way stronger in 1.9
                                    this.push.setX((Math.random() - 0.5) / 2.0);
                                    this.push.setZ((Math.random() - 0.5) / 2.0);
                                } else {
                                    this.push.setX(Math.random() - 0.5);
                                    this.push.setZ(Math.random() - 0.5);
                                }
                                this.push.multiply(config.getStumblePercent());
                                final var pushEvent = new PlayerPushEvent(player, this.push, this);
                                BreweryPlugin.getInstance().getServer().getPluginManager().callEvent(pushEvent);
                                this.push = pushEvent.getPush();
                                if (pushEvent.isCancelled() || this.push.lengthSquared() <= 0) {
                                    this.time = -10;
                                    return;
                                }
                                player.setVelocity(this.push);
                            } else if (this.time < 0 && this.time > -10) {
                                // push him some more in the same direction
                                player.setVelocity(this.push);
                            } else {
                                // when more alc, push him more often
                                this.time = (int) (Math.random() * (201.0 - (this.drunkenness * 2)));
                            }
                        }
                    }
                }
            }
        }
    }

    public final void passOut(final Player player) {
        player.kickPlayer(lang.getEntry("Player_DrunkPassOut"));
        this.offlineDrunk = this.drunkenness;
    }

    // can the player login or is he too drunk
    public final int canJoin() {
        if (this.drunkenness <= 70) {
            return 0;
        }
        if (!config.isEnableLoginDisallow()) {
            if (this.drunkenness <= 100) {
                return 0;
            } else {
                return 3;
            }
        }
        if (this.drunkenness <= 90) {
            if (Math.random() > 0.4) {
                return 0;
            } else {
                return 2;
            }
        }
        if (this.drunkenness <= 100) {
            if (Math.random() > 0.6) {
                return 0;
            } else {
                return 2;
            }
        }
        return 3;
    }

    // he may be having a hangover
    public final void join(final Player player) {
        // TODO: Rewrite part of this class to not use offlinedrunk, a bunch of this overhead boilerplate is completely unnecessary and overcomplicates our code
        // Modified this method a bit to just patch wakeups not working but this *REALLY* needs a rewrite

        if (this.drunkenness < 10) {
            if (this.offlineDrunk > 60) {
                if (config.isEnableHome() && !player.hasPermission("brewery.bypass.teleport")) {
                    this.goHome(player);
                }
            }
            if (this.offlineDrunk > 20) {
                this.hangoverEffects(player);
                this.showDrunkeness(player);
            }
            if (this.drunkenness <= 0) {
                remove(player);
            }

        } else if (this.offlineDrunk >= 30 || this.drunkenness >= 30) {
            if (config.isEnableWake() && !player.hasPermission("brewery.bypass.teleport")) {
                final var randomLoc = Wakeup.getRandom(player.getLocation());
                if (randomLoc != null) {
                    PaperLib.teleportAsync(player, randomLoc);
                    lang.sendEntry(player, "Player_Wake");
                }
            }
        }

        this.offlineDrunk = 0;
    }

    public final void disconnecting() {
        this.offlineDrunk = this.drunkenness;
    }

    public final void goHome(final Player player) {
        final var homeType = config.getHomeType();
        if (homeType == null) {
            return;
        }
        if (homeType.equalsIgnoreCase("bed")) {
            PaperLib.getBedSpawnLocationAsync(player, true).thenAcceptAsync(it -> {
                if (it != null) {
                    PaperLib.teleportAsync(player, it);
                }
            });
        } else if (homeType.startsWith("cmd: ")) {
            player.performCommand(homeType.substring(5));
        } else if (homeType.startsWith("cmd:")) {
            player.performCommand(homeType.substring(4));
        } else {
            Logging.errorLog("Config.yml 'homeType: " + homeType + "' unknown!");
        }
    }

    public final void recalculateAlcRecovery(@Nullable final Player player) {
        this.setAlcRecovery(2);
        if (player != null) {
            final var rec = PermissionUtil.getAlcRecovery(player);
            if (rec > -1) {
                this.setAlcRecovery(rec);
            }
        }
    }


    // #### Scheduled ####

    // Chance that players puke on big drunkenness
    // runs every 6 sec, average chance is 15%, so should puke about every 40 sec
    // good quality can decrease the chance by up to 15%
    public final void drunkPuke(final Player player) {
        if (this.drunkenness >= 90) {
            // chance between 20% and 10%
            if (Math.random() < 0.20f - (this.getQuality() / 100f)) {
                addPuke(player, 20 + (int) (Math.random() * 40));
            }
        } else if (this.drunkenness >= 80) {
            // chance between 15% and 0%
            if (Math.random() < 0.15f - (this.getQuality() / 66f)) {
                addPuke(player, 10 + (int) (Math.random() * 30));
            }
        } else if (this.drunkenness >= 70) {
            // chance between 10% at 1 quality and 0% at 6 quality
            if (Math.random() < 0.10f - (this.getQuality() / 60f)) {
                addPuke(player, 10 + (int) (Math.random() * 20));
            }
        }
    }

    public final void drunkEffects(final Player player) {
        var duration = 10 - this.getQuality();
        duration += this.drunkenness / 2;
        duration *= 5;
        if (duration > 240) {
            duration *= 5;
        } else if (duration < 115) {
            duration = 115;
        }
        if (VERSION.isOrEarlier(MinecraftVersion.V1_14)) {
            duration *= 4;
        }
        List<PotionEffect> l = new ArrayList<>(1);
        l.add(BukkitConstants.NAUSEA.createEffect(duration, 0));

        final var event = new PlayerEffectEvent(player, PlayerEffectEvent.EffectType.ALCOHOL, l);
        BreweryPlugin.getInstance().getServer().getPluginManager().callEvent(event);
        l = event.getEffects();
        if (event.isCancelled() || l == null) {
            return;
        }
        for (final var effect : l) {
            BreweryPlugin.getScheduler().runAtEntityLater(player, () -> effect.apply(player), 0); // Fix can't add effect to entities Async
        }
    }

    public final void hangoverEffects(final Player player) {
        var duration = this.offlineDrunk * 25 * this.getHangoverQuality();
        if (VERSION.isOrEarlier(MinecraftVersion.V1_14)) {
            duration *= 2;
        }
        final var amplifier = this.getHangoverQuality() / 3;

        List<PotionEffect> list = new ArrayList<>(2);
        list.add(BukkitConstants.SLOWNESS.createEffect(duration, amplifier));
        list.add(BukkitConstants.HUNGER.createEffect(duration, amplifier));

        final var event = new PlayerEffectEvent(player, PlayerEffectEvent.EffectType.HANGOVER, list);
        BreweryPlugin.getInstance().getServer().getPluginManager().callEvent(event);
        list = event.getEffects();
        if (event.isCancelled() || list == null) {
            return;
        }
        for (final var effect : list) {
            BUtil.reapplyPotionEffect(player, effect, true);
        }
    }


    // #### getter/setter ####

    public final String getUuid() {
        return this.uuid;
    }

    public final int getDrunkeness() {
        return this.drunkenness;
    }

    public final void setDrunkeness(final int value) {
        this.drunkenness = value;
    }

    public final void setData(final int drunkenness, final int quality) {
        if (quality > 0) {
            this.quality = quality * drunkenness;
        } else {
            if (this.quality == 0) {
                this.quality = 5 * drunkenness;
            } else {
                this.quality = this.getQuality() * drunkenness;
            }
        }
        this.drunkenness = drunkenness;
    }

    public final int getQuality() {
        if (this.drunkenness == 0) {
            // PAPI Placeholder %breweryx_quality% may be used on players that aren't drunk!
            // Logging.errorLog("drunkenness should not be 0!");
            return this.quality;
        }
        if (this.drunkenness < 0) {
            return this.quality;
        }
        return Math.round((float) this.quality / (float) this.drunkenness);
    }

    public final void setQuality(final int value) {
        this.quality = value;
    }

    public int getQualityData() {
        return this.quality;
    }

    // opposite of quality
    public final int getHangoverQuality() {
        if (this.drunkenness < 0) {
            return this.quality + 11;
        }
        return -this.getQuality() + 11;
    }

    /**
     * Drunkeness at the time he went offline
     */
    public final int getOfflineDrunkeness() {
        return this.offlineDrunk;
    }

    public final int getAlcRecovery() {
        return this.alcRecovery;
    }

    public final void setAlcRecovery(final int alcRecovery) {
        this.alcRecovery = alcRecovery;
    }


    public String getName() {
        final var player = BUtil.getPlayerfromString(this.uuid);
        final OfflinePlayer offlinePlayer;

        if (player != null) {
            return player.getName();
        } else {
            offlinePlayer = Bukkit.getOfflinePlayer(UUID.fromString(this.uuid));
        }
        return offlinePlayer.getName();
    }
}
