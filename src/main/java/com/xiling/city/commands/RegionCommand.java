package com.xiling.city.commands;

import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.LocalSession;
import com.xiling.city.CityPlugin;
import com.xiling.city.database.DatabaseManager;
import com.xiling.city.models.RegionModel;
import com.xiling.city.util.Perms;
import org.bukkit.Chunk;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;

public class RegionCommand implements CommandExecutor {

    private final DatabaseManager database;
    private final Map<UUID, Boolean> pendingCleanup = new HashMap<>();  // For cleanup all

    public RegionCommand() {
        this.database = CityPlugin.getInstance().getDatabaseManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "info":
                return showInfo(player);

            case "tag":
                if (!Perms.has(player, "city.admin")) {
                    player.sendMessage("§cYou don't have permission to use this command!");
                    return true;
                }
                return startTagCommand(player, args);

            case "list":
                if (args.length >= 2) {
                    // List regions in specific province
                    String province = args[1];
                    return listRegionsByProvince(player, province);
                } else {
                    // List all regions
                    return listAllRegions(player);
                }

            case "help":
            default:
                sendHelp(player);
                return true;

            case "cleanup":
                if (!Perms.has(player, "city.admin")) {
                    player.sendMessage("§cYou don't have permission to use this command!");
                    return true;
                }
                return cleanupEmptyRegions(player);

            case "cleanupconfirm":
                if (!Perms.has(player, "city.admin")) {
                    player.sendMessage("§cYou don't have permission to use this command!");
                    return true;
                }
                return cleanupConfirm(player);
        }
    }

    private boolean showInfo(Player player) {
        RegionModel region = database.getRegion(player.getLocation().getChunk());

        if (region == null) {
            player.sendMessage("§eThis chunk is not tagged to any region.");
            player.sendMessage("§7Use §f/region tag §7to tag the selected area.");
        } else {
            player.sendMessage("§aCurrent location: §f" + region.getFormattedDisplay());
            player.sendMessage("§7Region ID: §f" + region.getId());
        }
        return true;
    }

    private boolean startTagCommand(Player player, String[] args) {
        boolean force = false;
        int argIndex = 1;

        // Check for -f flag
        if (args.length > 1 && args[1].equalsIgnoreCase("-f")) {
            force = true;
            argIndex = 2;
        }

        // Check if we have enough arguments after the flag
        if (args.length < argIndex + 4) {
            player.sendMessage("§cUsage: /region tag [-f] <province> <city> <district> <village>");
            player.sendMessage("§7-f : Force tag (overwrite existing regions)");
            player.sendMessage("§7Example: /region tag -f Northern NewCity Downtown WestVillage");
            player.sendMessage("§7Use '-' for empty levels (e.g., for directly-administered cities)");
            return false;
        }

        String province = args[argIndex].equals("-") ? "" : args[argIndex];
        String city = args[argIndex + 1].equals("-") ? "" : args[argIndex + 1];
        String district = args[argIndex + 2].equals("-") ? "" : args[argIndex + 2];
        String village = args[argIndex + 3].equals("-") ? "" : args[argIndex + 3];

        if (province.isEmpty() && city.isEmpty() && district.isEmpty() && village.isEmpty()) {
            player.sendMessage("§cAt least one level must have a name!");
            return false;
        }

        return tagSelection(player, province, city, district, village, force);
    }

    private boolean listAllRegions(Player player) {
        List<RegionModel> regions = database.getAllRegions();

        if (regions.isEmpty()) {
            player.sendMessage("§eNo regions found. Use §f/region tag §eto create one.");
            return false;
        }

        // Sort by province, then city, then district, then village
        regions.sort((r1, r2) -> {
            int compare = compareStrings(r1.getLevel("province"), r2.getLevel("province"));
            if (compare != 0) return compare;
            compare = compareStrings(r1.getLevel("city"), r2.getLevel("city"));
            if (compare != 0) return compare;
            compare = compareStrings(r1.getLevel("district"), r2.getLevel("district"));
            if (compare != 0) return compare;
            return compareStrings(r1.getLevel("village"), r2.getLevel("village"));
        });

        player.sendMessage("§6=== Regions (" + regions.size() + " total) ===");

        // Group by province for better readability
        String currentProvince = null;

        for (RegionModel region : regions) {
            String province = region.getLevel("province");
            String city = region.getLevel("city");
            String district = region.getLevel("district");
            String village = region.getLevel("village");
            int chunkCount = database.getChunkCount(region.getId());

            // Show province header when it changes
            if (currentProvince == null || !currentProvince.equals(province)) {
                currentProvince = province;
                player.sendMessage("§7");
                player.sendMessage("§2§l[" + province + "]");
            }

            // Build region display
            String display = "";
            if (city != null && !city.isEmpty()) display += city;
            if (district != null && !district.isEmpty()) display += " §7- §f" + district;
            if (village != null && !village.isEmpty()) display += " §7(§f" + village + "§7)";

            // Add chunk count
            player.sendMessage("§f  " + display + " §7- §e" + chunkCount + " chunks");
        }

        player.sendMessage("§7");
        player.sendMessage("§7Use §f/region list <province> §7to view specific province");
        player.sendMessage("§7Use §f/region info §7to see current location");

        return true;
    }

    private boolean listRegionsByProvince(Player player, String province) {
        List<RegionModel> allRegions = database.getAllRegions();
        List<RegionModel> provinceRegions = new ArrayList<>();

        // Filter regions by province
        for (RegionModel region : allRegions) {
            String regionProvince = region.getLevel("province");
            if (province.equals(regionProvince)) {
                provinceRegions.add(region);
            }
        }

        if (provinceRegions.isEmpty()) {
            player.sendMessage("§eNo regions found in province: §f" + province);
            player.sendMessage("§7Use §f/region list §7to see all provinces");
            return true;
        }

        // Group by city for better organization
        Map<String, List<RegionModel>> citiesMap = new HashMap<>();
        for (RegionModel region : provinceRegions) {
            String city = region.getLevel("city");
            if (city == null || city.isEmpty()) city = "§7[No City]";
            citiesMap.computeIfAbsent(city, k -> new ArrayList<>()).add(region);
        }

        player.sendMessage("§6=== Regions in §f" + province + " §6===");

        // Sort cities alphabetically
        List<String> sortedCities = new ArrayList<>(citiesMap.keySet());
        Collections.sort(sortedCities);

        for (String city : sortedCities) {
            List<RegionModel> regionsInCity = citiesMap.get(city);

            // Sort regions by district then village
            regionsInCity.sort((r1, r2) -> {
                int compare = compareStrings(r1.getLevel("district"), r2.getLevel("district"));
                if (compare != 0) return compare;
                return compareStrings(r1.getLevel("village"), r2.getLevel("village"));
            });

            player.sendMessage("§7");
            player.sendMessage("§2§l" + city);

            for (RegionModel region : regionsInCity) {
                String district = region.getLevel("district");
                String village = region.getLevel("village");
                int chunkCount = database.getChunkCount(region.getId());

                String display = "";
                if (district != null && !district.isEmpty()) {
                    display += district;
                }
                if (village != null && !village.isEmpty()) {
                    if (!display.isEmpty()) display += " - ";
                    display += village;
                }

                if (display.isEmpty()) {
                    display = "§7(Unnamed Area)";
                }

                player.sendMessage("§f  " + display + " §7- §e" + chunkCount + " chunks");
            }
        }

        player.sendMessage("§7");
        player.sendMessage("§7Use §f/region info §7to see current location");
        player.sendMessage("§7Use §f/region tag §7to tag new areas");

        return true;
    }

    private int compareStrings(String s1, String s2) {
        if (s1 == null && s2 == null) return 0;
        if (s1 == null) return 1;
        if (s2 == null) return -1;
        return s1.compareToIgnoreCase(s2);
    }

    private void sendHelp(Player player) {
        player.sendMessage("§6=== CityPlugin Commands ===");
        player.sendMessage("§f/region info §7- Show current chunk's region");
        player.sendMessage("§f/region list §7- List all regions");
        player.sendMessage("§f/region list <province> §7- List regions in a province");

        if (Perms.has(player, "city.admin")) {
            player.sendMessage("§f/region tag [-f] <province|-|municipality> <city|-|district> <district|-|village> <village>");
            player.sendMessage("§7  -f : Force tag (overwrite existing)");
            player.sendMessage("§7  Use '-' for empty levels");
            player.sendMessage("§f/region cleanup §7- Remove all regions with 0 chunks");
        }
    }


    private boolean tagSelection(Player player, String province, String city, String district, String village, boolean force) {
        com.sk89q.worldedit.entity.Player wePlayer = BukkitAdapter.adapt(player);

        try {
            LocalSession session =
                    WorldEdit.getInstance().getSessionManager().get(wePlayer);

            if (!session.isSelectionDefined(wePlayer.getWorld())) {
                player.sendMessage("§cYou must make a WorldEdit selection first!");
                return false;
            }

            com.sk89q.worldedit.regions.Region selection =
                    session.getSelection(wePlayer.getWorld());

            String regionId = database.generateRegionId(province, city, district, village);

            RegionModel regionObj = new RegionModel(regionId);
            regionObj.setLevel("province", province);
            regionObj.setLevel("city", city);
            regionObj.setLevel("district", district);
            regionObj.setLevel("village", village);

            database.saveRegion(regionObj);

            BlockVector3 min = selection.getMinimumPoint();
            BlockVector3 max = selection.getMaximumPoint();

            int minChunkX = min.x() >> 4;
            int maxChunkX = max.x() >> 4;
            int minChunkZ = min.z() >> 4;
            int maxChunkZ = max.z() >> 4;

            if (!force) {
                int alreadyTaggedCount = 0;
                for (int x = minChunkX; x <= maxChunkX; x++) {
                    for (int z = minChunkZ; z <= maxChunkZ; z++) {
                        Chunk chunk = player.getWorld().getChunkAt(x, z);
                        String existingRegionId = database.getRegionId(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
                        if (existingRegionId != null) {
                            alreadyTaggedCount++;
                        }
                    }
                }

                if (alreadyTaggedCount > 0) {
                    player.sendMessage("§cSelection contains §e" + alreadyTaggedCount + " §calready tagged chunks!");
                    player.sendMessage("§cUse §f/region tag -f ... §cto overwrite them");
                    return false;
                }
            }

            int chunkCount = 0;
            int overwrittenCount = 0;

            for (int x = minChunkX; x <= maxChunkX; x++) {
                for (int z = minChunkZ; z <= maxChunkZ; z++) {
                    Chunk chunk = player.getWorld().getChunkAt(x, z);
                    String existingRegionId = database.getRegionId(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());

                    if (existingRegionId != null && !existingRegionId.equals(regionId)) {
                        overwrittenCount++;
                    }

                    database.assignChunk(chunk, regionId);
                    chunkCount++;
                }
            }

            player.sendMessage("§aSuccessfully tagged §e" + chunkCount + " §achunks as:");
            player.sendMessage("§f[" + province + "] " + city + " - " + district + " (" + village + ")");

            if (overwrittenCount > 0) {
                player.sendMessage("§e(§6" + overwrittenCount + " §echunks were reassigned from other regions§e)");
            }

            // Refresh boss bars
            CityPlugin.getInstance().getBossBarManager().refreshForAll();

            return true;

        } catch (Exception e) {
            player.sendMessage("§cError: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private boolean cleanupEmptyRegions(Player player) {
        List<RegionModel> allRegions = database.getAllRegions();
        List<RegionModel> emptyRegions = new ArrayList<>();

        // Find all regions with 0 chunks
        for (RegionModel region : allRegions) {
            if (database.getChunkCount(region.getId()) == 0) {
                emptyRegions.add(region);
            }
        }

        if (emptyRegions.isEmpty()) {
            player.sendMessage("§eNo empty regions found. Database is clean!");
            return true;
        }

        // Show what will be deleted
        player.sendMessage("§eFound §f" + emptyRegions.size() + " §eempty region(s):");
        for (RegionModel region : emptyRegions) {
            player.sendMessage("§7  - " + region.getFormattedDisplay());
        }

        // Confirm deletion
        player.sendMessage("§eType §f/region cleanupconfirm §eto confirm deletion.");

        // Store pending cleanup
        pendingCleanup.put(player.getUniqueId(), true);

        return true;
    }

    private boolean cleanupConfirm(Player player) {
        UUID playerId = player.getUniqueId();

        if (!pendingCleanup.containsKey(playerId)) {
            player.sendMessage("§cNo pending cleanup. Use /region cleanup first.");
            return false;
        }

        List<RegionModel> allRegions = database.getAllRegions();
        int deletedCount = 0;

        for (RegionModel region : allRegions) {
            if (database.getChunkCount(region.getId()) == 0) {
                database.deleteRegion(region.getId());
                deletedCount++;
            }
        }

        pendingCleanup.remove(playerId);

        player.sendMessage("§aCleaned up §f" + deletedCount + " §aempty region(s)!");

        // Refresh boss bars for all players
        CityPlugin.getInstance().getBossBarManager().refreshForAll();

        return true;
    }
}
