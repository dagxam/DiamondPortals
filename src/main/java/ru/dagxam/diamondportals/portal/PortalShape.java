package ru.dagxam.diamondportals.portal;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.List;

/** Проверенная прямоугольная рамка по логике обычного портала Minecraft. */
public record PortalShape(Material frame, Location origin, PortalAxis axis, int width, int height,
                          List<Location> inside) {

    public enum PortalAxis {
        X,
        Z
    }

    public static PortalShape find(Block ignitionBlock, Material frame, int maxSize) {
        if (ignitionBlock == null || frame == null) {
            return null;
        }

        int maximum = Math.max(5, maxSize);
        Location base = ignitionBlock.getLocation();

        for (PortalAxis axis : PortalAxis.values()) {
            for (int width = 4; width <= maximum; width++) {
                for (int height = 5; height <= maximum; height++) {
                    PortalShape shape = findAround(base, frame, axis, width, height);
                    if (shape != null) {
                        return shape;
                    }
                }
            }
        }
        return null;
    }

    private static PortalShape findAround(Location ignition, Material frame, PortalAxis axis,
                                          int width, int height) {
        // Огниво используется внутри рамки. Перебираем все возможные положения
        // нижнего левого угла так же, чтобы найденный блок обязательно был внутри портала.
        for (int horizontal = 1; horizontal <= width - 2; horizontal++) {
            for (int vertical = 1; vertical <= height - 2; vertical++) {
                Location origin = ignition.clone();
                if (axis == PortalAxis.X) {
                    origin.add(-horizontal, -vertical, 0);
                } else {
                    origin.add(0, -vertical, -horizontal);
                }

                PortalShape shape = createIfMatches(origin, ignition, frame, axis, width, height);
                if (shape != null) {
                    return shape;
                }
            }
        }
        return null;
    }

    private static PortalShape createIfMatches(Location origin, Location ignition, Material frame,
                                                PortalAxis axis, int width, int height) {
        List<Location> inside = new ArrayList<>((width - 2) * (height - 2));

        for (int w = 0; w < width; w++) {
            for (int h = 0; h < height; h++) {
                Location location = at(origin, axis, w, h);
                boolean border = w == 0 || w == width - 1 || h == 0 || h == height - 1;
                Material type = location.getBlock().getType();

                if (border) {
                    if (type != frame) {
                        return null;
                    }
                } else {
                    // Внутренность должна быть свободной, как у обычного портала.
                    if (type != Material.AIR && type != Material.FIRE && type != Material.NETHER_PORTAL) {
                        return null;
                    }
                    inside.add(location);
                }
            }
        }

        boolean ignitionInside = false;
        for (Location location : inside) {
            if (sameBlock(location, ignition)) {
                ignitionInside = true;
                break;
            }
        }
        if (!ignitionInside) {
            return null;
        }

        return new PortalShape(frame, origin, axis, width, height, inside);
    }

    private static Location at(Location origin, PortalAxis axis, int horizontal, int vertical) {
        if (axis == PortalAxis.X) {
            return origin.clone().add(horizontal, vertical, 0);
        }
        return origin.clone().add(0, vertical, horizontal);
    }

    private static boolean sameBlock(Location first, Location second) {
        return first.getWorld() == second.getWorld()
                && first.getBlockX() == second.getBlockX()
                && first.getBlockY() == second.getBlockY()
                && first.getBlockZ() == second.getBlockZ();
    }
}
