package com.avrgaming.civcraft.modern.build;

public record StructureDefinition(
        String id,
        String template,
        String displayName,
        double cost,
        double upkeep,
        double hammerCost,
        int maxHitpoints,
        String requiredTechnology,
        boolean strategic
) {
}
