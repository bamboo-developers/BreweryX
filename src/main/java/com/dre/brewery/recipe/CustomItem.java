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

package com.dre.brewery.recipe;

import com.dre.brewery.BreweryPlugin;
import com.dre.brewery.utility.Logging;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Minecraft Item with custon name and lore.
 * <p>Mostly used for Custom Items of the Config, but also for general custom items
 */
public final class CustomItem extends RecipeItem implements Ingredient {

    private Material mat;
    private String name;
    private List<String> lore;
    private int customModelData = 0;

    public CustomItem() {
    }

    public CustomItem(final Material mat) {
        this.mat = mat;
    }

    public CustomItem(final Material mat, final String name, final List<String> lore) {
        this.mat = mat;
        this.name = name;
        this.lore = lore;
    }

    public CustomItem(final Material mat, final String name, final List<String> lore, final int customModelData) {
        this.mat = mat;
        this.name = name;
        this.lore = lore;
        this.customModelData = customModelData;
    }

    public CustomItem(final ItemStack item) {
        this.mat = item.getType();
        if (!item.hasItemMeta()) {
            return;
        }
        final var itemMeta = item.getItemMeta();
        assert itemMeta != null;
        if (itemMeta.hasDisplayName()) {
            this.name = itemMeta.getDisplayName();
        } else if (itemMeta.hasItemName()) {
            this.name = itemMeta.getItemName();
        }
        if (itemMeta.hasLore()) {
            this.lore = itemMeta.getLore();
        }
        final var readCustomModelData = readCustomModelData(itemMeta);
        if (readCustomModelData != null) {
            this.customModelData = readCustomModelData;
        }
    }

    public static CustomItem loadFrom(final ItemLoader loader) {
        try {
            final var in = loader.getInputStream();
            final var item = new CustomItem();
            if (in.readBoolean()) {
                item.mat = Material.getMaterial(in.readUTF());
            }
            if (in.readBoolean()) {
                item.name = in.readUTF();
            }
            final var size = in.readShort();
            if (size > 0) {
                item.lore = new ArrayList<>(size);
                for (short i = 0; i < size; i++) {
                    item.lore.add(in.readUTF());
                }
            }
            if (in.readBoolean()) {
                item.customModelData = in.readInt();
            }
            return item;
        } catch (final IOException e) {
            Logging.errorLog("Failed to load CustomItem", e);
            return null;
        }
    }

    // Needs to be called at Server start
    public static void registerItemLoader(final BreweryPlugin breweryPlugin) {
        breweryPlugin.registerForItemLoader("CI", CustomItem::loadFrom);
    }

    @Override
    public final boolean hasMaterials() {
        return this.mat != null;
    }

    public final boolean hasName() {
        return this.name != null;
    }

    public final boolean hasLore() {
        return this.lore != null && !this.lore.isEmpty();
    }

    public final boolean hasCustomModelData() {
        return this.customModelData != 0;
    }

    @Override
    public List<Material> getMaterials() {
        final List<Material> l = new ArrayList<>(1);
        l.add(this.mat);
        return l;
    }

    @Nullable
    public final Material getMaterial() {
        return this.mat;
    }

    protected final void setMat(final Material mat) {
        this.mat = mat;
    }

    @Nullable
    public final String getName() {
        return this.name;
    }

    protected final void setName(final String name) {
        this.name = name;
    }

    @Nullable
    public final List<String> getLore() {
        return this.lore;
    }

    protected final void setLore(final List<String> lore) {
        this.lore = lore;
    }

    public final int getCustomModelData() {
        return this.customModelData;
    }

    protected final void setCustomModelData(final int customModelData) {
        this.customModelData = customModelData;
    }

    @NotNull
    @Override
    public Ingredient toIngredient(final ItemStack forItem) {
        return ((CustomItem) this.getMutableCopy());
    }

    @NotNull
    @Override
    public Ingredient toIngredientGeneric() {
        return ((CustomItem) this.getMutableCopy());
    }

    @Override
    public boolean matches(final Ingredient ingredient) {
        if (this.isSimilar(ingredient)) {
            return true;
        }
        if (ingredient instanceof final RecipeItem rItem) {
            if (rItem instanceof SimpleItem) {
                // If the recipe item is just a simple item, only match if we also only define material
                // If this is a custom item with more info, we don't want to match a simple item
                return this.hasMaterials() && !this.hasLore() && !this.hasName() && this.getMaterial() == ((SimpleItem) rItem).getMaterial();
            } else if (rItem instanceof final CustomItem other) {
                // If the other is a CustomItem as well and not Similar to ours, it might have more data and we still match
                if (this.mat == null || this.mat == other.mat) {
                    if (!this.hasName() || (other.name != null && this.name.equalsIgnoreCase(other.name))) {
                        if (this.hasCustomModelData() && this.customModelData != other.customModelData) {
                            return false;
                        }
                        return !this.hasLore() || this.lore == other.lore || (other.hasLore() && this.matchLore(other.lore));
                    }
                }
            }
        }
        return false;
    }

    @Override
    public boolean matches(final ItemStack item) {
        if (this.mat != null) {
            if (item.getType() != this.mat) {
                return false;
            }
        }
        if (!item.hasItemMeta()) {
            return false;
        }
        final var meta = item.getItemMeta();
        assert meta != null;
        if (this.name != null) {
            if (!meta.hasDisplayName() || !this.name.equalsIgnoreCase(meta.getDisplayName())) {
                return false;
            }
        }

        if (this.hasLore()) {
            if (!meta.hasLore()) {
                return false;
            }
            return this.matchLore(meta.getLore());
        }

        if (this.customModelData != 0) {
            final var usedCustomModelData = readCustomModelData(meta);
            return usedCustomModelData != null && usedCustomModelData == this.customModelData;
        }
        return true;
    }

    /**
     * If this item has lore that matches the given lore.
     * <p>It matches if our lore is contained in the given lore consecutively, ignoring color of the given lore.
     *
     * @param usedLore The given lore to match
     * @return True if the given lore contains our lore consecutively
     */
    public final boolean matchLore(final List<String> usedLore) {
        if (this.lore == null) return true;
        var lastIndex = 0;
        var foundFirst = false;
        for (final var line : this.lore) {
            do {
                if (lastIndex == usedLore.size()) {
                    // There is more in lore than in usedLore, bad
                    return false;
                }
                final var usedLine = usedLore.get(lastIndex);
                if (line.equalsIgnoreCase(usedLine) || line.equalsIgnoreCase(ChatColor.stripColor(usedLine))) {
                    // If the line is correct, we have found our first and we want all consecutive lines to also equal
                    foundFirst = true;
                } else if (foundFirst) {
                    // If a consecutive line is not equal, thats bad
                    return false;
                }
                lastIndex++;
                // If we once found one correct line, iterate over 'lore' consecutively
            } while (!foundFirst);
        }
        return true;
    }

    // We don't compare id here
    @Override
    public final boolean isSimilar(final Ingredient item) {
        if (this == item) {
            return true;
        }
        if (item instanceof final CustomItem ci) {
            return this.mat == ci.mat && Objects.equals(this.name, ci.name) && Objects.equals(this.lore, ci.lore) && this.customModelData == ci.customModelData;
        }
        return false;
    }

    @Override
    public final boolean equals(final Object obj) {
        if (!super.equals(obj)) return false;
        if (obj instanceof CustomItem) {
            return this.isSimilar(((CustomItem) obj));
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(super.hashCode(), this.mat, this.name, this.lore, this.customModelData);
    }

    @Override
    public final String toString() {
        return "CustomItem{" +
                "id=" + this.getConfigId() +
                ", mat=" + (this.mat != null ? this.mat.name().toLowerCase() : "null") +
                ", name='" + this.name + '\'' +
                ", loresize: " + (this.lore != null ? this.lore.size() : 0) +
                ", modelData=" + this.customModelData +
                '}';
    }

    @Override
    public String getDebugID() {
        return this.getConfigId();
    }

    @Override
    public void saveTo(final DataOutputStream out) throws IOException {
        out.writeUTF("CI");
        if (this.mat != null) {
            out.writeBoolean(true);
            out.writeUTF(this.mat.name());
        } else {
            out.writeBoolean(false);
        }
        if (this.name != null) {
            out.writeBoolean(true);
            out.writeUTF(this.name);
        } else {
            out.writeBoolean(false);
        }
        if (this.lore != null) {
            final var size = (short) Math.min(this.lore.size(), Short.MAX_VALUE);
            out.writeShort(size);
            for (var i = 0; i < size; i++) {
                out.writeUTF(this.lore.get(i));
            }
        } else {
            out.writeShort(0);
        }
        if (this.customModelData != 0) {
            out.writeBoolean(true);
            out.writeInt(this.customModelData);
        } else {
            out.writeBoolean(false);
        }
    }

    @Nullable
    static Integer readCustomModelData(final ItemMeta meta) {
        final var component = meta.getCustomModelDataComponent();
        final var floats = component.getFloats();
        if (floats.isEmpty()) {
            return null;
        }
        return floats.getFirst().intValue();
    }

}
