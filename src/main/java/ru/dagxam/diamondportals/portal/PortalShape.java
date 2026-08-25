package ru.dagxam.diamondportals.portal;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.BlockFace;

import java.util.ArrayList;
import java.util.List;

/** A validated Nether-like rectangular portal frame. */
public record PortalShape(Material frame, Location origin, BlockFace.Axis axis, int width, int height, List<Location> inside) {

    public static PortalShape find(Block ignitedBlock, Material frame, int maxSize) {
        Location base = ignitedBlock.getLocation();
        for (BlockFace.Axis axis : new BlockFace.Axis[]{BlockFace.Axis.X, BlockFace.Axis.Z}) {
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

    private static PortalShape tryExact(Location base, Material frame, BlockFace.Axis axis, int width, int height) {
        // Treat the clicked/ignited block as an interior candidate and try all
        // possible bottom-left origins around it.
        for (int ox = -width; ox <= 0; ox++) {
            for (int oy = -height; oy <= 0; oy++) {
                Location origin = base.clone();
                if (axis == BlockFace.Axis.Z) {
                    origin.add(ox, oy, 0);
                } else {
                    origin.add(0, oy, ox);
                }

                if (!matches(origin, frame, axis, width, height)) {
                    continue;
                }

                List<Location> inside = new ArrayList<>((width - 2) * (height - 2));
                for (int w = 1; w < width - 1; w++) {
                    for (int h = 1; h < height - 1; h++) {
                        Location location = origin.clone().add(
                                axis == BlockFace.Axis.Z ? w : 0,
                                h,
                                axis == BlockFace.Axis.Z ? 0 : w
                        );
                        inside.add(location);
                    }
                }
                return new PortalShape(frame, origin, axis, width, height, inside);
            }
        }
        return null;
    }

    private static boolean matches(Location origin, Material frame, BlockFace.Axis axis, int width, int height) {
        // Width/height here exclude the outer corner coordinates: a 2x3 inner
        // area gives a 4x5 outside frame, matching the classic minimum portal.
        for (int w = 0; w < width; w++) {
            if (!isFrame(origin, frame, axis, w, 0) || !isFrame(origin, frame, axis, w, height - 1)) {
                return false;
            }
        }
        for (int h = 0; h < height; h++) {
            if (!isFrame(origin, frame, axis, 0, h) || !isFrame(origin, frame, axis, width - 1, h)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isFrame(Location origin, Material frame, BlockFace.Axis axis, int w, int h) {
        Location location = origin.clone().add(
                axis == BlockFace.Axis.Z ? w : 0,
                h,
                axis == BlockFace.Axis.Z ? 0 : w
        );
        return location.getBlock().getType() == frame;
    }
}
