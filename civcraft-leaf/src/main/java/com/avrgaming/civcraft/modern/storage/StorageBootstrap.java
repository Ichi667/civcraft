package com.avrgaming.civcraft.modern.storage;

import com.avrgaming.civcraft.modern.config.ModernCivCraftSettings;
import com.avrgaming.civcraft.modern.domain.CampRecord;
import com.avrgaming.civcraft.modern.domain.CivRecord;
import com.avrgaming.civcraft.modern.domain.ResidentProfile;
import com.avrgaming.civcraft.modern.domain.TownRecord;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

public final class StorageBootstrap implements AutoCloseable {
    private final JavaPlugin plugin;
    private final ModernCivCraftSettings settings;
    private Connection connection;

    public StorageBootstrap(JavaPlugin plugin, ModernCivCraftSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
    }

    public void initializeLocalStorage() {
        if (!settings.storageType().equalsIgnoreCase("sqlite")) {
            plugin.getLogger().warning("Only local SQLite bootstrap is implemented in this port foundation. Requested: " + settings.storageType());
            return;
        }

        Path dataFolder = plugin.getDataFolder().toPath();
        Path databaseFile = settings.sqlitePath(dataFolder);
        try {
            Files.createDirectories(dataFolder);
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile);
            this.connection.setAutoCommit(true);
            createBootstrapSchema();
            plugin.getLogger().info("Local CivCraft SQLite storage ready: " + databaseFile);
        } catch (IOException | SQLException exception) {
            plugin.getLogger().severe("Unable to initialize local CivCraft storage: " + exception.getMessage());
        }
    }

    private void createBootstrapSchema() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("PRAGMA foreign_keys = ON");
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS civcraft_meta (
                        key TEXT PRIMARY KEY,
                        value TEXT NOT NULL
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS residents (
                        uuid TEXT PRIMARY KEY,
                        name TEXT NOT NULL,
                        coins REAL NOT NULL DEFAULT 0,
                        civ_id INTEGER,
                        town_id INTEGER,
                        camp_id INTEGER,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS camps (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL UNIQUE COLLATE NOCASE,
                        owner_uuid TEXT NOT NULL UNIQUE,
                        world TEXT NOT NULL,
                        x INTEGER NOT NULL,
                        y INTEGER NOT NULL,
                        z INTEGER NOT NULL,
                        hitpoints INTEGER NOT NULL,
                        firepoints_hours INTEGER NOT NULL,
                        created_at INTEGER NOT NULL,
                        FOREIGN KEY(owner_uuid) REFERENCES residents(uuid) ON DELETE CASCADE
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS civilizations (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL UNIQUE COLLATE NOCASE,
                        leader_uuid TEXT NOT NULL,
                        coins REAL NOT NULL DEFAULT 0,
                        government TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        FOREIGN KEY(leader_uuid) REFERENCES residents(uuid) ON DELETE CASCADE
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS towns (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL UNIQUE COLLATE NOCASE,
                        civ_id INTEGER NOT NULL,
                        mayor_uuid TEXT NOT NULL,
                        world TEXT NOT NULL,
                        x INTEGER NOT NULL,
                        y INTEGER NOT NULL,
                        z INTEGER NOT NULL,
                        level INTEGER NOT NULL DEFAULT 1,
                        coins REAL NOT NULL DEFAULT 0,
                        created_at INTEGER NOT NULL,
                        FOREIGN KEY(civ_id) REFERENCES civilizations(id) ON DELETE CASCADE,
                        FOREIGN KEY(mayor_uuid) REFERENCES residents(uuid) ON DELETE CASCADE
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO civcraft_meta(key, value)
                    VALUES('schema_version', 'modern-playable-1')
                    ON CONFLICT(key) DO UPDATE SET value = excluded.value
                    """);
        }
    }

    public boolean isReady() {
        return connection != null;
    }

    public ResidentProfile getOrCreateResident(UUID uuid, String name, double startingCoins) throws SQLException {
        Optional<ResidentProfile> existing = findResident(uuid);
        if (existing.isPresent()) {
            updateResidentName(uuid, name);
            return findResident(uuid).orElseThrow();
        }
        long now = System.currentTimeMillis();
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO residents(uuid, name, coins, created_at, updated_at) VALUES(?, ?, ?, ?, ?)")) {
            statement.setString(1, uuid.toString());
            statement.setString(2, name);
            statement.setDouble(3, startingCoins);
            statement.setLong(4, now);
            statement.setLong(5, now);
            statement.executeUpdate();
        }
        return findResident(uuid).orElseThrow();
    }

    public Optional<ResidentProfile> findResident(UUID uuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM residents WHERE uuid = ?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapResident(rs));
            }
        }
    }

    public Optional<CampRecord> findCampByOwner(UUID uuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM camps WHERE owner_uuid = ?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(mapCamp(rs)) : Optional.empty();
            }
        }
    }

    public Optional<CivRecord> findCiv(long id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM civilizations WHERE id = ?")) {
            statement.setLong(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(mapCiv(rs)) : Optional.empty();
            }
        }
    }

    public Optional<TownRecord> findTown(long id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM towns WHERE id = ?")) {
            statement.setLong(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(mapTown(rs)) : Optional.empty();
            }
        }
    }

    public boolean hasTownNear(Location location, double distance) throws SQLException {
        double squared = distance * distance;
        try (PreparedStatement statement = connection.prepareStatement("SELECT world, x, y, z FROM towns WHERE world = ?")) {
            statement.setString(1, location.getWorld().getName());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    double dx = rs.getInt("x") - location.getBlockX();
                    double dy = rs.getInt("y") - location.getBlockY();
                    double dz = rs.getInt("z") - location.getBlockZ();
                    if ((dx * dx) + (dy * dy) + (dz * dz) < squared) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public CampRecord createCamp(UUID owner, String name, Location location, double cost, int hitpoints, int firepointsHours) throws SQLException {
        connection.setAutoCommit(false);
        try {
            withdrawResident(owner, cost);
            long now = System.currentTimeMillis();
            long campId;
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO camps(name, owner_uuid, world, x, y, z, hitpoints, firepoints_hours, created_at)
                    VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, name);
                statement.setString(2, owner.toString());
                statement.setString(3, location.getWorld().getName());
                statement.setInt(4, location.getBlockX());
                statement.setInt(5, location.getBlockY());
                statement.setInt(6, location.getBlockZ());
                statement.setInt(7, hitpoints);
                statement.setInt(8, firepointsHours);
                statement.setLong(9, now);
                statement.executeUpdate();
                campId = generatedId(statement);
            }
            try (PreparedStatement statement = connection.prepareStatement("UPDATE residents SET camp_id = ?, updated_at = ? WHERE uuid = ?")) {
                statement.setLong(1, campId);
                statement.setLong(2, now);
                statement.setString(3, owner.toString());
                statement.executeUpdate();
            }
            connection.commit();
            return findCampByOwner(owner).orElseThrow();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    public CivRecord createCivilization(UUID leader, String name, double cost, String government) throws SQLException {
        connection.setAutoCommit(false);
        try {
            withdrawResident(leader, cost);
            long now = System.currentTimeMillis();
            long civId;
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO civilizations(name, leader_uuid, government, created_at) VALUES(?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, name);
                statement.setString(2, leader.toString());
                statement.setString(3, government);
                statement.setLong(4, now);
                statement.executeUpdate();
                civId = generatedId(statement);
            }
            try (PreparedStatement statement = connection.prepareStatement("UPDATE residents SET civ_id = ?, updated_at = ? WHERE uuid = ?")) {
                statement.setLong(1, civId);
                statement.setLong(2, now);
                statement.setString(3, leader.toString());
                statement.executeUpdate();
            }
            connection.commit();
            return findCiv(civId).orElseThrow();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    public TownRecord createTown(UUID mayor, long civId, String name, Location location, double cost) throws SQLException {
        connection.setAutoCommit(false);
        try {
            withdrawResident(mayor, cost);
            long now = System.currentTimeMillis();
            long townId;
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO towns(name, civ_id, mayor_uuid, world, x, y, z, created_at)
                    VALUES(?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, name);
                statement.setLong(2, civId);
                statement.setString(3, mayor.toString());
                statement.setString(4, location.getWorld().getName());
                statement.setInt(5, location.getBlockX());
                statement.setInt(6, location.getBlockY());
                statement.setInt(7, location.getBlockZ());
                statement.setLong(8, now);
                statement.executeUpdate();
                townId = generatedId(statement);
            }
            try (PreparedStatement statement = connection.prepareStatement("UPDATE residents SET town_id = ?, updated_at = ? WHERE uuid = ?")) {
                statement.setLong(1, townId);
                statement.setLong(2, now);
                statement.setString(3, mayor.toString());
                statement.executeUpdate();
            }
            connection.commit();
            return findTown(townId).orElseThrow();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private void updateResidentName(UUID uuid, String name) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("UPDATE residents SET name = ?, updated_at = ? WHERE uuid = ?")) {
            statement.setString(1, name);
            statement.setLong(2, System.currentTimeMillis());
            statement.setString(3, uuid.toString());
            statement.executeUpdate();
        }
    }

    private void withdrawResident(UUID uuid, double amount) throws SQLException {
        if (amount < 0 || Double.isNaN(amount) || Double.isInfinite(amount)) {
            throw new SQLException("Invalid amount: " + amount);
        }
        try (PreparedStatement check = connection.prepareStatement("SELECT coins FROM residents WHERE uuid = ?")) {
            check.setString(1, uuid.toString());
            try (ResultSet rs = check.executeQuery()) {
                if (!rs.next() || rs.getDouble("coins") < amount) {
                    throw new SQLException("Not enough coins");
                }
            }
        }
        try (PreparedStatement statement = connection.prepareStatement("UPDATE residents SET coins = coins - ?, updated_at = ? WHERE uuid = ?")) {
            statement.setDouble(1, amount);
            statement.setLong(2, System.currentTimeMillis());
            statement.setString(3, uuid.toString());
            statement.executeUpdate();
        }
    }

    private long generatedId(PreparedStatement statement) throws SQLException {
        try (ResultSet keys = statement.getGeneratedKeys()) {
            if (!keys.next()) {
                throw new SQLException("No generated id returned");
            }
            return keys.getLong(1);
        }
    }

    private ResidentProfile mapResident(ResultSet rs) throws SQLException {
        return new ResidentProfile(
                UUID.fromString(rs.getString("uuid")),
                rs.getString("name"),
                rs.getDouble("coins"),
                nullableLong(rs, "civ_id"),
                nullableLong(rs, "town_id"),
                nullableLong(rs, "camp_id")
        );
    }

    private CampRecord mapCamp(ResultSet rs) throws SQLException {
        return new CampRecord(
                rs.getLong("id"),
                rs.getString("name"),
                UUID.fromString(rs.getString("owner_uuid")),
                rs.getString("world"),
                rs.getInt("x"),
                rs.getInt("y"),
                rs.getInt("z"),
                rs.getInt("hitpoints"),
                rs.getInt("firepoints_hours")
        );
    }

    private CivRecord mapCiv(ResultSet rs) throws SQLException {
        return new CivRecord(rs.getLong("id"), rs.getString("name"), UUID.fromString(rs.getString("leader_uuid")), rs.getDouble("coins"), rs.getString("government"));
    }

    private TownRecord mapTown(ResultSet rs) throws SQLException {
        return new TownRecord(rs.getLong("id"), rs.getString("name"), rs.getLong("civ_id"), UUID.fromString(rs.getString("mayor_uuid")), rs.getString("world"), rs.getInt("x"), rs.getInt("y"), rs.getInt("z"), rs.getInt("level"), rs.getDouble("coins"));
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    @Override
    public void close() {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException exception) {
            plugin.getLogger().warning("Unable to close CivCraft storage cleanly: " + exception.getMessage());
        }
    }
}
