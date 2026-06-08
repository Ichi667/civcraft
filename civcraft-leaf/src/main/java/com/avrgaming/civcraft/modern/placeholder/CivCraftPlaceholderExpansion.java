package com.avrgaming.civcraft.modern.placeholder;

import com.avrgaming.civcraft.modern.CivCraftModernPlugin;
import com.avrgaming.civcraft.modern.config.ModernCivCraftSettings;
import com.avrgaming.civcraft.modern.domain.CivRecord;
import com.avrgaming.civcraft.modern.domain.ResidentProfile;
import com.avrgaming.civcraft.modern.domain.TownRecord;
import com.avrgaming.civcraft.modern.economy.CivCraftEconomyService;
import com.avrgaming.civcraft.modern.research.ResearchProgress;
import com.avrgaming.civcraft.modern.research.ResearchService;
import com.avrgaming.civcraft.modern.service.CivCraftGameService;
import com.avrgaming.civcraft.modern.service.TownClaimService;
import java.sql.SQLException;
import java.util.Locale;
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

    public CivCraftPlaceholderExpansion(CivCraftModernPlugin plugin, ModernCivCraftSettings settings, CivCraftGameService game, ResearchService research, TownClaimService townClaims, CivCraftEconomyService economy) {
        this.plugin = plugin;
        this.settings = settings;
        this.game = game;
        this.research = research;
        this.townClaims = townClaims;
        this.economy = economy;
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
            return switch (params.toLowerCase()) {
                case "player_name" -> resident.name();
                case "player_coins" -> String.valueOf(Math.round(economy.balance(player)));
                case "player_civ", "civ_name" -> game.civ(resident).map(CivRecord::name).orElse(settings.placeholderEmptyValue());
                case "player_town", "town_name" -> game.town(resident).map(TownRecord::name).orElse(settings.placeholderEmptyValue());
                case "player_camp", "camp_name" -> game.camp(resident).map(camp -> camp.name()).orElse(settings.placeholderEmptyValue());
                case "civ_government" -> game.civ(resident).map(CivRecord::government).orElse(settings.placeholderEmptyValue());
                case "civ_tag" -> game.civ(resident).map(CivRecord::tag).orElse(settings.placeholderEmptyValue());
                case "town_level" -> game.town(resident).map(town -> String.valueOf(town.level())).orElse(settings.placeholderEmptyValue());
                case "town_hammers", "town_hammers_per_hour" -> game.town(resident).map(town -> formatNumber(town.hammersPerHour())).orElse(settings.placeholderEmptyValue());
                case "town_beakers", "town_beakers_per_hour" -> game.town(resident).map(town -> formatNumber(town.beakersPerHour())).orElse(settings.placeholderEmptyValue());
                case "town_money_per_hour" -> game.town(resident).map(town -> formatNumber(town.moneyPerHour())).orElse(settings.placeholderEmptyValue());
                case "town_happiness" -> game.town(resident).map(town -> String.valueOf(town.happiness())).orElse(settings.placeholderEmptyValue());
                case "town_happiness_state" -> game.town(resident).map(TownRecord::happinessState).orElse(settings.placeholderEmptyValue());
                case "town_production_multiplier" -> game.town(resident).map(town -> formatNumber(town.productionMultiplier())).orElse(settings.placeholderEmptyValue());
                case "town_claims" -> resident.townId() == null ? settings.placeholderEmptyValue() : String.valueOf(townClaims.claimCount(player));
                case "camp_hitpoints" -> game.camp(resident).map(camp -> String.valueOf(camp.hitpoints())).orElse(settings.placeholderEmptyValue());
                case "camp_firepoints" -> game.camp(resident).map(camp -> String.valueOf(camp.firepointsHours())).orElse(settings.placeholderEmptyValue());
                case "civ_research" -> research.progress(resident).map(ResearchProgress::techId).orElse(settings.placeholderEmptyValue());
                case "civ_research_progress" -> research.progress(resident).map(progress -> String.valueOf(Math.round(progress.progress()))).orElse(settings.placeholderEmptyValue());
                case "civ_research_percent" -> research.progress(resident).map(progress -> String.valueOf(Math.round(progress.percent()))).orElse(settings.placeholderEmptyValue());
                default -> null;
            };
        } catch (SQLException exception) {
            return settings.placeholderEmptyValue();
        }
    }

    private String formatNumber(double value) {
        if (value == Math.rint(value)) {
            return Long.toString(Math.round(value));
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
