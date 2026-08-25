package ru.dagxam.diamondportals.world;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

public final class DimensionManager {

    private final JavaPlugin plugin;
    private final BlockWorldGenerator generator;

    public DimensionManager(JavaPlugin plugin, BlockWorldGenerator generator) {
        this.plugin = plugin;
        this.generator = generator;
    }

    public World getOrCreate(Material material) {
        String name = worldName(material);
        World existing = Bukkit.getWorld(name);
        if (existing != null) {
            return existing;
        }

        int minY = plugin.getConfig().getInt("world.min-height", 0);
        int surfaceY = plugin.getConfig().getInt("world.surface-height", 64);
        int maxY = plugin.getConfig().getInt("world.max-height", 128);

        generator.configure(material, minY, surfaceY, maxY);

        WorldCreator creator = new WorldCreator(name);
        creator.generator(generator);
        creator.generateStructures(false);
        creator.createWorld();

        World world = Bukkit.getWorld(name);
        if (world != null) {
            world.setAutoSave(true);
        }
        return world;
    }

    public String worldName(Material material) {
        String prefix = plugin.getConfig().getString("portal.world-prefix", "diamondportal_");
        return prefix + material.name().toLowerCase(Locale.ROOT);
    }
}
