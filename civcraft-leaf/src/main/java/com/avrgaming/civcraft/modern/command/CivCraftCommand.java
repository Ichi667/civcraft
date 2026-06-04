package com.avrgaming.civcraft.modern.command;

import com.avrgaming.civcraft.modern.CivCraftModernPlugin;
import com.avrgaming.civcraft.modern.build.LegacyStructureService;
import com.avrgaming.civcraft.modern.build.QueuedLegacyBuild;
import com.avrgaming.civcraft.modern.build.StructureDefinition;
import com.avrgaming.civcraft.modern.domain.CampRecord;
import com.avrgaming.civcraft.modern.economy.CivCraftEconomyService;
import com.avrgaming.civcraft.modern.domain.CivRecord;
import com.avrgaming.civcraft.modern.domain.ResidentProfile;
import com.avrgaming.civcraft.modern.domain.TownClaimRecord;
import com.avrgaming.civcraft.modern.domain.TownRecord;
import com.avrgaming.civcraft.modern.research.ResearchService;
import com.avrgaming.civcraft.modern.research.TechDefinition;
import com.avrgaming.civcraft.modern.service.CivCraftGameService;
import com.avrgaming.civcraft.modern.service.TownClaimService;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class CivCraftCommand implements CommandExecutor, TabCompleter {
    private final CivCraftModernPlugin plugin;
    private final CivCraftGameService game;
    private final LegacyStructureService legacyStructures;
    private final ResearchService research;
    private final TownClaimService townClaims;
    private final CivCraftEconomyService economy;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public CivCraftCommand(CivCraftModernPlugin plugin, CivCraftGameService game, LegacyStructureService legacyStructures, ResearchService research, TownClaimService townClaims, CivCraftEconomyService economy) {
        this.plugin = plugin;
        this.game = game;
        this.legacyStructures = legacyStructures;
        this.research = research;
        this.townClaims = townClaims;
        this.economy = economy;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        String primary = label.toLowerCase(Locale.ROOT);
        String[] effectiveArgs = args;
        if (List.of("resident", "res", "camp", "civ", "town", "t", "build", "research", "tech").contains(primary)) {
            String category = switch (primary) {
                case "res" -> "resident";
                case "t" -> "town";
                case "tech" -> "research";
                default -> primary;
            };
            effectiveArgs = new String[args.length + 1];
            effectiveArgs[0] = category;
            System.arraycopy(args, 0, effectiveArgs, 1, args.length);
        }

        if (effectiveArgs.length == 0) {
            help(sender, label);
            return true;
        }
        try {
            switch (effectiveArgs[0].toLowerCase(Locale.ROOT)) {
                case "reload" -> {
                    if (!sender.hasPermission("civcraft.admin")) {
                        error(sender, "Нет прав.");
                        return true;
                    }
                    plugin.reloadModernConfig();
                    economy.reload(plugin.settings());
                    game.updateSettings(plugin.settings());
                    legacyStructures.updateSettings(plugin.settings());
                    research.updateSettings(plugin.settings());
                    townClaims.updateSettings(plugin.settings());
                    plugin.reloadIntegrations();
                    success(sender, "Конфигурация перезагружена.");
                    return true;
                }
                case "integrations" -> {
                    sender.sendMessage(miniMessage.deserialize("<yellow>Интеграции CivCraft:</yellow>"));
                    plugin.integrations().statusLines().forEach(line -> sender.sendMessage(miniMessage.deserialize(line)));
                    return true;
                }
                case "resident" -> {
                    Player player = requirePlayer(sender);
                    showResident(player);
                    return true;
                }
                case "camp" -> {
                    Player player = requirePlayer(sender);
                    handleCamp(player, effectiveArgs);
                    return true;
                }
                case "civ" -> {
                    Player player = requirePlayer(sender);
                    handleCiv(player, effectiveArgs);
                    return true;
                }
                case "town" -> {
                    Player player = requirePlayer(sender);
                    handleTown(player, effectiveArgs);
                    return true;
                }
                case "build" -> {
                    Player player = requirePlayer(sender);
                    handleBuild(player, effectiveArgs);
                    return true;
                }
                case "research" -> {
                    Player player = requirePlayer(sender);
                    handleResearch(player, effectiveArgs);
                    return true;
                }
                default -> {
                    help(sender, label);
                    return true;
                }
            }
        } catch (IllegalArgumentException exception) {
            error(sender, exception.getMessage());
        } catch (SQLException exception) {
            plugin.getLogger().warning("CivCraft command failed: " + exception.getMessage());
            error(sender, "Ошибка базы данных: " + exception.getMessage());
        } catch (IOException exception) {
            plugin.getLogger().warning("CivCraft legacy build failed: " + exception.getMessage());
            error(sender, "Ошибка legacy-шаблона: " + exception.getMessage());
        }
        return true;
    }

    private void handleCamp(Player player, String[] args) throws SQLException {
        if (args.length >= 3 && args[1].equalsIgnoreCase("create")) {
            CampRecord camp = game.createCamp(player, joinName(args, 2));
            success(player, "Лагерь <yellow>" + camp.name() + "</yellow> создан. HP: " + camp.hitpoints() + ", firepoints: " + camp.firepointsHours());
            return;
        }
        game.camp(game.resident(player)).ifPresentOrElse(
                camp -> player.sendMessage(miniMessage.deserialize("<gold>Лагерь:</gold> <yellow>" + camp.name() + "</yellow> <gray>(" + camp.world() + " " + camp.x() + " " + camp.y() + " " + camp.z() + ")</gray>")),
                () -> error(player, "У вас нет лагеря. Используйте /civcraftmodern camp create <название>.")
        );
    }

    private void handleCiv(Player player, String[] args) throws SQLException {
        if (args.length >= 3 && args[1].equalsIgnoreCase("create")) {
            CivRecord civ = game.createCivilization(player, joinName(args, 2));
            success(player, "Цивилизация <yellow>" + civ.name() + "</yellow> создана. Правительство: " + civ.government());
            return;
        }
        if (args.length >= 3 && args[1].equalsIgnoreCase("deposit")) {
            CivRecord civ = game.depositCiv(player, parseAmount(args[2]));
            success(player, "Баланс цивилизации: <yellow>" + civ.coins() + "</yellow>.");
            return;
        }
        if (args.length >= 3 && args[1].equalsIgnoreCase("withdraw")) {
            CivRecord civ = game.withdrawCiv(player, parseAmount(args[2]));
            success(player, "Баланс цивилизации: <yellow>" + civ.coins() + "</yellow>.");
            return;
        }
        ResidentProfile resident = game.resident(player);
        game.civ(resident).ifPresentOrElse(
                civ -> player.sendMessage(miniMessage.deserialize("<gold>Цивилизация:</gold> <yellow>" + civ.name() + "</yellow> <gray>government=" + civ.government() + ", coins=" + civ.coins() + "</gray>")),
                () -> error(player, "Вы не состоите в цивилизации. Используйте /civcraftmodern civ create <название>.")
        );
    }

    private void handleTown(Player player, String[] args) throws SQLException {
        if (args.length >= 3 && args[1].equalsIgnoreCase("create")) {
            TownRecord town = game.createTown(player, joinName(args, 2));
            success(player, "Город <yellow>" + town.name() + "</yellow> основан. Уровень: " + town.level());
            return;
        }
        if (args.length >= 3 && args[1].equalsIgnoreCase("deposit")) {
            TownRecord town = game.depositTown(player, parseAmount(args[2]));
            success(player, "Баланс города: <yellow>" + town.coins() + "</yellow>.");
            return;
        }
        if (args.length >= 3 && args[1].equalsIgnoreCase("withdraw")) {
            TownRecord town = game.withdrawTown(player, parseAmount(args[2]));
            success(player, "Баланс города: <yellow>" + town.coins() + "</yellow>.");
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("claim")) {
            TownClaimRecord claim = townClaims.claim(player);
            success(player, "Чанк <yellow>" + claim.chunkX() + "," + claim.chunkZ() + "</yellow> заклаймлен городом #" + claim.townId() + ".");
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("unclaim")) {
            townClaims.unclaim(player);
            success(player, "Текущий чанк отклаймлен.");
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("claims")) {
            int count = townClaims.claimCount(player);
            player.sendMessage(miniMessage.deserialize("<gold>Клаймы города:</gold> <yellow>" + count + "/" + plugin.settings().maxTownClaims() + "</yellow>"));
            return;
        }
        ResidentProfile resident = game.resident(player);
        game.town(resident).ifPresentOrElse(
                town -> player.sendMessage(miniMessage.deserialize("<gold>Город:</gold> <yellow>" + town.name() + "</yellow> <gray>level=" + town.level() + ", coins=" + town.coins() + "</gray>")),
                () -> error(player, "У вас нет города. Используйте /civcraftmodern town create <название>.")
        );
    }


    private void handleBuild(Player player, String[] args) throws IOException, SQLException {
        if (!player.hasPermission("civcraft.build")) {
            throw new IllegalArgumentException("Нет прав на строительство legacy-шаблонов.");
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("list")) {
            List<String> structures = legacyStructures.listDefinitions(25).stream()
                    .map(definition -> definition.id() + "(" + definition.displayName() + ", cost=" + definition.cost() + ")")
                    .toList();
            player.sendMessage(miniMessage.deserialize("<gold>Structures:</gold> <yellow>" + String.join(", ", structures) + "</yellow>"));
            return;
        }
        if (args.length >= 3 && args[1].equalsIgnoreCase("structure")) {
            String direction = args.length >= 4 ? args[3] : plugin.settings().legacyDefaultDirection();
            QueuedLegacyBuild build = legacyStructures.buildStructure(player, args[2], direction);
            success(player, "Структура #" + build.id() + " оплачена и поставлена в очередь: " + build.queuedBlocks() + " блоков.");
            return;
        }
        if (args.length >= 4 && args[1].equalsIgnoreCase("legacy")) {
            String direction = args.length >= 5 ? args[4] : plugin.settings().legacyDefaultDirection();
            QueuedLegacyBuild build = legacyStructures.paste(player, args[2], args[3], direction);
            success(player, "Постройка #" + build.id() + " поставлена в очередь: " + build.queuedBlocks() + " блоков, размер " + build.sizeX() + "x" + build.sizeY() + "x" + build.sizeZ());
            return;
        }
        player.sendMessage(miniMessage.deserialize("<yellow>/build list</yellow> <gray>- список legacy structures</gray>"));
        player.sendMessage(miniMessage.deserialize("<yellow>/build structure s_townhall north</yellow> <gray>- построить структуру из structures.yml с оплатой и защитой</gray>"));
    }


    private void handleResearch(Player player, String[] args) throws SQLException {
        if (args.length >= 2 && args[1].equalsIgnoreCase("list")) {
            List<String> techs = research.listTechs(25).stream()
                    .map(tech -> tech.id() + "(" + tech.name() + ", beakers=" + tech.beakerCost() + ", coins=" + tech.coinCost() + ")")
                    .toList();
            player.sendMessage(miniMessage.deserialize("<gold>Технологии:</gold> <yellow>" + String.join(", ", techs) + "</yellow>"));
            return;
        }
        if (args.length >= 3 && args[1].equalsIgnoreCase("start")) {
            TechDefinition definition = research.startResearch(player, args[2]);
            success(player, "Исследование <yellow>" + definition.name() + "</yellow> начато. Нужно beakers: " + definition.beakerCost());
            return;
        }
        ResidentProfile resident = game.resident(player);
        research.progress(resident).ifPresentOrElse(
                progress -> player.sendMessage(miniMessage.deserialize("<gold>Исследование:</gold> <yellow>" + progress.techId() + "</yellow> <gray>" + Math.round(progress.progress()) + "/" + Math.round(progress.requiredBeakers()) + " (" + Math.round(progress.percent()) + "%)</gray>")),
                () -> error(player, "Активного исследования нет. Используйте /research list и /research start <id>.")
        );
    }

    private void showResident(Player player) throws SQLException {
        ResidentProfile resident = game.resident(player);
        player.sendMessage(miniMessage.deserialize("<gold>Resident:</gold> <yellow>" + resident.name() + "</yellow> <gray>" + economy.currencyId() + "=" + economy.balance(player) + "</gray>"));
        player.sendMessage(miniMessage.deserialize("<gray>civ=" + value(resident.civId()) + ", town=" + value(resident.townId()) + ", camp=" + value(resident.campId()) + "</gray>"));
    }

    private Player requirePlayer(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            throw new IllegalArgumentException("Эта команда доступна только игроку.");
        }
        return player;
    }

    private String joinName(String[] args, int start) {
        return String.join(" ", java.util.Arrays.copyOfRange(args, start, args.length)).trim();
    }

    private double parseAmount(String input) {
        try {
            double amount = Double.parseDouble(input);
            if (amount <= 0 || Double.isNaN(amount) || Double.isInfinite(amount)) {
                throw new NumberFormatException(input);
            }
            return amount;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Сумма должна быть положительным числом.");
        }
    }

    private String value(Long id) {
        return id == null ? "-" : id.toString();
    }

    private void help(CommandSender sender, String label) {
        sender.sendMessage(miniMessage.deserialize("<gold>CivCraft Leaf playable build</gold>"));
        sender.sendMessage(miniMessage.deserialize("<yellow>/" + label + " resident</yellow> <gray>- профиль игрока</gray>"));
        sender.sendMessage(miniMessage.deserialize("<yellow>/" + label + " camp create <name></yellow> <gray>- создать лагерь</gray>"));
        sender.sendMessage(miniMessage.deserialize("<yellow>/" + label + " civ create <name></yellow> <gray>- создать цивилизацию</gray>"));
        sender.sendMessage(miniMessage.deserialize("<yellow>/" + label + " town create <name></yellow> <gray>- создать город</gray>"));
        sender.sendMessage(miniMessage.deserialize("<yellow>/" + label + " town claim|unclaim|claims</yellow> <gray>- клаймы территории города</gray>"));
        sender.sendMessage(miniMessage.deserialize("<yellow>/" + label + " town deposit|withdraw <amount></yellow> <gray>- банк города</gray>"));
        sender.sendMessage(miniMessage.deserialize("<yellow>/" + label + " civ deposit|withdraw <amount></yellow> <gray>- банк цивилизации</gray>"));
        sender.sendMessage(miniMessage.deserialize("<yellow>/" + label + " research list|start|status</yellow> <gray>- технологии цивилизации</gray>"));
        sender.sendMessage(miniMessage.deserialize("<yellow>/" + label + " integrations</yellow> <gray>- статус интеграций</gray>"));
    }

    private void success(CommandSender sender, String message) {
        sender.sendMessage(miniMessage.deserialize("<green>" + message + "</green>"));
    }

    private void error(CommandSender sender, String message) {
        sender.sendMessage(miniMessage.deserialize("<red>" + message + "</red>"));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String @NotNull [] args) {
        String primary = alias.toLowerCase(Locale.ROOT);
        if (primary.equals("build") && args.length == 1) {
            return List.of("list", "structure", "legacy");
        }
        if (List.of("research", "tech").contains(primary) && args.length == 1) {
            return List.of("list", "start", "status");
        }
        if (primary.equals("camp") && args.length == 1) {
            return List.of("create", "info");
        }
        if (primary.equals("civ") && args.length == 1) {
            return List.of("create", "info", "deposit", "withdraw");
        }
        if (List.of("town", "t").contains(primary) && args.length == 1) {
            return List.of("create", "info", "claim", "unclaim", "claims", "deposit", "withdraw");
        }
        if (args.length == 1) {
            return List.of("resident", "camp", "civ", "town", "build", "research", "integrations", "reload");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("research")) {
            return List.of("list", "start", "status");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("camp")) {
            return List.of("create", "info");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("civ")) {
            return List.of("create", "info", "deposit", "withdraw");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("town")) {
            return List.of("create", "info", "claim", "unclaim", "claims", "deposit", "withdraw");
        }
        return List.of();
    }
}
