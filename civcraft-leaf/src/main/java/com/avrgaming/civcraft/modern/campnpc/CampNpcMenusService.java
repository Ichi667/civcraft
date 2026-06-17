package com.avrgaming.civcraft.modern.campnpc;

import com.avrgaming.civcraft.modern.campnpc.bridge.CivCraftBridge;
import com.avrgaming.civcraft.modern.campnpc.fancy.FancyNpcService;
import com.avrgaming.civcraft.modern.campnpc.gui.GuiListener;
import com.avrgaming.civcraft.modern.campnpc.gui.MenuManager;
import com.avrgaming.civcraft.modern.campnpc.quest.QuestManager;
import com.avrgaming.civcraft.modern.campnpc.quest.QuestProgressListener;
import com.avrgaming.civcraft.modern.campnpc.reward.CampBonusApplier;
import com.avrgaming.civcraft.modern.campnpc.reward.QuestRewardService;
import com.avrgaming.civcraft.modern.campnpc.storage.PluginStorage;
import java.io.File;
import org.bukkit.plugin.java.JavaPlugin;

public final class CampNpcMenusService {
    private final JavaPlugin plugin;
    private CivCraftBridge civCraft;
    private PluginStorage storage;
    private CampBonusApplier bonuses;
    private QuestRewardService rewards;
    private QuestManager quests;
    private MenuManager menus;
    private FancyNpcService npcs;
    private boolean started;

    public CampNpcMenusService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (started) {
            return;
        }
        started = true;
        saveResourceIfMissing("menus/main.yml");
        saveResourceIfMissing("menus/info.yml");
        saveResourceIfMissing("menus/camp.yml");
        saveResourceIfMissing("menus/quests.yml");
        saveResourceIfMissing("quests.yml");

        this.civCraft = new CivCraftBridge(plugin);
        this.storage = new PluginStorage(plugin);
        this.storage.initialize();
        this.bonuses = new CampBonusApplier(plugin, storage, civCraft);
        this.rewards = new QuestRewardService(plugin, storage, civCraft, bonuses);
        this.quests = new QuestManager(plugin, storage, civCraft, rewards);
        this.menus = new MenuManager(plugin, civCraft, quests);
        this.npcs = new FancyNpcService(plugin, civCraft, menus);

        plugin.getServer().getPluginManager().registerEvents(new GuiListener(menus), plugin);
        plugin.getServer().getPluginManager().registerEvents(new QuestProgressListener(quests), plugin);
        bonuses.start();
        quests.reload();
        menus.reload();
        npcs.start();
        plugin.getLogger().info("CivCraft camp NPC menus enabled.");
    }

    public void shutdown() {
        if (npcs != null) {
            npcs.shutdown();
        }
        if (storage != null) {
            storage.close();
        }
    }

    public void reloadEverything() {
        if (!started) {
            start();
            return;
        }
        civCraft.reload();
        quests.reload();
        menus.reload();
        if (bonuses != null) {
            bonuses.applyAllOnline();
        }
        npcs.forceSync();
    }

    public CivCraftBridge civCraft() {
        return civCraft;
    }

    public QuestManager quests() {
        return quests;
    }

    public MenuManager menus() {
        return menus;
    }

    private void saveResourceIfMissing(String path) {
        File file = plugin.getDataFolder().toPath().resolve(path).toFile();
        if (!file.exists()) {
            plugin.saveResource(path, false);
        }
    }
}
