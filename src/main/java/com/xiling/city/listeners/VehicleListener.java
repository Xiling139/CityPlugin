package com.xiling.city.listeners;

import com.xiling.city.CityPlugin;
import com.xiling.city.models.RegionModel;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;

public class VehicleListener implements Listener {

    @EventHandler
    public void onVehicleMove(VehicleMoveEvent event) {
        Vehicle vehicle = event.getVehicle();

        // Check if vehicle has a player passenger
        if (vehicle.getPassengers().isEmpty()) return;

        for (var passenger : vehicle.getPassengers()) {
            if (passenger instanceof Player) {
                Player player = (Player) passenger;

                // Check if chunk changed
                if (event.getFrom().getChunk() == event.getTo().getChunk()) {
                    return;
                }

                // Update boss bar
                RegionModel region = CityPlugin.getInstance().getDatabaseManager()
                        .getRegion(event.getTo().getChunk());
                CityPlugin.getInstance().getBossBarManager().updateBossBar(player, region);
            }
        }
    }

    @EventHandler
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (event.getEntered() instanceof Player) {
            Player player = (Player) event.getEntered();
            // Immediately update boss bar when entering vehicle
            RegionModel region = CityPlugin.getInstance().getDatabaseManager()
                    .getRegion(player.getLocation().getChunk());
            CityPlugin.getInstance().getBossBarManager().updateBossBar(player, region);
        }
    }

    @EventHandler
    public void onVehicleExit(VehicleExitEvent event) {
        if (event.getExited() instanceof Player) {
            Player player = (Player) event.getExited();
            // Update boss bar when exiting vehicle
            RegionModel region = CityPlugin.getInstance().getDatabaseManager()
                    .getRegion(player.getLocation().getChunk());
            CityPlugin.getInstance().getBossBarManager().updateBossBar(player, region);
        }
    }
}