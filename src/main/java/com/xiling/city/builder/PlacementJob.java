package com.xiling.city.builder;

import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One queued generation (or undo) task, executed gradually by
 * {@link PlacementQueue}.
 */
public final class PlacementJob {

    private final UUID owner;
    private final String ownerName;
    private final World world;
    private final String label;
    private final List<BlockChange> changes;
    private final boolean trackUndo;
    private final List<UndoEntry> undoEntries = new ArrayList<>();
    private final long startNanos = System.nanoTime();
    private int cursor;

    public PlacementJob(UUID owner, String ownerName, World world, String label,
                        List<BlockChange> changes, boolean trackUndo) {
        this.owner = owner;
        this.ownerName = ownerName;
        this.world = world;
        this.label = label;
        this.changes = changes;
        this.trackUndo = trackUndo;
    }

    public UUID owner() {
        return owner;
    }

    public String ownerName() {
        return ownerName;
    }

    public World world() {
        return world;
    }

    public String label() {
        return label;
    }

    public boolean trackUndo() {
        return trackUndo;
    }

    public boolean isDone() {
        return cursor >= changes.size();
    }

    public int total() {
        return changes.size();
    }

    public int completed() {
        return cursor;
    }

    public int remaining() {
        return changes.size() - cursor;
    }

    public BlockChange next() {
        return changes.get(cursor++);
    }

    public int progressPercent() {
        return changes.isEmpty() ? 100 : (int) (cursor * 100L / changes.size());
    }

    public void addUndo(UndoEntry entry) {
        undoEntries.add(entry);
    }

    public List<UndoEntry> undoEntries() {
        return undoEntries;
    }

    public double elapsedSeconds() {
        return (System.nanoTime() - startNanos) / 1_000_000_000.0;
    }
}
