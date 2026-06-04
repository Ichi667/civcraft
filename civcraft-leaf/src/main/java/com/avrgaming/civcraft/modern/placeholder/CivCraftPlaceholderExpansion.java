package com.avrgaming.civcraft.modern.placeholder;

import com.avrgaming.civcraft.modern.CivCraftModernPlugin;
import com.avrgaming.civcraft.modern.config.ModernCivCraftSettings;
import com.avrgaming.civcraft.modern.domain.CivRecord;
import com.avrgaming.civcraft.modern.domain.ResidentProfile;
import com.avrgaming.civcraft.modern.domain.TownRecord;
import com.avrgaming.civcraft.modern.service.CivCraftGameService;
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

    public CivCraftPlaceholderExpansion(CivCraftModernPlugin plugin, ModernCivCraftSettings settings, CivCraftGameService game) {
        this.plugin = plugin;
        this.settings = settings;
        this.game = game;
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
                case "player_coins" -> String.valueOf(resident.coins());
                case "player_civ", "civ_name" -> game.civ(resident).map(CivRecord::name).orElse(settings.placeholderEmptyValue());
                case "player_town", "town_name" -> game.town(resident).map(TownRecord::name).orElse(settings.placeholderEmptyValue());
                case "player_camp", "camp_name" -> game.camp(resident).map(camp -> camp.name()).orElse(settings.placeholderEmptyValue());
                case "civ_government" -> game.civ(resident).map(CivRecord::government).orElse(settings.placeholderEmptyValue());
                case "town_level" -> game.town(resident).map(town -> String.valueOf(town.level())).orElse(settings.placeholderEmptyValue());
                case "camp_hitpoints" -> game.camp(resident).map(camp -> String.valueOf(camp.hitpoints())).orElse(settings.placeholderEmptyValue());
                case "camp_firepoints" -> game.camp(resident).map(camp -> String.valueOf(camp.firepointsHours())).orElse(settings.placeholderEmptyValue());
                default -> null;
            };
        } catch (SQLException exception) {
            return settings.placeholderEmptyValue();
        }
    }
}
