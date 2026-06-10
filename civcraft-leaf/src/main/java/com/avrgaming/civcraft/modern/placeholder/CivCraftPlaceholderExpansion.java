package com.avrgaming.civcraft.modern.placeholder;

import com.avrgaming.civcraft.modern.CivCraftModernPlugin;
import com.avrgaming.civcraft.modern.camp.CampService;
import com.avrgaming.civcraft.modern.config.ModernCivCraftSettings;
import com.avrgaming.civcraft.modern.domain.CivRecord;
import com.avrgaming.civcraft.modern.economy.CivCraftEconomyService;
import com.avrgaming.civcraft.modern.domain.ResidentProfile;
import com.avrgaming.civcraft.modern.domain.TownRecord;
import com.avrgaming.civcraft.modern.research.ResearchProgress;
import com.avrgaming.civcraft.modern.research.ResearchService;
import com.avrgaming.civcraft.modern.service.CivCraftGameService;
import com.avrgaming.civcraft.modern.service.TownClaimService;
import java.sql.SQLException;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class CivCraftPlaceholderExpansion extends PlaceholderExpansion {
    private final CivCraftModernPlugin plugin;
    private final ModernCivCraftSettings settings;
    private final CivCraftGameService game;
    private final ResearchService research;
    private final TownClaimService townClaims;
    private final CivCraftEconomyService economy;
    private final CampService camps;

    public CivCraftPlaceholderExpansion(CivCraftModernPlugin plugin, ModernCivCraftSettings settings, CivCraftGameService game, ResearchService research, TownClaimService townClaims, CivCraftEconomyService economy, CampService camps) {
        this.plugin = plugin;
        this.settings = settings;
        this.game = game;
        this.research = research;
        this.townClaims = townClaims;
        this.economy = economy;
        this.camps = camps;
    }

    @Override
    public @NotNull String getIdentifier() {
        return settings.placeholderIdentifier();
    }

    @Override
    public @NotNull String getAuthor() {
        return "CivCraft Port Team";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer offlinePlayer, @NotNull String params) {
        if (!(offlinePlayer instanceof Player player)) {
            return settings.placeholderEmptyValue();
        }
        try {
            ResidentProfile resident = game.resident(player);
            String key = params.toLowerCase();
            if (key.startsWith("camp_")) {
                return camps.placeholderInfo(player.getUniqueId()).map(info -> switch (key) {
                    case "camp_name" -> info.name();
                    case "camp_leader" -> info.leaderName();
                    case "camp_level" -> String.valueOf(info.level());
                    case "camp_experience", "camp_xp" -> String.valueOf(info.experience());
                    case "camp_next_experience", "camp_next_xp" -> String.valueOf(info.nextLevelExperience());
                    default -> settings.placeholderEmptyValue();
                }).orElse(settings.placeholderEmptyValue());
            }
            return switch (key) {
                case "player_name" -> resident.name();
                case "player_coins" -> String.valueOf(Math.round(economy.balance(player)));
                case "player_civ", "civ_name" -> game.civ(resident).map(CivRecord::name).orElse(settings.placeholderEmptyValue());
                case "player_town", "town_name" -> game.town(resident).map(TownRecord::name).orElse(settings.placeholderEmptyValue());
                case "player_camp" -> camps.placeholderInfo(player.getUniqueId()).map(CampService.CampPlaceholderInfo::name).orElse(settings.placeholderEmptyValue());
                case "civ_government" -> game.civ(resident).map(CivRecord::government).orElse(settings.placeholderEmptyValue());
                case "town_level" -> game.town(resident).map(town -> String.valueOf(town.level())).orElse(settings.placeholderEmptyValue());
                case "town_claims" -> resident.townId() == null ? settings.placeholderEmptyValue() : String.valueOf(townClaims.claimCount(player));
                case "civ_research" -> research.progress(resident).map(ResearchProgress::techId).orElse(settings.placeholderEmptyValue());
                case "civ_research_progress" -> research.progress(resident).map(progress -> String.valueOf(Math.round(progress.progress()))).orElse(settings.placeholderEmptyValue());
                case "civ_research_percent" -> research.progress(resident).map(progress -> String.valueOf(Math.round(progress.percent()))).orElse(settings.placeholderEmptyValue());
                default -> null;
            };
        } catch (SQLException exception) {
            return settings.placeholderEmptyValue();
        }
    }
}
