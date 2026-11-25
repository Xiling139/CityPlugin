package com.xiling.city.managers;

import com.xiling.city.CityPlugin;
import com.xiling.city.models.RegionModel;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BossBarManager {
    private final Map<UUID, BossBar> playerBossBars = new HashMap<>();
    private final Map<UUID, String> playerLastRegionId = new HashMap<>();
    private final CityPlugin plugin;

    private final TextColor provinceColor = TextColor.color(0x00AA00);
    private final TextColor municipalityColor = TextColor.color(0x55FFFF);
    private final TextColor cityColor = TextColor.color(0x55FF55);
    private final TextColor districtColor = TextColor.color(0xAAAA00);
    private final TextColor villageColor = TextColor.color(0xFFAA00);

    public BossBarManager(CityPlugin plugin) {
        this.plugin = plugin;
    }

    public void updateBossBar(Player player, RegionModel region) {
        if (!plugin.getCityConfig().regionEnabled()) {
            removeBossBar(player);
            return;
        }

        UUID playerId = player.getUniqueId();

        String currentRegionId = (region == null) ? null : region.getId();
        String lastRegionId = playerLastRegionId.get(playerId);

        // Store new region ID
        if (currentRegionId != null) {
            playerLastRegionId.put(playerId, currentRegionId);
        } else {
            playerLastRegionId.remove(playerId);
        }

        // Build the new title
        Component title;
        BossBar.Color color;

        if (region == null) {
            title = Component.text("✦ Untagged Area").color(TextColor.color(0x888888));
            color = BossBar.Color.WHITE;
        } else {
            title = buildHierarchyComponent(region);
            color = BossBar.Color.GREEN;
        }

        // Get existing boss bar
        BossBar bar = playerBossBars.get(playerId);

        if (bar == null) {
            // Create new boss bar
            bar = BossBar.bossBar(title, 1.0f, color, BossBar.Overlay.PROGRESS);
            player.showBossBar(bar);
            playerBossBars.put(playerId, bar);
        } else {
            // Update existing boss bar
            bar.name(title);
            bar.color(color);
        }
    }

    private Component buildHierarchyComponent(RegionModel region) {
        Component result = Component.text("");

        String province = region.getLevel("province");
        if (province != null && !province.isEmpty()) {
            result = result.append(Component.text("[").color(provinceColor))
                    .append(Component.text(province).color(provinceColor))
                    .append(Component.text("] ").color(provinceColor));
        }

        String city = region.getLevel("city");
        if (city != null && !city.isEmpty()) {
            if (region.isMunicipality(city)) result = result.append(Component.text(city).color(municipalityColor));
            else result = result.append(Component.text(city).color(cityColor));
        }

        String district = region.getLevel("district");
        if (district != null && !district.isEmpty()) {
            if (city != null && !city.isEmpty()) {
                result = result.append(Component.text(" - ").color(districtColor));
            }
            result = result.append(Component.text(district).color(districtColor));
        }

        String village = region.getLevel("village");
        if (village != null && !village.isEmpty()) {
            result = result.append(Component.text(" (").color(villageColor))
                    .append(Component.text(village).color(villageColor))
                    .append(Component.text(")").color(villageColor));
        }

        result = Component.text("📍 ").color(TextColor.color(0xFF5555)).append(result);

        return result;
    }

    public void removeBossBar(Player player) {
        UUID playerId = player.getUniqueId();
        BossBar bar = playerBossBars.remove(playerId);
        if (bar != null) {
            player.hideBossBar(bar);
        }
        playerLastRegionId.remove(playerId);
    }

    /**
     * Refresh boss bars for all online players
     * Forces an update of everyone's boss bar to their current location
     */
    public void refreshForAll() {
        if (!plugin.getCityConfig().regionEnabled()) {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                removeBossBar(player);
            }
            return;
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            // Clear stored region ID to force update
            playerLastRegionId.remove(player.getUniqueId());

            // Get current region
            RegionModel region = plugin.getDatabaseManager().getRegion(player.getLocation().getChunk());

            // Update boss bar
            updateBossBar(player, region);
        }
    }
}
