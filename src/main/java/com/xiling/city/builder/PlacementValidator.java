package com.xiling.city.builder;

import com.xiling.city.CityPlugin;
import com.xiling.city.database.DatabaseManager;
import com.xiling.city.models.RegionModel;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Pre-generation checks: world height, world border, block occupancy and
 * region protection.
 */
public class PlacementValidator {

    private static final int MAX_OFFENDERS_REPORTED = 5;

    private final CityPlugin plugin;

    public PlacementValidator(CityPlugin plugin) {
        this.plugin = plugin;
    }

    public void validate(BuildingTemplate rotated, TerrainProfile terrain, World world,
                         int minX, int minZ, boolean force) throws BuildException {
        int baseY = terrain.baseY();
        int width = rotated.width();
        int depth = rotated.depth();

        int maxBuildingY = world.getMaxHeight() - 1;
        if (baseY + rotated.height() - 1 > maxBuildingY) {
            throw new BuildException("建筑高度超出世界上限（顶部 y="
                    + (baseY + rotated.height() - 1) + " > " + maxBuildingY + "）", "高度超限");
        }

        if (!isInsideBorder(world, minX, minZ, width, depth, baseY)) {
            throw new BuildException("建筑范围超出世界边界", "超出边界");
        }

        int maxBuildHeight = plugin.getCityConfig().maxBuildHeight();
        if (rotated.height() > maxBuildHeight) {
            throw new BuildException("建筑高度 " + rotated.height() + " 超过配置上限 " + maxBuildHeight,
                    "高度超限");
        }

        if (force) {
            return;
        }

        List<String> offenders = new ArrayList<>();
        Set<Material> natural = plugin.getCityConfig().naturalBlocks();

        // Blocks the building itself will occupy.
        for (BuildingTemplate.Block block : rotated.blocks()) {
            int x = minX + block.x();
            int y = baseY + block.y();
            int z = minZ + block.z();
            Material material = world.getBlockAt(x, y, z).getType();
            if (!natural.contains(material)) {
                offenders.add(x + "," + y + "," + z + "=" + material.getKey().getKey());
                if (offenders.size() >= MAX_OFFENDERS_REPORTED) {
                    break;
                }
            }
        }

        // Blocks between the ground and the building base that would be filled.
        if (offenders.size() < MAX_OFFENDERS_REPORTED) {
            outer:
            for (int dx = 0; dx < width; dx++) {
                for (int dz = 0; dz < depth; dz++) {
                    int ground = terrain.groundAt(dx, dz);
                    for (int y = ground + 1; y < baseY; y++) {
                        int x = minX + dx;
                        int z = minZ + dz;
                        Material material = world.getBlockAt(x, y, z).getType();
                        if (!natural.contains(material)) {
                            offenders.add(x + "," + y + "," + z + "=" + material.getKey().getKey());
                            if (offenders.size() >= MAX_OFFENDERS_REPORTED) {
                                break outer;
                            }
                        }
                    }
                }
            }
        }

        // The ground surface itself must be natural terrain; otherwise a block
        // placed on the surface would be treated as "ground" and built upon.
        if (offenders.size() < MAX_OFFENDERS_REPORTED) {
            for (int dx = 0; dx < width; dx++) {
                for (int dz = 0; dz < depth; dz++) {
                    int groundY = terrain.groundAt(dx, dz);
                    int x = minX + dx;
                    int z = minZ + dz;
                    Material material = world.getBlockAt(x, groundY, z).getType();
                    if (!natural.contains(material)) {
                        offenders.add(x + "," + groundY + "," + z + "=ground:" + material.getKey().getKey());
                        if (offenders.size() >= MAX_OFFENDERS_REPORTED) {
                            break;
                        }
                    }
                }
            }
        }

        if (!offenders.isEmpty()) {
            throw new BuildException("该位置已有非自然方块（可能为玩家建筑）："
                    + String.join("；", offenders)
                    + "。如确认覆盖请加 --force", "被占用");
        }
    }

    private static boolean isInsideBorder(World world, int minX, int minZ, int width, int depth, int y) {
        Location min = new Location(world, minX, y, minZ);
        Location max = new Location(world, minX + width - 1, y, minZ + depth - 1);
        return world.getWorldBorder().isInside(min) && world.getWorldBorder().isInside(max);
    }
}
