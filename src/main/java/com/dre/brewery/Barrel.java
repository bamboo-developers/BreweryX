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

import com.dre.brewery.api.events.barrel.BarrelAccessEvent;
import com.dre.brewery.api.events.barrel.BarrelCreateEvent;
import com.dre.brewery.api.events.barrel.BarrelDestroyEvent;
import com.dre.brewery.api.events.barrel.BarrelRemoveEvent;
import com.dre.brewery.configuration.ConfigManager;
import com.dre.brewery.configuration.files.Config;
import com.dre.brewery.configuration.files.Lang;
import com.dre.brewery.lore.BrewLore;
import com.dre.brewery.utility.BoundingBox;
import com.dre.brewery.utility.Logging;
import com.dre.brewery.utility.MinecraftVersion;
import com.google.common.base.Preconditions;
import com.tcoded.folialib.wrapper.task.WrappedTask;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * A Multi Block Barrel with Inventory
 */
@Getter
@Setter
public final class Barrel extends BarrelBody implements InventoryHolder {

    private static final Map<UUID, List<Barrel>> barrels = new ConcurrentHashMap<>();
    // Barrels set aside by onUnload(World), to be restored by onLoad(World) without re-reading storage
    private static final Map<UUID, List<Barrel>> unloadedBarrels = new ConcurrentHashMap<>();
    private static final Config config = ConfigManager.getConfig(Config.class);
    private static final Lang lang = ConfigManager.getConfig(Lang.class);
    private static Map<UUID, Integer> checkCounters = new ConcurrentHashMap<>(); // Which Barrel was last checked
    private static final Map<UUID, WrappedTask> barrelCheckTasks = new ConcurrentHashMap<>(); // One running BarrelCheck per world
    private static String etcBarrelTitle; // Cached, colored "Etc_Barrel" lang entry, used as the inventory title
    /**
     * -- GETTER --
     * Is this a small barrel?
     */
    private final boolean small;
    private final UUID id;
    private boolean checked; // Checked by the random BarrelCheck routine
    private Inventory inventory;
    private float time;

    /**
     * Create a new Barrel, has to be done in the primary thread
     */
    public Barrel(final Block spigot, final byte signoffset) {
        super(spigot, signoffset);
        this.small = this.computeSmall();
        this.inventory = Bukkit.createInventory(this, !this.small ? config.getBarrelInvSizeLarge() * 9 : config.getBarrelInvSizeSmall() * 9, getEtcBarrelTitle());
        this.id = UUID.randomUUID();
    }

    public Barrel(final Block spigot, final byte signoffset, final boolean isSmall) {
        super(spigot, signoffset);
        this.small = isSmall;
        this.inventory = Bukkit.createInventory(this, !isSmall ? config.getBarrelInvSizeLarge() * 9 : config.getBarrelInvSizeSmall() * 9, getEtcBarrelTitle());
        this.id = UUID.randomUUID();
    }

    /**
     * Load from File
     * <p>If async: true, The Barrel Bounds will not be recreated when missing/corrupt, getBody().getBounds() will be null if it needs recreating
     * Note from Jsinco, async is now checked using Bukkit.isPrimaryThread().^
     */
    public Barrel(final Block spigot, final byte sign, final BoundingBox bounds, @Nullable final Map<String, Object> items, final float time, final UUID id, final boolean isSmall) {
        super(spigot, sign, bounds);
        this.small = isSmall;
        this.inventory = Bukkit.createInventory(this, this.isLarge() ? config.getBarrelInvSizeLarge() * 9 : config.getBarrelInvSizeSmall() * 9, getEtcBarrelTitle());
        if (items != null) {
            for (final var slot : items.keySet()) {
                if (items.get(slot) instanceof ItemStack) {
                    this.inventory.setItem(Integer.parseInt(slot), (ItemStack) items.get(slot));
                }
            }
        }
        this.time = time;
        this.id = id;
    }

    public Barrel(final Block spigot, final byte sign, final BoundingBox bounds, final ItemStack[] items, final float time, final UUID id, final boolean isSmall) {
        super(spigot, sign, bounds);
        this.small = isSmall;
        this.inventory = Bukkit.createInventory(this, this.isLarge() ? config.getBarrelInvSizeLarge() * 9 : config.getBarrelInvSizeSmall() * 9, getEtcBarrelTitle());
        if (items != null) {
            for (var slot = 0; slot < items.length; slot++) {
                if (items[slot] != null) {
                    this.inventory.setItem(slot, items[slot]);
                }
            }
        }
        this.time = time;
        this.id = id;
    }

    public static void onUpdate() {
        barrels.values()
                .stream()
                .flatMap(List::stream)
                .filter(Objects::nonNull)
                .forEach(barrel -> barrel.time += (float) (1.0 / config.getAgingYearDuration()));
        for (final var worldUuid : barrels.keySet()) {
            final var worldBarrels = barrels.get(worldUuid);
            final var numBarrels = worldBarrels.size();
            if (numBarrels > 0 && !barrelCheckTasks.containsKey(worldUuid)) {
                final var random = worldBarrels.get((int) Math.floor(Math.random() * numBarrels));
                if (random != null) {
                    // You have been selected for a random search
                    // We want to check at least one barrel every time
                    random.checked = false;
                }
                if (numBarrels > 50) {
                    final var randomInTheBack = worldBarrels.get(numBarrels - 1 - (int) (Math.random() * (numBarrels >>> 2)));
                    if (randomInTheBack != null) {
                        // Prioritize checking one of the less recently used barrels as well
                        randomInTheBack.checked = false;
                    }
                }
                // computeIfAbsent keeps this to one running check per world; the task removes itself when its round finishes
                barrelCheckTasks.computeIfAbsent(worldUuid,
                        uuid -> BreweryPlugin.getScheduler().runTimer(new BarrelCheck(uuid), 1, 20));
            }
        }
    }

    public static @NotNull List<Barrel> getBarrels(final UUID worldUuid) {
        final var worldBarrels = barrels.get(worldUuid);
        return worldBarrels == null ? List.of() : worldBarrels;
    }

    /**
     * Get the Barrel by Block, null if that block is not part of a barrel
     */
    @Nullable
    public static Barrel get(final Block block) {
        if (block == null) {
            return null;
        }
        final var type = block.getType();
        if (BarrelAsset.isBarrelAsset(BarrelAsset.FENCE, type) || BarrelAsset.isBarrelAsset(BarrelAsset.SIGN, type)) {
            return getBySpigot(block);
        } else {
            return getByWood(block);
        }
    }

    /**
     * Get the Barrel by Sign or Spigot (Fastest)
     */
    @Nullable
    public static Barrel getBySpigot(final Block sign) {
        // convert spigot if neccessary
        final var spigot = BarrelBody.getSpigotOfSign(sign);

        byte signoffset = 0;
        if (!spigot.equals(sign)) {
            signoffset = (byte) (sign.getY() - spigot.getY());
        }
        final var worldBarrels = barrels.get(sign.getWorld().getUID());
        if (worldBarrels == null) {
            return null;
        }
        var i = 0;
        for (final var barrel : worldBarrels) {
            if (barrel != null && barrel.isSignOfBarrel(signoffset)) {
                if (barrel.spigot.equals(spigot)) {
                    if (barrel.getSignoffset() == 0 && signoffset != 0) {
                        // Barrel has no signOffset even though we clicked a sign, may be old
                        barrel.setSignoffset(signoffset);
                    }
                    moveMRU(sign.getWorld().getUID(), i);
                    return barrel;
                }
            }
            i++;
        }
        return null;
    }

    /**
     * Get the barrel by its corpus (Wood Planks, Stairs)
     */
    @Nullable
    public static Barrel getByWood(final Block wood) {
        if (!BarrelAsset.isBarrelAsset(BarrelAsset.PLANKS, wood.getType()) && !BarrelAsset.isBarrelAsset(BarrelAsset.STAIRS, wood.getType())) {
            return null;
        }
        final var worldBarrels = barrels.get(wood.getWorld().getUID());
        if (worldBarrels == null) {
            return null;
        }
        for (var i = 0; i < worldBarrels.size(); i++) {
            final var barrel = worldBarrels.get(i);
            if (barrel.getSpigot().getWorld().equals(wood.getWorld()) && barrel.getBounds().contains(wood)) {
                moveMRU(wood.getWorld().getUID(), i);
                return barrel;
            }
        }
        return null;
    }

    // Move Barrel that was recently used more towards the front of the List
    // Optimizes retrieve by Block over time
    private static void moveMRU(final UUID worldUuid, final int index) {
        if (index <= 0) {
            return;
        }
        final var worldBarrels = barrels.get(worldUuid);
        if (index >= worldBarrels.size()) {
            return;
        }
        worldBarrels.set(index - 1, worldBarrels.set(index, worldBarrels.get(index - 1)));
    }

    /**
     * creates a new Barrel out of a sign
     */
    public static boolean create(final Block sign, final Player player) {
        final var spigot = BarrelBody.getSpigotOfSign(sign);

        // Check for already existing barrel at this location
        if (Barrel.get(spigot) != null) return false;

        byte signoffset = 0;
        if (!spigot.equals(sign)) {
            signoffset = (byte) (sign.getY() - spigot.getY());
        }

        var barrel = getBySpigot(spigot);
        if (barrel == null) {
            barrel = new Barrel(spigot, signoffset);
            if (barrel.getBrokenBlock(true) == null) {
                if (overlapsExistingBarrel(barrel)) {
                    return false;
                }
                if (BarrelAsset.isBarrelAsset(BarrelAsset.SIGN, spigot.getType())) {
                    if (!player.hasPermission("brewery.createbarrel.small")) {
                        lang.sendEntry(player, "Perms_NoBarrelCreate");
                        return false;
                    }
                } else {
                    if (!player.hasPermission("brewery.createbarrel.big")) {
                        lang.sendEntry(player, "Perms_NoBigBarrelCreate");
                        return false;
                    }
                }
                final var createEvent = new BarrelCreateEvent(barrel, player);
                BreweryPlugin.getInstance().getServer().getPluginManager().callEvent(createEvent);
                if (!createEvent.isCancelled()) {
                    barrels.computeIfAbsent(sign.getWorld().getUID(), ignored -> new ArrayList<>()).addFirst(barrel);
                    return true;
                }
            }
        } else {
            if (barrel.getSignoffset() == 0 && signoffset != 0) {
                barrel.setSignoffset(signoffset);
                return true;
            }
        }
        return false;
    }

    private static boolean overlapsExistingBarrel(final Barrel candidate) {
        final var candidateBounds = candidate.getBounds();
        if (candidateBounds == null) {
            return false;
        }

        final var worldBarrels = barrels.get(candidate.getSpigot().getWorld().getUID());
        if (worldBarrels == null) {
            return false;
        }

        for (final var existing : worldBarrels) {
            if (existing == null) {
                continue;
            }
            if (existing.getSpigot().equals(candidate.getSpigot())) {
                return true;
            }
            final var existingBounds = existing.getBounds();
            if (existingBounds != null && existingBounds.intersects(candidateBounds)) {
                return true;
            }
        }

        return false;
    }

    /**
     * @param spigotPosition Position of spigot
     * @return Future on whether this barrel is a small barrel, the future will be in an appropriate thread for modifying the world in that position
     */
    public static CompletableFuture<Boolean> computeSmall(final Location spigotPosition) {
        if (!MinecraftVersion.isFolia()) {
            return CompletableFuture.completedFuture(BarrelAsset.isBarrelAsset(BarrelAsset.SIGN, spigotPosition.getBlock().getType()));
        }

        final var output = new CompletableFuture<Boolean>();
        BreweryPlugin.getScheduler().runAtLocationLater(spigotPosition,
                () -> output.complete(BarrelAsset.isBarrelAsset(BarrelAsset.SIGN, spigotPosition.getBlock().getType())), 0);
        return output;
    }

    /**
     * returns the fence above/below a block, itself if there is none
     */
    public static Block getSpigotOfSign(final Block block) {
        return BarrelBody.getSpigotOfSign(block);
    }

    /**
     * Are any Barrels in that World
     */
    public static boolean hasDataInWorld(final World world) {
        return barrels.containsKey(world.getUID()) && !barrels.get(world.getUID()).isEmpty();
    }

    /**
     * unloads barrels that are in a unloading world.
     * The removed barrels are kept in memory (not persisted again, they were already saved just before unload),
     * so {@link #onLoad(World)} can restore them without hitting storage again if the world loads back.
     */
    public static void onUnload(final World world) {
        final var removed = barrels.remove(world.getUID());
        if (removed != null) {
            unloadedBarrels.put(world.getUID(), removed);
        }
    }

    /**
     * Restores barrels for a world that has (re)loaded, from the in-memory barrels that were set aside
     * when that world was previously unloaded via {@link #onUnload(World)}. Does not touch storage.
     */
    public static void onLoad(final World world) {
        final var restored = unloadedBarrels.remove(world.getUID());
        if (restored != null) {
            barrels.put(world.getUID(), restored);
        }
    }

    public static void registerBarrel(final Barrel barrel) {
        barrels.computeIfAbsent(barrel.spigot.getWorld().getUID(), ignored -> new ArrayList<>())
                .add(barrel);
    }

    /**
     * Every Barrel we know about, including those set aside by {@link #onUnload(World)}.
     * <p>Barrels of unloaded worlds have to stay in here: the DataManager saves with a full rewrite,
     * so anything missing from this list would be deleted from storage on the next autosave.
     */
    public static List<Barrel> getAllBarrels() {
        return Stream.concat(barrels.values().stream(), unloadedBarrels.values().stream())
                .flatMap(List::stream)
                .filter(Objects::nonNull)
                .toList();
    }

    public final boolean hasPermsOpen(final Player player, final PlayerInteractEvent event) {
        if (this.isLarge()) {
            if (!player.hasPermission("brewery.openbarrel.big")) {
                lang.sendEntry(player, "Error_NoBarrelAccess");
                return false;
            }
        } else {
            if (!player.hasPermission("brewery.openbarrel.small")) {
                lang.sendEntry(player, "Error_NoBarrelAccess");
                return false;
            }
        }

        // Call event
        final var accessEvent = new BarrelAccessEvent(this, player, event.getClickedBlock(), event.getBlockFace());
        // Listened to by IntegrationListener
        BreweryPlugin.getInstance().getServer().getPluginManager().callEvent(accessEvent);
        return !accessEvent.isCancelled();
    }

    /**
     * Ask for permission to destroy barrel
     */
    public final boolean hasPermsDestroy(final Player player, final Block block, final BarrelDestroyEvent.Reason reason) {
        final var destroyEvent = new BarrelDestroyEvent(this, block, reason, player);
        BreweryPlugin.getInstance().getServer().getPluginManager().callEvent(destroyEvent);
        return !destroyEvent.isCancelled();
    }

    /**
     * player opens the barrel
     */
    public final void open(final Player player) {
        if (this.inventory == null) {
            this.inventory = Bukkit.createInventory(this, this.isLarge() ? config.getBarrelInvSizeLarge() * 9 : config.getBarrelInvSizeSmall() * 9, getEtcBarrelTitle());
        } else {
            if (this.time > 0) {
                // if nobody has the inventory opened
                if (this.inventory.getViewers().isEmpty()) {
                    // if inventory contains potions
                    if (this.inventory.contains(Material.POTION)) {
                        final var wood = this.getWood();
                        final var debug = config.isDebug();
                        var loadTime = debug ? System.nanoTime() : 0L;
                        for (final var item : this.inventory.getContents()) {
                            if (item != null) {
                                final var brew = Brew.get(item);
                                if (brew != null) {
                                    brew.age(item, this.time, wood);
                                }
                            }
                        }
                        if (debug) {
                            loadTime = System.nanoTime() - loadTime;
                            final var ftime = (float) (loadTime / 1000000.0);
                            Logging.debugLog("opening Barrel with potions (" + ftime + "ms)");
                        }
                    }
                }
            }
        }
        // reset barreltime, potions have new age
        this.time = 0;

        player.openInventory(this.inventory);
    }

    public final void playOpeningSound() {
        final var randPitch = (float) (Math.random() * 0.1);
        final var location = this.getSpigot().getLocation();
        if (location.getWorld() == null) return;
        if (this.isLarge()) {
            location.getWorld().playSound(location, Sound.BLOCK_CHEST_OPEN, SoundCategory.BLOCKS, 0.4f, 0.55f + randPitch);
            //getSpigot().getWorld().playSound(getSpigot().getLocation(), Sound.ITEM_BUCKET_EMPTY, SoundCategory.BLOCKS, 0.5f, 0.6f + randPitch);
            location.getWorld().playSound(location, Sound.BLOCK_BREWING_STAND_BREW, SoundCategory.BLOCKS, 0.4f, 0.45f + randPitch);
        } else {
            location.getWorld().playSound(location, Sound.BLOCK_BARREL_OPEN, SoundCategory.BLOCKS, 0.5f, 0.8f + randPitch);
        }
    }

    public final void playClosingSound() {
        final var randPitch = (float) (Math.random() * 0.1);
        final var location = this.getSpigot().getLocation();
        if (location.getWorld() == null) return;
        if (this.isLarge()) {
            location.getWorld().playSound(location, Sound.BLOCK_BARREL_CLOSE, SoundCategory.BLOCKS, 0.5f, 0.5f + randPitch);
            location.getWorld().playSound(location, Sound.ITEM_BUCKET_EMPTY, SoundCategory.BLOCKS, 0.2f, 0.6f + randPitch);
        } else {
            location.getWorld().playSound(location, Sound.BLOCK_BARREL_CLOSE, SoundCategory.BLOCKS, 0.5f, 0.8f + randPitch);
        }
    }

    @Override
    @NotNull
    public final Inventory getInventory() {
        return this.inventory;
    }

    /**
     * @deprecated just use hasBlock
     */
    @Deprecated
    public boolean hasWoodBlock(final Block block) {
        return this.hasBlock(block);
    }

    /**
     * @deprecated just use hasBlock
     */
    @Deprecated
    public boolean hasStairsBlock(final Block block) {
        return this.hasBlock(block);
    }

    /**
     * Removes a barrel, throwing included potions to the ground
     *
     * @param broken    The Block that was broken
     * @param breaker   The Player that broke it, or null if not known
     * @param dropItems If the items in the barrels inventory should drop to the ground
     */
    @Override
    public final void remove(@Nullable final Block broken, @Nullable final Player breaker, final boolean dropItems) {
        final var event = new BarrelRemoveEvent(this, dropItems);
        BreweryPlugin.getInstance().getServer().getPluginManager().callEvent(event);

        if (this.inventory != null) {
            final List<HumanEntity> viewers = new ArrayList<>(this.inventory.getViewers());
            // Copy List to fix ConcModExc
            for (final var viewer : viewers) {
                viewer.closeInventory();
            }
            final var items = this.inventory.getContents();
            this.inventory.clear();
            if (event.willDropItems()) {
                if (this.getBounds() == null) {
                    Logging.debugLog("Barrel Body is null, can't drop items: " + this.id);
                    barrels.getOrDefault(this.spigot.getWorld().getUID(), new ArrayList<>()).remove(this);
                    return;
                }

                final var wood = this.getWood();
                for (final var item : items) {
                    try {
                        if (item != null) {
                            final var brew = Brew.get(item);
                            if (brew != null) {
                                // Brew before throwing
                                brew.age(item, this.time, wood);
                                final var meta = (PotionMeta) item.getItemMeta();
                                if (BrewLore.hasColorLore(meta)) {
                                    final var lore = new BrewLore(brew, meta);
                                    lore.convertLore(false);
                                    lore.write();
                                    item.setItemMeta(meta);
                                }
                            }
                            // "broken" is the block that destroyed, throw them there!
                            if (broken != null) {
                                broken.getWorld().dropItem(broken.getLocation(), item);
                            } else {
                                this.spigot.getWorld().dropItem(this.spigot.getLocation(), item);
                            }
                        }
                    } catch (final Throwable e) {
                        // Sensitive code, we do not want some items to drop, throw an error in the for loop and not
                        // deregister the barrel.
                        e.printStackTrace();
                    }
                }
            }
        }

        barrels.getOrDefault(this.spigot.getWorld().getUID(), new ArrayList<>()).remove(this);
    }

    @Override
    public boolean regenerateBounds() {
        Logging.debugLog("Regenerating Barrel BoundingBox: " + (this.bounds == null ? "was null" : "volume=" + this.bounds.volume()));
        final var broken = this.getBrokenBlock(true);
        if (broken != null) {
            this.remove(broken, null, true);
            return false;
        }
        return true;
    }

    /**
     * is this a Large barrel?
     */
    public final boolean isLarge() {
        return !this.isSmall();
    }

    /**
     * @return true if this is a small barrel
     */
    private boolean computeSmall() {
        Preconditions.checkState(BreweryPlugin.getScheduler().isOwnedByCurrentRegion(this.spigot.getLocation()));
        return BarrelAsset.isBarrelAsset(BarrelAsset.SIGN, this.spigot.getType());
    }

    /**
     * Get the (cached) colored "Etc_Barrel" lang entry, used as the title for every Barrel's Inventory.
     * Resolved once and reused, since coloring/looking up the lang entry on every Barrel construction is wasteful.
     */
    private static String getEtcBarrelTitle() {
        if (etcBarrelTitle == null) {
            etcBarrelTitle = lang.getEntry("Etc_Barrel");
        }
        return etcBarrelTitle;
    }

    /**
     * Invalidate the cached "Etc_Barrel" title, so it picks up an updated lang file.
     * Called from {@code /brewery reload}.
     */
    public static void invalidateEtcBarrelTitleCache() {
        etcBarrelTitle = null;
    }

    public static final class BarrelCheck implements Runnable {
        private final UUID worldUuid;

        public BarrelCheck(final UUID worldUuid) {
            this.worldUuid = worldUuid;
        }

        /**
         * End this world's round, so {@link Barrel#onUpdate()} can start a fresh one later.
         */
        private void finishRound() {
            checkCounters.remove(this.worldUuid);
            final var task = barrelCheckTasks.remove(this.worldUuid);
            if (task != null) {
                task.cancel();
            }
        }

        @Override
        public final void run() {
            // Folia doesn't fire 'WorldUnloadEvent' but Canvas does.
            if (MinecraftVersion.isFolia() && !MinecraftVersion.isCanvas() && Bukkit.getWorld(this.worldUuid) == null) {
                barrels.remove(this.worldUuid); // remove this world and assume that it was unloaded on Folia servers
                this.finishRound();
                return;
            }

            var counter = checkCounters.computeIfAbsent(this.worldUuid, ignored -> -1);

            final var worldBarrels = barrels.get(this.worldUuid);
            if (worldBarrels == null || worldBarrels.isEmpty()) {
                // Nothing (left) to check in this world, allow onUpdate to start a fresh round later
                this.finishRound();
                return;
            }

            counter = (counter + 1) % worldBarrels.size();
            while (counter < worldBarrels.size()) {
                final var barrel = worldBarrels.get(counter++);
                if (barrel.checked) {
                    continue;
                }
                // Persist progress so the next tick (or a fresh task after a restart of this one) continues on
                checkCounters.put(this.worldUuid, counter);
                BreweryPlugin.getScheduler().runAtLocationLater(barrel.getSpigot().getLocation(), () -> {
                    final var broken = barrel.getBrokenBlock(false);
                    if (broken != null) {
                        Logging.debugLog(() -> "Barrel at "
                                + broken.getWorld().getName() + "/" + broken.getX() + "/" + broken.getY() + "/" + broken.getZ()
                                + " has been destroyed unexpectedly, contents will drop");
                        // remove the barrel if it was destroyed
                        barrel.remove(broken, null, true);
                    } else {
                        // Dont check this barrel again, its enough to check it once after every restart (and when randomly chosen)
                        // as now this is only the backup if we dont register the barrel breaking,
                        // for example when removing it with some world editor
                        barrel.checked = true;
                    }
                }, 0);
                return;
            }
            // Every barrel in this world has been checked, allow onUpdate to start a fresh round later
            this.finishRound();
        }

    }

}
