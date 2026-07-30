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

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Custom Item that matches any one of the given info.
 * <p>Does not implement Ingredient, as it can not directly be added to an ingredient
 */
public final class CustomMatchAnyItem extends RecipeItem {

    private List<Material> materials;
    private List<String> names;
    private List<String> lore;
    private List<Integer> customModelDatas;


    @Override
    public final boolean hasMaterials() {
        return this.materials != null && !this.materials.isEmpty();
    }

    public final boolean hasNames() {
        return this.names != null && !this.names.isEmpty();
    }

    public final boolean hasLore() {
        return this.lore != null && !this.lore.isEmpty();
    }

    public final boolean hasCustomModelDatas() {
        return this.customModelDatas != null && !this.customModelDatas.isEmpty();
    }

    @Override
    @Nullable
    public List<Material> getMaterials() {
        return this.materials;
    }

    protected final void setMaterials(final List<Material> materials) {
        this.materials = materials;
    }

    @Nullable
    public List<String> getNames() {
        return this.names;
    }

    protected final void setNames(final List<String> names) {
        this.names = names;
    }

    @Nullable
    public List<String> getLore() {
        return this.lore;
    }

    protected final void setLore(final List<String> lore) {
        this.lore = lore;
    }

    @Nullable
    public List<Integer> getCustomModelDatas() {
        return this.customModelDatas;
    }

    protected final void setCustomModelDatas(final List<Integer> customModelDatas) {
        this.customModelDatas = customModelDatas;
    }


    @NotNull
    @Override
    public Ingredient toIngredient(final ItemStack forItem) {
        // We only use the one part of this item that actually matched the given item to add to ingredients
        final var mat = this.getMaterialMatch(forItem);
        if (mat != null) {
            return new CustomItem(mat);
        }
        final var name = this.getNameMatch(forItem);
        if (name != null) {
            return new CustomItem(null, name, null);
        }
        final var l = this.getLoreMatch(forItem);
        if (l != null) {
            final List<String> lore = new ArrayList<>(1);
            lore.add(l);
            return new CustomItem(null, null, lore);
        }
        final var cmData = this.getCustomModelDataMatch(forItem);
        if (cmData != null) {
            return new CustomItem(null, null, null, cmData);
        }

        // Shouldnt happen
        return new SimpleItem(Material.GOLDEN_HOE);
    }

    @NotNull
    @Override
    public Ingredient toIngredientGeneric() {
        if (this.hasMaterials()) {
            return new CustomItem(this.materials.getFirst());
        }
        if (this.hasNames()) {
            return new CustomItem(null, this.names.getFirst(), null);
        }
        if (this.hasLore()) {
            return new CustomItem(null, null, new ArrayList<>(List.of(this.lore.getFirst())));
        }
        if (this.hasCustomModelDatas()) {
            return new CustomItem(null, null, null, this.customModelDatas.getFirst());
        }

        // Shouldnt happen
        return new SimpleItem(Material.GOLDEN_HOE);
    }

    public final Material getMaterialMatch(final ItemStack item) {
        if (!this.hasMaterials()) return null;

        final var usedMat = item.getType();
        for (final var mat : this.materials) {
            if (usedMat == mat) {
                return mat;
            }
        }
        return null;
    }

    public final String getNameMatch(final ItemStack item) {
        if (!item.hasItemMeta() || !this.hasNames()) {
            return null;
        }
        final var meta = item.getItemMeta();
        assert meta != null;
        if (meta.hasDisplayName()) {
            return this.getNameMatch(meta.getDisplayName());
        }
        return null;
    }

    public final String getNameMatch(final String usedName) {
        if (!this.hasNames()) return null;

        for (final var name : this.names) {
            if (name.equalsIgnoreCase(usedName)) {
                return name;
            }
        }
        return null;
    }

    public final String getLoreMatch(final ItemStack item) {
        if (!item.hasItemMeta() || !this.hasLore()) {
            return null;
        }
        final var meta = item.getItemMeta();
        assert meta != null;
        if (meta.hasLore()) {
            return this.getLoreMatch(meta.getLore());
        }
        return null;
    }

    public final String getLoreMatch(final List<String> usedLore) {
        if (!this.hasLore()) return null;

        for (final var line : this.lore) {
            for (final var usedLine : usedLore) {
                if (line.equalsIgnoreCase(usedLine) || line.equalsIgnoreCase(ChatColor.stripColor(usedLine))) {
                    return line;
                }
            }
        }
        return null;
    }

    @Nullable
    public final Integer getCustomModelDataMatch(final ItemStack item) {
        if (!item.hasItemMeta() || !this.hasCustomModelDatas()) {
            return null;
        }
        final var meta = item.getItemMeta();
        assert meta != null;
        final var customModelData = CustomItem.readCustomModelData(meta);
        if (customModelData != null) {
            return this.getCustomModelDataMatch(customModelData);
        }
        return null;
    }

    @Nullable
    public final Integer getCustomModelDataMatch(final int usedCustomModelData) {
        if (!this.hasCustomModelDatas()) return null;

        for (final int customModelData : this.customModelDatas) {
            if (customModelData == usedCustomModelData) {
                return customModelData;
            }
        }
        return null;
    }

    @Override
    public boolean matches(final ItemStack item) {
        if (this.getMaterialMatch(item) != null) {
            return true;
        }
        if (this.getNameMatch(item) != null) {
            return true;
        }
        if (this.getLoreMatch(item) != null) {
            return true;
        }
        return this.getCustomModelDataMatch(item) != null;
    }

    @Override
    public boolean matches(final Ingredient ingredient) {
        // Ingredient can not be CustomMatchAnyItem, so we don't need to/can't check for similarity.
        if (ingredient instanceof final CustomItem ci) {
            // If the custom item has any of our data, we match
            if (this.hasMaterials() && ci.hasMaterials()) {
                if (this.materials.contains(ci.getMaterial())) {
                    return true;
                }
            }
            if (this.hasNames() && ci.hasName()) {
                if (this.getNameMatch(ci.getName()) != null) {
                    return true;
                }
            }
            if (this.hasLore() && ci.hasLore()) {
                return this.getLoreMatch(ci.getLore()) != null;
            }
            if (this.hasCustomModelDatas() && ci.hasCustomModelData()) {
                return this.getCustomModelDataMatch(ci.getCustomModelData()) != null;
            }
        } else if (ingredient instanceof final SimpleItem si) {
            // If we contain the Material of the Simple Item, we match
            return this.hasMaterials() && this.materials.contains(si.getMaterial());
        }
        return false;
    }

    @Override
    public String getDebugID() {
        final var id = this.getConfigId();
        return id != null ? id : String.join("|", this.names);
    }

    @Override
    public final boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || this.getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        final var that = (CustomMatchAnyItem) o;
        return Objects.equals(this.materials, that.materials) &&
                Objects.equals(this.names, that.names) &&
                Objects.equals(this.lore, that.lore) &&
                Objects.equals(this.customModelDatas, that.customModelDatas);
    }

    @Override
    public final int hashCode() {
        return Objects.hash(super.hashCode(), this.materials, this.names, this.lore, this.customModelDatas);
    }

    @Override
    public final String toString() {
        return "CustomMatchAnyItem{" +
                "id=" + this.getConfigId() +
                ", materials: " + (this.materials != null ? this.materials.size() : 0) +
                ", names:" + (this.names != null ? this.names.size() : 0) +
                ", loresize: " + (this.lore != null ? this.lore.size() : 0) +
                ", customModelDatas: " + (this.customModelDatas != null ? this.customModelDatas.size() : 0) +
                '}';
    }
}
