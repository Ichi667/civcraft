package com.avrgaming.civcraft.modern.domain;

import java.util.UUID;

public record CivRecord(long id, String name, UUID leaderUuid, double coins, String government) {
}
