package ru.dagxam.diamondportals.portal;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.List;

/** Проверенная прямоугольная рамка портала, похожая на портал в Ад. */
public record PortalShape(Material frame, Location origin, PortalAxis axis, int width, int height,
                          List<Location> inside) {

    public enum PortalAxis {
        X,
        Z
    }

    public static PortalShape find(Block ignitedBlock, Material frame, int maxSize) {
        Location base = ignitedBlock.getLocation();
        for (PortalAxis axis : PortalAxis.values()) {
            for (int width = 2; width <= maxSize - 2; width++) {
                for (int height = 3; height <= maxSize - 2; height++) {
                    PortalShape result = tryExact(base, frame, axis, width, height);
                    if (result != null) {
                        return result;
                    }
                }
            }
        }
        return null;
    }

    private static PortalShape tryExact(Location base, Material frame, PortalAxis axis, int width, int height) {
        // Считаем поджигаемый блок возможной внутренней частью портала
        // и перебираем возможные положения нижнего левого угла рамки.
        for (int offset = -width; offset <= 0; offset++) {
            for (int oy = -height; oy <= 0; oy++) {
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

    private static boolean matches(Location origin, Material frame, PortalAxis axis, int width, int height) {
        // Минимальная внешняя рамка 4x5, как у стандартного портала в Ад.
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

    private static boolean isFrame(Location origin, Material frame, PortalAxis axis, int w, int h) {
        Location location = origin.clone().add(
                axis == PortalAxis.Z ? w : 0,
                h,
                axis == PortalAxis.Z ? 0 : w
        );
        return location.getBlock().getType() == frame;
    }
}
