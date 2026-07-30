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

package com.dre.brewery.storage.impls;

import com.dre.brewery.*;
import com.dre.brewery.configuration.sector.capsule.ConfiguredDataManager;
import com.dre.brewery.storage.DataManager;
import com.dre.brewery.storage.StorageInitException;
import com.dre.brewery.storage.interfaces.SerializableThing;
import com.dre.brewery.storage.records.BreweryMiscData;
import com.dre.brewery.storage.serialization.BukkitSerialization;
import com.dre.brewery.storage.serialization.SQLDataSerializer;
import com.dre.brewery.utility.BUtil;
import com.dre.brewery.utility.BoundingBox;
import com.dre.brewery.utility.FutureUtil;
import com.dre.brewery.utility.Logging;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.CompletableFuture;

// TODO: Simplify methods
public final class FlatFileStorage extends DataManager {

    private final File rawFile;
    private final YamlConfiguration dataFile;
    private SQLDataSerializer serializer;

    public FlatFileStorage(final ConfiguredDataManager record) throws StorageInitException {
        super(record.getType());
        final var fileName = record.getDatabase() + ".yml";
        this.rawFile = new File(plugin.getDataFolder(), fileName);

        if (!this.rawFile.exists()) {
            try {
                this.rawFile.createNewFile();
            } catch (final IOException e) {
                throw new StorageInitException("Failed to create file! " + fileName, e);
            }
        }

        this.dataFile = YamlConfiguration.loadConfiguration(this.rawFile);
    }


    private void save() {
        try {
            this.dataFile.save(this.rawFile);
        } catch (final IOException e) {
            Logging.errorLog("Failed to save to FlatFile!", e);
        }
    }

    private SQLDataSerializer getLazySerializerInstance() {
        if (this.serializer == null) {
            this.serializer = new SQLDataSerializer();
        }
        return this.serializer;
    }

    @Override
    public boolean createTable(final String name, final int maxIdLength) {
        if (this.dataFile.contains(name)) {
            return false;
        }
        this.dataFile.createSection(name);
        this.save();
        return true;
    }

    @Override
    public boolean dropTable(final String name) {
        this.dataFile.set(name, null);
        this.save();
        return true;
    }


    @Override
    public final <T extends SerializableThing> T getGeneric(final String id, final String table, final Class<T> type) {
        final var path = table + "." + id;

        final var section = this.dataFile.getConfigurationSection(path);
        if (section == null) {
            return null;
        }

        // Get all values at the path as a Map
        final var map = section.getValues(false);
        final var gson = this.getLazySerializerInstance().getGson();

        // Gson writes ints as doubles sometimes, but they seem to serialize back to ints just fine.
        final var jsonElement = gson.toJsonTree(map);
        return gson.fromJson(jsonElement, type);
    }

    @Override
    public <T extends SerializableThing> List<T> getAllGeneric(final String table, final Class<T> type) {
        final var section = this.dataFile.getConfigurationSection(table);
        if (section == null) {
            return Collections.emptyList();
        }
        final List<T> things = new ArrayList<>();
        for (final var key : section.getKeys(false)) {
            things.add(this.getGeneric(key, table, type));
        }
        return things;
    }

    @Override
    public <T extends SerializableThing> void saveAllGeneric(final List<T> serializableThings, final String table, @Nullable final Class<T> type) {
        final var section = this.dataFile.getConfigurationSection(table);
        if (section != null) {
            section.getKeys(false).forEach(key -> this.dataFile.set(table + "." + key, null));
        } else {
            this.dataFile.createSection(table);
        }

        for (final var thing : serializableThings) {
            this.setGeneric(thing, table);
        }
        this.save();
    }

    private void setGeneric(final SerializableThing serializableThing, final String table) {
        final var path = table + "." + serializableThing.getId();

        final var gson = this.getLazySerializerInstance().getGson();
        final var jsonObject = gson.toJsonTree(serializableThing).getAsJsonObject();
        final var mapType = new TypeToken<Map<String, Object>>() {
        }.getType();
        final Map<String, Object> map = gson.fromJson(jsonObject, mapType);
        for (final var entry : map.entrySet()) {
            this.dataFile.set(path + "." + entry.getKey(), entry.getValue());
        }
    }

    @Override
    public final <T extends SerializableThing> void saveGeneric(final T serializableThing, final String table) {
        this.setGeneric(serializableThing, table);
        this.save();
    }

    @Override
    public void deleteGeneric(final String id, final String table) {
        this.dataFile.set(table + "." + id, null);
        this.save();
    }

    @Override
    public final CompletableFuture<Barrel> getBarrel(final UUID id) {
        final var path = "barrels." + id;

        final var spigotLoc = deserializeLocation(this.dataFile.getString(path + ".spigot"));
        if (spigotLoc == null) {
            return CompletableFuture.completedFuture(null);
        }

        final var bounds = Arrays.stream(
                        this.dataFile.getString(path + ".bounds").split(",")
                )
                .mapToInt(Integer::parseInt).toArray();

        final var boundingBox = BoundingBox.fromPoints(bounds);
        final var time = (float) this.dataFile.getDouble(path + ".time", 0.0);
        final var sign = (byte) this.dataFile.getInt(path + ".sign", 0);
        final var items = BukkitSerialization.itemStackArrayFromBase64(this.dataFile.getString(path + ".items", null));


        return Barrel.computeSmall(spigotLoc).thenApplyAsync(small ->
                new Barrel(spigotLoc.getBlock(), sign, boundingBox, items, time, id, small)
        );
    }

    @Override
    public CompletableFuture<List<Barrel>> getAllBarrels() {
        final var section = this.dataFile.getConfigurationSection("barrels");
        if (section == null) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        final List<CompletableFuture<Barrel>> barrels = new ArrayList<>();

        for (final var key : section.getKeys(false)) {
            final var barrel = this.getBarrel(BUtil.uuidFromString(key));
            if (barrel != null) {
                barrels.add(barrel);
            }
        }
        return FutureUtil.mergeFutures(barrels);
    }

    @Override
    public void saveAllBarrels(final Collection<Barrel> barrels) {
        this.dataFile.set("barrels", null);
        for (final var barrel : barrels) {
            this.setBarrel(barrel);
        }
        this.save();
    }

    private void setBarrel(final Barrel barrel) {
        if (barrel.getBounds() == null) {
            return;
        }
        final var path = "barrels." + barrel.getId();

        this.dataFile.set(path + ".spigot", serializeLocation(barrel.getSpigot().getLocation()));
        this.dataFile.set(path + ".bounds", barrel.getBounds().serialize());
        this.dataFile.set(path + ".time", barrel.getTime());
        this.dataFile.set(path + ".sign", barrel.getSignoffset());
        this.dataFile.set(path + ".items", BukkitSerialization.itemStackArrayToBase64(barrel.getInventory().getContents()));
    }

    @Override
    public final void saveBarrel(final Barrel barrel) {
        this.setBarrel(barrel);
        this.save();
    }

    @Override
    public void deleteBarrel(final UUID id) {
        this.dataFile.set("barrels." + id, null);
        this.save();
    }

    @Override
    public final BCauldron getCauldron(final UUID id) {
        final var path = "cauldrons." + id;

        final var loc = deserializeLocation(this.dataFile.getString(path + ".block"));
        if (loc == null) {
            return null;
        }
        final var ingredients = BIngredients.deserializeIngredients(this.dataFile.getString(path + ".ingredients"));
        final var state = this.dataFile.getInt(path + ".state", 0);

        return new BCauldron(loc.getBlock(), ingredients, state, id);
    }

    @Override
    public Collection<BCauldron> getAllCauldrons() {
        final var section = this.dataFile.getConfigurationSection("cauldrons");

        if (section == null) {
            return Collections.emptyList();
        }

        final List<BCauldron> cauldrons = new ArrayList<>();

        for (final var key : section.getKeys(false)) {
            final var cauldron = this.getCauldron(BUtil.uuidFromString(key));
            if (cauldron != null) {
                cauldrons.add(cauldron);
            }
        }
        return cauldrons;
    }

    @Override
    public void saveAllCauldrons(final Collection<BCauldron> cauldrons) {
        this.dataFile.set("cauldrons", null);
        for (final var cauldron : cauldrons) {
            this.setCauldron(cauldron);
        }
        this.save();
    }

    private void setCauldron(final BCauldron cauldron) {
        final var path = "cauldrons." + cauldron.getId();

        this.dataFile.set(path + ".block", serializeLocation(cauldron.getBlock().getLocation()));
        this.dataFile.set(path + ".ingredients", cauldron.getIngredients().serializeIngredients());
        this.dataFile.set(path + ".state", cauldron.getState());
    }

    @Override
    public final void saveCauldron(final BCauldron cauldron) {
        this.setCauldron(cauldron);
        this.save();
    }


    @Override
    public void deleteCauldron(final UUID id) {
        this.dataFile.set("cauldrons." + id, null);
        this.save();
    }


    @Override
    public final BPlayer getPlayer(final UUID playerUUID) {
        final var path = "players." + playerUUID;

        final var quality = this.dataFile.getInt(path + ".quality", 0);
        final var drunkenness = this.dataFile.getInt(path + ".drunkenness", 0);
        final var offlineDrunkenness = this.dataFile.getInt(path + ".offlineDrunkenness", 0);
        return new BPlayer(playerUUID, quality, drunkenness, offlineDrunkenness);
    }

    @Override
    public Collection<BPlayer> getAllPlayers() {
        final var section = this.dataFile.getConfigurationSection("players");

        if (section == null) {
            return Collections.emptyList();
        }

        final List<BPlayer> players = new ArrayList<>();

        for (final var key : section.getKeys(false)) {
            final var player = this.getPlayer(BUtil.uuidFromString(key));
            if (player != null) {
                players.add(player);
            }
        }
        return players;
    }

    @Override
    public void saveAllPlayers(final Collection<BPlayer> players) {
        this.dataFile.set("players", null);
        for (final var player : players) {
            this.setPlayer(player);
        }
        this.save();
    }

    private void setPlayer(final BPlayer player) {
        final var path = "players." + player.getUuid();

        this.dataFile.set(path + ".quality", player.getQuality());
        this.dataFile.set(path + ".drunkenness", player.getDrunkeness());
        this.dataFile.set(path + ".offlineDrunkenness", player.getOfflineDrunkeness());
    }

    @Override
    public final void savePlayer(final BPlayer player) {
        this.setPlayer(player);
        this.save();
    }

    @Override
    public void deletePlayer(final UUID playerUUID) {
        this.dataFile.set("players." + playerUUID, null);
        this.save();
    }

    @Override
    public final Wakeup getWakeup(final UUID id) {
        final var path = "wakeups." + id;
        final var wakeupLocation = deserializeLocation(this.dataFile.getString(path + ".location"), true);
        if (wakeupLocation == null) {
            return null;
        }
        return new Wakeup(wakeupLocation, id);
    }

    @Override
    public Collection<Wakeup> getAllWakeups() {
        final var section = this.dataFile.getConfigurationSection("wakeups");

        if (section == null) {
            return Collections.emptyList();
        }

        final List<Wakeup> wakeups = new ArrayList<>();

        for (final var key : section.getKeys(false)) {
            final var wakeup = this.getWakeup(BUtil.uuidFromString(key));
            if (wakeup != null) {
                wakeups.add(wakeup);
            }
        }
        return wakeups;
    }

    @Override
    public void saveAllWakeups(final Collection<Wakeup> wakeups) {
        this.dataFile.set("wakeups", null);
        for (final var wakeup : wakeups) {
            this.setWakeup(wakeup);
        }
        this.save();
    }

    private void setWakeup(final Wakeup wakeup) {
        final var path = "wakeups." + wakeup.getId();
        this.dataFile.set(path + ".location", serializeLocation(wakeup.getLoc(), true));
    }

    @Override
    public final void saveWakeup(final Wakeup wakeup) {
        this.setWakeup(wakeup);
        this.save();
    }

    @Override
    public void deleteWakeup(final UUID id) {
        this.dataFile.set("wakeups." + id, null);
        this.save();
    }

    @Override
    public BreweryMiscData getBreweryMiscData() {
        return new BreweryMiscData(
                this.dataFile.getLong("misc.installTime", System.currentTimeMillis()),
                this.dataFile.getLong("misc.mcBarrelTime", 0),
                this.dataFile.getLongList("misc.previousSaveSeeds"),
                this.dataFile.getIntegerList("misc.brewsCreated"),
                this.dataFile.getInt("misc.brewsCreatedHash", 0)
        );
    }

    @Override
    public void saveBreweryMiscData(final BreweryMiscData data) {
        this.dataFile.set("misc.installTime", data.installTime());
        this.dataFile.set("misc.mcBarrelTime", data.mcBarrelTime());
        this.dataFile.set("misc.previousSaveSeeds", data.prevSaveSeeds());
        this.dataFile.set("misc.brewsCreated", data.brewsCreated());
        this.dataFile.set("misc.brewsCreatedHash", data.brewsCreatedHash());
        this.save();
    }
}
