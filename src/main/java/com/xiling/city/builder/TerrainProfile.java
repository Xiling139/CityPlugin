package com.xiling.city.builder;

/**
 * Terrain scan result for a placement footprint.
 *
 * <p>{@code ground[x][z]} is the Y of the highest solid ground block in that
 * column; {@code water[x][z]} marks columns where water was found above the
 * ground.</p>
 */
public final class TerrainProfile {

    private final int minX;
    private final int minZ;
    private final int width;
    private final int depth;
    private final int[][] ground;
    private final boolean[][] water;
    private final int baseY;
    private final int minGround;
    private final int maxGround;

    public TerrainProfile(int minX, int minZ, int width, int depth,
                          int[][] ground, boolean[][] water,
                          int baseY, int minGround, int maxGround) {
        this.minX = minX;
        this.minZ = minZ;
        this.width = width;
        this.depth = depth;
        this.ground = ground;
        this.water = water;
        this.baseY = baseY;
        this.minGround = minGround;
        this.maxGround = maxGround;
    }

    public int minX() {
        return minX;
    }

    public int minZ() {
        return minZ;
    }

    public int width() {
        return width;
    }

    public int depth() {
        return depth;
    }

    public int groundAt(int dx, int dz) {
        return ground[dx][dz];
    }

    public boolean hasWaterAt(int dx, int dz) {
        return water[dx][dz];
    }

    public int baseY() {
        return baseY;
    }

    public int minGround() {
        return minGround;
    }

    public int maxGround() {
        return maxGround;
    }

    /**
     * Number of foundation layers required at the lowest column.
     */
    public int foundationDepth() {
        return baseY - minGround - 1;
    }
}
