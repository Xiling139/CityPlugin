package com.xiling.city.listeners;

import com.xiling.city.CityPlugin;
import com.xiling.city.database.DatabaseManager;
import com.xiling.city.models.RegionModel;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ChunkListener implements Listener {

    private final DatabaseManager database;
    private final Map<UUID, String> playerLastChunkKey = new HashMap<>();

    public ChunkListener() {
        this.database = CityPlugin.getInstance().getDatabaseManager();
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();

        // Check if position actually changed (not just rotation)
        if (from.getBlockX() == to.getBlockX() &&
                from.getBlockY() == to.getBlockY() &&
                from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        Chunk fromChunk = from.getChunk();
        Chunk toChunk = to.getChunk();

        String fromKey = fromChunk.getWorld().getName() + ":" + fromChunk.getX() + "," + fromChunk.getZ();
        String toKey = toChunk.getWorld().getName() + ":" + toChunk.getX() + "," + toChunk.getZ();

        if (fromKey.equals(toKey)) {
            return;
        }

        UUID playerId = player.getUniqueId();
        String lastChunk = playerLastChunkKey.get(playerId);

        if (toKey.equals(lastChunk)) {
            return;
        }

        playerLastChunkKey.put(playerId, toKey);

        RegionModel region = database.getRegion(toChunk);
        CityPlugin.getInstance().getBossBarManager().updateBossBar(player, region);
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        Chunk toChunk = event.getTo().getChunk();

        String chunkKey = toChunk.getWorld().getName() + ":" + toChunk.getX() + "," + toChunk.getZ();
        playerLastChunkKey.put(player.getUniqueId(), chunkKey);

        RegionModel region = database.getRegion(toChunk);
        CityPlugin.getInstance().getBossBarManager().updateBossBar(player, region);
    }
}

