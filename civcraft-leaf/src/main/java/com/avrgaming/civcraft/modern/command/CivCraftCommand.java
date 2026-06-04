package com.avrgaming.civcraft.modern.command;

import com.avrgaming.civcraft.modern.CivCraftModernPlugin;
import com.avrgaming.civcraft.modern.domain.CampRecord;
import com.avrgaming.civcraft.modern.domain.CivRecord;
import com.avrgaming.civcraft.modern.domain.ResidentProfile;
import com.avrgaming.civcraft.modern.domain.TownRecord;
import com.avrgaming.civcraft.modern.service.CivCraftGameService;
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
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public CivCraftCommand(CivCraftModernPlugin plugin, CivCraftGameService game) {
        this.plugin = plugin;
        this.game = game;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        String primary = label.toLowerCase(Locale.ROOT);
        String[] effectiveArgs = args;
        if (List.of("resident", "res", "camp", "civ", "town", "t").contains(primary)) {
            String category = switch (primary) {
                case "res" -> "resident";
                case "t" -> "town";
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
                    game.updateSettings(plugin.settings());
                    plugin.integrations().detect();
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
        ResidentProfile resident = game.resident(player);
        game.town(resident).ifPresentOrElse(
                town -> player.sendMessage(miniMessage.deserialize("<gold>Город:</gold> <yellow>" + town.name() + "</yellow> <gray>level=" + town.level() + ", coins=" + town.coins() + "</gray>")),
                () -> error(player, "У вас нет города. Используйте /civcraftmodern town create <название>.")
        );
    }

    private void showResident(Player player) throws SQLException {
        ResidentProfile resident = game.resident(player);
        player.sendMessage(miniMessage.deserialize("<gold>Resident:</gold> <yellow>" + resident.name() + "</yellow> <gray>coins=" + resident.coins() + "</gray>"));
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

    private String value(Long id) {
        return id == null ? "-" : id.toString();
    }

    private void help(CommandSender sender, String label) {
        sender.sendMessage(miniMessage.deserialize("<gold>CivCraft Leaf playable build</gold>"));
        sender.sendMessage(miniMessage.deserialize("<yellow>/" + label + " resident</yellow> <gray>- профиль игрока</gray>"));
        sender.sendMessage(miniMessage.deserialize("<yellow>/" + label + " camp create <name></yellow> <gray>- создать лагерь</gray>"));
        sender.sendMessage(miniMessage.deserialize("<yellow>/" + label + " civ create <name></yellow> <gray>- создать цивилизацию</gray>"));
        sender.sendMessage(miniMessage.deserialize("<yellow>/" + label + " town create <name></yellow> <gray>- создать город</gray>"));
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
        if (List.of("camp", "civ", "town", "t").contains(primary) && args.length == 1) {
            return List.of("create", "info");
        }
        if (args.length == 1) {
            return List.of("resident", "camp", "civ", "town", "integrations", "reload");
        }
        if (args.length == 2 && List.of("camp", "civ", "town").contains(args[0].toLowerCase(Locale.ROOT))) {
            return List.of("create", "info");
        }
        return List.of();
    }
}
