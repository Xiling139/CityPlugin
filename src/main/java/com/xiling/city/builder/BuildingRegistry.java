package com.xiling.city.builder;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockTypes;
import com.xiling.city.CityPlugin;
import org.bukkit.block.data.BlockData;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Scans {@code plugins/CityPlugin/buildings/} for WorldEdit schematics and
 * loads them as {@link BuildingTemplate}s.
 *
 * <p>Template names are the path relative to the buildings folder without the
 * extension, e.g. {@code residential/house_small}. The first path segment is
 * the category.</p>
 */
public class BuildingRegistry {

    private final CityPlugin plugin;
    private final Map<String, BuildingTemplate> templates = new LinkedHashMap<>();
    private final Map<String, List<BuildingTemplate>> byCategory = new TreeMap<>();

    public BuildingRegistry(CityPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        templates.clear();
        byCategory.clear();

        File directory = new File(plugin.getDataFolder(), plugin.getCityConfig().buildingsFolder());
        if (!directory.exists() && !directory.mkdirs()) {
            plugin.getLogger().warning("Could not create buildings folder: " + directory.getAbsolutePath());
            return;
        }

        List<File> files;
        try (Stream<Path> stream = Files.walk(directory.toPath())) {
            files = stream
                    .filter(Files::isRegularFile)
                    .filter(BuildingRegistry::isSchematic)
                    .sorted()
                    .map(Path::toFile)
                    .toList();
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to scan buildings folder: " + e.getMessage());
            return;
        }

        // Prefer .schem over .schematic when both exist for the same name.
        Map<String, File> chosen = new LinkedHashMap<>();
        for (File file : files) {
            String name = relativeName(directory, file);
            String key = name.toLowerCase(Locale.ROOT);
            File existing = chosen.get(key);
            if (existing == null || prefer(file, existing)) {
                chosen.put(key, file);
            }
        }

        int loaded = 0;
        for (Map.Entry<String, File> entry : chosen.entrySet()) {
            String name = relativeName(directory, entry.getValue());
            try {
                BuildingTemplate template = loadTemplate(name, entry.getValue());
                templates.put(entry.getKey(), template);
                byCategory.computeIfAbsent(template.category(), key -> new ArrayList<>()).add(template);
                loaded++;
            } catch (IOException | RuntimeException e) {
                plugin.getLogger().warning("Skipped building template '" + name + "': " + e.getMessage());
            }
        }

        if (loaded == 0) {
            plugin.getLogger().info("No building templates found. Put WorldEdit .schem files into "
                    + directory.getAbsolutePath() + "/<category>/<name>.schem");
        } else {
            plugin.getLogger().info("Loaded " + loaded + " building template(s) in "
                    + byCategory.size() + " category/categories.");
        }
    }

    public BuildingTemplate get(String name) {
        return templates.get(name.toLowerCase(Locale.ROOT));
    }

    public List<BuildingTemplate> all() {
        return List.copyOf(templates.values());
    }

    public Collection<String> categories() {
        return byCategory.keySet();
    }

    public List<BuildingTemplate> byCategory(String category) {
        return byCategory.getOrDefault(category.toLowerCase(Locale.ROOT), List.of());
    }

    /**
     * Resolves a command argument into candidate templates: an exact template
     * name, {@code random} / {@code all}, or a category name.
     */
    public List<BuildingTemplate> resolve(String spec) {
        if (spec == null || spec.isBlank()) {
            return List.of();
        }
        String key = spec.toLowerCase(Locale.ROOT);
        BuildingTemplate exact = templates.get(key);
        if (exact != null) {
            return List.of(exact);
        }
        if (key.equals("random") || key.equals("all")) {
            return all();
        }
        return byCategory(key);
    }

    public int size() {
        return templates.size();
    }

    private BuildingTemplate loadTemplate(String name, File file) throws IOException {
        ClipboardFormat format = ClipboardFormats.findByFile(file);
        if (format == null) {
            throw new IOException("unsupported schematic format");
        }

        try (ClipboardReader reader = format.getReader(new FileInputStream(file))) {
            Clipboard clipboard = reader.read();
            Region region = clipboard.getRegion();
            BlockVector3 min = region.getMinimumPoint();
            BlockVector3 max = region.getMaximumPoint();

            int width = max.x() - min.x() + 1;
            int height = max.y() - min.y() + 1;
            int depth = max.z() - min.z() + 1;

            int maxBlocks = plugin.getCityConfig().maxTemplateBlocks();
            if ((long) width * height * depth > maxBlocks) {
                throw new IOException("volume " + width + "x" + height + "x" + depth
                        + " exceeds max-template-blocks (" + maxBlocks + ")");
            }

            List<BuildingTemplate.Block> blocks = new ArrayList<>();
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < depth; z++) {
                    for (int x = 0; x < width; x++) {
                        BlockState state = clipboard.getBlock(min.add(x, y, z));
                        if (isEmpty(state)) {
                            continue;
                        }
                        BlockData data = BukkitAdapter.adapt(state);
                        blocks.add(new BuildingTemplate.Block(x, y, z, data));
                    }
                }
            }

            if (blocks.isEmpty()) {
                throw new IOException("template contains no blocks");
            }

            String category = categoryOf(name);
            return new BuildingTemplate(name, category, file, width, height, depth, blocks);
        }
    }

    private static boolean isEmpty(BlockState state) {
        if (state.getBlockType() == BlockTypes.AIR) {
            return true;
        }
        String id = state.getBlockType().id();
        return id.equals("minecraft:cave_air")
                || id.equals("minecraft:void_air")
                || id.equals("minecraft:structure_void");
    }

    private static String categoryOf(String name) {
        int slash = name.indexOf('/');
        return slash > 0 ? name.substring(0, slash) : "default";
    }

    private static String relativeName(File directory, File file) {
        String relative = directory.toPath().relativize(file.toPath()).toString().replace('\\', '/');
        int dot = relative.lastIndexOf('.');
        return dot >= 0 ? relative.substring(0, dot) : relative;
    }

    private static boolean isSchematic(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".schem") || name.endsWith(".schematic");
    }

    private static boolean prefer(File candidate, File existing) {
        return candidate.getName().toLowerCase(Locale.ROOT).endsWith(".schem")
                && !existing.getName().toLowerCase(Locale.ROOT).endsWith(".schem");
    }
}
