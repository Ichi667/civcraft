package com.avrgaming.civcraft.modern;

import com.avrgaming.civcraft.modern.build.LegacyStructureService;
import com.avrgaming.civcraft.modern.build.ProtectedBlockListener;
import com.avrgaming.civcraft.modern.command.CivCraftCommand;
import com.avrgaming.civcraft.modern.config.ModernCivCraftSettings;
import com.avrgaming.civcraft.modern.economy.CivCraftEconomyService;
import com.avrgaming.civcraft.modern.integration.IntegrationRegistry;
import com.avrgaming.civcraft.modern.placeholder.CivCraftPlaceholderExpansion;
import com.avrgaming.civcraft.modern.research.ResearchService;
import com.avrgaming.civcraft.modern.service.CivCraftGameService;
import com.avrgaming.civcraft.modern.service.ResidentJoinListener;
import com.avrgaming.civcraft.modern.service.TownClaimProtectionListener;
import com.avrgaming.civcraft.modern.service.TownClaimService;
import com.avrgaming.civcraft.modern.storage.StorageBootstrap;
import java.util.List;
import org.bukkit.plugin.java.JavaPlugin;

public final class CivCraftModernPlugin extends JavaPlugin {

    private ModernCivCraftSettings settings;
    private IntegrationRegistry integrations;
    private StorageBootstrap storage;
    private CivCraftEconomyService economy;
    private CivCraftGameService game;
    private LegacyStructureService legacyStructures;
    private ResearchService research;
    private TownClaimService townClaims;
    private CivCraftPlaceholderExpansion placeholderExpansion;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResourceIfMissing("messages.yml");

        reloadModernConfig();
        this.storage = new StorageBootstrap(this, settings);
        storage.initializeLocalStorage();

        this.economy = new CivCraftEconomyService(this, storage, settings);
        this.game = new CivCraftGameService(storage, settings, economy);
        this.legacyStructures = new LegacyStructureService(this, storage, settings, economy);
        this.research = new ResearchService(this, storage, game, settings, economy);
        this.townClaims = new TownClaimService(storage, game, settings, economy);
        getServer().getScheduler().runTaskTimer(this, research::tick, 20L * 60L, 20L * 60L);
        getServer().getPluginManager().registerEvents(new ResidentJoinListener(this, game), this);
        getServer().getPluginManager().registerEvents(new TownClaimProtectionListener(this, townClaims), this);
        getServer().getPluginManager().registerEvents(new ProtectedBlockListener(this, storage), this);

        this.integrations = new IntegrationRegistry(this, settings);
        integrations.detect();

        if (settings.placeholderApiEnabled() && getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            this.placeholderExpansion = new CivCraftPlaceholderExpansion(this, settings, game, research, townClaims, economy);
            placeholderExpansion.register();
        }

        CivCraftCommand executor = new CivCraftCommand(this, game, legacyStructures, research, townClaims, economy);
        for (String commandName : List.of("civcraftmodern", "resident", "camp", "civ", "town", "build", "research")) {
            var command = getCommand(commandName);
            if (command != null) {
                command.setExecutor(executor);
                command.setTabCompleter(executor);
            }
        }

        getLogger().info("CivCraft Leaf 1.21.4 port foundation enabled.");
    }

    @Override
    public void onDisable() {
        if (placeholderExpansion != null) {
            placeholderExpansion.unregister();
        }
        if (storage != null) {
            storage.close();
        }
    }

    public void reloadModernConfig() {
        reloadConfig();
        this.settings = ModernCivCraftSettings.from(getConfig());
    }


    public void reloadIntegrations() {
        this.integrations = new IntegrationRegistry(this, settings);
        integrations.detect();
    }

    public ModernCivCraftSettings settings() {
        return settings;
    }

    public IntegrationRegistry integrations() {
        return integrations;
    }

    private void saveResourceIfMissing(String path) {
        if (!getDataFolder().toPath().resolve(path).toFile().exists()) {
            saveResource(path, false);
        }
    }
}
