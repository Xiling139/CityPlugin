package com.xiling.city;

import com.xiling.city.builder.BuildUndoManager;
import com.xiling.city.builder.BuildingRegistry;
import com.xiling.city.builder.PlacementPlanner;
import com.xiling.city.builder.PlacementQueue;
import com.xiling.city.builder.ZonePlanner;
import com.xiling.city.commands.BuildCommand;
import com.xiling.city.commands.BuildTabCompleter;
import com.xiling.city.commands.CityCommand;
import com.xiling.city.commands.CityTabCompleter;
import com.xiling.city.config.CityConfig;
import com.xiling.city.database.DatabaseManager;
import com.xiling.city.listeners.ChunkListener;
import com.xiling.city.listeners.PlayerListener;
import com.xiling.city.listeners.VehicleListener;
import com.xiling.city.managers.BossBarManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.command.CommandExecutor;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * CityPlugin entry point.
 *
 * <p>Merges the former RegionPlugin (administrative region display) and the
 * planned BuilderPlugin (terrain-adaptive building generation) into a single
 * plugin with one data folder and one command root ({@code /city}).</p>
 */
public class CityPlugin extends JavaPlugin {

    private static CityPlugin instance;

    private CityConfig cityConfig;
    private DatabaseManager databaseManager;
    private BossBarManager bossBarManager;

    private BuildingRegistry buildingRegistry;
    private PlacementPlanner placementPlanner;
    private ZonePlanner zonePlanner;
    private PlacementQueue placementQueue;
    private BuildUndoManager undoManager;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        this.cityConfig = new CityConfig(this);

        warnIfLegacyPluginLoaded();
        migrateLegacyDatabase();

        this.databaseManager = new DatabaseManager(this);
        this.bossBarManager = new BossBarManager(this);

        this.undoManager = new BuildUndoManager(this);
        this.placementPlanner = new PlacementPlanner(this);
        this.zonePlanner = new ZonePlanner(this, placementPlanner);
        this.placementQueue = new PlacementQueue(this);
        this.placementQueue.start();

        if (cityConfig.builderEnabled()) {
            this.buildingRegistry = new BuildingRegistry(this);
            this.buildingRegistry.reload();
        } else {
            getLogger().info("Builder module disabled in config.yml.");
        }

        registerCommands();
        registerListeners();

        getLogger().info("CityPlugin v" + getDescription().getVersion() + " has been enabled!");
    }

    @Override
    public void onDisable() {
        if (placementQueue != null) {
            placementQueue.stop();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
        getLogger().info("CityPlugin has been disabled!");
    }

    private void registerCommands() {
        PluginCommand city = getCommand("city");
        if (city == null) {
            getLogger().severe("Command 'city' is missing from plugin.yml!");
        } else {
            city.setExecutor(new CityCommand(this));
            city.setTabCompleter(new CityTabCompleter(this));
        }

        registerStandalone("buildat", new BuildCommand(this, "at"), new BuildTabCompleter(this, "at"));
        registerStandalone("buildzone", new BuildCommand(this, "zone"), new BuildTabCompleter(this, "zone"));
    }

    private void registerStandalone(String name, CommandExecutor executor, TabCompleter tabCompleter) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().warning("Command '" + name + "' is missing from plugin.yml!");
            return;
        }
        command.setExecutor(executor);
        command.setTabCompleter(tabCompleter);
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new ChunkListener(), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(), this);
        getServer().getPluginManager().registerEvents(new VehicleListener(), this);
    }

    /**
     * The old RegionPlugin also registered {@code /region}; running both at the
     * same time leads to duplicate command dispatch.
     */
    private void warnIfLegacyPluginLoaded() {
        if (getServer().getPluginManager().getPlugin("RegionPlugin") != null) {
            getLogger().warning("Legacy RegionPlugin is still loaded! Remove RegionPlugin.jar from the "
                    + "plugins folder, otherwise /region is registered twice.");
        }
    }

    /**
     * Copies plugins/RegionPlugin/regions.db into plugins/CityPlugin/regions.db
     * once, so existing region tags survive the rename. The old file is kept.
     */
    private void migrateLegacyDatabase() {
        File pluginsDir = getDataFolder().getParentFile();
        File legacyDatabase = new File(pluginsDir, "RegionPlugin" + File.separator + "regions.db");
        File targetDatabase = new File(getDataFolder(), "regions.db");

        if (targetDatabase.exists() || !legacyDatabase.isFile()) {
            return;
        }

        try {
            getDataFolder().mkdirs();
            Files.copy(legacyDatabase.toPath(), targetDatabase.toPath());
            getLogger().info("Migrated region database from plugins/RegionPlugin/regions.db");
        } catch (IOException e) {
            getLogger().severe("Failed to migrate legacy region database: " + e.getMessage());
        }
    }

    public static CityPlugin getInstance() {
        return instance;
    }

    public CityConfig getCityConfig() {
        return cityConfig;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public BossBarManager getBossBarManager() {
        return bossBarManager;
    }

    public BuildingRegistry getBuildingRegistry() {
        return buildingRegistry;
    }

    public PlacementPlanner getPlacementPlanner() {
        return placementPlanner;
    }

    public ZonePlanner getZonePlanner() {
        return zonePlanner;
    }

    public PlacementQueue getPlacementQueue() {
        return placementQueue;
    }

    public BuildUndoManager getUndoManager() {
        return undoManager;
    }
}
