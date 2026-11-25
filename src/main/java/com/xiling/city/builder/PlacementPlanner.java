package com.xiling.city.builder;

import com.xiling.city.CityPlugin;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns a template + target position into a validated {@link PlacementPlan}:
 * scans terrain, fills the foundation and lays out the building blocks.
 */
public class PlacementPlanner {

    private final CityPlugin plugin;
    private final PlacementValidator validator;

    public PlacementPlanner(CityPlugin plugin) {
        this.plugin = plugin;
        this.validator = new PlacementValidator(plugin);
    }

    public PlacementPlan plan(BuildingTemplate template, TemplateRotation rotation, World world,
                              int centerX, int centerZ, boolean force) throws BuildException {
        BuildingTemplate rotated = template.rotated(rotation);

        int width = rotated.width();
        int depth = rotated.depth();
        int minX = centerX - rotated.anchorX();
        int minZ = centerZ - rotated.anchorZ();

        TerrainProfile terrain = TerrainScanner.scan(world, minX, minZ, width, depth, plugin);
        validator.validate(rotated, terrain, world, minX, minZ, force);

        int baseY = terrain.baseY();
        Material foundationMaterial = plugin.getCityConfig().foundationBlock(template.category());
        BlockData foundationData = foundationMaterial.createBlockData();

        List<BlockChange> changes = new ArrayList<>();

        // Foundation: fill each column from just above the ground up to the base.
        for (int dx = 0; dx < width; dx++) {
            for (int dz = 0; dz < depth; dz++) {
                int ground = terrain.groundAt(dx, dz);
                int worldX = minX + dx;
                int worldZ = minZ + dz;
                for (int y = ground + 1; y < baseY; y++) {
                    if (world.getBlockAt(worldX, y, worldZ).getType() != foundationMaterial) {
                        changes.add(new BlockChange(worldX, y, worldZ, foundationData));
                    }
                }
            }
        }

        // Building blocks.
        for (BuildingTemplate.Block block : rotated.blocks()) {
            changes.add(new BlockChange(minX + block.x(), baseY + block.y(), minZ + block.z(), block.data()));
        }

        int maxQueueBlocks = plugin.getCityConfig().maxQueueBlocks();
        if (changes.size() > maxQueueBlocks) {
            throw new BuildException("本次建造需要 " + changes.size()
                    + " 个方块，超过单任务上限 " + maxQueueBlocks, "超过上限");
        }

        return new PlacementPlan(world, template.name(), changes, baseY, terrain.foundationDepth(),
                width, depth, minX, minZ);
    }
}
