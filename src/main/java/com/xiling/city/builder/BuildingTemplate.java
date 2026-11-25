package com.xiling.city.builder;

import org.bukkit.block.data.BlockData;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A loaded building template: its blocks relative to the template's minimum
 * corner, plus cached rotated variants.
 *
 * <p>The placement anchor is the horizontal centre ({@link #anchorX()} /
 * {@link #anchorZ()}) and the lowest template layer (y = 0).</p>
 */
public final class BuildingTemplate {

    /**
     * A template block relative to the template's min corner.
     */
    public record Block(int x, int y, int z, BlockData data) {
    }

    private final String name;
    private final String category;
    private final File file;
    private final int width;
    private final int height;
    private final int depth;
    private final List<Block> blocks;
    private final Map<TemplateRotation, BuildingTemplate> rotations = new EnumMap<>(TemplateRotation.class);

    public BuildingTemplate(String name, String category, File file,
                            int width, int height, int depth, List<Block> blocks) {
        this.name = name;
        this.category = category;
        this.file = file;
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.blocks = List.copyOf(blocks);
    }

    public String name() {
        return name;
    }

    public String category() {
        return category;
    }

    public File file() {
        return file;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int depth() {
        return depth;
    }

    public List<Block> blocks() {
        return blocks;
    }

    public int anchorX() {
        return width / 2;
    }

    public int anchorZ() {
        return depth / 2;
    }

    public int volume() {
        return width * height * depth;
    }

    /**
     * Returns the template rotated around the vertical axis. Results are cached
     * per rotation; the original instance is returned for {@link TemplateRotation#NONE}.
     */
    public BuildingTemplate rotated(TemplateRotation rotation) {
        if (rotation == TemplateRotation.NONE) {
            return this;
        }
        BuildingTemplate cached = rotations.get(rotation);
        if (cached != null) {
            return cached;
        }

        int newWidth = rotation.swapsAxes() ? depth : width;
        int newDepth = rotation.swapsAxes() ? width : depth;

        List<Block> rotatedBlocks = new ArrayList<>(blocks.size());
        for (Block block : blocks) {
            int x;
            int z;
            switch (rotation) {
                case CLOCKWISE_90 -> {
                    x = depth - 1 - block.z();
                    z = block.x();
                }
                case CLOCKWISE_180 -> {
                    x = width - 1 - block.x();
                    z = depth - 1 - block.z();
                }
                case COUNTERCLOCKWISE_90 -> {
                    x = block.z();
                    z = width - 1 - block.x();
                }
                default -> {
                    x = block.x();
                    z = block.z();
                }
            }

            BlockData data = block.data().clone();
            data.rotate(rotation.structureRotation());
            rotatedBlocks.add(new Block(x, block.y(), z, data));
        }

        BuildingTemplate result = new BuildingTemplate(name, category, file,
                newWidth, height, newDepth, rotatedBlocks);
        rotations.put(rotation, result);
        return result;
    }

    @Override
    public String toString() {
        return name + " (" + width + "x" + height + "x" + depth + ", " + blocks.size() + " blocks)";
    }
}
