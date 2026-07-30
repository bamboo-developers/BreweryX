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
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Simple Minecraft Item with just Material
 */
public final class SimpleItem extends RecipeItem implements Ingredient {


    private final Material mat;
    private final short dur; // Unused, kept because it is part of the item save format


    public SimpleItem(final Material mat) {
        this(mat, (short) 0);
    }

    public SimpleItem(final Material mat, final short dur) {
        this.mat = mat;
        this.dur = dur;
    }

    public static SimpleItem loadFrom(final ItemLoader loader) {
        try {
            final var in = loader.getInputStream();
            final var mat = Material.getMaterial(in.readUTF());
            final var dur = in.readShort();
            if (mat != null) {
                final var item = new SimpleItem(mat, dur);
                return item;
            }
        } catch (final IOException e) {
            Logging.errorLog("Failed to load SimpleItem", e);
        }
        return null;
    }

    // Needs to be called at Server start
    public static void registerItemLoader(final BreweryPlugin breweryPlugin) {
        breweryPlugin.registerForItemLoader("SI", SimpleItem::loadFrom);
    }

    @Override
    public boolean hasMaterials() {
        return this.mat != null;
    }

    public final Material getMaterial() {
        return this.mat;
    }

    @Override
    public List<Material> getMaterials() {
        final List<Material> l = new ArrayList<>(1);
        l.add(this.mat);
        return l;
    }

    @NotNull
    @Override
    public Ingredient toIngredient(final ItemStack forItem) {
        return ((SimpleItem) this.getMutableCopy());
    }

    @NotNull
    @Override
    public Ingredient toIngredientGeneric() {
        return ((SimpleItem) this.getMutableCopy());
    }

    @Override
    public boolean matches(final ItemStack item) {
        if (!this.mat.equals(item.getType())) {
            return false;
        }
        return true;
    }

    @Override
    public boolean matches(final Ingredient ingredient) {
        if (this.isSimilar(ingredient)) {
            return true;
        }
        if (ingredient instanceof RecipeItem) {
            if (!((RecipeItem) ingredient).hasMaterials()) {
                return false;
            }
            if (ingredient instanceof final CustomItem ci) {
                // Only match if the Custom Item also only defines material
                // If the custom item has more info like name and lore, it is not supposed to match a simple item
                return !ci.hasLore() && !ci.hasName() && this.mat == ci.getMaterial();
            }
        }
        return false;
    }

    @Override
    public final boolean isSimilar(final Ingredient item) {
        if (this == item) {
            return true;
        }
        if (item instanceof final SimpleItem si) {
            return si.mat == this.mat && si.dur == this.dur;
        }
        return false;
    }

    @Override
    public final boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || this.getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        final var item = (SimpleItem) o;
        return this.dur == item.dur &&
                this.mat == item.mat;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(super.hashCode(), this.mat, this.dur);
    }

    @Override
    public final String toString() {
        return "SimpleItem{" +
                "mat=" + this.getDebugID() +
                " amount=" + this.getAmount() +
                '}';
    }

    @Override
    public final String getDebugID() {
        return this.mat.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public void saveTo(final DataOutputStream out) throws IOException {
        out.writeUTF("SI");
        out.writeUTF(this.mat.name());
        out.writeShort(this.dur);
    }

}
