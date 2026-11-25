package com.xiling.city.builder;

import com.xiling.city.CityPlugin;
import org.bukkit.World;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player, in-memory undo history of completed (or cancelled) builds.
 */
public class BuildUndoManager {

    public record UndoRecord(World world, List<UndoEntry> entries, String label) {
    }

    private final CityPlugin plugin;
    private final Map<UUID, Deque<UndoRecord>> history = new HashMap<>();

    public BuildUndoManager(CityPlugin plugin) {
        this.plugin = plugin;
    }

    public void push(UUID owner, UndoRecord record) {
        int maxHistory = plugin.getCityConfig().undoHistory();
        if (maxHistory <= 0 || record.entries().isEmpty()) {
            return;
        }
        Deque<UndoRecord> deque = history.computeIfAbsent(owner, key -> new ArrayDeque<>());
        deque.addLast(record);
        while (deque.size() > maxHistory) {
            deque.removeFirst();
        }
    }

    public UndoRecord pop(UUID owner) {
        Deque<UndoRecord> deque = history.get(owner);
        if (deque == null) {
            return null;
        }
        UndoRecord record = deque.pollLast();
        if (deque.isEmpty()) {
            history.remove(owner);
        }
        return record;
    }

    public boolean has(UUID owner) {
        Deque<UndoRecord> deque = history.get(owner);
        return deque != null && !deque.isEmpty();
    }

    public void clear(UUID owner) {
        history.remove(owner);
    }
}
