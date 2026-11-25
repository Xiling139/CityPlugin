package com.xiling.city.builder;

import org.bukkit.block.data.BlockData;

/**
 * The previous state of a block, recorded while a job runs so the build can be
 * undone.
 */
public record UndoEntry(int x, int y, int z, BlockData previous) {
}
