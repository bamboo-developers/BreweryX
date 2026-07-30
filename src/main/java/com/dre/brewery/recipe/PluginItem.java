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
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * An Item of a Recipe or as Ingredient in a Brew that corresponds to an item from another plugin.
 * <p>See /integration/item for examples on how to extend this class.
 * <p>This class stores items as name of the plugin and item id
 */
public abstract class PluginItem extends RecipeItem implements Ingredient {

    private static final Map<String, Supplier<PluginItem>> constructors = new HashMap<>();

    private String plugin;
    private String itemId;

    /**
     * New Empty PluginItem
     */
    public PluginItem() {
    }

    /**
     * New PluginItem with both fields already set
     *
     * @param plugin The name of the Plugin
     * @param itemId The ItemID
     */
    public PluginItem(final String plugin, final String itemId) {
        this.plugin = plugin;
        this.itemId = itemId;
    }

    /**
     * Called when loading this Plugin Item from Ingredients (of a Brew).
     * <p>The default loading is the same as loading from Config
     *
     * @param loader The ItemLoader from which to load the data, use loader.getInputStream()
     * @return The constructed PluginItem
     */
    public static PluginItem loadFrom(final ItemLoader loader) {
        try {
            final var in = loader.getInputStream();
            final var plugin = in.readUTF();
            final var itemId = in.readUTF();
            var item = fromConfig(plugin, itemId);
            if (item == null) {
                // Plugin not found when loading from Item, use a generic PluginItem that never matches other items
                item = new PluginItem(plugin, itemId) {
                    @Override
                    public boolean matches(final ItemStack item) {
                        return false;
                    }
                };
            }
            return item;
        } catch (final IOException e) {
            Logging.errorLog("Failed to load PluginItem from Ingredients", e);
            return null;
        }
    }

    /**
     * Registers the chosen SaveID and the loading Method for loading from Brew or BCauldron.
     * <p>Needs to be called at Server start.
     */
    public static void registerItemLoader(final BreweryPlugin breweryPlugin) {
        breweryPlugin.registerForItemLoader("PI", PluginItem::loadFrom);
    }

    /**
     * Called when loading trying to find a config defined Plugin Item. By default also when loading from ingredients
     * <p>Will call a registered constructor matching the given plugin identifier
     *
     * @param plugin The Identifier of the Plugin used in the config
     * @param itemId The Identifier of the Item belonging to this Plugin used in the config
     * @return The Plugin Item if found, or null if there is no plugin for the given String
     */
    @Nullable
    public static PluginItem fromConfig(String plugin, final String itemId) {
        plugin = plugin.toLowerCase();
        if (constructors.containsKey(plugin)) {
            final var item = constructors.get(plugin).get();
            item.setPlugin(plugin);
            item.setItemId(itemId);
            item.onConstruct();
            return item;
        }
        return null;
    }

    /**
     * This needs to be called at Server Start before Brewery loads its data.
     * <p>When implementing this, put Brewery as softdepend in your plugin.yml!
     * <p>Registers a Constructor that returns a new or cloned instance of a PluginItem
     * <br>This Constructor will be called when loading a Plugin Item from Config or by default from ingredients
     * <br>After the Constructor is called, the plugin and itemid will be set on the new instance
     * <p>Finally the onConstruct is called.
     *
     * @param pluginId    The ID to use in the config
     * @param constructor The constructor i.e. YourPluginItem::new
     */
    public static void registerForConfig(final String pluginId, final Supplier<PluginItem> constructor) {
        constructors.put(pluginId.toLowerCase(), constructor);
    }

    public static void unRegisterForConfig(final String pluginId) {
        constructors.remove(pluginId.toLowerCase());
    }

    @Override
    public boolean hasMaterials() {
        return false;
    }

    @Override
    public List<Material> getMaterials() {
        return null;
    }

    public final String getPlugin() {
        return this.plugin;
    }

    protected final void setPlugin(final String plugin) {
        this.plugin = plugin;
    }

    public final String getItemId() {
        return this.itemId;
    }

    protected final void setItemId(final String itemId) {
        this.itemId = itemId;
    }

    /**
     * Called after Loading this Plugin Item from Config, or (by default) from Ingredients.
     * <p>Allows Override to define custom actions after an Item was constructed
     */
    protected final void onConstruct() {
    }

    /**
     * Does this PluginItem Match the other Ingredient.
     * <p>By default it matches exactly when they are similar, i.e. also a PluginItem with same parameters
     *
     * @param ingredient The ingredient that needs to fulfill the requirements
     * @return True if the ingredient matches the required info of this
     */
    @Override
    public boolean matches(final Ingredient ingredient) {
        return this.isSimilar(ingredient);
    }

    @NotNull
    @Override
    public Ingredient toIngredient(final ItemStack forItem) {
        return ((PluginItem) this.getMutableCopy());
    }

    @NotNull
    @Override
    public Ingredient toIngredientGeneric() {
        return ((PluginItem) this.getMutableCopy());
    }

    @Override
    public final boolean isSimilar(final Ingredient item) {
        if (item instanceof PluginItem) {
            return Objects.equals(this.plugin, ((PluginItem) item).plugin) && Objects.equals(this.itemId, ((PluginItem) item).itemId);
        }
        return false;
    }

    @Override
    public final boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || this.getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        final var item = (PluginItem) o;
        return Objects.equals(this.plugin, item.plugin) &&
                Objects.equals(this.itemId, item.itemId);
    }

    @Override
    public final int hashCode() {
        return Objects.hash(super.hashCode(), this.plugin, this.itemId);
    }

    @Override
    public String getDebugID() {
        if (this.plugin == null) {
            return this.itemId;
        }
        return this.plugin + ":" + this.itemId;
    }

    @Override
    public void saveTo(final DataOutputStream out) throws IOException {
        out.writeUTF("PI");
        out.writeUTF(this.plugin);
        out.writeUTF(this.itemId);
    }

}
