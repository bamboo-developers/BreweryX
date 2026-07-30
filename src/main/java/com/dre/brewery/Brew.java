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

import com.dre.brewery.api.events.brew.BrewModifyEvent;
import com.dre.brewery.configuration.ConfigManager;
import com.dre.brewery.configuration.files.Config;
import com.dre.brewery.configuration.files.Lang;
import com.dre.brewery.lore.*;
import com.dre.brewery.recipe.BEffect;
import com.dre.brewery.recipe.BRecipe;
import com.dre.brewery.recipe.BestRecipeResult;
import com.dre.brewery.recipe.PotionColor;
import com.dre.brewery.utility.BUtil;
import com.dre.brewery.utility.Logging;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.security.InvalidKeyException;
import java.util.*;

/**
 * Represents the liquid in the brewed Potions
 */
@Getter
@Setter
public final class Brew implements Cloneable {
    public static final byte SAVE_VER = 1;
    private static final Config config = ConfigManager.getConfig(Config.class);
    private static final Lang lang = ConfigManager.getConfig(Lang.class);
    public static long installTime = System.currentTimeMillis(); // plugin install time in millis after epoch
    private static long saveSeed;
    private static List<Long> prevSaveSeeds = new ArrayList<>(); // Save Seeds that have been used in the past, stored to decode brews made at that time
    private BIngredients ingredients;
    private int quality;
    private int alc;
    private byte distillRuns;
    private float ageTime;
    private BarrelWoodType wood = BarrelWoodType.ANY;
    // TODO: This should extend BRecipe, not hold a reference.
    private BRecipe currentRecipe; // Recipe this Brew is currently based off. May change between modifications and is often null when not modifying
    private boolean unlabeled;
    private boolean immutable; // static/immutable potions should not be changed
    private boolean stripped; // Most Brewing information removed, only drinking and rough quality information available. Brew should not change anymore
    private int lastUpdate; // last update in hours after install time
    private boolean needsSave; // There was a change that has not yet been saved
    private boolean hasGlint; // The Brew has a glint effect

    /**
     * A new Brew with only ingredients
     */
    public Brew(final BIngredients ingredients) {
        this.ingredients = ingredients;
        this.touch();
    }

    /**
     * A Brew with quality, alc and recipe already set
     */
    public Brew(final int quality, final int alc, final BRecipe recipe, final BIngredients ingredients) {
        this.ingredients = ingredients;
        this.quality = quality;
        this.alc = alc;
        this.currentRecipe = recipe;
        this.touch();
    }

    /**
     * Loading a Brew with all values set
     */
    public Brew(final BIngredients ingredients, final int quality, final int alc, final byte distillRuns, final float ageTime, final BarrelWoodType wood, final String recipe, final boolean unlabeled, final boolean immutable, final int lastUpdate) {
        this.ingredients = ingredients;
        this.quality = quality;
        this.alc = alc;
        this.distillRuns = distillRuns;
        this.ageTime = ageTime;
        this.wood = wood;
        this.unlabeled = unlabeled;
        this.immutable = immutable;
        this.lastUpdate = lastUpdate;
        this.setRecipeFromString(recipe);
    }

    // Loading from InputStream
    private Brew() {
    }

    /**
     * returns a Brew by ItemMeta
     *
     * @param meta The meta to get the brew from
     * @return The Brew if meta is a brew, null if not
     */
    @Nullable
    public static Brew get(final ItemMeta meta) {
        return load(meta);
    }

    /**
     * returns a Brew by ItemStack
     *
     * @param item The Item to get the brew from
     * @return The Brew if item is a brew, null if not
     */
    @Nullable
    public static Brew get(final ItemStack item) {
        if (item.getType() != Material.POTION) return null;
        if (!item.hasItemMeta()) return null;

        final var meta = item.getItemMeta();
        assert meta != null;

        final var brew = load(meta);
        if (brew != null && brew.needsSave) {
            // Brew needs saving from a previous encode setting or save seed
            brew.save(meta);
            item.setItemMeta(meta);
        }
        return brew;
    }

    /**
     * distill all custom potions in the brewer
     *
     * @param inv      The Inventory of the Distiller
     * @param contents The Brews in the 3 slots of the Inventory
     */
    public static void distillAll(final BrewerInventory inv, final Brew[] contents) {
        for (var slot = 0; slot < 3; slot++) {
            if (contents[slot] != null) {
                final var slotItem = inv.getItem(slot);
                final var potionMeta = (PotionMeta) slotItem.getItemMeta();
                contents[slot].distillSlot(slotItem, potionMeta);
            }
        }
    }

    /**
     * Performant way of checking if this item is a Brew.
     * <p>Does not give any guarantees that get() will return notnull for this item, i.e. if it is a brew but the data is corrupt
     *
     * @param item The Item to check
     * @return True if the item is a brew
     */
    public static boolean isBrew(final ItemStack item) {
        if (item == null || item.getType() != Material.POTION) return false;
        if (!item.hasItemMeta()) return false;

        final var meta = item.getItemMeta();
        assert meta != null;
        return NBTLoadStream.hasDataInMeta(meta);
    }

    // Copy a Brew with a new unique ID and return its item
    // Not needed anymore

    private static Brew load(final ItemMeta meta) {
        // Load the Item Data from the PersistentDataContainer
        final var itemLoadStream = new NBTLoadStream(meta);
        if (!itemLoadStream.hasData()) {
            // No Brew data found in Meta
            return null;
        }

        final var unscrambler = new XORUnscrambleStream(itemLoadStream, saveSeed, prevSaveSeeds);
        try (final var in = new DataInputStream(unscrambler)) {
            var parityFailed = false;
            if (in.readByte() != 86) {
                Logging.errorLog("Parity check failed on Brew while loading, trying to load anyways!");
                parityFailed = true;
            }
            final var brew = new Brew();
            final var ver = in.readByte();
            switch (ver) {
                case 1:

                    unscrambler.start();
                    brew.loadFromStream(in, ver);

                    break;
                default:
                    if (parityFailed) {
                        Logging.errorLog("Failed to load Brew. Maybe something corrupted the Lore of the Item?");
                    } else {
                        Logging.errorLog("Brew has data stored in v" + ver + " this Plugin version supports up to v" + SAVE_VER);
                    }
                    return null;
            }

            final var successType = unscrambler.getSuccessType();
            if (successType == XORUnscrambleStream.SuccessType.PREV_SEED) {
                Logging.debugLog("Converting Brew from previous Seed");
                brew.setNeedsSave(true);
            } else if ((config.isEnableEncode() && !brew.isStripped()) != (successType == XORUnscrambleStream.SuccessType.MAIN_SEED)) {
                // We have either enabled encode and the data was not encoded or the other way round
                Logging.debugLog("Converting Brew to new encode setting");
                brew.setNeedsSave(true);
            }
            return brew;
        } catch (final IOException e) {
            Logging.errorLog("IO Error while loading Brew", e);
        } catch (final InvalidKeyException e) {
            Logging.errorLog("Failed to load Brew, has the data key 'encodeKey' in the config.yml been changed?", e);
        }
        return null;
    }

    public static void loadSeed(final long seed) {
        saveSeed = seed;
        updatePrevSeeds();
    }

    public static void loadPrevSeeds(final ConfigurationSection section) {
        if (section.contains("prevSaveSeeds")) {
            prevSaveSeeds = section.getLongList("prevSaveSeeds");
            updatePrevSeeds();
        }
    }

    // remove potion from file (drinking, despawning, combusting, cmdDeleting, should be more!)
    // Not needed anymore

    public static void loadPrevSeeds(final List<Long> list) {
        prevSaveSeeds = list;
        updatePrevSeeds();
    }

    private static void updatePrevSeeds() {
        if (!prevSaveSeeds.contains(saveSeed)) {
            prevSaveSeeds.add(saveSeed);
        }
    }

    public static List<Long> getPrevSeeds() {
        return prevSaveSeeds;
    }

    /**
     * returns the recipe with the given name, recalculates if not found
     */
    public final boolean setRecipeFromString(final String name) {
        this.currentRecipe = null;
        if (name != null && !name.equals("")) {
            for (final var recipe : BRecipe.getAllRecipes()) {
                if (recipe.getRecipeName().equalsIgnoreCase(name)) {
                    this.currentRecipe = recipe;
                    return true;
                }
            }

            if (this.quality > 0) {
                this.currentRecipe = this.ingredients.getBestRecipe(this.wood, this.ageTime, this.distillRuns > 0);
                if (this.currentRecipe != null) {
                    Logging.log("A Brew was made from Recipe: '" + name + "' which could not be found. '" + this.currentRecipe.getRecipeName() + "' used instead!");
                    return true;
                } else {
                    Logging.errorLog("A Brew was made from Recipe: '" + name + "' which could not be found!");
                }
            }
        }
        return false;
    }

    public final boolean reloadRecipe() {
        return this.currentRecipe == null || this.setRecipeFromString(this.currentRecipe.getRecipeName());
    }

    public boolean isSimilar(final Brew brew) {
        if (brew == null) return false;
        if (this.equals(brew)) return true;
        return this.quality == brew.quality &&
                this.alc == brew.alc &&
                this.distillRuns == brew.distillRuns &&
                Float.compare(brew.ageTime, this.ageTime) == 0 &&
                brew.wood == this.wood &&
                this.unlabeled == brew.unlabeled &&
                this.immutable == brew.immutable &&
                this.stripped == brew.stripped &&
                this.ingredients.equals(brew.ingredients) &&
                (Objects.equals(this.currentRecipe, brew.currentRecipe));
    }

    /**
     * Clones this instance
     */
    @Override
    public final Brew clone() {
        try {
            final var brew = (Brew) super.clone();
            brew.ingredients = this.ingredients.copy();
            return brew;
        } catch (final CloneNotSupportedException e) {
            throw new InternalError(e);
        }
    }

    @Override
    public final String toString() {
        return "Brew{" +
                "ingredients=" + this.ingredients +
                ", quality=" + this.quality +
                ", alc=" + this.alc +
                ", distillRuns=" + this.distillRuns +
                ", ageTime=" + this.ageTime +
                ", wood=" + this.wood +
                ", currentRecipe=" + this.currentRecipe +
                ", unlabeled=" + this.unlabeled +
                ", immutable=" + this.immutable +
                ", stripped=" + this.stripped +
                '}';
    }

    /**
     * calculate alcohol from recipe
     */
    @Contract(pure = true)
    public final int calcAlcohol() {
        if (this.quality == 0) {
            // Give bad potions some alc
            var badAlc = 0;
            if (this.distillRuns > 1) {
                badAlc = this.distillRuns;
            }
            if (this.ageTime > 10) {
                badAlc += 5;
            } else if (this.ageTime > 2) {
                badAlc += 3;
            }
            if (this.currentRecipe != null) {
                return badAlc;
            } else {
                return badAlc / 2;
            }
        }

        if (this.currentRecipe != null) {
            var alc = this.currentRecipe.getAlcohol();
            if (this.currentRecipe.needsDistilling()) {
                if (this.distillRuns == 0) {
                    return 0;
                }
                // bad quality can decrease alc by up to 40%
                alc *= 1 - ((float) (10 - this.quality) * 0.04f);
                // distillable Potions should have half alc after one and full alc after all needed distills
                alc /= 2;
                alc *= 1.0F + ((float) this.distillRuns / this.currentRecipe.getDistillruns());
            } else {
                // quality decides 10% - 100%
                alc *= ((float) this.quality / 10.0f);
            }
            return alc;
        }
        return 0;
    }

    // Distilling section ---------------

    /**
     * calculating quality
     */
    @Contract(pure = true)
    public final int calcQuality() {
        // calculate quality from all of the factors
        float quality = this.ingredients.getIngredientQuality(this.currentRecipe) + this.ingredients.getCookingQuality(this.currentRecipe, this.distillRuns > 0);
        if (this.currentRecipe.needsToAge() || this.ageTime > 0.5) {
            quality += this.ingredients.getWoodQuality(this.currentRecipe, this.wood) + this.ingredients.getAgeQuality(this.currentRecipe, this.ageTime);
            quality /= 4;
        } else {
            quality /= 2;
        }
        return Math.round(quality);
    }

    public final boolean canDistill() {
        if (this.immutable) return false;
        if (this.currentRecipe != null) {
            return this.currentRecipe.getDistillruns() > this.distillRuns;
        } else {
            return this.distillRuns < 6;
        }
    }

    public final void updateCustomModelData(final ItemMeta meta) {
        if (this.currentRecipe != null && this.currentRecipe.getItemModel() != null && !this.currentRecipe.getItemModel()[0].isEmpty()) {
            final String cm;
            if (this.quality > 7) {
                cm = this.currentRecipe.getItemModel()[2];
            } else if (this.quality > 3) {
                cm = this.currentRecipe.getItemModel()[1];
            } else {
                cm = this.currentRecipe.getItemModel()[0];
            }
            if (cm == null || cm.isEmpty()) {
                meta.setItemModel(null);
            } else {
                meta.setItemModel(NamespacedKey.fromString(cm.toLowerCase()));
//                BreweryPlugin.getInstance().getLogger().info("set itemmodel to "+cm.toLowerCase());
            }
        } else if (this.currentRecipe != null && this.currentRecipe.getCmData() != null) {
            final int cm;
            if (this.quality > 7) {
                cm = this.currentRecipe.getCmData()[2];
            } else if (this.quality > 3) {
                cm = this.currentRecipe.getCmData()[1];
            } else {
                cm = this.currentRecipe.getCmData()[0];
            }
            if (cm == 0) {
                meta.setCustomModelData(null);
            } else {
                meta.setCustomModelData(cm);
            }
        } else {
            meta.setCustomModelData(null);
        }
    }

    // Ageing Section ------------------

    /**
     * Get Special Drink Effects
     */
    public final List<BEffect> getEffects() {
        if (this.currentRecipe != null && this.quality > 0) {
            return this.currentRecipe.getEffects();
        }
        return null;
    }

    /**
     * Set unlabeled to true to hide the numbers in Lore
     *
     * @param item The Item this Brew is on
     */
    public final void unLabel(final ItemStack item) {
        if (this.unlabeled) return;
        this.unlabeled = true;
        final var meta = item.getItemMeta();
        if (meta instanceof PotionMeta && meta.hasLore()) {
            final var lore = new BrewLore(this, ((PotionMeta) meta));
            if (this.distillRuns > 0) {
                lore.updateDistillLore(false);
            }
            if (this.ageTime >= 1) {
                lore.updateAgeLore(false);
            }
            lore.updateIngredientLore(false);
            lore.updateCookLore(false);
            lore.updateDistillLore(false);
            lore.updateAgeLore(false);
            lore.updateWoodLore(false);
            lore.updateQualityStars(false);
            lore.updateAlc(false);
            lore.write();
            item.setItemMeta(meta);
        }
    }

    /**
     * Sealing the Brew to make it Immutable, Unlabeled and Stripped
     * <p>This makes it easier to sell in shops as Brews that are mostly the same will be equal after
     *
     * @param potion The Item this Brew is on
     */
    public final void seal(final ItemStack potion, @Nullable final Player player) {
        if (this.stripped) return;
        final var origMeta = potion.getItemMeta();
        if (!(origMeta instanceof PotionMeta)) return;

        if (this.quality == 1) {
            this.quality = 2;
        } else if (this.quality % 2 == 1) {
            this.quality--;
        }
        this.alc = this.calcAlcohol();

        this.setStatic(true, potion);
        this.unLabel(potion);
        final var meta = (PotionMeta) potion.getItemMeta();
        final var lore = new BrewLore(this, meta);
        lore.updateQualityStars(false, true);
        lore.write();

        this.stripped = true;
        this.ingredients = new BIngredients();
        this.ageTime = 0;
        this.wood = BarrelWoodType.NONE;
        this.touch();

        final var modifyEvent = new BrewModifyEvent(this, meta, BrewModifyEvent.Type.SEAL, player);
        BreweryPlugin.getInstance().getServer().getPluginManager().callEvent(modifyEvent);

        if (modifyEvent.isCancelled()) {
            // As the brew and everything connected to it is only saved on the meta from now on,
            // restoring the origMeta is enough in this case
            potion.setItemMeta(origMeta);
            return;
        }
        this.save(meta);
        potion.setItemMeta(meta);
    }

    /**
     * Do some regular updates.
     */
    public final void touch() {
        this.lastUpdate = (int) ((double) (System.currentTimeMillis() - installTime) / 3600000D);
    }

    public final int getOrCalcAlc() {
        if (this.alc == 0) {
            this.alc = this.calcAlcohol();
        }
        return this.alc;
    }

    public final boolean hasRecipe() {
        return this.currentRecipe != null;
    }

    public boolean isSealed() {
        return this.stripped && this.immutable;
    }

    public final boolean isStatic() {
        return this.immutable;
    }

    /**
     * Set the Static flag, so potion is unchangeable
     */
    public final void setStatic(final boolean immutable, final ItemStack potion) {
        if (!immutable && this.isStripped()) {
            throw new IllegalStateException("Cannot make stripped Brews non-static");
        }
        this.immutable = immutable;
    }

    /**
     * distill custom potion in a distiller slot
     *
     * @param slotItem   The item in the slot
     * @param potionMeta The meta of the item
     */
    public final void distillSlot(final ItemStack slotItem, final PotionMeta potionMeta) {
        if (this.immutable) return;

        this.distillRuns += 1;
        final var lore = new BrewLore(this, potionMeta);
        final var result = this.ingredients.getDistillRecipeFull(this.wood, this.ageTime);
        if (result instanceof final BestRecipeResult.Found found) {
            // distillRuns will have an effect on the amount of alcohol, not the quality
            this.currentRecipe = found.recipe();
            this.quality = this.calcQuality();

            lore.addOrReplaceEffects(this.getEffects(), this.quality);
            potionMeta.setDisplayName(BUtil.color("&f" + this.currentRecipe.getName(this.quality)));
            this.currentRecipe.getColor().colorBrew(potionMeta);

        } else {
            this.quality = 0;
            lore.removeEffects();
            Logging.debugLog("Distill brew ruined! " + result);
            if (config.isShowRuinedBrewHints()) {
                final var defect = result.getWorstDefect();
                assert defect != null; // since no recipe was found, there must be a defect
                lore.updateDefect(BUtil.choose(defect.getMessages(lang)));
            }
            potionMeta.setDisplayName(BUtil.color("&f" + lang.getEntry("Brew_DistillUndefined")));
            PotionColor.GREY.colorBrew(potionMeta);
        }
        this.alc = this.calcAlcohol();
        this.updateCustomModelData(potionMeta);

        // Distill Lore
        if (this.currentRecipe != null && config.isColorInBrewer() != BrewLore.hasColorLore(potionMeta)) {
            lore.convertLore(config.isColorInBrewer());
        } else {
            lore.updateQualityStars(config.isColorInBrewer());
            lore.updateCustomLore();
            lore.updateDistillLore(config.isColorInBrewer());
        }
        lore.updateAlc(true);
        lore.write();
        this.touch();
        final var modifyEvent = new BrewModifyEvent(this, potionMeta, BrewModifyEvent.Type.DISTILL);
        BreweryPlugin.getInstance().getServer().getPluginManager().callEvent(modifyEvent);
        if (modifyEvent.isCancelled()) {
            // As the brew and everything connected to it is only saved on the meta from now on,
            // not saving the brew into potionMeta is enough to not change anything in case of cancel
            return;
        }
        this.save(potionMeta);

        slotItem.setItemMeta(potionMeta);
    }

    public final int getDistillTimeNextRun() {
        if (!this.canDistill()) {
            return -1;
        }

        if (this.currentRecipe != null) {
            return this.currentRecipe.getDistillTime();
        }

        final var recipe = this.ingredients.getDistillRecipe(this.wood, this.ageTime);
        if (recipe != null) {
            return recipe.getDistillTime();
        }
        return 0;
    }

    public final void age(final ItemStack item, final float time, final BarrelWoodType woodType) {
        if (this.immutable) return;
        final var potionMeta = (PotionMeta) item.getItemMeta();

        final var lore = new BrewLore(this, potionMeta);
        this.ageTime += time;

        // if younger than half a day, it shouldnt get aged form
        if (this.ageTime > 0.5) {
            this.woodShift(time, woodType);
            final var result = this.ingredients.getAgeRecipeFull(this.wood, this.ageTime, this.distillRuns > 0);
            if (result instanceof final BestRecipeResult.Found found) {
                this.currentRecipe = found.recipe();
                this.quality = this.calcQuality();

                lore.addOrReplaceEffects(this.getEffects(), this.quality);
                potionMeta.setDisplayName(BUtil.color("&f" + this.currentRecipe.getName(this.quality)));
                this.currentRecipe.getColor().colorBrew(potionMeta);

                if (this.currentRecipe.isGlint()) {
                    potionMeta.addEnchant(Enchantment.MENDING, 1, true);
                    potionMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                }
            } else {
                this.quality = 0;
                lore.convertLore(false);
                lore.removeEffects();
                Logging.debugLog("Aging brew ruined! " + result);
                if (config.isShowRuinedBrewHints()) {
                    final var defect = result.getWorstDefect();
                    assert defect != null; // since no recipe was found, there must be a defect
                    lore.updateDefect(BUtil.choose(defect.getMessages(lang)));
                }
                this.currentRecipe = null;
                potionMeta.setDisplayName(BUtil.color("&f" + lang.getEntry("Brew_BadPotion")));
                PotionColor.GREY.colorBrew(potionMeta);
            }
        }
        this.alc = this.calcAlcohol();
        this.updateCustomModelData(potionMeta);

        // Lore
        if (this.currentRecipe != null && config.isColorInBarrels() != BrewLore.hasColorLore(potionMeta)) {
            lore.convertLore(config.isColorInBarrels());
        } else {
            if (this.ageTime >= 1) {
                lore.updateAgeLore(config.isColorInBarrels());
            }
            if (this.ageTime > 0.5) {
                if (config.isColorInBarrels()) {
                    lore.updateWoodLore(true);
                    lore.updateIngredientLore(true);
                    lore.updateCookLore(true);
                }
                lore.updateQualityStars(config.isColorInBarrels());
                lore.updateCustomLore();
                lore.updateAlc(false);
            }
        }
        lore.write();
        this.touch();
        final var modifyEvent = new BrewModifyEvent(this, potionMeta, BrewModifyEvent.Type.AGE);
        BreweryPlugin.getInstance().getServer().getPluginManager().callEvent(modifyEvent);
        if (modifyEvent.isCancelled()) {
            // As the brew and everything connected to it is only saved on the meta from now on,
            // not saving the brew into potionMeta is enough to not change anything in case of cancel
            return;
        }
        this.save(potionMeta);
        item.setItemMeta(potionMeta);
    }

    /**
     * Slowly shift the wood of the Brew to the new Type
     */
    public final void woodShift(final float time, final BarrelWoodType to) {
        if (this.immutable || this.wood == to) {
            return;
        }
        if (this.wood == BarrelWoodType.ANY) {
            this.wood = to;
            return;
        }
        if (config.isNewBarrelTypeAlgorithm()) {
            this.woodShiftNew(time, to);
            return;
        }

        var fromIndex = this.wood.getIndex();
        final var toIndex = to.getIndex();

        final var factor = this.woodShiftFactor();
        if (fromIndex > toIndex) {
            fromIndex -= (int) (time / factor);
            if (fromIndex < toIndex) {
                this.wood = to;
            }
        } else {
            fromIndex += (int) (time / factor);
            if (fromIndex > toIndex) {
                this.wood = to;
            }
        }
    }

    private void woodShiftNew(final float time, final BarrelWoodType to) {
        final var old = this.wood;
        final var factor = this.woodShiftFactor();
        final var shift = time / factor;
        // If the old algorithm would shift by 2 indexes,
        // shift the barrel type by 1 group (or 1 barrel type within a group)
        final var steps = (int) (2.0f * shift);
        this.wood = this.wood.stepTowards(to, steps);
        Logging.debugLog(String.format("Shifted wood from %s to %s by %s steps", old, this.wood, steps));
        Logging.debugLog(String.format("time=%.3f, factor=%.3f, shift=%.3f", time, factor, shift));
    }

    private float woodShiftFactor() {
        float factor = 1;
        if (this.ageTime > 5) {
            factor = 2;
        }
        if (this.ageTime > 10) {
            factor += this.ageTime / 10F;
        }
        return factor;
    }

    public ItemStack createItem() {
        return this.createItem(null, true, null);
    }

    /**
     * Create a new Item of this Brew. A BrewModifyEvent type CREATE will be called.
     *
     * @param recipe Recipe is required if the brew doesn't have a currentRecipe
     * @return The created Item, null if the Event is cancelled
     */
    public final ItemStack createItem(@Nullable final BRecipe recipe) {
        return this.createItem(recipe, true, null);
    }

    public final ItemStack createItem(@Nullable final BRecipe recipe, @Nullable final Player player) {
        return this.createItem(recipe, true, player);
    }

    public ItemStack createItem(@Nullable final BRecipe recipe, final boolean event) {
        return this.createItem(recipe, true, null);
    }

    /**
     * Create a new Item of this Brew.
     *
     * @param recipe Recipe is required if the brew doesn't have a currentRecipe
     * @param event  Set event to true if a BrewModifyEvent type CREATE should be called and may be cancelled. Only then may this method return null
     * @return The created Item, null if the Event is cancelled
     */
    @Contract("_, false -> !null")
    public final ItemStack createItem(@Nullable BRecipe recipe, final boolean event, @Nullable final Player player) {
        if (recipe == null) {
            recipe = this.getCurrentRecipe();
        }
        if (recipe == null) {
            throw new IllegalArgumentException("Argument recipe can't be null if the brew doesn't have a currentRecipe");
        }
        final var potion = new ItemStack(Material.POTION);
        final var potionMeta = (PotionMeta) potion.getItemMeta();

        recipe.getColor().colorBrew(potionMeta);
        this.updateCustomModelData(potionMeta);


        if (recipe.isGlint()) {
            potionMeta.addEnchant(Enchantment.MENDING, 1, true);
            potionMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        potionMeta.setDisplayName(BUtil.color("&f" + recipe.getName(this.quality)));
        //if (!P.use1_14) {
        // Before 1.14 the effects duration would strangely be only a quarter of what we tell it to be
        // This is due to the Duration Modifier, that is removed in 1.14
        //	uid *= 4;
        //}
        // This effect stores the UID in its Duration
        //potionMeta.addCustomEffect((PotionEffectType.REGENERATION).createEffect((uid * 4), 0), true);

        final var lore = new BrewLore(this, potionMeta);
        lore.convertLore(false);
        lore.addOrReplaceEffects(recipe.getEffects(), this.quality);
        lore.write();
        this.touch();
        if (event) {
            final var modifyEvent = new BrewModifyEvent(this, potionMeta, BrewModifyEvent.Type.CREATE, player);
            BreweryPlugin.getInstance().getServer().getPluginManager().callEvent(modifyEvent);
            if (modifyEvent.isCancelled()) {
                return null;
            }
        }
        this.save(potionMeta);
        potion.setItemMeta(potionMeta);
        BreweryPlugin.getInstance().getBreweryStats().metricsForCreate(true);
        return potion;
    }

    private void loadFromStream(final DataInputStream in, final byte dataVersion) throws IOException {
        this.quality = in.readByte();
        final var bools = in.readUnsignedByte();
        if ((bools & 64) != 0) {
            this.alc = in.readShort();
        }
        if ((bools & 1) != 0) {
            this.distillRuns = in.readByte();
        }
        if ((bools & 2) != 0) {
            this.ageTime = in.readFloat();
        }
        if ((bools & 4) != 0) {
            this.wood = BarrelWoodType.fromAny(in.readFloat());
        }
        String recipe = null;
        if ((bools & 8) != 0) {
            recipe = in.readUTF();
        }
        this.unlabeled = (bools & 16) != 0;
        this.immutable = (bools & 32) != 0;
        this.stripped = (bools & 128) != 0;
        this.ingredients = BIngredients.load(in, dataVersion);
        this.setRecipeFromString(recipe);
    }

    /**
     * Save brew data into meta: lore/nbt.
     * <p>Should be called after any changes made to the brew
     */
    public final void save(final ItemMeta meta) {
        final var itemSaveStream = new NBTSaveStream(meta);
        final var scrambler = new XORScrambleStream(itemSaveStream, saveSeed);
        try (final var out = new DataOutputStream(scrambler)) {
            out.writeByte(86); // Parity/sanity
            out.writeByte(SAVE_VER); // Version
            // If Stripped of data, we can save everything unscrambled
            if (config.isEnableEncode() && !this.isStripped()) {
                scrambler.start();
            } else {
                scrambler.startUnscrambled();
            }
            this.saveToStream(out);
        } catch (final IOException e) {
            Logging.errorLog("IO Error while saving Brew", e);
        }
    }

    /**
     * Save brew data into the meta/lore of the specified item.
     * <p>The meta on the item changes, so to make further changes to the meta, item.getItemMeta() has to be called again after this
     *
     * @param item The item to save this brew into
     */
    public void save(final ItemStack item) {
        final ItemMeta meta;
        if (!item.hasItemMeta()) {
            meta = BreweryPlugin.getInstance().getServer().getItemFactory().getItemMeta(item.getType());
        } else {
            meta = item.getItemMeta();
        }
        this.save(meta);
        item.setItemMeta(meta);
    }

    public final void saveToStream(final DataOutputStream out) throws IOException {
        if (this.quality > 10) {
            this.quality = 10;
        }
        this.alc = Math.min(this.alc, Short.MAX_VALUE);
        this.alc = Math.max(this.alc, Short.MIN_VALUE);

        out.writeByte((byte) this.quality);
        var bools = 0;
        bools |= ((this.distillRuns != 0) ? 1 : 0);
        bools |= (this.ageTime > 0 ? 2 : 0);
        bools |= (this.wood != BarrelWoodType.NONE ? 4 : 0);
        bools |= (this.currentRecipe != null ? 8 : 0);
        bools |= (this.unlabeled ? 16 : 0);
        bools |= (this.immutable ? 32 : 0);
        bools |= (this.alc != 0 ? 64 : 0);
        bools |= (this.stripped ? 128 : 0);
        out.writeByte(bools);
        if (this.alc != 0) {
            out.writeShort(this.alc);
        }
        if (this.distillRuns != 0) {
            out.writeByte(this.distillRuns);
        }
        if (this.ageTime > 0) {
            out.writeFloat(this.ageTime);
        }
        if (this.wood != BarrelWoodType.NONE) {
            out.writeFloat(this.wood != null ? this.wood.getIndex() : 0);
        }
        if (this.currentRecipe != null) {
            out.writeUTF(this.currentRecipe.getRecipeName());
        }
        this.ingredients.save(out);
    }
}
