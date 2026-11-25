package com.xiling.city.commands;

import com.xiling.city.CityPlugin;
import com.xiling.city.database.DatabaseManager;
import com.xiling.city.models.RegionModel;
import com.xiling.city.util.Perms;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.*;

public class RegionTabCompleter implements TabCompleter {

    private final DatabaseManager database;
    private final List<String> subcommands = Arrays.asList("info", "list", "tag", "cleanup");

    public RegionTabCompleter() {
        this.database = CityPlugin.getInstance().getDatabaseManager();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return new ArrayList<>();
        }

        Player player = (Player) sender;

        if (args.length == 1) {
            // Complete subcommand
            return filterSuggestions(subcommands, args[0]);
        }

        if (args.length == 2) {
            String subcommand = args[0].toLowerCase();

            if (subcommand.equals("list")) {
                // Suggest provinces for /region list <province>
                return filterSuggestions(getUniqueProvinces(), args[1]);
            }

            if (subcommand.equals("tag")) {
                return handleTagCompletion(player, args);
            }
        }

        if (args.length >= 3 && args[0].toLowerCase().equals("tag")) {
            return handleTagCompletion(player, args);
        }

        return new ArrayList<>();
    }

    private List<String> handleTagCompletion(Player player, String[] args) {
        // Check if player has permission for tag command
        if (!Perms.has(player, "city.admin")) {
            return new ArrayList<>();
        }

        // Check for -f flag
        boolean hasForceFlag = args.length > 1 && args[1].equalsIgnoreCase("-f");
        int argOffset = hasForceFlag ? 1 : 0;

        // args[0] is "tag"
        // args[1] might be "-f" or first argument
        int currentArg = args.length - 1; // Current argument index (0-based)

        // Determine which level we're completing
        int level = currentArg - (hasForceFlag ? 2 : 1); // After "tag" and optional "-f"

        List<String> suggestions = new ArrayList<>();

        switch (level) {
            case 0: // Province
                suggestions.addAll(getUniqueProvinces());
                suggestions.add("-");  // Allow empty province
                break;

            case 1: // City
                String province = args[hasForceFlag ? 2 : 1];
                if (!province.equals("-") && !province.isEmpty()) {
                    suggestions.addAll(getCitiesInProvince(province));
                } else {
                    suggestions.addAll(getAllCities());  // Cities not tied to province
                }
                suggestions.add("-");
                break;

            case 2: // District
                String province2 = args[hasForceFlag ? 2 : 1];
                String city = args[hasForceFlag ? 3 : 2];
                return getDistrictsInCity(province2, city);

            case 3: // Village
                String province3 = args[hasForceFlag ? 2 : 1];
                String city2 = args[hasForceFlag ? 3 : 2];
                String district = args[hasForceFlag ? 4 : 3];
                return getVillagesInDistrict(province3, city2, district);

            default:
                return new ArrayList<>();
        }

        return suggestions;
    }

    // Helper methods for suggestions
    private List<String> getAllCities() {
        Set<String> cities = new HashSet<>();
        for (RegionModel region : database.getAllRegions()) {
            String province = region.getLevel("province");
            String city = region.getLevel("city");
            if (city != null && !city.isEmpty() && province.isEmpty()) {
                cities.add(city);
            }
        }
        return new ArrayList<>(cities);
    }

    private List<String> getUniqueProvinces() {
        Set<String> provinces = new HashSet<>();
        for (RegionModel region : database.getAllRegions()) {
            String province = region.getLevel("province");
            if (province != null && !province.isEmpty()) {
                provinces.add(province);
            }
        }
        return new ArrayList<>(provinces);
    }

    private List<String> getCitiesInProvince(String province) {
        Set<String> cities = new HashSet<>();
        for (RegionModel region : database.getAllRegions()) {
            String regionProvince = region.getLevel("province");
            String city = region.getLevel("city");
            if (province.equals(regionProvince) && city != null && !city.isEmpty()) {
                cities.add(city);
            }
        }
        return new ArrayList<>(cities);
    }

    private List<String> getDistrictsInCity(String province, String city) {
        Set<String> districts = new HashSet<>();
        for (RegionModel region : database.getAllRegions()) {
            String regionProvince = region.getLevel("province");
            String regionCity = region.getLevel("city");
            String district = region.getLevel("district");
            if (province.equals(regionProvince) && city.equals(regionCity) &&
                    district != null && !district.isEmpty()) {
                districts.add(district);
            }
        }
        return new ArrayList<>(districts);
    }

    private List<String> getVillagesInDistrict(String province, String city, String district) {
        Set<String> villages = new HashSet<>();
        for (RegionModel region : database.getAllRegions()) {
            String regionProvince = region.getLevel("province");
            String regionCity = region.getLevel("city");
            String regionDistrict = region.getLevel("district");
            String village = region.getLevel("village");
            if (province.equals(regionProvince) && city.equals(regionCity) &&
                    district.equals(regionDistrict) && village != null && !village.isEmpty()) {
                villages.add(village);
            }
        }
        return new ArrayList<>(villages);
    }

    private List<String> filterSuggestions(List<String> suggestions, String currentInput) {
        return StringUtil.copyPartialMatches(currentInput, suggestions, new ArrayList<>());
    }
}