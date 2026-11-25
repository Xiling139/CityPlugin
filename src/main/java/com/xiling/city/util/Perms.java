package com.xiling.city.util;

import org.bukkit.command.CommandSender;

/**
 * Permission helpers.
 *
 * <p>New permission nodes live under {@code city.*}. The legacy
 * {@code region.admin} / {@code region.user} nodes are still honoured so that
 * existing permission groups keep working after the plugin rename.</p>
 */
public final class Perms {

    private Perms() {
    }

    public static boolean has(CommandSender sender, String node) {
        if (sender.hasPermission(node)) {
            return true;
        }
        return switch (node) {
            case "city.admin" -> sender.hasPermission("region.admin");
            case "city.user" -> sender.hasPermission("region.user");
            default -> false;
        };
    }
}
