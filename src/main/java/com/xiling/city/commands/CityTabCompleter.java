package com.xiling.city.commands;

import com.xiling.city.CityPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Tab completion for {@code /city}: root subcommands, then nested region or
 * build completion.
 */
public class CityTabCompleter implements TabCompleter {

    private static final List<String> ROOT_SUBCOMMANDS =
            Arrays.asList("region", "build", "info", "list", "tag", "cleanup", "help");

    private final RegionTabCompleter regionTabCompleter = new RegionTabCompleter();
    private final BuildTabCompleter buildTabCompleter;

    public CityTabCompleter(CityPlugin plugin) {
        this.buildTabCompleter = new BuildTabCompleter(plugin, null);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return StringUtil.copyPartialMatches(args[0], ROOT_SUBCOMMANDS, new ArrayList<>());
        }

        if (args[0].equalsIgnoreCase("region")) {
            return regionTabCompleter.onTabComplete(sender, command, alias,
                    Arrays.copyOfRange(args, 1, args.length));
        }
        if (args[0].equalsIgnoreCase("build")) {
            return buildTabCompleter.onTabComplete(sender, command, alias,
                    Arrays.copyOfRange(args, 1, args.length));
        }

        return regionTabCompleter.onTabComplete(sender, command, alias, args);
    }
}
