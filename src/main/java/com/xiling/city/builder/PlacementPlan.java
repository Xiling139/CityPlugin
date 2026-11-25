package com.xiling.city.builder;

import org.bukkit.World;

import java.util.List;

/**
 * A fully validated list of block changes for one building (or a whole zone).
 */
public final class PlacementPlan {

    private final World world;
    private final String label;
    private final List<BlockChange> changes;
    private final int baseY;
    private final int foundationDepth;
    private final int width;
    private final int depth;
    private final int minX;
    private final int minZ;

    public PlacementPlan(World world, String label, List<BlockChange> changes,
                         int baseY, int foundationDepth,
                         int width, int depth, int minX, int minZ) {
        this.world = world;
        this.label = label;
        this.changes = List.copyOf(changes);
        this.baseY = baseY;
        this.foundationDepth = foundationDepth;
        this.width = width;
        this.depth = depth;
        this.minX = minX;
        this.minZ = minZ;
    }

    public World world() {
        return world;
    }

    public String label() {
        return label;
    }

    public List<BlockChange> changes() {
        return changes;
    }

    public int baseY() {
        return baseY;
    }

    public int foundationDepth() {
        return foundationDepth;
    }

    public int width() {
        return width;
    }

    public int depth() {
        return depth;
    }

    public int minX() {
        return minX;
    }

    public int minZ() {
        return minZ;
    }

    public int blockCount() {
        return changes.size();
    }
}
