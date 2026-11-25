package com.xiling.city.config;

import com.xiling.city.CityPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Typed access to config.yml.
 *
 * <p>Material lists (ground-ignore / natural-blocks) accept three forms:
 * {@code #tag}, {@code minecraft:stone}, and {@code stone}. Tags are expanded
 * once at load time for fast lookups during terrain scanning.</p>
 */
public class CityConfig {

    private final CityPlugin plugin;
    private final FileConfiguration config;

    private final Set<Material> groundIgnore;
    private final Set<Material> naturalBlocks;
    private final Map<String, Material> foundationPerCategory;
    private final Material foundationBlock;
    private final String messagePrefix;

    public CityConfig(CityPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();

        this.groundIgnore = resolveMaterials("builder.ground-ignore", defaultGroundIgnore());
        this.naturalBlocks = resolveMaterials("builder.natural-blocks", defaultNaturalBlocks());
        this.foundationBlock = parseMaterial(
                config.getString("builder.foundation-block", "minecraft:stone_bricks"),
                Material.STONE_BRICKS, "builder.foundation-block");
        this.foundationPerCategory = readFoundationPerCategory();
        this.messagePrefix = ChatColor.translateAlternateColorCodes('&',
                config.getString("builder.messages.prefix", "§6[City] §r"));
    }

    // ------------------------------------------------------------------
    // region
    // ------------------------------------------------------------------

    public boolean regionEnabled() {
        return config.getBoolean("region.enabled", true);
    }

    // ------------------------------------------------------------------
    // builder
    // ------------------------------------------------------------------

    public boolean builderEnabled() {
        return config.getBoolean("builder.enabled", true);
    }

    public String buildingsFolder() {
        return config.getString("builder.buildings-folder", "buildings");
    }

    public int blocksPerTick() {
        return Math.max(1, config.getInt("builder.blocks-per-tick", 2000));
    }

    public int maxQueueBlocks() {
        return Math.max(1, config.getInt("builder.max-queue-blocks", 300_000));
    }

    public int maxTemplateBlocks() {
        return Math.max(1, config.getInt("builder.max-template-blocks", 200_000));
    }

    public int maxFoundationDepth() {
        return Math.max(0, config.getInt("builder.max-foundation-depth", 32));
    }

    public int maxBuildHeight() {
        return Math.max(1, config.getInt("builder.max-build-height", 160));
    }

    public boolean fillWater() {
        return config.getBoolean("builder.fill-water", false);
    }

    public boolean respectRegions() {
        return config.getBoolean("builder.respect-regions", true);
    }

    public int undoHistory() {
        return Math.max(0, config.getInt("builder.undo-history", 5));
    }

    public int zoneMaxArea() {
        return Math.max(1, config.getInt("builder.zone.max-area", 16_384));
    }

    public int zoneMaxBuildings() {
        return Math.max(1, config.getInt("builder.zone.max-buildings", 64));
    }

    public int zoneSpacing() {
        return Math.max(0, config.getInt("builder.zone.spacing", 7));
    }

    public Set<Material> groundIgnore() {
        return groundIgnore;
    }

    public Set<Material> naturalBlocks() {
        return naturalBlocks;
    }

    /**
     * Foundation material for a building category, falling back to the global
     * default.
     */
    public Material foundationBlock(String category) {
        if (category != null) {
            Material perCategory = foundationPerCategory.get(category.toLowerCase());
            if (perCategory != null) {
                return perCategory;
            }
        }
        return foundationBlock;
    }

    public Material defaultFoundationBlock() {
        return foundationBlock;
    }

    public String messagePrefix() {
        return messagePrefix;
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private Map<String, Material> readFoundationPerCategory() {
        ConfigurationSection section = config.getConfigurationSection("builder.foundation-block-per-category");
        if (section == null) {
            return Collections.emptyMap();
        }
        Map<String, Material> result = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            Material material = parseMaterial(section.getString(key), null,
                    "builder.foundation-block-per-category." + key);
            if (material != null) {
                result.put(key.toLowerCase(), material);
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private Set<Material> resolveMaterials(String path, List<String> defaults) {
        List<String> entries = config.getStringList(path);
        if (entries.isEmpty()) {
            entries = defaults;
        }

        Set<Material> result = new LinkedHashSet<>();
        for (String entry : entries) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            String value = entry.trim();

            if (value.startsWith("#")) {
                String tagName = value.substring(1);
                Tag<Material> tag = Bukkit.getTag(Tag.REGISTRY_BLOCKS,
                        NamespacedKey.minecraft(tagName), Material.class);
                if (tag == null) {
                    plugin.getLogger().warning("Unknown block tag '" + value + "' in " + path + ", ignoring.");
                } else {
                    result.addAll(tag.getValues());
                }
                continue;
            }

            Material material = parseMaterial(value, null, path);
            if (material != null) {
                result.add(material);
            }
        }
        return Collections.unmodifiableSet(result);
    }

    private Material parseMaterial(String name, Material fallback, String path) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        Material material = Material.matchMaterial(name.trim());
        if (material == null) {
            plugin.getLogger().warning("Unknown material '" + name + "' in " + path + ", ignoring.");
            return fallback;
        }
        return material;
    }

    private static List<String> defaultGroundIgnore() {
        List<String> list = new ArrayList<>();
        list.add("minecraft:water");
        list.add("minecraft:lava");
        list.add("#leaves");
        list.add("#logs");
        list.add("minecraft:short_grass");
        list.add("minecraft:tall_grass");
        list.add("minecraft:fern");
        list.add("minecraft:large_fern");
        list.add("minecraft:dead_bush");
        list.add("minecraft:snow");
        list.add("minecraft:vine");
        return list;
    }

    private static List<String> defaultNaturalBlocks() {
        List<String> list = new ArrayList<>();
        list.add("minecraft:air");
        list.add("minecraft:cave_air");
        list.add("minecraft:void_air");
        list.add("#leaves");
        list.add("#logs");
        list.add("#flowers");
        list.add("#saplings");
        list.add("#crops");
        list.add("#replaceable_by_trees");
        list.add("#base_stone_overworld");
        list.add("#base_stone_nether");
        list.add("#dirt");
        list.add("#sand");
        list.add("#snow");
        list.add("#ice");
        list.add("minecraft:short_grass");
        list.add("minecraft:tall_grass");
        list.add("minecraft:fern");
        list.add("minecraft:large_fern");
        list.add("minecraft:dead_bush");
        list.add("minecraft:vine");
        list.add("minecraft:water");
        list.add("minecraft:lava");
        list.add("minecraft:gravel");
        list.add("minecraft:clay");
        list.add("minecraft:moss_block");
        list.add("minecraft:podzol");
        list.add("minecraft:mycelium");
        list.add("minecraft:mud");
        list.add("minecraft:sandstone");
        list.add("minecraft:red_sandstone");
        list.add("minecraft:terracotta");
        list.add("minecraft:netherrack");
        list.add("minecraft:soul_sand");
        list.add("minecraft:soul_soil");
        list.add("minecraft:basalt");
        list.add("minecraft:blackstone");
        list.add("minecraft:end_stone");
        list.add("minecraft:obsidian");
        list.add("minecraft:bedrock");
        list.add("minecraft:magma_block");
        list.add("minecraft:dripstone_block");
        list.add("minecraft:moss_carpet");
        list.add("minecraft:crimson_nylium");
        list.add("minecraft:warped_nylium");
        return list;
    }
}
