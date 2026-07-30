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

import com.dre.brewery.BCauldron;
import com.dre.brewery.BPlayer;
import com.dre.brewery.Barrel;
import com.dre.brewery.Wakeup;
import com.dre.brewery.configuration.sector.capsule.ConfiguredDataManager;
import com.dre.brewery.storage.DataManager;
import com.dre.brewery.storage.DataManagerType;
import com.dre.brewery.storage.StorageInitException;
import com.dre.brewery.storage.interfaces.SerializableThing;
import com.dre.brewery.storage.records.*;
import com.dre.brewery.storage.serialization.SQLDataSerializer;
import com.dre.brewery.utility.FutureUtil;
import com.dre.brewery.utility.Logging;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

// Handles MySQL, SQLite and PostgreSQL through a shared HikariCP-pooled DataSource,
// dispatching on the dialect only where the SQL actually differs.
@SuppressWarnings({"SqlSourceToSinkFlow", "Duplicates"})
public final class MySQLStorage extends DataManager {

    private static final String[] TABLES_LONGTEXT = {
            "misc (id VARCHAR(4) PRIMARY KEY, data LONGTEXT);",
            "barrels (id VARCHAR(36) PRIMARY KEY, data LONGTEXT);",
            "cauldrons (id VARCHAR(36) PRIMARY KEY, data LONGTEXT);",
            "players (id VARCHAR(36) PRIMARY KEY, data LONGTEXT);",
            "wakeups (id VARCHAR(36) PRIMARY KEY, data LONGTEXT);"
    };
    private static final String[] TABLES_TEXT = {
            "misc (id VARCHAR(4) PRIMARY KEY, data TEXT);",
            "barrels (id VARCHAR(36) PRIMARY KEY, data TEXT);",
            "cauldrons (id VARCHAR(36) PRIMARY KEY, data TEXT);",
            "players (id VARCHAR(36) PRIMARY KEY, data TEXT);",
            "wakeups (id VARCHAR(36) PRIMARY KEY, data TEXT);"
    };

    private final DataManagerType dialect;
    private final String tablePrefix;
    private final SQLDataSerializer serializer;
    private final HikariDataSource source;

    public MySQLStorage(final ConfiguredDataManager record) throws StorageInitException {
        super(record.getType());
        this.dialect = record.getType();
        this.tablePrefix = record.getTablePrefix();
        this.serializer = new SQLDataSerializer();

        final var config = new HikariConfig();
        switch (this.dialect) {
            case MYSQL -> {
                config.setJdbcUrl("jdbc:mysql://" + record.getAddress() + "/" + record.getDatabase());
                config.setUsername(record.getUsername());
                config.setPassword(record.getPassword());
            }
            case POSTGRESQL -> {
                config.setJdbcUrl("jdbc:postgresql://" + record.getAddress() + "/" + record.getDatabase());
                config.setUsername(record.getUsername());
                config.setPassword(record.getPassword());
            }
            case SQLITE -> {
                final var rawFile = new File(plugin.getDataFolder(), record.getDatabase() + ".db");
                if (!rawFile.exists()) {
                    try {
                        rawFile.createNewFile();
                    } catch (final IOException e) {
                        throw new StorageInitException("Failed to create db file! " + rawFile.getName(), e);
                    }
                }
                config.setJdbcUrl("jdbc:sqlite:" + rawFile.getAbsolutePath());
                // SQLite only supports a single writer at a time
                config.setMaximumPoolSize(1);
            }
            default -> throw new StorageInitException("Unsupported SQL dialect: " + this.dialect, null);
        }
        this.source = new HikariDataSource(config);

        final var tables = this.dialect == DataManagerType.MYSQL ? TABLES_LONGTEXT : TABLES_TEXT;
        try (final var connection = this.source.getConnection()) {
            for (final var table : tables) {
                try (final var statement = connection.prepareStatement("CREATE TABLE IF NOT EXISTS " + this.tablePrefix + table)) {
                    statement.execute();
                }
            }
        } catch (final SQLException e) {
            throw new StorageInitException("Failed to connect or create tables!", e);
        }
    }

    @Override
    protected void closeConnection() {
        this.source.close();
    }

    private String upsertSql(final String table) {
        return switch (this.dialect) {
            case MYSQL ->
                    "INSERT INTO " + this.tablePrefix + table + " (id, data) VALUES (?, ?) ON DUPLICATE KEY UPDATE data = VALUES(data)";
            case SQLITE, POSTGRESQL ->
                    "INSERT INTO " + this.tablePrefix + table + " (id, data) VALUES (?, ?) ON CONFLICT (id) DO UPDATE SET data = EXCLUDED.data";
            default -> throw new IllegalStateException("Unsupported SQL dialect: " + this.dialect);
        };
    }

    @Override
    public boolean createTable(final String name, final int maxIdLength) {
        final var columnType = this.dialect == DataManagerType.MYSQL ? "LONGTEXT" : "TEXT";
        final var sql = "CREATE TABLE IF NOT EXISTS " + this.tablePrefix + name + " (id VARCHAR(" + maxIdLength + ") PRIMARY KEY, data " + columnType + ");";
        try (final var connection = this.source.getConnection()) {
            connection.prepareStatement(sql).execute();
            return true;
        } catch (final SQLException e) {
            Logging.errorLog("Failed to create table: " + name + " due to a database exception!", e);
            return false;
        }
    }

    @Override
    public boolean dropTable(final String name) {
        final var sql = "DROP TABLE IF EXISTS " + this.tablePrefix + name;
        try (final var connection = this.source.getConnection()) {
            connection.prepareStatement(sql).execute();
            return true;
        } catch (final SQLException e) {
            Logging.errorLog("Failed to drop table: " + name + " due to a database exception!", e);
            return false;
        }
    }

    @Override
    public final <T extends SerializableThing> T getGeneric(final String id, final String table, final Class<T> type) {
        final var sql = "SELECT data FROM " + this.tablePrefix + table + " WHERE id = ?";
        try (final var connection = this.source.getConnection(); final var statement = connection.prepareStatement(sql)) {
            statement.setString(1, id);
            try (final var resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return this.serializer.deserialize(resultSet.getString("data"), type);
                }
            }
        } catch (final SQLException e) {
            Logging.errorLog("Failed to retrieve object from table: " + table + ", from database!", e);
        }
        return null;
    }

    @Override
    public final <T extends SerializableThing> List<T> getAllGeneric(final String table, final Class<T> type) {
        final var sql = "SELECT id, data FROM " + this.tablePrefix + table;
        final List<T> objects = new ArrayList<>();

        try (final var connection = this.source.getConnection(); final var statement = connection.prepareStatement(sql);
             final var resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                final var data = resultSet.getString("data");
                objects.add(this.serializer.deserialize(data, type));
            }
        } catch (final SQLException e) {
            Logging.errorLog("Failed to retrieve objects from table: " + table + ", from database!", e);
        }
        return objects;
    }

    public final <T extends SerializableThing> void saveAllGeneric(final List<T> serializableThings, final String table) {
        this.saveAllGeneric(serializableThings, table, null);
    }

    // Batch saving/deleting
    @Override
    public final <T extends SerializableThing> void saveAllGeneric(final List<T> serializableThings, final String table, @Nullable final Class<T> type) {
        final var createTempTableSql = "CREATE TEMPORARY TABLE temp_" + table + " (id VARCHAR(36) PRIMARY KEY, data " + (this.dialect == DataManagerType.MYSQL ? "LONGTEXT" : "TEXT") + ")";
        final var insertTempTableSql = this.dialect == DataManagerType.MYSQL
                ? "INSERT INTO temp_" + table + " (id, data) VALUES (?, ?) ON DUPLICATE KEY UPDATE data = VALUES(data)"
                : "INSERT INTO temp_" + table + " (id, data) VALUES (?, ?) ON CONFLICT (id) DO UPDATE SET data = EXCLUDED.data";
        final var deleteOldRecordsSql = "DELETE FROM " + this.tablePrefix + table + " WHERE id NOT IN (SELECT id FROM temp_" + table + ")";
        final var replaceTableSql = switch (this.dialect) {
            case MYSQL -> "REPLACE INTO " + this.tablePrefix + table + " SELECT * FROM temp_" + table;
            case SQLITE -> "INSERT OR REPLACE INTO " + this.tablePrefix + table + " (id, data) SELECT id, data FROM temp_" + table;
            case POSTGRESQL ->
                    "INSERT INTO " + this.tablePrefix + table + " (id, data) SELECT id, data FROM temp_" + table + " ON CONFLICT (id) DO UPDATE SET data = EXCLUDED.data";
            default -> throw new IllegalStateException("Unsupported SQL dialect: " + this.dialect);
        };
        final var dropTempTableSql = "DROP TABLE IF EXISTS temp_" + table;

        try (final var connection = this.source.getConnection()) {
            connection.setAutoCommit(false);
            try (final var createTempTableStmt = connection.prepareStatement(createTempTableSql);
                 final var insertTempTableStmt = connection.prepareStatement(insertTempTableSql)) {
                createTempTableStmt.execute();

                for (final SerializableThing serializableThing : serializableThings) {
                    insertTempTableStmt.setString(1, serializableThing.getId());
                    insertTempTableStmt.setString(2, this.serializer.serialize(serializableThing));
                    insertTempTableStmt.addBatch();
                }
                insertTempTableStmt.executeBatch();

                try (final var deleteStmt = connection.prepareStatement(deleteOldRecordsSql)) {
                    deleteStmt.executeUpdate();
                }

                try (final var replaceTableStmt = connection.prepareStatement(replaceTableSql)) {
                    replaceTableStmt.executeUpdate();
                }

                connection.commit();
            } catch (final SQLException e) {
                connection.rollback();
                Logging.errorLog("Failed to save objects to: " + table + " due to a database exception!", e);
            } finally {
                try (final var dropTempTableStmt = connection.prepareStatement(dropTempTableSql)) {
                    dropTempTableStmt.execute();
                } catch (final SQLException e) {
                    Logging.errorLog("Failed to drop temporary table for saving objects to: " + table + " due to a database exception!", e);
                } finally {
                    connection.setAutoCommit(true);
                }
            }
        } catch (final SQLException e) {
            Logging.errorLog("Failed to manage transaction for saving objects to: " + table + " due to a database exception!", e);
        }
    }

    @Override
    public final <T extends SerializableThing> void saveGeneric(final T serializableThing, final String table) {
        final var sql = this.upsertSql(table);
        try (final var connection = this.source.getConnection(); final var statement = connection.prepareStatement(sql)) {
            statement.setString(1, serializableThing.getId());
            statement.setString(2, this.serializer.serialize(serializableThing));
            statement.execute();
        } catch (final SQLException e) {
            Logging.errorLog("Failed to save object to:" + table + ", to database!", e);
        }
    }

    @Override
    public final void deleteGeneric(final String id, final String table) {
        final var sql = "DELETE FROM " + this.tablePrefix + table + " WHERE id = ?";
        try (final var connection = this.source.getConnection(); final var statement = connection.prepareStatement(sql)) {
            statement.setString(1, id);
            statement.execute();
        } catch (final SQLException e) {
            Logging.errorLog("Failed to delete object from: " + table + ", from database!", e);
        }
    }

    @Override
    public CompletableFuture<Barrel> getBarrel(final UUID id) {
        final var serializableBarrel = this.getGeneric(id.toString(), "barrels", SerializableBarrel.class);
        if (serializableBarrel != null) {
            return serializableBarrel.toBarrel();
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<List<Barrel>> getAllBarrels() {
        return FutureUtil.mergeFutures(
                this.getAllGeneric("barrels", SerializableBarrel.class).stream()
                        .map(SerializableBarrel::toBarrel)
                        .toList()
        );
    }

    @Override
    public void saveAllBarrels(final Collection<Barrel> barrels) {
        final var serializableBarrels = barrels.stream()
                .filter(it -> it.getBounds() != null)
                .map(SerializableBarrel::new)
                .toList();
        this.saveAllGeneric(serializableBarrels, "barrels");
    }

    @Override
    public void saveBarrel(final Barrel barrel) {
        this.saveGeneric(new SerializableBarrel(barrel), "barrels");
    }

    @Override
    public void deleteBarrel(final UUID id) {
        this.deleteGeneric(id.toString(), "barrels");
    }

    @Override
    public BCauldron getCauldron(final UUID id) {
        final var serializableCauldron = this.getGeneric(id.toString(), "cauldrons", SerializableCauldron.class);
        if (serializableCauldron != null) {
            return serializableCauldron.toCauldron();
        }
        return null;
    }

    @Override
    public Collection<BCauldron> getAllCauldrons() {
        return this.getAllGeneric("cauldrons", SerializableCauldron.class).stream()
                .map(SerializableCauldron::toCauldron)
                .toList();
    }

    @Override
    public void saveAllCauldrons(final Collection<BCauldron> cauldrons) {
        final var serializableCauldrons = cauldrons.stream()
                .map(SerializableCauldron::new)
                .toList();
        this.saveAllGeneric(serializableCauldrons, "cauldrons");
    }

    @Override
    public void saveCauldron(final BCauldron cauldron) {
        this.saveGeneric(new SerializableCauldron(cauldron), "cauldrons");
    }

    @Override
    public void deleteCauldron(final UUID id) {
        this.deleteGeneric(id.toString(), "cauldrons");
    }

    @Override
    public BPlayer getPlayer(final UUID playerUUID) {
        final var serializableBPlayer = this.getGeneric(playerUUID.toString(), "players", SerializableBPlayer.class);
        if (serializableBPlayer != null) {
            return serializableBPlayer.toBPlayer();
        }
        return null;
    }

    @Override
    public Collection<BPlayer> getAllPlayers() {
        return this.getAllGeneric("players", SerializableBPlayer.class).stream()
                .map(SerializableBPlayer::toBPlayer)
                .toList();
    }

    @Override
    public void saveAllPlayers(final Collection<BPlayer> players) {
        final var serializableBPlayers = players.stream()
                .map(SerializableBPlayer::new)
                .toList();
        this.saveAllGeneric(serializableBPlayers, "players");
    }

    @Override
    public void savePlayer(final BPlayer player) {
        this.saveGeneric(new SerializableBPlayer(player), "players");
    }

    @Override
    public void deletePlayer(final UUID playerUUID) {
        this.deleteGeneric(playerUUID.toString(), "players");
    }

    @Override
    public Wakeup getWakeup(final UUID id) {
        final var serializableWakeup = this.getGeneric(id.toString(), "wakeups", SerializableWakeup.class);
        if (serializableWakeup != null) {
            return serializableWakeup.toWakeup();
        }
        return null;
    }

    @Override
    public Collection<Wakeup> getAllWakeups() {
        return this.getAllGeneric("wakeups", SerializableWakeup.class).stream()
                .map(SerializableWakeup::toWakeup)
                .toList();
    }

    @Override
    public void saveAllWakeups(final Collection<Wakeup> wakeups) {
        final var serializableWakeups = wakeups.stream()
                .map(SerializableWakeup::new)
                .toList();
        this.saveAllGeneric(serializableWakeups, "wakeups");
    }

    @Override
    public void saveWakeup(final Wakeup wakeup) {
        this.saveGeneric(new SerializableWakeup(wakeup), "wakeups");
    }

    @Override
    public void deleteWakeup(final UUID id) {
        this.deleteGeneric(id.toString(), "wakeups");
    }

    @Override
    public BreweryMiscData getBreweryMiscData() {
        final var sql = "SELECT CASE WHEN EXISTS (SELECT 1 FROM " + this.tablePrefix + "misc WHERE id = 'misc') THEN (SELECT data FROM " + this.tablePrefix + "misc WHERE id = 'misc') ELSE NULL END AS data";
        try (final var connection = this.source.getConnection(); final var statement = connection.prepareStatement(sql);
             final var resultSet = statement.executeQuery()) {
            if (resultSet.next() && resultSet.getString("data") != null) {
                return this.serializer.deserialize(resultSet.getString("data"), BreweryMiscData.class);
            }
        } catch (final SQLException e) {
            Logging.errorLog("Failed to retrieve misc data from database!", e);
        }
        return new BreweryMiscData(System.currentTimeMillis(), 0, new ArrayList<>(), new ArrayList<>(), 0);
    }

    @Override
    public void saveBreweryMiscData(final BreweryMiscData data) {
        final var sql = switch (this.dialect) {
            case MYSQL ->
                    "INSERT INTO " + this.tablePrefix + "misc (id, data) VALUES ('misc', ?) ON DUPLICATE KEY UPDATE data = VALUES(data)";
            case SQLITE, POSTGRESQL ->
                    "INSERT INTO " + this.tablePrefix + "misc (id, data) VALUES ('misc', ?) ON CONFLICT (id) DO UPDATE SET data = EXCLUDED.data";
            default -> throw new IllegalStateException("Unsupported SQL dialect: " + this.dialect);
        };
        try (final var connection = this.source.getConnection(); final var statement = connection.prepareStatement(sql)) {
            statement.setString(1, this.serializer.serialize(data));
            statement.execute();
        } catch (final SQLException e) {
            Logging.errorLog("Failed to save misc data to database!", e);
        }
    }
}
