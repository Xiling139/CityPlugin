package com.xiling.city.database;

import com.xiling.city.CityPlugin;
import com.xiling.city.models.RegionModel;
import org.bukkit.Chunk;

import java.sql.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DatabaseManager {
    private final CityPlugin plugin;
    private Connection connection;
    private final Map<String, String> chunkToRegion;
    private final Map<String, RegionModel> regions;

    public DatabaseManager(CityPlugin plugin) {
        this.plugin = plugin;
        this.chunkToRegion = new ConcurrentHashMap<>();
        this.regions = new ConcurrentHashMap<>();
        initializeDatabase();
        loadAllData();
    }

    private void initializeDatabase() {
        try {
            // Create plugins/CityPlugin/data folder
            plugin.getDataFolder().mkdirs();
            String dbPath = plugin.getDataFolder().getAbsolutePath() + "/regions.db";

            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);

            // Create tables
            try (Statement stmt = connection.createStatement()) {
                // Regions table
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS regions (
                        id TEXT PRIMARY KEY,
                        province TEXT,
                        city TEXT,
                        district TEXT,
                        village TEXT,
                        created_at INTEGER
                    )
                """);

                // Chunks table (world,x,z -> region_id)
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS chunks (
                        world TEXT,
                        x INTEGER,
                        z INTEGER,
                        region_id TEXT,
                        PRIMARY KEY (world, x, z),
                        FOREIGN KEY (region_id) REFERENCES regions(id) ON DELETE CASCADE
                    )
                """);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to initialize database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void loadAllData() {
        // Load all regions
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM regions")) {

            while (rs.next()) {
                String id = rs.getString("id");
                RegionModel region = new RegionModel(id);

                String province = rs.getString("province");
                String city = rs.getString("city");
                String district = rs.getString("district");
                String village = rs.getString("village");

                if (province != null) region.setLevel("province", province);
                if (city != null) region.setLevel("city", city);
                if (district != null) region.setLevel("district", district);
                if (village != null) region.setLevel("village", village);

                regions.put(id, region);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load regions: " + e.getMessage());
        }

        // Load all chunk mappings
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM chunks")) {

            while (rs.next()) {
                String world = rs.getString("world");
                int x = rs.getInt("x");
                int z = rs.getInt("z");
                String regionId = rs.getString("region_id");

                String key = world + ":" + x + "," + z;
                chunkToRegion.put(key, regionId);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load chunks: " + e.getMessage());
        }

        plugin.getLogger().info("Loaded " + regions.size() + " regions and " + chunkToRegion.size() + " chunk mappings");
    }

    public String getRegionId(String world, int x, int z) {
        String key = world + ":" + x + "," + z;
        return chunkToRegion.get(key);
    }

    public RegionModel getRegion(org.bukkit.Chunk chunk) {
        String regionId = getRegionId(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
        if (regionId == null) return null;
        return regions.get(regionId);
    }

    public RegionModel getRegionById(String regionId) {
        return regionId == null ? null : regions.get(regionId);
    }

    public void assignChunk(org.bukkit.Chunk chunk, String regionId) {
        String key = chunk.getWorld().getName() + ":" + chunk.getX() + "," + chunk.getZ();
        chunkToRegion.put(key, regionId);

        // Save to database
        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT OR REPLACE INTO chunks (world, x, z, region_id) VALUES (?, ?, ?, ?)")) {
            stmt.setString(1, chunk.getWorld().getName());
            stmt.setInt(2, chunk.getX());
            stmt.setInt(3, chunk.getZ());
            stmt.setString(4, regionId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to save chunk: " + e.getMessage());
        }
    }

    public void saveRegion(RegionModel region) {
        regions.put(region.getId(), region);

        // Save to database
        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT OR REPLACE INTO regions (id, province, city, district, village, created_at) VALUES (?, ?, ?, ?, ?, ?)")) {
            stmt.setString(1, region.getId());
            stmt.setString(2, region.getLevel("province"));
            stmt.setString(3, region.getLevel("city"));
            stmt.setString(4, region.getLevel("district"));
            stmt.setString(5, region.getLevel("village"));
            stmt.setLong(6, System.currentTimeMillis());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to save region: " + e.getMessage());
        }
    }

    public String generateRegionId(String province, String city, String district, String village) {
        List<String> parts = new ArrayList<>();

        // Only add non-empty parts
        if (province != null && !province.isEmpty()) parts.add(province.replace(" ", "_"));
        if (city != null && !city.isEmpty()) parts.add(city.replace(" ", "_"));
        if (district != null && !district.isEmpty()) parts.add(district.replace(" ", "_"));
        if (village != null && !village.isEmpty()) parts.add(village.replace(" ", "_"));

        if (parts.isEmpty()) {
            return "unknown_" + System.currentTimeMillis();
        }

        return String.join("_", parts).toLowerCase();
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to close database: " + e.getMessage());
        }
    }

    public List<RegionModel> getAllRegions() {
        return new ArrayList<>(regions.values());
    }

    public int getChunkCount(String regionId) {
        int count = 0;
        for (String chunkKey : chunkToRegion.keySet()) {
            if (chunkToRegion.get(chunkKey).equals(regionId)) {
                count++;
            }
        }
        return count;
    }

    public boolean deleteRegion(String regionId) {
        // Check if region exists
        RegionModel region = regions.get(regionId);
        if (region == null) {
            return false;
        }

        // Remove from memory
        regions.remove(regionId);

        // Remove all chunk mappings for this region from memory
        Iterator<Map.Entry<String, String>> iterator = chunkToRegion.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, String> entry = iterator.next();
            if (entry.getValue().equals(regionId)) {
                iterator.remove();
            }
        }

        // Delete from database
        try {
            // Delete chunks first (foreign key constraint)
            try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM chunks WHERE region_id = ?")) {
                stmt.setString(1, regionId);
                int deletedChunks = stmt.executeUpdate();
                plugin.getLogger().info("Deleted " + deletedChunks + " chunk mappings for region: " + regionId);
            }

            // Delete region
            try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM regions WHERE id = ?")) {
                stmt.setString(1, regionId);
                stmt.executeUpdate();
            }

            plugin.getLogger().info("Deleted region: " + regionId + " (" + region.getFormattedDisplay() + ")");
            return true;

        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to delete region " + regionId + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}
