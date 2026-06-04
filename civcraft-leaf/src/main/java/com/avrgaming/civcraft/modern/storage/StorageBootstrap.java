package com.avrgaming.civcraft.modern.storage;

import com.avrgaming.civcraft.modern.build.BlockPlacement;
import com.avrgaming.civcraft.modern.config.ModernCivCraftSettings;
import com.avrgaming.civcraft.modern.domain.CampRecord;
import com.avrgaming.civcraft.modern.domain.CivRecord;
import com.avrgaming.civcraft.modern.domain.ResidentProfile;
import com.avrgaming.civcraft.modern.domain.TownClaimRecord;
import com.avrgaming.civcraft.modern.domain.TownRecord;
import com.avrgaming.civcraft.modern.research.ActiveResearch;
import com.avrgaming.civcraft.modern.research.ResearchProgress;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
                    CREATE TABLE IF NOT EXISTS town_claims (
                        world TEXT NOT NULL,
                        chunk_x INTEGER NOT NULL,
                        chunk_z INTEGER NOT NULL,
                        town_id INTEGER NOT NULL,
                        civ_id INTEGER NOT NULL,
                        claimed_by_uuid TEXT NOT NULL,
                        claimed_at INTEGER NOT NULL,
                        PRIMARY KEY(world, chunk_x, chunk_z),
                        FOREIGN KEY(town_id) REFERENCES towns(id) ON DELETE CASCADE,
                        FOREIGN KEY(civ_id) REFERENCES civilizations(id) ON DELETE CASCADE,
                        FOREIGN KEY(claimed_by_uuid) REFERENCES residents(uuid) ON DELETE CASCADE
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS structure_builds (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        builder_uuid TEXT NOT NULL,
                        type TEXT NOT NULL,
                        template TEXT NOT NULL,
                        direction TEXT NOT NULL,
                        world TEXT NOT NULL,
                        x INTEGER NOT NULL,
                        y INTEGER NOT NULL,
                        z INTEGER NOT NULL,
                        template_path TEXT NOT NULL,
                        queued_blocks INTEGER NOT NULL,
                        structure_id TEXT,
                        display_name TEXT,
                        cost REAL NOT NULL DEFAULT 0,
                        max_hitpoints INTEGER NOT NULL DEFAULT 1,
                        status TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        completed_at INTEGER,
                        FOREIGN KEY(builder_uuid) REFERENCES residents(uuid) ON DELETE CASCADE
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS protected_blocks (
                        world TEXT NOT NULL,
                        x INTEGER NOT NULL,
                        y INTEGER NOT NULL,
                        z INTEGER NOT NULL,
                        build_id INTEGER NOT NULL,
                        material TEXT NOT NULL,
                        PRIMARY KEY(world, x, y, z),
                        FOREIGN KEY(build_id) REFERENCES structure_builds(id) ON DELETE CASCADE
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS civ_technologies (
                        civ_id INTEGER NOT NULL,
                        tech_id TEXT NOT NULL,
                        researched_at INTEGER NOT NULL,
                        PRIMARY KEY(civ_id, tech_id),
                        FOREIGN KEY(civ_id) REFERENCES civilizations(id) ON DELETE CASCADE
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS civ_research (
                        civ_id INTEGER PRIMARY KEY,
                        tech_id TEXT NOT NULL,
                        progress REAL NOT NULL DEFAULT 0,
                        updated_at INTEGER NOT NULL,
                        FOREIGN KEY(civ_id) REFERENCES civilizations(id) ON DELETE CASCADE
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


    public void depositTown(long townId, double amount) throws SQLException {
        validateMoney(amount);
        try (PreparedStatement statement = connection.prepareStatement("UPDATE towns SET coins = coins + ? WHERE id = ?")) {
            statement.setDouble(1, amount);
            statement.setLong(2, townId);
            if (statement.executeUpdate() == 0) {
                throw new SQLException("Town does not exist: " + townId);
            }
        }
    }

    public void withdrawTown(long townId, double amount) throws SQLException {
        validateMoney(amount);
        connection.setAutoCommit(false);
        try {
            try (PreparedStatement check = connection.prepareStatement("SELECT coins FROM towns WHERE id = ?")) {
                check.setLong(1, townId);
                try (ResultSet rs = check.executeQuery()) {
                    if (!rs.next() || rs.getDouble("coins") < amount) {
                        throw new SQLException("Town does not have enough coins");
                    }
                }
            }
            try (PreparedStatement statement = connection.prepareStatement("UPDATE towns SET coins = coins - ? WHERE id = ?")) {
                statement.setDouble(1, amount);
                statement.setLong(2, townId);
                statement.executeUpdate();
            }
            connection.commit();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    public void depositCiv(long civId, double amount) throws SQLException {
        validateMoney(amount);
        try (PreparedStatement statement = connection.prepareStatement("UPDATE civilizations SET coins = coins + ? WHERE id = ?")) {
            statement.setDouble(1, amount);
            statement.setLong(2, civId);
            if (statement.executeUpdate() == 0) {
                throw new SQLException("Civilization does not exist: " + civId);
            }
        }
    }

    public void withdrawCiv(long civId, double amount) throws SQLException {
        validateMoney(amount);
        connection.setAutoCommit(false);
        try {
            try (PreparedStatement check = connection.prepareStatement("SELECT coins FROM civilizations WHERE id = ?")) {
                check.setLong(1, civId);
                try (ResultSet rs = check.executeQuery()) {
                    if (!rs.next() || rs.getDouble("coins") < amount) {
                        throw new SQLException("Civilization does not have enough coins");
                    }
                }
            }
            try (PreparedStatement statement = connection.prepareStatement("UPDATE civilizations SET coins = coins - ? WHERE id = ?")) {
                statement.setDouble(1, amount);
                statement.setLong(2, civId);
                statement.executeUpdate();
            }
            connection.commit();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
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



    public Optional<TownClaimRecord> findTownClaim(String world, int chunkX, int chunkZ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM town_claims WHERE world = ? AND chunk_x = ? AND chunk_z = ?")) {
            statement.setString(1, world);
            statement.setInt(2, chunkX);
            statement.setInt(3, chunkZ);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(mapTownClaim(rs)) : Optional.empty();
            }
        }
    }

    public int townClaimCount(long townId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) AS count FROM town_claims WHERE town_id = ?")) {
            statement.setLong(1, townId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt("count") : 0;
            }
        }
    }

    public TownClaimRecord claimTownChunk(UUID claimant, long townId, long civId, String world, int chunkX, int chunkZ, double cost) throws SQLException {
        connection.setAutoCommit(false);
        try {
            withdrawResident(claimant, cost);
            try (PreparedStatement check = connection.prepareStatement("SELECT town_id FROM town_claims WHERE world = ? AND chunk_x = ? AND chunk_z = ?")) {
                check.setString(1, world);
                check.setInt(2, chunkX);
                check.setInt(3, chunkZ);
                try (ResultSet rs = check.executeQuery()) {
                    if (rs.next()) {
                        throw new SQLException("Chunk is already claimed by town #" + rs.getLong("town_id"));
                    }
                }
            }
            long now = System.currentTimeMillis();
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO town_claims(world, chunk_x, chunk_z, town_id, civ_id, claimed_by_uuid, claimed_at)
                    VALUES(?, ?, ?, ?, ?, ?, ?)
                    """)) {
                statement.setString(1, world);
                statement.setInt(2, chunkX);
                statement.setInt(3, chunkZ);
                statement.setLong(4, townId);
                statement.setLong(5, civId);
                statement.setString(6, claimant.toString());
                statement.setLong(7, now);
                statement.executeUpdate();
            }
            connection.commit();
            return new TownClaimRecord(world, chunkX, chunkZ, townId, civId, claimant, now);
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    public void unclaimTownChunk(long townId, String world, int chunkX, int chunkZ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM town_claims WHERE town_id = ? AND world = ? AND chunk_x = ? AND chunk_z = ?")) {
            statement.setLong(1, townId);
            statement.setString(2, world);
            statement.setInt(3, chunkX);
            statement.setInt(4, chunkZ);
            if (statement.executeUpdate() == 0) {
                throw new SQLException("This chunk is not claimed by your town");
            }
        }
    }

    public long recordStructureBuild(UUID builder, String type, String template, String direction, Location origin, String templatePath, int queuedBlocks, String structureId, String displayName, double cost, int maxHitpoints) throws SQLException {
        long now = System.currentTimeMillis();
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO structure_builds(builder_uuid, type, template, direction, world, x, y, z, template_path, queued_blocks, structure_id, display_name, cost, max_hitpoints, status, created_at)
                VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'QUEUED', ?)
                """, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, builder.toString());
            statement.setString(2, type);
            statement.setString(3, template);
            statement.setString(4, direction);
            statement.setString(5, origin.getWorld().getName());
            statement.setInt(6, origin.getBlockX());
            statement.setInt(7, origin.getBlockY());
            statement.setInt(8, origin.getBlockZ());
            statement.setString(9, templatePath);
            statement.setInt(10, queuedBlocks);
            statement.setString(11, structureId);
            statement.setString(12, displayName);
            statement.setDouble(13, cost);
            statement.setInt(14, maxHitpoints);
            statement.setLong(15, now);
            statement.executeUpdate();
            return generatedId(statement);
        }
    }

    public void completeStructureBuild(long id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("UPDATE structure_builds SET status = 'COMPLETE', completed_at = ? WHERE id = ?")) {
            statement.setLong(1, System.currentTimeMillis());
            statement.setLong(2, id);
            statement.executeUpdate();
        }
    }



    public Set<String> researchedTechs(long civId) throws SQLException {
        Set<String> techs = new HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT tech_id FROM civ_technologies WHERE civ_id = ?")) {
            statement.setLong(1, civId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    techs.add(rs.getString("tech_id"));
                }
            }
        }
        return techs;
    }

    public Optional<ResearchProgress> researchProgress(long civId, double requiredBeakers) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT tech_id, progress FROM civ_research WHERE civ_id = ?")) {
            statement.setLong(1, civId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new ResearchProgress(civId, rs.getString("tech_id"), rs.getDouble("progress"), requiredBeakers));
            }
        }
    }

    public void startResearch(long civId, String techId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO civ_research(civ_id, tech_id, progress, updated_at)
                VALUES(?, ?, 0, ?)
                ON CONFLICT(civ_id) DO UPDATE SET tech_id = excluded.tech_id, progress = 0, updated_at = excluded.updated_at
                """)) {
            statement.setLong(1, civId);
            statement.setString(2, techId);
            statement.setLong(3, System.currentTimeMillis());
            statement.executeUpdate();
        }
    }

    public List<ActiveResearch> activeResearches() throws SQLException {
        List<ActiveResearch> researches = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT civ_id, tech_id, progress FROM civ_research")) {
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    researches.add(new ActiveResearch(rs.getLong("civ_id"), rs.getString("tech_id"), rs.getDouble("progress")));
                }
            }
        }
        return researches;
    }

    public boolean addResearchBeakers(long civId, String techId, double beakers, double requiredBeakers) throws SQLException {
        if (Double.isNaN(beakers) || Double.isInfinite(beakers) || beakers < 0) {
            throw new SQLException("Invalid beaker amount: " + beakers);
        }
        connection.setAutoCommit(false);
        try {
            double progress;
            try (PreparedStatement read = connection.prepareStatement("SELECT progress FROM civ_research WHERE civ_id = ? AND tech_id = ?")) {
                read.setLong(1, civId);
                read.setString(2, techId);
                try (ResultSet rs = read.executeQuery()) {
                    if (!rs.next()) {
                        connection.rollback();
                        return false;
                    }
                    progress = rs.getDouble("progress") + beakers;
                }
            }
            if (progress >= requiredBeakers) {
                try (PreparedStatement insert = connection.prepareStatement("INSERT OR IGNORE INTO civ_technologies(civ_id, tech_id, researched_at) VALUES(?, ?, ?)")) {
                    insert.setLong(1, civId);
                    insert.setString(2, techId);
                    insert.setLong(3, System.currentTimeMillis());
                    insert.executeUpdate();
                }
                try (PreparedStatement delete = connection.prepareStatement("DELETE FROM civ_research WHERE civ_id = ?")) {
                    delete.setLong(1, civId);
                    delete.executeUpdate();
                }
                connection.commit();
                return true;
            }
            try (PreparedStatement update = connection.prepareStatement("UPDATE civ_research SET progress = ?, updated_at = ? WHERE civ_id = ? AND tech_id = ?")) {
                update.setDouble(1, progress);
                update.setLong(2, System.currentTimeMillis());
                update.setLong(3, civId);
                update.setString(4, techId);
                update.executeUpdate();
            }
            connection.commit();
            return false;
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    public int townCount(long civId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) AS count FROM towns WHERE civ_id = ?")) {
            statement.setLong(1, civId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt("count") : 0;
            }
        }
    }

    public void chargeAndStartResearch(UUID uuid, double amount, long civId, String techId) throws SQLException {
        connection.setAutoCommit(false);
        try {
            withdrawResident(uuid, amount);
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO civ_research(civ_id, tech_id, progress, updated_at)
                    VALUES(?, ?, 0, ?)
                    ON CONFLICT(civ_id) DO UPDATE SET tech_id = excluded.tech_id, progress = 0, updated_at = excluded.updated_at
                    """)) {
                statement.setLong(1, civId);
                statement.setString(2, techId);
                statement.setLong(3, System.currentTimeMillis());
                statement.executeUpdate();
            }
            connection.commit();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    public void chargeResident(UUID uuid, double amount) throws SQLException {
        connection.setAutoCommit(false);
        try {
            withdrawResident(uuid, amount);
            connection.commit();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    public void depositResident(UUID uuid, double amount) throws SQLException {
        if (amount < 0 || Double.isNaN(amount) || Double.isInfinite(amount)) {
            throw new SQLException("Invalid amount: " + amount);
        }
        try (PreparedStatement statement = connection.prepareStatement("UPDATE residents SET coins = coins + ?, updated_at = ? WHERE uuid = ?")) {
            statement.setDouble(1, amount);
            statement.setLong(2, System.currentTimeMillis());
            statement.setString(3, uuid.toString());
            if (statement.executeUpdate() == 0) {
                throw new SQLException("Resident does not exist: " + uuid);
            }
        }
    }

    public void recordProtectedBlocks(long buildId, Collection<BlockPlacement> placements) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT OR REPLACE INTO protected_blocks(world, x, y, z, build_id, material)
                VALUES(?, ?, ?, ?, ?, ?)
                """)) {
            for (BlockPlacement placement : placements) {
                statement.setString(1, placement.world());
                statement.setInt(2, placement.x());
                statement.setInt(3, placement.y());
                statement.setInt(4, placement.z());
                statement.setLong(5, buildId);
                statement.setString(6, placement.material());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    public boolean isProtectedBlock(String world, int x, int y, int z) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM protected_blocks WHERE world = ? AND x = ? AND y = ? AND z = ?")) {
            statement.setString(1, world);
            statement.setInt(2, x);
            statement.setInt(3, y);
            statement.setInt(4, z);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
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


    private void validateMoney(double amount) throws SQLException {
        if (amount < 0 || Double.isNaN(amount) || Double.isInfinite(amount)) {
            throw new SQLException("Invalid amount: " + amount);
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


    private TownClaimRecord mapTownClaim(ResultSet rs) throws SQLException {
        return new TownClaimRecord(
                rs.getString("world"),
                rs.getInt("chunk_x"),
                rs.getInt("chunk_z"),
                rs.getLong("town_id"),
                rs.getLong("civ_id"),
                UUID.fromString(rs.getString("claimed_by_uuid")),
                rs.getLong("claimed_at")
        );
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
