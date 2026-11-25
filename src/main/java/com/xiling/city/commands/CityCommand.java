package com.xiling.city.commands;

import com.xiling.city.CityPlugin;
import com.xiling.city.util.Perms;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Locale;

/**
 * Root dispatcher for {@code /city} (aliases {@code /region}, {@code /rg}).
 *
 * <p>Region subcommands are forwarded unchanged, so old-style
 * {@code /region info} / {@code /region list} keeps working, and the
 * {@code /city region <sub>} form is available as well. The
 * {@code /city build <sub>} group is handled by {@link BuildCommand}.</p>
 */
public class CityCommand implements CommandExecutor {

    private final RegionCommand regionCommand = new RegionCommand();
    private final BuildCommand buildCommand;

    public CityCommand(CityPlugin plugin) {
        this.buildCommand = new BuildCommand(plugin, null);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("region")) {
            return regionCommand.onCommand(player, command, label, dropFirst(args));
        }
        if (sub.equals("build")) {
            return buildCommand.onCommand(player, command, label, dropFirst(args));
        }

        switch (sub) {
            case "info":
            case "list":
            case "tag":
            case "cleanup":
            case "cleanupconfirm":
            case "help":
                return regionCommand.onCommand(player, command, label, args);
            default:
                sendHelp(player);
                return true;
        }
    }

    private void sendHelp(Player player) {
        player.sendMessage("§6=== CityPlugin Commands ===");
        player.sendMessage("§f/city region info §7- Show current chunk's region");
        player.sendMessage("§f/city region list §7- List all regions");
        player.sendMessage("§f/city region list <province> §7- List regions in a province");
        player.sendMessage("§7Shortcut: §f/city info§7, §f/city list§7, §f/region <sub>");
        if (Perms.has(player, "city.admin")) {
            player.sendMessage("§f/city region tag [-f] <province|-> <city|-> <district|-> <village|->");
            player.sendMessage("§f/city region cleanup §7- Remove regions with 0 chunks");
        }

        if (Perms.has(player, "city.build")) {
            player.sendMessage("§f/city build at <模板> [0|90|180|270] §7- Generate a building");
            player.sendMessage("§f/city build zone <模板|random|类别> §7- Generate a grid of buildings");
            player.sendMessage("§f/city build list §7- List building templates");
            player.sendMessage("§7Also: §f/buildat§7, §f/buildzone");
        }
    }

    private String[] dropFirst(String[] args) {
        return Arrays.copyOfRange(args, 1, args.length);
    }
}
