package com.avrgaming.civcraft.modern.domain;

import java.util.UUID;

public record ResidentProfile(UUID uuid, String name, double coins, Long civId, Long townId, Long campId) {
}
