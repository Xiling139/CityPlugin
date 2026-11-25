package com.xiling.city.builder;

import com.xiling.city.CityPlugin;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Lays out a grid of buildings inside a rectangular zone.
 *
 * <p>Cells are {@code maxFootprint + spacing} wide, so buildings never
 * overlap and leave a road-width gap between them. Each cell is checked
 * independently; invalid cells are skipped and counted by reason.</p>
 */
public class ZonePlanner {

    public record ZoneResult(List<BlockChange> changes, int placedBuildings, int totalCells,
                             Map<String, Integer> skippedReasons) {
    }

    private final CityPlugin plugin;
    private final PlacementPlanner planner;

    public ZonePlanner(CityPlugin plugin, PlacementPlanner planner) {
        this.plugin = plugin;
        this.planner = planner;
    }

    public ZoneResult plan(World world, int minX, int minZ, int maxX, int maxZ,
                           List<BuildingTemplate> candidates, TemplateRotation fixedRotation,
                           int spacing, boolean force) throws BuildException {
        if (candidates.isEmpty()) {
            throw new BuildException("没有可用的建筑模板", "无模板");
        }

        int maxWidth = 0;
        int maxDepth = 0;
        for (BuildingTemplate candidate : candidates) {
            for (TemplateRotation rotation : TemplateRotation.values()) {
                BuildingTemplate rotated = candidate.rotated(rotation);
                maxWidth = Math.max(maxWidth, rotated.width());
                maxDepth = Math.max(maxDepth, rotated.depth());
            }
        }

        int areaWidth = maxX - minX + 1;
        int areaDepth = maxZ - minZ + 1;

        if (areaWidth < maxWidth || areaDepth < maxDepth) {
            throw new BuildException("区域太小：至少需要 " + maxWidth + "x" + maxDepth
                    + " 格，当前为 " + areaWidth + "x" + areaDepth, "区域太小");
        }

        int cellWidth = maxWidth + spacing;
        int cellDepth = maxDepth + spacing;
        int cellsX = (areaWidth - maxWidth) / cellWidth + 1;
        int cellsZ = (areaDepth - maxDepth) / cellDepth + 1;
        int totalCells = cellsX * cellsZ;

        int maxBuildings = plugin.getCityConfig().zoneMaxBuildings();
        if (totalCells > maxBuildings) {
            throw new BuildException("预计 " + totalCells + " 栋建筑，超过单次上限 " + maxBuildings
                    + "；请加大 --spacing 或缩小区域", "超过上限");
        }

        List<BlockChange> changes = new ArrayList<>();
        Map<String, Integer> skipped = new LinkedHashMap<>();
        int placed = 0;
        int maxQueueBlocks = plugin.getCityConfig().maxQueueBlocks();

        for (int i = 0; i < cellsX; i++) {
            for (int j = 0; j < cellsZ; j++) {
                int centerX = minX + maxWidth / 2 + i * cellWidth;
                int centerZ = minZ + maxDepth / 2 + j * cellDepth;

                Random random = new Random(zoneSeed(minX, minZ, i, j));
                BuildingTemplate template = candidates.get(random.nextInt(candidates.size()));
                TemplateRotation rotation = fixedRotation != null
                        ? fixedRotation
                        : TemplateRotation.byIndex(i * 31 + j * 17);

                try {
                    PlacementPlan plan = planner.plan(template, rotation, world, centerX, centerZ, force);
                    if ((long) changes.size() + plan.blockCount() > maxQueueBlocks) {
                        throw new BuildException("批量建造方块总数超过单任务上限 " + maxQueueBlocks, "超过上限");
                    }
                    changes.addAll(plan.changes());
                    placed++;
                } catch (BuildException e) {
                    skipped.merge(e.reason(), 1, Integer::sum);
                }
            }
        }

        return new ZoneResult(List.copyOf(changes), placed, totalCells, skipped);
    }

    private static long zoneSeed(int minX, int minZ, int cellX, int cellZ) {
        return (long) minX * 31L + (long) minZ * 17L + (long) cellX * 341_873L + (long) cellZ * 132_897L;
    }
}
