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

package com.dre.brewery.storage.serialization;

import com.dre.brewery.utility.Logging;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

public final class BukkitSerialization {
    /**
     * Converts the player inventory to a String array of Base64 strings. First string is the content and second string is the armor.
     *
     * @param playerInventory to turn into an array of strings.
     * @return Array of strings: [ main content, armor content ]
     * @throws IllegalStateException
     */
    public static String[] playerInventoryToBase64(final PlayerInventory playerInventory) throws IllegalStateException {
        //get the main content part, this doesn't return the armor
        final var content = toBase64(playerInventory);
        final var armor = itemStackArrayToBase64(playerInventory.getArmorContents());

        return new String[]{content, armor};
    }

    /**
     * A method to serialize an {@link ItemStack} array to Base64 String.
     * <p>
     * <p/>
     * <p>
     * Based off of {@link #toBase64(Inventory)}.
     *
     * @param items to turn into a Base64 String.
     * @return Base64 string of the items.
     * @throws IllegalStateException
     */
    public static String itemStackArrayToBase64(final ItemStack[] items) throws IllegalStateException {

        try {
            final var outputStream = new ByteArrayOutputStream();
            final var dataOutput = new BukkitObjectOutputStream(outputStream);

            // Write the size of the inventory
            dataOutput.writeInt(items.length);

            // Save every element in the list
            for (final var item : items) {
                dataOutput.writeObject(item);
            }

            // Serialize that array
            dataOutput.close();
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (final Exception e) {
            throw new IllegalStateException("Unable to save item stacks.", e);
        }
    }

    /**
     * A method to serialize an inventory to Base64 string.
     * <p>
     * <p/>
     * <p>
     * Special thanks to Comphenix in the Bukkit forums or also known
     * as aadnk on GitHub.
     *
     * <a href="https://gist.github.com/aadnk/8138186">Original Source</a>
     *
     * @param inventory to serialize
     * @return Base64 string of the provided inventory
     * @throws IllegalStateException
     */
    public static String toBase64(final Inventory inventory) throws IllegalStateException {
        try {
            final var outputStream = new ByteArrayOutputStream();
            final var dataOutput = new BukkitObjectOutputStream(outputStream);

            // Write the size of the inventory
            dataOutput.writeInt(inventory.getSize());

            // Save every element in the list
            for (var i = 0; i < inventory.getSize(); i++) {
                dataOutput.writeObject(inventory.getItem(i));
            }

            // Serialize that array
            dataOutput.close();
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (final Exception e) {
            throw new IllegalStateException("Unable to save item stacks.", e);
        }
    }

    /**
     * A method to get an {@link Inventory} from an encoded, Base64, string.
     * <p>
     * <p/>
     * <p>
     * Special thanks to Comphenix in the Bukkit forums or also known
     * as aadnk on GitHub.
     *
     * <a href="https://gist.github.com/aadnk/8138186">Original Source</a>
     *
     * @param data Base64 string of data containing an inventory.
     * @return Inventory created from the Base64 string.
     * @throws IOException
     */
    public static Inventory fromBase64(final String data) throws IOException {
        if (data == null || data.isEmpty()) {
            return null;
        }
        try {
            final var inputStream = new ByteArrayInputStream(Base64.getMimeDecoder().decode(data));
            final var dataInput = new BukkitObjectInputStream(inputStream);
            final var inventory = Bukkit.getServer().createInventory(null, dataInput.readInt());

            // Read the serialized inventory
            for (var i = 0; i < inventory.getSize(); i++) {
                inventory.setItem(i, (ItemStack) dataInput.readObject());
            }

            dataInput.close();
            return inventory;
        } catch (final ClassNotFoundException e) {
            throw new IOException("Unable to decode class type.", e);
        }
    }

    /**
     * Gets an array of ItemStacks from Base64 string.
     * <p>
     * <p/>
     * <p>
     * Base off of {@link #fromBase64(String)}.
     *
     * @param data Base64 string to convert to ItemStack array.
     * @return ItemStack array created from the Base64 string.
     * @throws IOException
     */
    public static ItemStack[] itemStackArrayFromBase64(final String data) {
        if (data == null || data.isEmpty()) {
            return null;
        }

        try {
            final var inputStream = new ByteArrayInputStream(Base64.getMimeDecoder().decode(data));
            final var dataInput = new BukkitObjectInputStream(inputStream);
            final var items = new ItemStack[dataInput.readInt()];

            // Read the serialized inventory
            for (var i = 0; i < items.length; i++) {
                items[i] = (ItemStack) dataInput.readObject();
            }

            dataInput.close();
            return items;
        } catch (final ClassNotFoundException | IOException e) {
            Logging.errorLog("Unable to decode class type.", e);
        }
        return null;
    }


}
