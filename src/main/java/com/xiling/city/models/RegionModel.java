package com.xiling.city.models;

import java.util.HashMap;
import java.util.Map;

public class RegionModel {
    private final String id;
    private final Map<String, String> hierarchy; // level -> name
    private final long createdAt;

    public RegionModel(String id) {
        this.id = id;
        this.hierarchy = new HashMap<>();
        this.createdAt = System.currentTimeMillis();
    }

    public RegionModel(String id, Map<String, String> hierarchy) {
        this.id = id;
        this.hierarchy = new HashMap<>(hierarchy);
        this.createdAt = System.currentTimeMillis();
    }

    public String getId() {
        return id;
    }

    public Map<String, String> getHierarchy() {
        return hierarchy;
    }

    public void setLevel(String level, String name) {
        hierarchy.put(level, name);
    }

    public String getLevel(String level) {
        return hierarchy.get(level);
    }

    public String getFormattedDisplay() {
        // Format: [Province] City - District (Village)
        String province = hierarchy.getOrDefault("province", "");
        String city = hierarchy.getOrDefault("city", "");
        String district = hierarchy.getOrDefault("district", "");
        String village = hierarchy.getOrDefault("village", "");

        StringBuilder result = new StringBuilder();

        if (!province.isEmpty()) {
            result.append("[").append(province).append("] ");
        }

        if (!city.isEmpty()) {
            result.append(city);
        }

        if (!district.isEmpty()) {
            if (!city.isEmpty()) {
                result.append(" - ");
            }
            result.append(district);
        }

        if (!village.isEmpty()) {
            result.append(" (").append(village).append(")");
        }

        // If everything is empty (shouldn't happen), show region ID
        if (result.isEmpty()) {
            result.append(id);
        }

        return result.toString();
    }

    public boolean isMunicipality(String city) {
        // You can define a list or config
        return city.contains("廣域市") || city.contains("特別市");
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        RegionModel region = (RegionModel) obj;
        return id.equals(region.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}