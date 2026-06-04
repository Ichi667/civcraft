package com.avrgaming.civcraft.modern.service;

import java.sql.SQLException;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class ResidentJoinListener implements Listener {
    private final JavaPlugin plugin;
    private final CivCraftGameService game;

    public ResidentJoinListener(JavaPlugin plugin, CivCraftGameService game) {
        this.plugin = plugin;
        this.game = game;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        try {
            game.resident(event.getPlayer());
        } catch (SQLException exception) {
            plugin.getLogger().warning("Unable to create/load resident for " + event.getPlayer().getName() + ": " + exception.getMessage());
        }
    }
}
