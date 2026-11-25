package com.xiling.city.listeners;

import com.xiling.city.CityPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerListener implements Listener {

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        var chunk = player.getLocation().getChunk();
        var region = CityPlugin.getInstance().getDatabaseManager().getRegion(chunk);

        CityPlugin.getInstance().getLogger().info("PlayerJoin - " + player.getName() +
                ", UUID: " + player.getUniqueId().toString());

        CityPlugin.getInstance().getBossBarManager().updateBossBar(player, region);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        CityPlugin.getInstance().getBossBarManager().removeBossBar(event.getPlayer());
    }
}
