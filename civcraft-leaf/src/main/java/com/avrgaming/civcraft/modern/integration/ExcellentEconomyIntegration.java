package com.avrgaming.civcraft.modern.integration;

public record ExcellentEconomyIntegration(boolean enabledInConfig, boolean pluginPresent, boolean vaultBridgeAllowed, boolean vaultPresent) {
    public IntegrationStatus status() {
        String mode = pluginPresent ? "direct-or-service" : (vaultBridgeAllowed && vaultPresent ? "vault-bridge" : "missing");
        String note = pluginPresent
                ? "предпочитаем прямой API/Service ExcellentEconomy; Vault bridge только как fallback"
                : "нужен ExcellentEconomy или разрешённый Vault bridge";
        return new IntegrationStatus("ExcellentEconomy", enabledInConfig, pluginPresent || (vaultBridgeAllowed && vaultPresent), mode, note);
    }
}
