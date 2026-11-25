package com.xiling.city.commands;

import com.xiling.city.CityPlugin;
import com.xiling.city.builder.BuildingRegistry;
import com.xiling.city.builder.BuildingTemplate;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Tab completion for {@code /city build ...} and the standalone
 * {@code /buildat} / {@code /buildzone} commands.
 */
public class BuildTabCompleter implements TabCompleter {

    private static final List<String> SUBCOMMANDS =
            Arrays.asList("at", "zone", "list", "reload", "status", "cancel", "undo");

    private final CityPlugin plugin;
    private final String defaultSubcommand;

    public BuildTabCompleter(CityPlugin plugin, String defaultSubcommand) {
        this.plugin = plugin;
        this.defaultSubcommand = defaultSubcommand;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return List.of();
        }

        String subcommand;
        String[] rest;

        if (defaultSubcommand != null) {
            subcommand = defaultSubcommand;
            rest = args;
        } else {
            if (args.length == 0) {
                return List.of();
            }
            if (args.length == 1) {
                return filter(SUBCOMMANDS, args[0]);
            }
            subcommand = args[0].toLowerCase(Locale.ROOT);
            rest = Arrays.copyOfRange(args, 1, args.length);
        }

        return switch (subcommand) {
            case "at" -> completeAt(rest);
            case "zone" -> completeZone(rest);
            case "list" -> completeCategories(rest);
            default -> List.of();
        };
    }

    private List<String> completeAt(String[] rest) {
        BuildingRegistry registry = plugin.getBuildingRegistry();
        if (rest.length <= 1) {
            List<String> suggestions = new ArrayList<>();
            if (registry != null) {
                for (BuildingTemplate template : registry.all()) {
                    suggestions.add(template.name());
                }
            }
            if (rest.length == 0) {
                return suggestions;
            }
            return filter(suggestions, rest[0]);
        }
        if (rest.length == 2) {
            return filter(Arrays.asList("0", "90", "180", "270", "--force", "--preview"), rest[1]);
        }
        return filter(flags(rest), rest[rest.length - 1]);
    }

    private List<String> completeZone(String[] rest) {
        if (rest.length <= 1) {
            Set<String> suggestions = new LinkedHashSet<>();
            suggestions.add("random");
            BuildingRegistry registry = plugin.getBuildingRegistry();
            if (registry != null) {
                suggestions.addAll(registry.categories());
                for (BuildingTemplate template : registry.all()) {
                    suggestions.add(template.name());
                }
            }
            if (rest.length == 0) {
                return new ArrayList<>(suggestions);
            }
            return filter(new ArrayList<>(suggestions), rest[0]);
        }

        String previous = rest[rest.length - 2].toLowerCase(Locale.ROOT);
        String current = rest[rest.length - 1].toLowerCase(Locale.ROOT);

        if (previous.equals("--rotation")) {
            return filter(Arrays.asList("0", "90", "180", "270", "auto"), current);
        }
        if (previous.equals("--spacing") || previous.equals("--radius")) {
            return List.of();
        }
        return filter(flags(rest), rest[rest.length - 1]);
    }

    private List<String> completeCategories(String[] rest) {
        BuildingRegistry registry = plugin.getBuildingRegistry();
        if (registry == null) {
            return List.of();
        }
        List<String> categories = new ArrayList<>(registry.categories());
        return rest.length == 0 ? categories : filter(categories, rest[0]);
    }

    private List<String> flags(String[] rest) {
        Set<String> used = new LinkedHashSet<>();
        for (int i = 1; i < rest.length; i++) {
            used.add(rest[i].toLowerCase(Locale.ROOT));
        }
        List<String> flags = new ArrayList<>();
        for (String flag : Arrays.asList("--force", "--preview", "--spacing", "--radius", "--rotation")) {
            if (!used.contains(flag)) {
                flags.add(flag);
            }
        }
        return flags;
    }

    private List<String> filter(List<String> suggestions, String current) {
        return StringUtil.copyPartialMatches(current, suggestions, new ArrayList<>());
    }
}
