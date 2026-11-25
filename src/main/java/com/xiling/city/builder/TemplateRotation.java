package com.xiling.city.builder;

import org.bukkit.block.structure.StructureRotation;

/**
 * The four rotations supported when placing a building template.
 */
public enum TemplateRotation {

    NONE(0, StructureRotation.NONE),
    CLOCKWISE_90(90, StructureRotation.CLOCKWISE_90),
    CLOCKWISE_180(180, StructureRotation.CLOCKWISE_180),
    COUNTERCLOCKWISE_90(270, StructureRotation.COUNTERCLOCKWISE_90);

    private final int degrees;
    private final StructureRotation structureRotation;

    TemplateRotation(int degrees, StructureRotation structureRotation) {
        this.degrees = degrees;
        this.structureRotation = structureRotation;
    }

    public int degrees() {
        return degrees;
    }

    public StructureRotation structureRotation() {
        return structureRotation;
    }

    public boolean swapsAxes() {
        return this == CLOCKWISE_90 || this == COUNTERCLOCKWISE_90;
    }

    public static TemplateRotation ofDegrees(int degrees) {
        return switch (Math.floorMod(degrees, 360)) {
            case 90 -> CLOCKWISE_90;
            case 180 -> CLOCKWISE_180;
            case 270 -> COUNTERCLOCKWISE_90;
            default -> NONE;
        };
    }

    public static TemplateRotation byIndex(int index) {
        TemplateRotation[] values = values();
        return values[Math.floorMod(index, values.length)];
    }
}
