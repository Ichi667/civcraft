package com.avrgaming.civcraft.modern.build;

import com.avrgaming.civcraft.modern.config.ModernCivCraftSettings;
import com.avrgaming.civcraft.modern.economy.CivCraftEconomyService;
import com.avrgaming.civcraft.modern.storage.StorageBootstrap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class LegacyStructureService {
    private final JavaPlugin plugin;
    private final StorageBootstrap storage;
    private final CivCraftEconomyService economy;
    private final LegacyDefTemplateLoader loader = new LegacyDefTemplateLoader();
    private final StructureDefinitionService definitions;
    private final BuildQueue buildQueue;
    private ModernCivCraftSettings settings;

    public LegacyStructureService(JavaPlugin plugin, StorageBootstrap storage, ModernCivCraftSettings settings, CivCraftEconomyService economy) {
        this.plugin = plugin;
        this.storage = storage;
        this.economy = economy;
        this.settings = settings;
        this.definitions = new StructureDefinitionService(plugin, settings);
        this.buildQueue = new BuildQueue(plugin, settings.maxStructureBlocksPerTick());
    }

    public void updateSettings(ModernCivCraftSettings settings) {
        this.settings = settings;
        this.definitions.updateSettings(settings);
        this.buildQueue.updateBudget(settings.maxStructureBlocksPerTick());
    }

    public List<StructureDefinition> listDefinitions(int limit) {
        return definitions.list(limit);
    }

    public List<String> listTemplates(String type, int limit) throws IOException {
        Path root = templateRoot().resolve("themes").resolve(settings.legacyDefaultTheme()).resolve(type);
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (var stream = Files.list(root)) {
            return stream.filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .sorted()
                    .limit(limit)
                    .toList();
        }
    }

    public QueuedLegacyBuild paste(Player player, String type, String templateName, String direction) throws IOException, SQLException {
        if (!settings.legacyTemplatesEnabled()) {
            throw new IllegalArgumentException("Legacy .def templates are disabled in config.");
        }
        String cleanType = normalize(type);
        String cleanTemplate = normalize(templateName);
        String cleanDirection = normalize(direction == null || direction.isBlank() ? settings.legacyDefaultDirection() : direction);
        Path templatePath = resolveTemplate(cleanType, cleanTemplate, cleanDirection);
        LegacyDefTemplate template = loader.load(templatePath);
        Location origin = player.getLocation().getBlock().getLocation();
        long buildId = storage.recordStructureBuild(player.getUniqueId(), cleanType, cleanTemplate, cleanDirection, origin, templatePath.toString(), template.nonAirBlocks(), null, cleanTemplate, 0.0, 1);
        int queuedBlocks = buildQueue.paste(origin, template, settings.legacyIncludeAir(), placements -> {
            try {
                storage.recordProtectedBlocks(buildId, placements);
                storage.completeStructureBuild(buildId);
            } catch (SQLException exception) {
                plugin.getLogger().warning("Unable to mark structure build " + buildId + " complete: " + exception.getMessage());
            }
        });
        return new QueuedLegacyBuild(buildId, templatePath, queuedBlocks, template.sizeX(), template.sizeY(), template.sizeZ());
    }


    public QueuedLegacyBuild buildStructure(Player player, String structureId, String direction) throws IOException, SQLException {
        StructureDefinition definition = definitions.find(structureId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown structure id: " + structureId));
        String cleanDirection = normalize(direction == null || direction.isBlank() ? settings.legacyDefaultDirection() : direction);
        Path templatePath = resolveTemplate("structures", normalize(definition.template()), cleanDirection);
        LegacyDefTemplate template = loader.load(templatePath);
        Location origin = player.getLocation().getBlock().getLocation();
        economy.withdraw(player, definition.cost(), "structure build " + definition.id());
        long buildId;
        try {
            buildId = storage.recordStructureBuild(
                player.getUniqueId(),
                "structures",
                definition.template(),
                cleanDirection,
                origin,
                templatePath.toString(),
                template.nonAirBlocks(),
                definition.id(),
                definition.displayName(),
                    definition.cost(),
                    definition.maxHitpoints()
            );
        } catch (SQLException | RuntimeException exception) {
            economy.deposit(player, definition.cost(), "structure build refund " + definition.id());
            throw exception;
        }
        int queuedBlocks = buildQueue.paste(origin, template, settings.legacyIncludeAir(), placements -> {
            try {
                storage.recordProtectedBlocks(buildId, placements);
                storage.completeStructureBuild(buildId);
            } catch (SQLException exception) {
                plugin.getLogger().warning("Unable to complete structure build " + buildId + ": " + exception.getMessage());
            }
        });
        return new QueuedLegacyBuild(buildId, templatePath, queuedBlocks, template.sizeX(), template.sizeY(), template.sizeZ());
    }

    private Path resolveTemplate(String type, String templateName, String direction) throws IOException {
        Path base = templateRoot().resolve("themes").resolve(settings.legacyDefaultTheme()).resolve(type).resolve(templateName);
        Path directed = base.resolve(templateName + "_" + direction + ".def");
        if (Files.isRegularFile(directed)) {
            return directed;
        }
        Path undirected = base.resolve(templateName + ".def");
        if (Files.isRegularFile(undirected)) {
            return undirected;
        }
        try (var stream = Files.walk(templateRoot(), 6)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equalsIgnoreCase(templateName + "_" + direction + ".def") || path.getFileName().toString().equalsIgnoreCase(templateName + ".def"))
                    .min(Comparator.comparing(Path::toString))
                    .orElseThrow(() -> new IOException("Legacy template not found: " + templateName + " direction=" + direction));
        }
    }

    private Path templateRoot() {
        return Path.of(settings.legacyTemplateRoot()).toAbsolutePath().normalize();
    }

    private String normalize(String input) {
        String value = input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_/-]", "");
        if (value.contains("..") || value.isBlank()) {
            throw new IllegalArgumentException("Invalid template name: " + input);
        }
        return value;
    }
}
