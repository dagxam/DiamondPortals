package ru.dagxam.diamondportals.portal;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.List;

/** Проверенная прямоугольная рамка портала по правилам стандартного портала в Ад. */
public record PortalShape(Material frame, Location origin, PortalAxis axis, int width, int height,
                          List<Location> inside) {

    public enum PortalAxis {
        X,
        Z
    }

    public static PortalShape find(Block ignitedBlock, Material frame, int maxSize) {
        if (ignitedBlock == null || frame == null) {
            return null;
        }

        Location base = ignitedBlock.getLocation();
        int maximum = Math.max(4, maxSize);

        for (PortalAxis axis : PortalAxis.values()) {
            for (int width = 4; width <= maximum; width++) {
                for (int height = 5; height <= maximum; height++) {
                    PortalShape result = tryExact(base, frame, axis, width, height);
                    if (result != null) {
                        return result;
                    }
                }
            }
        }
        return null;
    }

    private static PortalShape tryExact(Location base, Material frame, PortalAxis axis,
                                        int width, int height) {
        for (int offset = -(width - 1); offset <= 0; offset++) {
            for (int oy = -(height - 1); oy <= 0; oy++) {
                Location origin = base.clone();
                if (axis == PortalAxis.Z) {
                    origin.add(offset, oy, 0);
                } else {
                    origin.add(0, oy, offset);
                }

                if (!matches(origin, frame, axis, width, height)) {
                    continue;
                }

                List<Location> inside = new ArrayList<>((width - 2) * (height - 2));
                for (int w = 1; w < width - 1; w++) {
                    for (int h = 1; h < height - 1; h++) {
                        Location location = origin.clone().add(
                                axis == PortalAxis.Z ? w : 0,
                                h,
                                axis == PortalAxis.Z ? 0 : w
                        );
                        inside.add(location);
                    }
                }

                return new PortalShape(frame, origin, axis, width, height, inside);
            }
        }
        return null;
    }

    private static boolean matches(Location origin, Material frame, PortalAxis axis,
                                   int width, int height) {
        for (int w = 0; w < width; w++) {
            if (!isFrame(origin, frame, axis, w, 0)
                    || !isFrame(origin, frame, axis, w, height - 1)) {
                return false;
            }
        }

        for (int h = 0; h < height; h++) {
            if (!isFrame(origin, frame, axis, 0, h)
                    || !isFrame(origin, frame, axis, width - 1, h)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isFrame(Location origin, Material frame, PortalAxis axis,
                                   int w, int h) {
        Location location = origin.clone().add(
                axis == PortalAxis.Z ? w : 0,
                h,
                axis == PortalAxis.Z ? 0 : w
        );
        return location.getBlock().getType() == frame;
    }
}
