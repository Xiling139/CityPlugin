package com.xiling.city.builder;

import com.xiling.city.CityPlugin;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * Scans terrain under a footprint and derives the build base level.
 */
public final class TerrainScanner {

    private TerrainScanner() {
    }

    public static TerrainProfile scan(World world, int minX, int minZ, int width, int depth,
                                      CityPlugin plugin) throws BuildException {
        int[][] ground = new int[width][depth];
        boolean[][] water = new boolean[width][depth];

        int worldMinY = world.getMinHeight();
        int worldMaxY = world.getMaxHeight() - 1;
        int minGround = Integer.MAX_VALUE;
        int maxGround = Integer.MIN_VALUE;

        for (int dx = 0; dx < width; dx++) {
            for (int dz = 0; dz < depth; dz++) {
                int worldX = minX + dx;
                int worldZ = minZ + dz;

                int highest = Math.min(world.getHighestBlockYAt(worldX, worldZ), worldMaxY);
                int foundY = Integer.MIN_VALUE;
                boolean sawWater = false;

                for (int y = highest; y >= worldMinY; y--) {
                    Block block = world.getBlockAt(worldX, y, worldZ);
                    Material material = block.getType();

                    if (material == Material.WATER) {
                        sawWater = true;
                        continue;
                    }
                    if (material == Material.LAVA) {
                        sawWater = true;
                        continue;
                    }
                    if (isGround(material, plugin)) {
                        foundY = y;
                        break;
                    }
                }

                if (foundY == Integer.MIN_VALUE) {
                    throw new BuildException("该位置下方没有可用地面（x=" + worldX + ", z=" + worldZ + "）",
                            "没有可用地面");
                }

                ground[dx][dz] = foundY;
                water[dx][dz] = sawWater;
                minGround = Math.min(minGround, foundY);
                maxGround = Math.max(maxGround, foundY);
            }
        }

        int baseY = maxGround + 1;

        if (baseY > worldMaxY) {
            throw new BuildException("建造高度超出世界上限（基准面 y=" + baseY + "）", "高度超限");
        }
        if (baseY < worldMinY) {
            throw new BuildException("建造高度低于世界下限（基准面 y=" + baseY + "）", "高度超限");
        }

        if (!plugin.getCityConfig().fillWater()) {
            for (int dx = 0; dx < width; dx++) {
                for (int dz = 0; dz < depth; dz++) {
                    if (water[dx][dz]) {
                        throw new BuildException(
                                "足迹内有水域（x=" + (minX + dx) + ", z=" + (minZ + dz)
                                        + "）；可在 config.yml 开启 builder.fill-water 后重试",
                                "水域");
                    }
                }
            }
        }

        int foundationDepth = baseY - minGround - 1;
        int maxDepth = plugin.getCityConfig().maxFoundationDepth();
        if (foundationDepth > maxDepth) {
            throw new BuildException("地形过陡：地基深度 " + foundationDepth + " 超过上限 " + maxDepth,
                    "地形过陡");
        }

        return new TerrainProfile(minX, minZ, width, depth, ground, water, baseY, minGround, maxGround);
    }

    private static boolean isGround(Material material, CityPlugin plugin) {
        if (material.isAir()) {
            return false;
        }
        if (material == Material.WATER || material == Material.LAVA) {
            return false;
        }
        return !plugin.getCityConfig().groundIgnore().contains(material);
    }
}
