package ru.dagxam.diamondportals.portal;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.List;

/**
 * Форма кастомного портала фиксирована и повторяет минимальный ванильный портал:
 * внешняя рамка 4 блока в ширину и 5 блоков в высоту,
 * внутренняя область 2 x 3 блока.
 */
public record PortalShape(Material frame, Location origin, PortalAxis axis, List<Location> inside) {

    public static final int OUTER_WIDTH = 4;
    public static final int OUTER_HEIGHT = 5;
    public static final int INNER_WIDTH = 2;
    public static final int INNER_HEIGHT = 3;

    public enum PortalAxis {
        X,
        Z
    }

    public static PortalShape find(Block ignitionBlock, Material frame) {
        if (ignitionBlock == null || frame == null || ignitionBlock.getWorld() == null) {
            return null;
        }

        Location ignition = ignitionBlock.getLocation();
        for (PortalAxis axis : PortalAxis.values()) {
            // Блок огня/портала должен находиться в одной из 6 внутренних ячеек.
            for (int horizontal = 1; horizontal <= INNER_WIDTH; horizontal++) {
                for (int vertical = 1; vertical <= INNER_HEIGHT; vertical++) {
                    Location origin = ignition.clone();
                    if (axis == PortalAxis.X) {
                        origin.add(-horizontal, -vertical, 0);
                    } else {
                        origin.add(0, -vertical, -horizontal);
                    }

                    PortalShape shape = createIfMatches(origin, ignition, frame, axis);
                    if (shape != null) {
                        return shape;
                    }
                }
            }
        }
        return null;
    }

    private static PortalShape createIfMatches(Location origin, Location ignition,
                                                Material frame, PortalAxis axis) {
        List<Location> inside = new ArrayList<>(INNER_WIDTH * INNER_HEIGHT);

        for (int horizontal = 0; horizontal < OUTER_WIDTH; horizontal++) {
            for (int vertical = 0; vertical < OUTER_HEIGHT; vertical++) {
                Location location = at(origin, axis, horizontal, vertical);
                boolean border = horizontal == 0 || horizontal == OUTER_WIDTH - 1
                        || vertical == 0 || vertical == OUTER_HEIGHT - 1;
                Material type = location.getBlock().getType();

                if (border) {
                    if (type != frame) {
                        return null;
                    }
                } else {
                    if (type != Material.AIR
                            && type != Material.FIRE
                            && type != Material.NETHER_PORTAL) {
                        return null;
                    }
                    inside.add(location);
                }
            }
        }

        for (Location location : inside) {
            if (sameBlock(location, ignition)) {
                return new PortalShape(frame, origin, axis, inside);
            }
        }
        return null;
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
