package com.avrgaming.civcraft.modern.campnpc.reward;

import com.avrgaming.civcraft.modern.campnpc.bridge.CivCraftBridge;
import com.avrgaming.civcraft.modern.campnpc.quest.QuestDefinition;
import com.avrgaming.civcraft.modern.campnpc.quest.QuestReward;
import com.avrgaming.civcraft.modern.campnpc.storage.PluginStorage;
import com.avrgaming.civcraft.modern.campnpc.util.ItemsAdderHook;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Chest;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

public final class QuestRewardService {
    private final JavaPlugin plugin;
    private final PluginStorage storage;
    private final CivCraftBridge civCraft;
    private final CampBonusApplier bonusApplier;
    private final ItemsAdderHook itemsAdder = new ItemsAdderHook();
    private final MmoItemsHook mmoItems = new MmoItemsHook();

    public QuestRewardService(JavaPlugin plugin, PluginStorage storage, CivCraftBridge civCraft, CampBonusApplier bonusApplier) {
        this.plugin = plugin;
        this.storage = storage;
        this.civCraft = civCraft;
        this.bonusApplier = bonusApplier;
    }

    public void give(Player completer, long campId, QuestDefinition quest) {
        for (QuestReward reward : quest.rewards()) {
            applyReward(completer, campId, quest, reward);
        }
    }

    private void applyReward(Player completer, long campId, QuestDefinition quest, QuestReward reward) {
        String type = reward.type().trim().toUpperCase(Locale.ROOT);
        switch (type) {
            case "MONEY", "COINS", "COIN" -> civCraft.addCampLeaderCoins(campId, Math.round(reward.amount()));
            case "CAMP_EXPERIENCE", "CAMP_XP", "EXPERIENCE", "XP" -> civCraft.addCampExperience(campId, (int) Math.round(reward.amount()));
            case "CONTROL_HP", "CONTROL_BLOCK_HP" -> civCraft.addCampControlHp(campId, (int) Math.round(reward.amount()));
            case "UPGRADE", "CAMP_UPGRADE" -> civCraft.setCampUpgrade(campId, reward.value(), true);
            case "HEALTH_BONUS", "CAMP_HEALTH" -> addBonus(campId, "health_bonus", reward.amount());
            case "DAMAGE_BONUS", "CAMP_DAMAGE" -> addBonus(campId, "damage_bonus", reward.amount());
            case "ITEM", "VANILLA_ITEM", "ITEMSADDER", "MMOITEMS" -> giveRewardItem(campId, reward.item(), reward.amount(), type);
            case "COMMAND", "COMMANDS" -> runCommands(reward.commands(), completer, campId, quest);
            default -> {
                if (!reward.commands().isEmpty()) {
                    runCommands(reward.commands(), completer, campId, quest);
                }
            }
        }
    }

    private void addBonus(long campId, String column, double amount) {
        try (Connection connection = storage.connect(); PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO camp_npc_bonuses(camp_id, health_bonus, damage_bonus)
                VALUES(?, ?, ?)
                ON CONFLICT(camp_id) DO UPDATE SET
                    health_bonus = health_bonus + excluded.health_bonus,
                    damage_bonus = damage_bonus + excluded.damage_bonus
                """)) {
            statement.setLong(1, campId);
            statement.setDouble(2, column.equals("health_bonus") ? amount : 0D);
            statement.setDouble(3, column.equals("damage_bonus") ? amount : 0D);
            statement.executeUpdate();
            bonusApplier.applyAllOnline();
        } catch (SQLException exception) {
            plugin.getLogger().warning("Unable to add camp bonus: " + exception.getMessage());
        }
    }

    private void giveRewardItem(long campId, ConfigurationSection itemSection, double fallbackAmount, String rewardType) {
        ItemStack item = createRewardItem(itemSection, rewardType);
        if (item == null || item.getType().isAir()) {
            return;
        }
        int amount = Math.max(1, itemSection.getInt("amount", fallbackAmount > 0D ? (int) Math.round(fallbackAmount) : 1));
        List<ItemStack> stacks = new ArrayList<>();
        int left = amount;
        while (left > 0) {
            ItemStack stack = item.clone();
            int take = Math.min(stack.getMaxStackSize(), left);
            stack.setAmount(take);
            stacks.add(stack);
            left -= take;
        }
        deliverToCamp(campId, stacks);
    }

    private ItemStack createRewardItem(ConfigurationSection section, String rewardType) {
        String mmoType = section.getString("mmoitems-type", section.getString("mmoitems.type", section.getString("mmo-type", null)));
        String mmoId = section.getString("mmoitems-id", section.getString("mmoitems.id", section.getString("mmo-id", null)));
        if ("MMOITEMS".equals(rewardType) || (mmoType != null && mmoId != null)) {
            Optional<ItemStack> item = mmoItems.create(mmoType, mmoId);
            if (item.isPresent()) {
                return item.get();
            }
        }

        String itemsAdderId = section.getString("itemsadder", null);
        if ("ITEMSADDER".equals(rewardType) || (itemsAdderId != null && !itemsAdderId.isBlank())) {
            Optional<ItemStack> item = itemsAdder.create(itemsAdderId);
            if (item.isPresent()) {
                return item.get();
            }
        }

        Material material = Material.matchMaterial(section.getString("material", "STONE").toUpperCase(Locale.ROOT));
        ItemStack item = new ItemStack(material == null ? Material.STONE : material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null && section.contains("custom-model-data")) {
            meta.setCustomModelData(section.getInt("custom-model-data"));
            item.setItemMeta(meta);
        }
        return item;
    }

    private void deliverToCamp(long campId, List<ItemStack> items) {
        List<ItemStack> leftovers = new ArrayList<>(items);
        Optional<Location> chestLocation = civCraft.getCampFoodChestLocation(campId);
        if (chestLocation.isPresent() && chestLocation.get().getBlock().getState() instanceof Chest chest) {
            leftovers = addItems(chest.getInventory(), leftovers);
        }
        if (leftovers.isEmpty()) {
            return;
        }
        UUID leaderUuid = civCraft.getCampLeaderUuid(campId).orElse(null);
        Player leader = leaderUuid == null ? null : Bukkit.getPlayer(leaderUuid);
        if (leader != null) {
            leftovers = addItems(leader.getInventory(), leftovers);
        }
        if (!leftovers.isEmpty()) {
            Location drop = chestLocation.orElse(leader == null ? null : leader.getLocation());
            if (drop != null && drop.getWorld() != null) {
                for (ItemStack item : leftovers) {
                    drop.getWorld().dropItemNaturally(drop, item);
                }
            }
        }
    }

    private List<ItemStack> addItems(Inventory inventory, List<ItemStack> items) {
        Map<Integer, ItemStack> overflow = new HashMap<>();
        for (ItemStack item : items) {
            overflow.putAll(inventory.addItem(item.clone()));
        }
        return new ArrayList<>(overflow.values());
    }

    private void runCommands(List<String> commands, Player player, long campId, QuestDefinition quest) {
        for (String command : commands) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), replacePlaceholders(command, player, campId, quest));
        }
    }

    private String replacePlaceholders(String command, Player player, long campId, QuestDefinition quest) {
        return command
                .replace("{player}", player.getName())
                .replace("{uuid}", player.getUniqueId().toString())
                .replace("{camp_id}", String.valueOf(campId))
                .replace("{quest_id}", quest == null ? "" : quest.id())
                .replace("{quest_title}", quest == null ? "" : quest.title());
    }
}
