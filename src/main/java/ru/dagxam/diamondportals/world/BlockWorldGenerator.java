package ru.dagxam.diamondportals.world;

import org.bukkit.Material;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.Random;

/**
 * Генератор измерений из одного материала. Рельеф строится плавным
 * многослойным шумом, поэтому поверхность выглядит как обычный ландшафт,
 * а не как случайные вертикальные столбы.
 */
public final class BlockWorldGenerator extends ChunkGenerator {

    private static final int DEFAULT_SURFACE_Y = 64;

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
    public boolean shouldGenerateNoise() {
        return false;
    }

    @Override
    public boolean shouldGenerateSurface() {
        return false;
    }

    @Override
    public boolean shouldGenerateCaves() {
        return false;
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return false;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return false;
    }

    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        int bottom = Math.max(minY, chunkData.getMinHeight());
        int topLimit = Math.min(maxY, chunkData.getMaxHeight() - 1);
        long seed = worldInfo.getSeed();

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int worldX = chunkX * 16 + localX;
                int worldZ = chunkZ * 16 + localZ;

                double broad = valueNoise(seed + 0x1A2B3C4DL, worldX / 96.0, worldZ / 96.0);
                double hills = valueNoise(seed + 0x5E6F7788L, worldX / 48.0, worldZ / 48.0);
                double detail = valueNoise(seed + 0x0F123ABCL, worldX / 20.0, worldZ / 20.0);

                double terrain = broad * 14.0 + hills * 7.0 + detail * 2.0;
                terrain = terrain * 0.5 + 0.5;

                int height = surfaceY + (int) Math.round((terrain - 0.5) * 32.0);

                // Небольшая ровная площадка у точки появления и обратного портала.
                double distance = Math.sqrt((worldX - 4.0) * (worldX - 4.0) + (double) worldZ * worldZ);
                if (distance < 18.0) {
                    double blend = distance / 18.0;
                    height = (int) Math.round(surfaceY * (1.0 - blend) + height * blend);
                }

                height = Math.max(bottom + 4, Math.min(topLimit - 2, height));
                for (int y = bottom; y <= height; y++) {
                    chunkData.setBlock(localX, y, localZ, material);
                }
            }
        }
    }

    private static double valueNoise(long seed, double x, double z) {
        int x0 = fastFloor(x);
        int z0 = fastFloor(z);
        double tx = x - x0;
        double tz = z - z0;

        double v00 = lattice(seed, x0, z0);
        double v10 = lattice(seed, x0 + 1, z0);
        double v01 = lattice(seed, x0, z0 + 1);
        double v11 = lattice(seed, x0 + 1, z0 + 1);

        double sx = smooth(tx);
        double sz = smooth(tz);
        return lerp(lerp(v00, v10, sx), lerp(v01, v11, sx), sz);
    }

    private static int fastFloor(double value) {
        int integer = (int) value;
        return value < integer ? integer - 1 : integer;
    }

    private static double smooth(double value) {
        return value * value * (3.0 - 2.0 * value);
    }

    private static double lerp(double first, double second, double amount) {
        return first + (second - first) * amount;
    }

    private static double lattice(long seed, int x, int z) {
        long value = seed;
        value ^= (long) x * 0x9E3779B97F4A7C15L;
        value ^= (long) z * 0xC2B2AE3D27D4EB4FL;
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return ((value >>> 11) * 0x1.0p-53) * 2.0 - 1.0;
    }
}
