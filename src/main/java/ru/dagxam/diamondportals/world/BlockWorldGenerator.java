package ru.dagxam.diamondportals.world;

import org.bukkit.Material;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.Random;

/**
 * Generates a natural-looking height profile, while every solid block is
 * the configured portal frame material. The generator deliberately uses
 * only the supplied material: no grass, dirt, stone or ores are introduced.
 */
public final class BlockWorldGenerator extends ChunkGenerator {

    private static final int DEFAULT_SURFACE_Y = 64;
    private static final int DEFAULT_SEA_LEVEL = 63;

    private Material material = Material.DIAMOND_BLOCK;
    private int surfaceY = DEFAULT_SURFACE_Y;
    private int minY = 0;
    private int maxY = 128;

    public void configure(Material material, int minY, int surfaceY, int maxY) {
        if (material == null || !material.isBlock()) {
            throw new IllegalArgumentException("Generator material must be a block");
        }
        if (minY >= surfaceY || surfaceY >= maxY) {
            throw new IllegalArgumentException("Expected minY < surfaceY < maxY");
        }
        this.material = material;
        this.minY = minY;
        this.surfaceY = surfaceY;
        this.maxY = maxY;
    }

    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        int base = Math.max(minY, chunkData.getMinHeight());
        int top = Math.min(maxY, chunkData.getMaxHeight());
        int sea = Math.min(DEFAULT_SEA_LEVEL, top - 1);

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int worldX = chunkX * 16 + localX;
                int worldZ = chunkZ * 16 + localZ;

                double broad = noise(worldX * 0.006, worldZ * 0.006);
                double medium = noise(worldX * 0.018, worldZ * 0.018);
                double detail = noise(worldX * 0.055, worldZ * 0.055);

                int height = surfaceY
                        + (int) Math.round(broad * 22.0)
                        + (int) Math.round(medium * 8.0)
                        + (int) Math.round(detail * 3.0);

                // Keep a useful playable range around the configured surface.
                height = Math.max(base + 4, Math.min(top - 2, height));

                for (int y = base; y <= height; y++) {
                    chunkData.setBlock(localX, y, localZ, material);
                }

                // The generator intentionally leaves all air above the surface.
                // 'sea' is declared to make the intended vanilla-like reference
                // level explicit without introducing a second solid material.
                if (height < sea) {
                    // No water or other blocks: the dimension stays single-material.
                }
            }
        }
    }

    private static double noise(double x, double z) {
        long n = 1469598103934665603L;
        n ^= Double.doubleToLongBits(x);
        n *= 1099511628211L;
        n ^= Double.doubleToLongBits(z);
        n *= 1099511628211L;
        n ^= (n >>> 32);
        double a = ((n & 0x7fffffffffffffffL) / (double) Long.MAX_VALUE) * 2.0 - 1.0;
        return a;
    }
}
