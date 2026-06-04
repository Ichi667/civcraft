package com.avrgaming.civcraft.modern.service;

import com.avrgaming.civcraft.modern.config.ModernCivCraftSettings;
import com.avrgaming.civcraft.modern.domain.CampRecord;
import com.avrgaming.civcraft.modern.domain.CivRecord;
import com.avrgaming.civcraft.modern.domain.ResidentProfile;
import com.avrgaming.civcraft.modern.domain.TownRecord;
import com.avrgaming.civcraft.modern.storage.StorageBootstrap;
import java.sql.SQLException;
import java.util.Optional;
import org.bukkit.entity.Player;

public final class CivCraftGameService {
    private final StorageBootstrap storage;
    private ModernCivCraftSettings settings;

    public CivCraftGameService(StorageBootstrap storage, ModernCivCraftSettings settings) {
        this.storage = storage;
        this.settings = settings;
    }

    public void updateSettings(ModernCivCraftSettings settings) {
        this.settings = settings;
    }

    public ResidentProfile resident(Player player) throws SQLException {
        return storage.getOrCreateResident(player.getUniqueId(), player.getName(), settings.startingCoins());
    }

    public Optional<CivRecord> civ(ResidentProfile resident) throws SQLException {
        return resident.civId() == null ? Optional.empty() : storage.findCiv(resident.civId());
    }

    public Optional<TownRecord> town(ResidentProfile resident) throws SQLException {
        return resident.townId() == null ? Optional.empty() : storage.findTown(resident.townId());
    }

    public Optional<CampRecord> camp(ResidentProfile resident) throws SQLException {
        return storage.findCampByOwner(resident.uuid());
    }

    public CampRecord createCamp(Player player, String name) throws SQLException, IllegalArgumentException {
        if (!Validation.isValidName(name)) {
            throw new IllegalArgumentException("Название лагеря должно быть от 3 до 32 символов и без спецсимволов.");
        }
        ResidentProfile resident = resident(player);
        if (resident.campId() != null || storage.findCampByOwner(player.getUniqueId()).isPresent()) {
            throw new IllegalArgumentException("У вас уже есть лагерь.");
        }
        return storage.createCamp(player.getUniqueId(), name, player.getLocation(), settings.campCost(), settings.campHitpoints(), settings.campFirepointsHours());
    }

    public CivRecord createCivilization(Player player, String name) throws SQLException, IllegalArgumentException {
        if (!Validation.isValidName(name)) {
            throw new IllegalArgumentException("Название цивилизации должно быть от 3 до 32 символов и без спецсимволов.");
        }
        ResidentProfile resident = resident(player);
        if (resident.civId() != null) {
            throw new IllegalArgumentException("Вы уже состоите в цивилизации.");
        }
        return storage.createCivilization(player.getUniqueId(), name, settings.civCost(), settings.startingGovernment());
    }

    public TownRecord createTown(Player player, String name) throws SQLException, IllegalArgumentException {
        if (!Validation.isValidName(name)) {
            throw new IllegalArgumentException("Название города должно быть от 3 до 32 символов и без спецсимволов.");
        }
        ResidentProfile resident = resident(player);
        if (resident.civId() == null) {
            throw new IllegalArgumentException("Сначала создайте или вступите в цивилизацию.");
        }
        if (resident.townId() != null) {
            throw new IllegalArgumentException("У вас уже есть город.");
        }
        if (storage.hasTownNear(player.getLocation(), settings.minTownDistance())) {
            throw new IllegalArgumentException("Слишком близко к другому городу. Минимальная дистанция: " + settings.minTownDistance());
        }
        return storage.createTown(player.getUniqueId(), resident.civId(), name, player.getLocation(), settings.townCost());
    }
}
