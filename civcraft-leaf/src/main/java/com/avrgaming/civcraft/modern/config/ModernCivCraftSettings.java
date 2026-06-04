package com.avrgaming.civcraft.modern.config;

import java.nio.file.Path;
import org.bukkit.configuration.file.FileConfiguration;

public record ModernCivCraftSettings(
        String targetCore,
        boolean requireLeafRuntime,
        boolean allowPaperCompatibleDevRuntime,
        String schedulerStrategy,
        boolean debug,
        double startingCoins,
        double campCost,
        int campHitpoints,
        int campFirepointsHours,
        double civCost,
        String startingGovernment,
        double townCost,
        double minTownDistance,
        String storageType,
        String sqliteFile,
        boolean mythicMobsEnabled,
        boolean mmoItemsEnabled,
        boolean dynmapEnabled,
        boolean placeholderApiEnabled,
        boolean packetEventsEnabled,
        boolean typewriterEnabled,
        String economyProvider,
        boolean useVaultBridgeForEconomy,
        String placeholderIdentifier,
        String placeholderEmptyValue,
        int maxStructureBlocksPerTick,
        int maxDatabaseWritesPerTick
) {
    public static ModernCivCraftSettings from(FileConfiguration config) {
        return new ModernCivCraftSettings(
                config.getString("server.target-core", "Leaf"),
                config.getBoolean("server.require-leaf-runtime", true),
                config.getBoolean("server.allow-paper-compatible-dev-runtime", true),
                config.getString("server.scheduler-strategy", "bukkit-main-thread"),
                config.getBoolean("global.debug", false),
                config.getDouble("global.starting-coins", 250.0),
                config.getDouble("camp.cost", 2500.0),
                config.getInt("camp.hitpoints", 5000),
                config.getInt("camp.firepoints-hours", 24),
                config.getDouble("civ.cost", 0.0),
                config.getString("civ.starting-government", "Tribalism"),
                config.getDouble("town.cost", 10000.0),
                config.getDouble("town.min-town-distance", 150.0),
                config.getString("storage.type", "sqlite"),
                config.getString("storage.sqlite.file", "civcraft.db"),
                config.getBoolean("integrations.mythicmobs.enabled", true),
                config.getBoolean("integrations.mmoitems.enabled", true),
                config.getBoolean("integrations.dynmap.enabled", true),
                config.getBoolean("integrations.placeholderapi.enabled", true),
                config.getBoolean("integrations.packetevents.enabled", false),
                config.getBoolean("integrations.typewriter.enabled", false),
                config.getString("integrations.economy.provider", "ExcellentEconomy"),
                config.getBoolean("integrations.economy.use-vault-bridge-if-direct-api-unavailable", true),
                config.getString("integrations.placeholderapi.identifier", "civcraft"),
                config.getString("placeholders.empty-value", "-"),
                config.getInt("global.tick-budgets.max-structure-blocks-per-tick", 512),
                config.getInt("global.tick-budgets.max-database-writes-per-tick", 256)
        );
    }

    public Path sqlitePath(Path dataFolder) {
        return dataFolder.resolve(sqliteFile).normalize();
    }
}
