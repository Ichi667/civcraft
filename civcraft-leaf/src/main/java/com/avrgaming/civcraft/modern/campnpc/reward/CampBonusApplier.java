package com.avrgaming.civcraft.modern.campnpc.reward;

import com.avrgaming.civcraft.modern.campnpc.bridge.CivCraftBridge;
import com.avrgaming.civcraft.modern.campnpc.bridge.PlayerCampInfo;
import com.avrgaming.civcraft.modern.campnpc.storage.PluginStorage;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class CampBonusApplier implements Listener {
    private static final double DEFAULT_MAX_HEALTH = 20.0D;
    private static final double DEFAULT_ATTACK_DAMAGE = 1.0D;

    private final JavaPlugin plugin;
    private final PluginStorage storage;
    private final CivCraftBridge civCraft;

    public CampBonusApplier(JavaPlugin plugin, PluginStorage storage, CivCraftBridge civCraft) {
        this.plugin = plugin;
        this.storage = storage;
        this.civCraft = civCraft;
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::applyAllOnline, 40L, 20L * 30L);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> apply(event.getPlayer()), 20L);
    }

    public void applyAllOnline() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            apply(player);
        }
    }

    public void apply(Player player) {
        Optional<PlayerCampInfo> camp = civCraft.getPlayerCamp(player.getUniqueId());
        CampBonuses bonuses = camp.map(info -> bonuses(info.campId())).orElse(new CampBonuses(0D, 0D));
        setBase(player, "MAX_HEALTH", "GENERIC_MAX_HEALTH", DEFAULT_MAX_HEALTH + Math.max(0D, bonuses.healthBonus()));
        setBase(player, "ATTACK_DAMAGE", "GENERIC_ATTACK_DAMAGE", DEFAULT_ATTACK_DAMAGE + Math.max(0D, bonuses.damageBonus()));
        if (player.getHealth() > DEFAULT_MAX_HEALTH + bonuses.healthBonus()) {
            player.setHealth(Math.max(1D, DEFAULT_MAX_HEALTH + bonuses.healthBonus()));
        }
    }

    public CampBonuses bonuses(long campId) {
        try (Connection connection = storage.connect(); PreparedStatement statement = connection.prepareStatement("SELECT health_bonus, damage_bonus FROM camp_npc_bonuses WHERE camp_id = ?")) {
            statement.setLong(1, campId);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return new CampBonuses(rs.getDouble("health_bonus"), rs.getDouble("damage_bonus"));
                }
            }
        } catch (SQLException exception) {
            plugin.getLogger().warning("Unable to load camp bonuses: " + exception.getMessage());
        }
        return new CampBonuses(0D, 0D);
    }

    private void setBase(Player player, String modernAttribute, String legacyAttribute, double value) {
        Attribute attribute = attribute(modernAttribute, legacyAttribute);
        if (attribute == null) {
            return;
        }
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null && Math.abs(instance.getBaseValue() - value) > 0.001D) {
            instance.setBaseValue(value);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Attribute attribute(String modernAttribute, String legacyAttribute) {
        Class enumClass = Attribute.class;
        try {
            return (Attribute) Enum.valueOf(enumClass, modernAttribute);
        } catch (IllegalArgumentException ignored) {
            try {
                return (Attribute) Enum.valueOf(enumClass, legacyAttribute);
            } catch (IllegalArgumentException ignoredAgain) {
                return null;
            }
        }
    }

    public record CampBonuses(double healthBonus, double damageBonus) {
    }
}
