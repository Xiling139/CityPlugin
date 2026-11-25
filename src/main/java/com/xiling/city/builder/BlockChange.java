package com.xiling.city.builder;

import org.bukkit.block.data.BlockData;

/**
 * A single block to place, in world coordinates.
 */
public record BlockChange(int x, int y, int z, BlockData data) {
}
