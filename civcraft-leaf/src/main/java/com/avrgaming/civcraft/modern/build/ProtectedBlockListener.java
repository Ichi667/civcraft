package com.avrgaming.civcraft.modern.build;

import com.avrgaming.civcraft.modern.storage.StorageBootstrap;
import java.sql.SQLException;
import net.kyori.adventure.text.Component;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class ProtectedBlockListener implements Listener {
    private final JavaPlugin plugin;
    private final StorageBootstrap storage;

    public ProtectedBlockListener(JavaPlugin plugin, StorageBootstrap storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getPlayer().hasPermission("civcraft.admin")) {
            return;
        }
        Block block = event.getBlock();
        try {
            if (storage.isProtectedBlock(block.getWorld().getName(), block.getX(), block.getY(), block.getZ())) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(Component.text("Этот блок защищён CivCraft-постройкой."));
            }
        } catch (SQLException exception) {
            plugin.getLogger().warning("Unable to check protected block: " + exception.getMessage());
        }
    }
}
