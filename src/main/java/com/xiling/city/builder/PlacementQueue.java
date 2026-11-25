package com.xiling.city.builder;

import com.xiling.city.CityPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Global FIFO queue that places blocks across ticks, sharing one
 * {@code builder.blocks-per-tick} budget between all players.
 */
public class PlacementQueue {

    private final CityPlugin plugin;
    private final Deque<PlacementJob> queue = new ArrayDeque<>();
    private BukkitTask task;
    private int tickCounter;

    public PlacementQueue(CityPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (task != null) {
            return;
        }
        task = new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        queue.clear();
    }

    public boolean hasJobs(UUID owner) {
        for (PlacementJob job : queue) {
            if (job.owner().equals(owner)) {
                return true;
            }
        }
        return false;
    }

    public PlacementJob submit(UUID owner, String ownerName, World world,
                               List<BlockChange> changes, String label, boolean trackUndo)
            throws BuildException {
        if (changes.isEmpty()) {
            throw new BuildException("没有需要放置的方块", "空任务");
        }
        int maxQueueBlocks = plugin.getCityConfig().maxQueueBlocks();
        if (changes.size() > maxQueueBlocks) {
            throw new BuildException("任务方块数 " + changes.size()
                    + " 超过上限 " + maxQueueBlocks, "超过上限");
        }
        if (hasJobs(owner)) {
            throw new BuildException("你已有正在进行的建造任务，请等待完成或使用 /city build cancel", "任务进行中");
        }

        PlacementJob job = new PlacementJob(owner, ownerName, world, label, changes, trackUndo);
        queue.addLast(job);
        return job;
    }

    public int cancel(UUID owner) {
        int cancelled = 0;
        Iterator<PlacementJob> iterator = queue.iterator();
        while (iterator.hasNext()) {
            PlacementJob job = iterator.next();
            if (job.owner().equals(owner)) {
                iterator.remove();
                finish(job, false);
                cancelled++;
            }
        }
        return cancelled;
    }

    /**
     * @return a short status line, or {@code null} when the player has no job.
     */
    public String status(UUID owner) {
        for (PlacementJob job : queue) {
            if (job.owner().equals(owner)) {
                return job.label() + " — " + job.progressPercent() + "%（"
                        + job.completed() + "/" + job.total() + " 方块）";
            }
        }
        return null;
    }

    private void tick() {
        tickCounter++;

        int budget = plugin.getCityConfig().blocksPerTick();
        while (budget > 0 && !queue.isEmpty()) {
            PlacementJob job = queue.peekFirst();
            int processed = 0;

            while (processed < budget && !job.isDone()) {
                BlockChange change = job.next();
                Block block = job.world().getBlockAt(change.x(), change.y(), change.z());
                if (job.trackUndo()) {
                    job.addUndo(new UndoEntry(change.x(), change.y(), change.z(), block.getBlockData()));
                }
                block.setBlockData(change.data(), false);
                processed++;
            }

            budget -= processed;

            if (job.isDone()) {
                queue.pollFirst();
                finish(job, true);
            } else {
                break;
            }
        }

        if (tickCounter % 10 == 0) {
            PlacementJob head = queue.peekFirst();
            if (head != null) {
                showProgress(head);
            }
        }
    }

    private void showProgress(PlacementJob job) {
        Player player = Bukkit.getPlayer(job.owner());
        if (player == null || !player.isOnline()) {
            return;
        }
        Component bar = Component.text("🏗 建造中 " + job.progressPercent() + "%（"
                + job.completed() + "/" + job.total() + "）", NamedTextColor.YELLOW);
        player.sendActionBar(bar);
    }

    private void finish(PlacementJob job, boolean completed) {
        Player player = Bukkit.getPlayer(job.owner());
        if (player != null && player.isOnline()) {
            if (completed) {
                player.sendMessage(plugin.getCityConfig().messagePrefix()
                        + "§a建造完成：§f" + job.label()
                        + "§a，§f" + job.total() + " §a方块，用时 §f"
                        + String.format("%.1f", job.elapsedSeconds()) + "s");
            } else {
                player.sendMessage(plugin.getCityConfig().messagePrefix()
                        + "§e建造已取消：§f" + job.label()
                        + "§e（已完成 §f" + job.progressPercent() + "%§e，可 /city build undo 撤销）");
            }
        }

        if (job.trackUndo() && !job.undoEntries().isEmpty()) {
            plugin.getUndoManager().push(job.owner(),
                    new BuildUndoManager.UndoRecord(job.world(), List.copyOf(job.undoEntries()), job.label()));
        }
    }
}
