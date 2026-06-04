package com.avrgaming.civcraft.modern.build;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.function.Consumer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public final class BuildQueue {
    private final JavaPlugin plugin;
    private int maxBlocksPerTick;

    public BuildQueue(JavaPlugin plugin, int maxBlocksPerTick) {
        this.plugin = plugin;
        this.maxBlocksPerTick = maxBlocksPerTick;
    }

    public void updateBudget(int maxBlocksPerTick) {
        this.maxBlocksPerTick = Math.max(1, maxBlocksPerTick);
    }

    public int paste(Location origin, LegacyDefTemplate template, boolean includeAir, Consumer<List<BlockPlacement>> onComplete) {
        Queue<LegacyBlock> queue = new ArrayDeque<>();
        List<BlockPlacement> placements = new ArrayList<>(template.nonAirBlocks());
        int queued = 0;
        for (LegacyBlock block : template.blocks()) {
            if (!includeAir && block.isAir()) {
                continue;
            }
            queue.add(block);
            queued++;
        }
        new BukkitRunnable() {
            @Override
            public void run() {
                int placed = 0;
                while (placed < maxBlocksPerTick && !queue.isEmpty()) {
                    BlockPlacement placement = place(origin, queue.poll());
                    if (placement != null) {
                        placements.add(placement);
                    }
                    placed++;
                }
                if (queue.isEmpty()) {
                    cancel();
                    onComplete.accept(List.copyOf(placements));
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return queued;
    }

    private BlockPlacement place(Location origin, LegacyBlock legacyBlock) {
        Material material = LegacyMaterialMapper.map(legacyBlock.legacyId(), legacyBlock.legacyData());
        Block block = origin.getWorld().getBlockAt(
                origin.getBlockX() + legacyBlock.x(),
                origin.getBlockY() + legacyBlock.y(),
                origin.getBlockZ() + legacyBlock.z()
        );
        block.setType(material, false);
        if (!legacyBlock.signLines().isEmpty() && block.getState() instanceof Sign sign) {
            for (int i = 0; i < Math.min(4, legacyBlock.signLines().size()); i++) {
                sign.line(i, net.kyori.adventure.text.Component.text(legacyBlock.signLines().get(i)));
            }
            sign.update(true, false);
        }
        if (material.isAir()) {
            return null;
        }
        return new BlockPlacement(block.getWorld().getName(), block.getX(), block.getY(), block.getZ(), material.name());
    }
}
