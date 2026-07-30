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

import com.dre.brewery.api.events.IngedientAddEvent;
import com.dre.brewery.configuration.ConfigManager;
import com.dre.brewery.configuration.files.Config;
import com.dre.brewery.configuration.files.Lang;
import com.dre.brewery.recipe.BCauldronRecipe;
import com.dre.brewery.recipe.RecipeItem;
import com.dre.brewery.utility.*;
import com.tcoded.folialib.wrapper.task.WrappedTask;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Getter
@Setter
public final class BCauldron {

    public static final int PARTICLEPAUSE = 15;
    private static final MinecraftVersion VERSION = BreweryPlugin.getMCVersion();
    private static final Config config = ConfigManager.getConfig(Config.class);
    private static final Lang lang = ConfigManager.getConfig(Lang.class);
    private static final Set<UUID> plInteracted = new HashSet<>(); // Interact Event helper
    @Getter
    public static final Map<Block, BCauldron> bcauldrons = new ConcurrentHashMap<>(); // All active cauldrons. Mapped to their block for fast retrieve
    private final Block block;
    private final Location particleLocation;
    private final UUID id;
    private BIngredients ingredients = new BIngredients();
    private int state = 0;
    private boolean changed = false; // Not really needed anymore
    private BCauldronRecipe particleRecipe; // null if we haven't checked, empty if there is none
    private Color particleColor;
    private WrappedTask foliaParticleTask;

    public BCauldron(final Block block) {
        this.block = block;
        this.particleLocation = block.getLocation().add(0.5, 0.9, 0.5);
        this.id = UUID.randomUUID();
    }

    // loading from file
    public BCauldron(final Block block, final BIngredients ingredients, final int state, final UUID id) {
        this.block = block;
        this.state = state;
        this.ingredients = ingredients;
        this.particleLocation = block.getLocation().add(0.5, 0.9, 0.5);
        this.id = id;
    }

    // get cauldron by Block
    @Nullable
    public static BCauldron get(final Block block) {
        return bcauldrons.get(block);
    }

    // get cauldron from block and add given ingredient
    // Calls the IngredientAddEvent and may be cancelled or changed
    public static boolean ingredientAdd(final Block block, final ItemStack ingredient, final Player player) {
        // if not empty
        if (MaterialUtil.getFillLevel(block) != MaterialUtil.EMPTY) {

            if (!BCauldronRecipe.acceptedMaterials.contains(ingredient.getType()) && !ingredient.hasItemMeta()) {
                // Extremely fast way to check for most items
                return false;
            }
            // If the Item is on the list, or customized, we have to do more checks
            final var rItem = RecipeItem.getMatchingRecipeItem(ingredient, false);
            if (rItem == null) {
                return false;
            }

            var bcauldron = get(block);
            if (bcauldron == null) {
                bcauldron = new BCauldron(block);
                BCauldron.bcauldrons.put(block, bcauldron);
                bcauldron.startFoliaParticleTask();
            }

            final var event = new IngedientAddEvent(player, block, bcauldron, ingredient.clone(), rItem);
            BreweryPlugin.getInstance().getServer().getPluginManager().callEvent(event);
            if (!event.isCancelled()) {
                bcauldron.add(event.getIngredient(), event.getRecipeItem());
                //P.p.debugLog("Cauldron add: t2 " + ((t2 - t1) / 1000) + " t3: " + ((t3 - t2) / 1000) + " t4: " + ((t4 - t3) / 1000) + " t5: " + ((t5 - t4) / 1000) + "µs");
                return event.willTakeItem();
            } else {
                return false;
            }
        }
        return false;
    }

    // prints the current cooking time to the player
    public static void printTime(final Player player, final Block block) {
        if (!player.hasPermission("brewery.cauldron.time")) {
            lang.sendEntry(player, "Error_NoPermissions");
            return;
        }
        final var bcauldron = get(block);
        if (bcauldron != null) {
            if (bcauldron.state > 1) {
                lang.sendEntry(player, "Player_CauldronInfo1", "" + bcauldron.state);
            } else {
                lang.sendEntry(player, "Player_CauldronInfo2");
            }
        }
    }

    public static void processCookEffects() {
        if (MinecraftVersion.isFolia()) return;
        if (!config.isEnableCauldronParticles()) return;
        if (bcauldrons.isEmpty()) {
            return;
        }
        final var chance = 1f / PARTICLEPAUSE;

        for (final var cauldron : bcauldrons.values()) {
            if (ThreadLocalRandom.current().nextFloat() < chance) {
                BreweryPlugin.getScheduler().runAtLocationLater(cauldron.block.getLocation(), cauldron::cookEffect, 0);
            }
        }
    }

    public static void startAllFoliaParticleTasks() {
        if (!MinecraftVersion.isFolia()) {
            return;
        }
        if (!config.isEnableCauldronParticles()) {
            stopAllFoliaParticleTasks();
            return;
        }
        for (final var cauldron : bcauldrons.values()) {
            cauldron.startFoliaParticleTask();
        }
    }

    public static void stopAllFoliaParticleTasks() {
        if (!MinecraftVersion.isFolia()) {
            return;
        }
        for (final var cauldron : bcauldrons.values()) {
            cauldron.stopFoliaParticleTask();
        }
    }

    public static void clickCauldron(final PlayerInteractEvent event) {
        var materialInHand = event.getMaterial();
        var item = event.getItem();
        final var player = event.getPlayer();
        final var clickedBlock = event.getClickedBlock();
        assert clickedBlock != null;

        if (materialInHand == Material.AIR || materialInHand == Material.BUCKET) {
            return;

        } else if (materialInHand == MaterialUtil.CLOCK) {
            printTime(player, clickedBlock);
            return;

            // fill a glass bottle with potion
        } else if (materialInHand == Material.GLASS_BOTTLE) {
            assert item != null;
            if (player.getInventory().firstEmpty() != -1 || item.getAmount() == 1) {
                final var bcauldron = get(clickedBlock);
                if (bcauldron != null) {
                    if (bcauldron.fill(player, clickedBlock)) {
                        event.setCancelled(true);
                        if (player.hasPermission("brewery.cauldron.fill")) {
                            if (item.getAmount() > 1) {
                                item.setAmount(item.getAmount() - 1);
                            } else {
                                BUtil.setItemInHand(event, Material.AIR, false);
                            }
                        }
                    }
                }
            } else {
                event.setCancelled(true);
            }
            return;

            // Ignore Water Buckets
        } else if (materialInHand == Material.WATER_BUCKET) {
            if (VERSION.isOrEarlier(MinecraftVersion.V1_9)) {
                // reset < 1.9 cauldron when refilling to prevent unlimited source of potions
                // We catch >=1.9 cases in the Cauldron Listener
                if (MaterialUtil.getFillLevel(clickedBlock) == 1) {
                    // will only remove when existing
                    BCauldron.remove(clickedBlock);
                }
            }
            return;
        }

        // Check if fire alive below cauldron when adding ingredients
        final var down = clickedBlock.getRelative(BlockFace.DOWN);
        if (MaterialUtil.isCauldronHeatSource(down)) {

            event.setCancelled(true);
            var handSwap = false;

            // Interact event is called twice!!!?? in 1.9, once for each hand.
            // Certain Items in Hand cause one of them to be cancelled or not called at all sometimes.
            // We mark if a player had the event for the main hand
            // If not, we handle the main hand in the event for the offhand
            if (VERSION.isOrLater(MinecraftVersion.V1_9)) {
                if (event.getHand() == EquipmentSlot.HAND) {
                    final var id = player.getUniqueId();
                    plInteracted.add(id);
                    BreweryPlugin.getScheduler().runLater(() -> plInteracted.remove(id), 0);
                } else if (event.getHand() == EquipmentSlot.OFF_HAND) {
                    if (!plInteracted.remove(player.getUniqueId())) {
                        item = player.getInventory().getItemInMainHand();
                        if (item.getType() != Material.AIR) {
                            materialInHand = item.getType();
                            handSwap = true;
                        } else {
                            item = config.isUseOffhandForCauldron() ? event.getItem() : null;
                        }
                    }
                }
            }
            if (item == null) return;

            if (!player.hasPermission("brewery.cauldron.insert")) {
                lang.sendEntry(player, "Perms_NoCauldronInsert");
                return;
            }
            if (ingredientAdd(clickedBlock, item, player)) {
                final var isBucket = item.getType().name().endsWith("_BUCKET");
                final var isBottle = MaterialUtil.isBottle(item.getType());
                if (item.getAmount() > 1) {
                    item.setAmount(item.getAmount() - 1);

                    if (isBucket) {
                        giveItem(player, new ItemStack(Material.BUCKET));
                    } else if (isBottle) {
                        giveItem(player, new ItemStack(Material.GLASS_BOTTLE));
                    }
                } else {
                    if (isBucket) {
                        BUtil.setItemInHand(event, Material.BUCKET, handSwap);
                    } else if (isBottle) {
                        BUtil.setItemInHand(event, Material.GLASS_BOTTLE, handSwap);
                    } else {
                        item.setAmount(0);
                    }
                }
            }
        }
    }

    /**
     * Recalculate the Cauldron Particle Recipe
     */
    public static void reload() {
        if (!config.isEnableCauldronParticles()) {
            stopAllFoliaParticleTasks();
            return;
        }
        startAllFoliaParticleTasks();

        final var scheduler = BreweryPlugin.getScheduler();
        for (final var cauldron : bcauldrons.values()) {
            cauldron.particleRecipe = null;
            cauldron.particleColor = null;

            scheduler.runAtLocationLater(cauldron.block.getLocation(), () -> {
                if (BUtil.isChunkLoaded(cauldron.block) && MaterialUtil.isCauldronHeatSource(cauldron.block.getRelative(BlockFace.DOWN))) {
                    cauldron.getParticleColor();
                }
            }, 0);
        }
    }

    /**
     * reset to normal cauldron
     */
    public static boolean remove(final Block block) {
        final var removed = bcauldrons.remove(block);
        if (removed != null) {
            removed.stopFoliaParticleTask();
            return true;
        }
        return false;
    }

    /**
     * Are any Cauldrons in that World
     */
    public static boolean hasDataInWorld(final World world) {
        return bcauldrons.keySet().stream().anyMatch(block -> block.getWorld().equals(world));
    }

    // unloads cauldrons that are in a unloading world
    // as they were written to file just before, this is safe to do
    public static void onUnload(final World world) {
        final var blocksToRemove = bcauldrons.keySet().stream()
                .filter(block -> block.getWorld().equals(world))
                .toList();
        blocksToRemove.forEach(BCauldron::remove);
    }

    /**
     * Unload all Cauldrons that have are in a unloaded World
     */
    public static void unloadWorlds() {
        final var worlds = BreweryPlugin.getInstance().getServer().getWorlds();
        final var blocksToRemove = bcauldrons.keySet().stream()
                .filter(block -> !worlds.contains(block.getWorld()))
                .toList();
        blocksToRemove.forEach(BCauldron::remove);
    }

    public static void save(final ConfigurationSection config, final ConfigurationSection oldData) {
        BUtil.createWorldSections(config);

        if (!bcauldrons.isEmpty()) {
            var id = 0;
            for (final var cauldron : bcauldrons.values()) {
                final var worldName = cauldron.block.getWorld().getName();
                final String prefix;

                if (worldName.startsWith("DXL_")) {
                    prefix = BUtil.getDxlName(worldName) + "." + id;
                } else {
                    prefix = cauldron.block.getWorld().getUID() + "." + id;
                }

                config.set(prefix + ".block", cauldron.block.getX() + "/" + cauldron.block.getY() + "/" + cauldron.block.getZ());
                if (cauldron.state != 0) {
                    config.set(prefix + ".state", cauldron.state);
                }
                config.set(prefix + ".ingredients", cauldron.ingredients.serializeIngredients());
                id++;
            }
        }
        // copy cauldrons that are not loaded
        if (oldData != null) {
            for (final var uuid : oldData.getKeys(false)) {
                if (!config.contains(uuid)) {
                    config.set(uuid, oldData.get(uuid));
                }
            }
        }
    }

    // bukkit bug not updating the inventory while executing event, have to
    // schedule the give
    public static void giveItem(final Player player, final ItemStack item) {
        BreweryPlugin.getScheduler().runLater(() -> player.getInventory().addItem(item), 1L);
    }

    /**
     * Updates this Cauldron, increasing the cook time and checking for Heatsource
     *
     * @return false if Cauldron needs to be removed
     */
    public final boolean onUpdate() {
        // add a minute to cooking time
        if (!BUtil.isChunkLoaded(this.block)) {
            this.increaseState();
        } else {
            if (!MaterialUtil.isWaterCauldron(this.block.getType())) {
                // Catch any WorldEdit etc. removal
                return false;
            }
            // Check if fire still alive
            if (MaterialUtil.isCauldronHeatSource(this.block.getRelative(BlockFace.DOWN))) {
                this.increaseState();
            }
        }
        return true;
    }

    /**
     * Will add a minute to the cooking time
     */
    public final void increaseState() {
        this.state++;
        if (this.changed) {
            this.ingredients = this.ingredients.copy();
            this.changed = false;
        }
        this.particleColor = null;
    }

    // add an ingredient to the cauldron
    public final void add(final ItemStack ingredient, final RecipeItem rItem) {
        if (ingredient == null || ingredient.getType() == Material.AIR) return;
        if (this.changed) {
            this.ingredients = this.ingredients.copy();
            this.changed = false;
        }

        this.particleRecipe = null;
        this.particleColor = null;
        this.ingredients.add(ingredient, rItem);
        this.block.getWorld().playEffect(this.block.getLocation(), Effect.EXTINGUISH, 0);
        if (this.state > 0) {
            this.state--;
        }
        if (config.isEnableCauldronParticles() && !config.isMinimalParticles()) {
            // Few little sparks and lots of water splashes. Offset by 0.2 in x and z
            this.block.getWorld().spawnParticle(BukkitConstants.INSTANT_EFFECT, this.particleLocation, 2, 0.2, 0, 0.2, new BukkitConstants.ParticleSpellWrapper().toInstance(Color.WHITE, 1f));
            this.block.getWorld().spawnParticle(BukkitConstants.SPLASH, this.particleLocation, 10, 0.2, 0, 0.2);
        }
    }

    // fills players bottle with cooked brew
    public final boolean fill(final Player player, final Block block) {
        if (!player.hasPermission("brewery.cauldron.fill")) {
            lang.sendEntry(player, "Perms_NoCauldronFill");
            return true;
        }
        final var potion = this.ingredients.cook(this.state, player);
        if (potion == null) return false;

        if (VERSION.isOrLater(MinecraftVersion.V1_13)) {
            final var data = block.getBlockData();
            if (!(data instanceof final Levelled cauldron)) {
                remove(block);
                return false;
            }
            if (cauldron.getLevel() <= 0) {
                remove(block);
                return false;
            }

            // If the Water_Cauldron type exists and the cauldron is on last level
            if (MaterialUtil.WATER_CAULDRON != null && cauldron.getLevel() == 1) {
                // Empty Cauldron
                block.setType(Material.CAULDRON);
                remove(block);
            } else {
                cauldron.setLevel(cauldron.getLevel() - 1);

                // Update the new Level to the Block
                // We have to use the BlockData variable "data" here instead of the casted "cauldron"
                // otherwise < 1.13 crashes on plugin load for not finding the BlockData Class
                block.setBlockData(data);

                if (cauldron.getLevel() <= 0) {
                    remove(block);
                } else {
                    this.changed = true;
                }
            }

        } else {
            @SuppressWarnings("deprecation")
            var data = block.getData();
            if (data > 3) {
                data = 3;
            } else if (data <= 0) {
                remove(block);
                return false;
            }
            data -= 1;
            MaterialUtil.setData(block, data);

            if (data == 0) {
                remove(block);
            } else {
                this.changed = true;
            }
        }
        if (VERSION.isOrLater(MinecraftVersion.V1_9)) {
            block.getWorld().playSound(block.getLocation(), Sound.ITEM_BOTTLE_FILL, 1f, 1f);
        }
        // Bukkit Bug, inventory not updating while in event so this
        // will delay the give
        // but could also just use deprecated updateInventory()
        giveItem(player, potion);
        return true;
    }

    public final void cookEffect() {
        assert !MinecraftVersion.isFolia() || BreweryPlugin.getScheduler().isOwnedByCurrentRegion(this.block.getLocation())
                : "cookEffect must run on owning region thread";
        if (BUtil.isChunkLoaded(this.block) && MaterialUtil.isCauldronHeatSource(this.block.getRelative(BlockFace.DOWN))) {
            final var color = this.getParticleColor();
            // Colorable spirally spell, 0 count enables color instead of the offset variables
            // Configurable RGB color. The last parameter seems to control the hue and motion, but I couldn't find
            // how exactly in the client code. 1025 seems to be the best for color brightness and upwards motion

            if (VERSION.isOrLater(MinecraftVersion.V1_21)) {
                this.block.getWorld().spawnParticle(BukkitConstants.ENTITY_EFFECT, this.getRandParticleLoc(), 0, color);
            } else {
                this.block.getWorld().spawnParticle(BukkitConstants.ENTITY_EFFECT, this.getRandParticleLoc(), 0,
                        ((double) color.getRed()) / 255.0,
                        ((double) color.getGreen()) / 255.0,
                        ((double) color.getBlue()) / 255.0,
                        1025.0);
            }

            if (config.isMinimalParticles()) {
                return;
            }

            if (ThreadLocalRandom.current().nextFloat() > 0.85f) {
                // Dark pixely smoke cloud at 0.4 random in x and z
                // 0 count enables direction, send to y = 1 with speed 0.09
                this.block.getWorld().spawnParticle(BukkitConstants.LARGE_SMOKE, this.getRandParticleLoc(), 0, 0, 1, 0, 0.09);
            }
            if (ThreadLocalRandom.current().nextFloat() > 0.2f) {
                // A Water Splash with 0.2 offset in x and z
                this.block.getWorld().spawnParticle(BukkitConstants.SPLASH, this.particleLocation, 1, 0.2, 0, 0.2);
            }

            if (VERSION.isOrLater(MinecraftVersion.V1_13) && ThreadLocalRandom.current().nextFloat() > 0.4f) {
                // Two hovering pixely dust clouds, a bit of offset and with DustOptions to give some color and size
                this.block.getWorld().spawnParticle(BukkitConstants.DUST, this.particleLocation, 2, 0.15, 0.2, 0.15, new Particle.DustOptions(color, 1.5f));
            }
        }
    }

    private Location getRandParticleLoc() {
        return new Location(this.particleLocation.getWorld(),
                this.particleLocation.getX() + (ThreadLocalRandom.current().nextDouble() * 0.8) - 0.4,
                this.particleLocation.getY(),
                this.particleLocation.getZ() + (ThreadLocalRandom.current().nextDouble() * 0.8) - 0.4);
    }

    /**
     * Get or calculate the particle color from the current best Cauldron Recipe
     * Also calculates the best Cauldron Recipe if not yet done
     *
     * @return the Particle Color, after potentially calculating it
     */
    @NotNull
    public final Color getParticleColor() {
        if (this.state < 1) {
            return Color.fromRGB(153, 221, 255); // Bright Blue
        }
        if (this.particleColor != null) {
            return this.particleColor;
        }
        if (this.particleRecipe == null) {
            // Check for Cauldron Recipe
            this.particleRecipe = this.ingredients.getCauldronRecipe();
        }

        List<Tuple<Integer, Color>> colorList = null;
        if (this.particleRecipe != null) {
            colorList = this.particleRecipe.getParticleColor();
        }

        if (colorList == null || colorList.isEmpty()) {
            // No color List configured, or no recipe found
            colorList = new ArrayList<>(1);
            colorList.add(new Tuple<>(10, Color.fromRGB(77, 166, 255))); // Dark Aqua kind of Blue
        }
        var index = 0;
        while (index < colorList.size() - 1 && colorList.get(index).a() < this.state) {
            // Find the first index where the colorList Minute is higher than the state
            index++;
        }

        final int minute = colorList.get(index).a();
        if (minute > this.state) {
            // going towards the minute
            final int prevPos;
            final Color prevColor;
            if (index > 0) {
                // has previous colours
                prevPos = colorList.get(index - 1).a();
                prevColor = colorList.get(index - 1).b();
            } else {
                prevPos = 0;
                prevColor = Color.fromRGB(153, 221, 255); // Bright Blue
            }

            this.particleColor = BUtil.weightedMixColor(prevColor, prevPos, this.state, colorList.get(index).b(), minute);
        } else if (minute == this.state) {
            // reached the minute
            this.particleColor = colorList.get(index).b();
        } else {
            // passed the last minute configured
            if (index > 0) {
                // We have more than one color, just use the last one
                this.particleColor = colorList.get(index).b();
            } else {
                // Only have one color, go towards a Gray
                final var nextColor = Color.fromRGB(138, 153, 168); // Dark Teal, Gray
                final var nextPos = (int) (minute * 2.6f);

                if (nextPos <= this.state) {
                    // We are past the next color (Gray) as well, keep using it
                    this.particleColor = nextColor;
                } else {
                    this.particleColor = BUtil.weightedMixColor(colorList.get(index).b(), minute, this.state, nextColor, nextPos);
                }
            }
        }
        //P.p.log("RGB: " + particleColor.getRed() + "|" + particleColor.getGreen() + "|" + particleColor.getBlue());
        return this.particleColor;
    }

    private synchronized void startFoliaParticleTask() {
        if (!MinecraftVersion.isFolia()) {
            return;
        }
        if (!config.isEnableCauldronParticles()) {
            this.stopFoliaParticleTask();
            return;
        }
        if (this.foliaParticleTask != null && !this.foliaParticleTask.isCancelled()) {
            return;
        }
        final var delay = ThreadLocalRandom.current().nextLong(1, PARTICLEPAUSE + 1L);
        this.foliaParticleTask = BreweryPlugin.getScheduler().runAtLocationTimer(this.block.getLocation(), () -> {
            if (config.isMinimalParticles() && ThreadLocalRandom.current().nextFloat() > 0.5f) {
                return;
            }
            this.cookEffect();
        }, delay, PARTICLEPAUSE);
    }

    private synchronized void stopFoliaParticleTask() {
        if (!MinecraftVersion.isFolia()) {
            return;
        }
        if (this.foliaParticleTask != null) {
            this.foliaParticleTask.cancel();
            this.foliaParticleTask = null;
        }
    }

}
