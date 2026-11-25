package com.xiling.city.commands;

import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.xiling.city.CityPlugin;
import com.xiling.city.builder.BuildException;
import com.xiling.city.builder.BuildUndoManager;
import com.xiling.city.builder.BuildingRegistry;
import com.xiling.city.builder.BuildingTemplate;
import com.xiling.city.builder.TemplateRotation;
import com.xiling.city.builder.UndoEntry;
import com.xiling.city.builder.ZonePlanner;
import com.xiling.city.builder.BlockChange;
import com.xiling.city.builder.PlacementPlan;
import com.xiling.city.util.Perms;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Implements {@code /city build at|zone|list|reload|status|cancel|undo}.
 *
 * <p>When {@code defaultSubcommand} is set, the same parser is reused by the
 * standalone {@code /buildat} and {@code /buildzone} commands.</p>
 */
public class BuildCommand implements CommandExecutor {

    private final CityPlugin plugin;
    private final String defaultSubcommand;

    public BuildCommand(CityPlugin plugin, String defaultSubcommand) {
        this.plugin = plugin;
        this.defaultSubcommand = defaultSubcommand;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        String subcommand;
        String[] rest;

        if (defaultSubcommand != null) {
            subcommand = defaultSubcommand;
            rest = args;
        } else {
            if (args.length == 0) {
                sendHelp(player);
                return true;
            }
            subcommand = args[0].toLowerCase(Locale.ROOT);
            rest = Arrays.copyOfRange(args, 1, args.length);
        }

        return switch (subcommand) {
            case "at" -> runAt(player, rest);
            case "zone" -> runZone(player, rest);
            case "list" -> runList(player, rest);
            case "reload" -> runReload(player);
            case "status" -> runStatus(player);
            case "cancel" -> runCancel(player);
            case "undo" -> runUndo(player);
            default -> {
                sendHelp(player);
                yield true;
            }
        };
    }

    // ------------------------------------------------------------------
    // /city build at
    // ------------------------------------------------------------------

    private boolean runAt(Player player, String[] args) {
        if (!require(player, "city.build")) {
            return true;
        }
        BuildingRegistry registry = readyRegistry(player);
        if (registry == null) {
            return true;
        }
        if (args.length == 0) {
            msg(player, "§c用法：/city build at <模板> [0|90|180|270] [--force] [--preview]");
            return true;
        }

        String templateName = args[0];
        TemplateRotation rotation = TemplateRotation.NONE;
        boolean force = false;
        boolean preview = false;

        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            if (arg.equalsIgnoreCase("--force") || arg.equalsIgnoreCase("-f")) {
                force = true;
            } else if (arg.equalsIgnoreCase("--preview")) {
                preview = true;
            } else {
                try {
                    rotation = TemplateRotation.ofDegrees(Integer.parseInt(arg));
                } catch (NumberFormatException e) {
                    msg(player, "§c无效的旋转角度：§f" + arg + "§c（可用 0/90/180/270）");
                    return true;
                }
            }
        }

        if (force && !Perms.has(player, "city.build.admin")) {
            msg(player, "§c你没有权限使用 --force");
            return true;
        }

        BuildingTemplate template = registry.get(templateName);
        if (template == null) {
            msg(player, "§c找不到建筑模板：§f" + templateName);
            suggestTemplates(player, registry);
            return true;
        }

        Block target = player.getTargetBlockExact(6);
        if (target == null) {
            target = player.getLocation().getBlock().getRelative(BlockFace.DOWN);
        }

        PlacementPlan plan;
        try {
            plan = plugin.getPlacementPlanner().plan(template, rotation, player.getWorld(),
                    target.getX(), target.getZ(), force);
        } catch (BuildException e) {
            msg(player, "§c" + e.getMessage());
            return true;
        }

        if (preview) {
            msg(player, "§e预览：§f" + template.name() + "§e，旋转 §f" + rotation.degrees()
                    + "°§e，占地 §f" + plan.width() + "×" + plan.depth()
                    + "§e（x " + plan.minX() + ".." + (plan.minX() + plan.width() - 1)
                    + "，z " + plan.minZ() + ".." + (plan.minZ() + plan.depth() - 1)
                    + "），基准面 §fy=" + plan.baseY()
                    + "§e，地基深度 §f" + plan.foundationDepth()
                    + "§e，共 §f" + plan.blockCount() + " §e方块");
            return true;
        }

        if (plugin.getPlacementQueue().hasJobs(player.getUniqueId())) {
            msg(player, "§c你已有正在进行的建造任务，请等待完成或使用 §f/city build cancel");
            return true;
        }

        try {
            plugin.getPlacementQueue().submit(player.getUniqueId(), player.getName(), plan.world(),
                    plan.changes(), template.name(), true);
        } catch (BuildException e) {
            msg(player, "§c" + e.getMessage());
            return true;
        }

        msg(player, "§a已加入建造队列：§f" + template.name() + "§a，旋转 §f" + rotation.degrees()
                + "°§a，共 §f" + plan.blockCount() + " §a方块，预计 §f"
                + estimateSeconds(plan.blockCount()) + "s");
        return true;
    }

    // ------------------------------------------------------------------
    // /city build zone
    // ------------------------------------------------------------------

    private boolean runZone(Player player, String[] args) {
        if (!require(player, "city.build.zone")) {
            return true;
        }
        BuildingRegistry registry = readyRegistry(player);
        if (registry == null) {
            return true;
        }
        if (args.length == 0) {
            msg(player, "§c用法：/city build zone <模板|random|类别> [--spacing N] [--radius N] "
                    + "[--rotation 0|90|180|270|auto] [--force] [--preview]");
            return true;
        }

        String spec = args[0];
        TemplateRotation rotation = TemplateRotation.NONE;
        boolean autoRotation = false;
        boolean force = false;
        boolean preview = false;
        Integer spacing = null;
        Integer radius = null;

        for (int i = 1; i < args.length; i++) {
            String arg = args[i].toLowerCase(Locale.ROOT);
            switch (arg) {
                case "--force", "-f" -> force = true;
                case "--preview" -> preview = true;
                case "--spacing" -> {
                    if (++i >= args.length) {
                        msg(player, "§c--spacing 需要一个数值（0–32）");
                        return true;
                    }
                    spacing = parseRange(player, args[i], 0, 32, "--spacing");
                    if (spacing == null) {
                        return true;
                    }
                }
                case "--radius" -> {
                    if (++i >= args.length) {
                        msg(player, "§c--radius 需要一个数值（1–64）");
                        return true;
                    }
                    radius = parseRange(player, args[i], 1, 64, "--radius");
                    if (radius == null) {
                        return true;
                    }
                }
                case "--rotation" -> {
                    if (++i >= args.length) {
                        msg(player, "§c--rotation 需要 0/90/180/270/auto");
                        return true;
                    }
                    String value = args[i];
                    if (value.equalsIgnoreCase("auto")) {
                        autoRotation = true;
                    } else {
                        try {
                            rotation = TemplateRotation.ofDegrees(Integer.parseInt(value));
                            autoRotation = false;
                        } catch (NumberFormatException e) {
                            msg(player, "§c无效的旋转角度：§f" + value + "§c（可用 0/90/180/270/auto）");
                            return true;
                        }
                    }
                }
                default -> {
                    msg(player, "§c未知参数：§f" + arg);
                    msg(player, "§7可用：--spacing N、--radius N、--rotation …、--force、--preview");
                    return true;
                }
            }
        }

        if (force && !Perms.has(player, "city.build.admin")) {
            msg(player, "§c你没有权限使用 --force");
            return true;
        }

        List<BuildingTemplate> candidates = registry.resolve(spec);
        if (candidates.isEmpty()) {
            msg(player, "§c未知模板或类别：§f" + spec);
            suggestTemplates(player, registry);
            return true;
        }

        World world = player.getWorld();
        int minX;
        int minZ;
        int maxX;
        int maxZ;

        Region selection = getWorldEditSelection(player);
        if (selection != null) {
            BlockVector3 min = selection.getMinimumPoint();
            BlockVector3 max = selection.getMaximumPoint();
            minX = min.x();
            minZ = min.z();
            maxX = max.x();
            maxZ = max.z();
        } else {
            int r = radius != null ? radius : 16;
            Block base = player.getLocation().getBlock();
            minX = base.getX() - r;
            minZ = base.getZ() - r;
            maxX = base.getX() + r;
            maxZ = base.getZ() + r;
        }

        long area = (long) (maxX - minX + 1) * (maxZ - minZ + 1);
        if (area > plugin.getCityConfig().zoneMaxArea()) {
            msg(player, "§c区域过大：§f" + area + " §c格，上限 §f"
                    + plugin.getCityConfig().zoneMaxArea()
                    + "§c（可缩小选区或使用 --radius）");
            return true;
        }

        int spacingValue = spacing != null ? spacing : plugin.getCityConfig().zoneSpacing();
        TemplateRotation fixedRotation = autoRotation ? null : rotation;

        ZonePlanner.ZoneResult result;
        try {
            result = plugin.getZonePlanner().plan(world, minX, minZ, maxX, maxZ, candidates,
                    fixedRotation, spacingValue, force);
        } catch (BuildException e) {
            msg(player, "§c" + e.getMessage());
            return true;
        }

        String skipSummary = formatSkips(result);

        if (result.changes().isEmpty()) {
            msg(player, "§c没有可生成的位置" + (skipSummary.isEmpty() ? "" : "（" + skipSummary + "）"));
            return true;
        }

        if (preview) {
            msg(player, "§e预览：区域 §f" + (maxX - minX + 1) + "×" + (maxZ - minZ + 1)
                    + "§e，成功 §f" + result.placedBuildings() + "/" + result.totalCells()
                    + " §e栋，共 §f" + result.changes().size() + " §e方块"
                    + (skipSummary.isEmpty() ? "" : "（" + skipSummary + "）"));
            return true;
        }

        if (plugin.getPlacementQueue().hasJobs(player.getUniqueId())) {
            msg(player, "§c你已有正在进行的建造任务，请等待完成或使用 §f/city build cancel");
            return true;
        }

        try {
            plugin.getPlacementQueue().submit(player.getUniqueId(), player.getName(), world,
                    result.changes(), "zone:" + spec, true);
        } catch (BuildException e) {
            msg(player, "§c" + e.getMessage());
            return true;
        }

        msg(player, "§a街区已加入建造队列：§f" + result.placedBuildings() + "/"
                + result.totalCells() + " §a栋，共 §f" + result.changes().size() + " §a方块，预计 §f"
                + estimateSeconds(result.changes().size()) + "s"
                + (skipSummary.isEmpty() ? "" : "（" + skipSummary + "）"));
        return true;
    }

    // ------------------------------------------------------------------
    // other subcommands
    // ------------------------------------------------------------------

    private boolean runList(Player player, String[] args) {
        if (!require(player, "city.build")) {
            return true;
        }
        BuildingRegistry registry = readyRegistry(player);
        if (registry == null) {
            return true;
        }

        List<BuildingTemplate> templates = registry.all();
        if (args.length > 0) {
            templates = registry.byCategory(args[0]);
            if (templates.isEmpty()) {
                msg(player, "§c没有类别：§f" + args[0]);
                msg(player, "§7可用类别：§f" + String.join("、", registry.categories()));
                return true;
            }
        }

        if (templates.isEmpty()) {
            msg(player, "§e还没有建筑模板。把 WorldEdit .schem 文件放入：");
            msg(player, "§f" + plugin.getDataFolder().getAbsolutePath() + "/"
                    + plugin.getCityConfig().buildingsFolder() + "/<类别>/<名称>.schem");
            return true;
        }

        Map<String, List<BuildingTemplate>> grouped = new TreeMap<>();
        for (BuildingTemplate template : templates) {
            grouped.computeIfAbsent(template.category(), key -> new ArrayList<>()).add(template);
        }

        msg(player, "§6=== 建筑模板（" + templates.size() + "）===");
        for (Map.Entry<String, List<BuildingTemplate>> entry : grouped.entrySet()) {
            player.sendMessage("§2§l[" + entry.getKey() + "]");
            for (BuildingTemplate template : entry.getValue()) {
                player.sendMessage("§f  " + template.name()
                        + " §7- §f" + template.width() + "×" + template.height() + "×" + template.depth()
                        + "§7，§e" + template.blocks().size() + " §7方块");
            }
        }
        return true;
    }

    private boolean runReload(Player player) {
        if (!require(player, "city.build.admin")) {
            return true;
        }
        if (plugin.getBuildingRegistry() == null) {
            msg(player, "§c建筑生成功能不可用（缺少 WorldEdit 或已在配置中关闭）");
            return true;
        }
        plugin.getBuildingRegistry().reload();
        msg(player, "§a已重新加载建筑模板：§f" + plugin.getBuildingRegistry().size() + " §a个");
        return true;
    }

    private boolean runStatus(Player player) {
        if (!require(player, "city.build")) {
            return true;
        }
        String status = plugin.getPlacementQueue().status(player.getUniqueId());
        msg(player, status != null ? "§e当前任务：§f" + status : "§e当前没有进行中的建造任务");
        return true;
    }

    private boolean runCancel(Player player) {
        if (!require(player, "city.build")) {
            return true;
        }
        int cancelled = plugin.getPlacementQueue().cancel(player.getUniqueId());
        if (cancelled == 0) {
            msg(player, "§e当前没有进行中的建造任务");
        } else {
            msg(player, "§a已取消 §f" + cancelled + " §a个建造任务（已放置方块保留，可 undo）");
        }
        return true;
    }

    private boolean runUndo(Player player) {
        if (!require(player, "city.build.undo")) {
            return true;
        }
        if (plugin.getPlacementQueue().hasJobs(player.getUniqueId())) {
            msg(player, "§c请等待当前建造任务结束后再撤销");
            return true;
        }

        BuildUndoManager.UndoRecord record = plugin.getUndoManager().pop(player.getUniqueId());
        if (record == null) {
            msg(player, "§e没有可撤销的建造记录");
            return true;
        }

        List<UndoEntry> entries = record.entries();
        List<BlockChange> changes = new ArrayList<>(entries.size());
        for (int i = entries.size() - 1; i >= 0; i--) {
            UndoEntry entry = entries.get(i);
            changes.add(new BlockChange(entry.x(), entry.y(), entry.z(), entry.previous()));
        }

        try {
            plugin.getPlacementQueue().submit(player.getUniqueId(), player.getName(), record.world(),
                    changes, "undo:" + record.label(), false);
        } catch (BuildException e) {
            msg(player, "§c" + e.getMessage());
            return true;
        }

        msg(player, "§a开始撤销：§f" + record.label() + "§a，共 §f" + changes.size() + " §a方块");
        return true;
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private void sendHelp(Player player) {
        msg(player, "§6=== 建筑生成 ===");
        player.sendMessage("§f/city build at <模板> [0|90|180|270] [--force] [--preview]");
        player.sendMessage("§f/city build zone <模板|random|类别> [--spacing N] [--radius N] [--rotation …]");
        player.sendMessage("§f/city build list [类别] §7- 查看模板");
        player.sendMessage("§f/city build status §7/ §fcancel §7/ §fundo");
        player.sendMessage("§7别名：§f/buildat§7、§f/buildzone");
        if (Perms.has(player, "city.build.admin")) {
            player.sendMessage("§f/city build reload §7- 重新扫描模板目录");
        }
    }

    private boolean require(Player player, String node) {
        if (Perms.has(player, node)) {
            return true;
        }
        msg(player, "§c你没有权限执行该命令");
        return false;
    }

    private BuildingRegistry readyRegistry(Player player) {
        if (!plugin.getCityConfig().builderEnabled()) {
            msg(player, "§c建筑生成功能已在配置中关闭");
            return null;
        }
        BuildingRegistry registry = plugin.getBuildingRegistry();
        if (registry == null) {
            msg(player, "§c建筑生成功能不可用（需要安装 WorldEdit）");
            return null;
        }
        return registry;
    }

    private Integer parseRange(Player player, String value, int min, int max, String option) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < min || parsed > max) {
                msg(player, "§c" + option + " 必须在 " + min + "–" + max + " 之间");
                return null;
            }
            return parsed;
        } catch (NumberFormatException e) {
            msg(player, "§c" + option + " 需要一个整数");
            return null;
        }
    }

    private void suggestTemplates(Player player, BuildingRegistry registry) {
        if (registry.size() == 0) {
            msg(player, "§7请将 .schem 模板放入 plugins/CityPlugin/buildings/<类别>/");
            return;
        }
        List<String> names = new ArrayList<>();
        for (BuildingTemplate template : registry.all()) {
            names.add(template.name());
            if (names.size() >= 10) {
                break;
            }
        }
        msg(player, "§7可用模板（部分）：§f" + String.join("、", names)
                + (registry.size() > names.size() ? " ……" : ""));
    }

    private String formatSkips(ZonePlanner.ZoneResult result) {
        Map<String, Integer> skipped = result.skippedReasons();
        if (skipped.isEmpty()) {
            return "";
        }
        Map<String, Integer> sorted = new LinkedHashMap<>();
        skipped.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(entry -> sorted.put(entry.getKey(), entry.getValue()));

        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : sorted.entrySet()) {
            parts.add(entry.getKey() + " " + entry.getValue());
        }
        return "跳过 " + (result.totalCells() - result.placedBuildings()) + " 栋：" + String.join("、", parts);
    }

    private String estimateSeconds(int blocks) {
        double seconds = blocks / (double) plugin.getCityConfig().blocksPerTick() / 20.0;
        return String.format("%.1f", seconds);
    }

    private Region getWorldEditSelection(Player player) {
        if (plugin.getServer().getPluginManager().getPlugin("WorldEdit") == null) {
            return null;
        }
        var wePlayer = BukkitAdapter.adapt(player);
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(wePlayer);
        if (!session.isSelectionDefined(wePlayer.getWorld())) {
            return null;
        }
        try {
            return session.getSelection(wePlayer.getWorld());
        } catch (com.sk89q.worldedit.IncompleteRegionException e) {
            return null;
        }
    }

    private void msg(Player player, String text) {
        player.sendMessage(plugin.getCityConfig().messagePrefix() + text);
    }
}
