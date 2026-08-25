package ru.dagxam.diamondportals.world;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/** Управляет мирами измерений и кэширует уже найденные миры. */
public final class DimensionManager {

    private final JavaPlugin plugin;
    private final Map<Material, World> worldCache = new EnumMap<>(Material.class);

    public DimensionManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public World getOrCreate(Material material) {
        World cached = worldCache.get(material);
        if (cached != null) {
            return cached;
        }

        String name = worldName(material);
        World existing = Bukkit.getWorld(name);
        if (existing != null) {
            worldCache.put(material, existing);
            return existing;
        }

        int minY = plugin.getConfig().getInt("world.min-height", 0);
        int surfaceY = plugin.getConfig().getInt("world.surface-height", 64);
        int maxY = plugin.getConfig().getInt("world.max-height", 128);

        BlockWorldGenerator generator = new BlockWorldGenerator();
        generator.configure(material, minY, surfaceY, maxY);

        WorldCreator creator = new WorldCreator(name);
        creator.generator(generator);
        creator.generateStructures(false);
        World world = creator.createWorld();

        if (world != null) {
            world.setAutoSave(true);
            worldCache.put(material, world);
        }
        return world;
    }

    /** Вызывать после перезагрузки конфигурации, если изменён префикс миров. */
    public void reloadSettings() {
        worldCache.clear();
    }

    public String worldName(Material material) {
        String prefix = plugin.getConfig().getString("portal.world-prefix", "diamondportal_");
        if (prefix == null || prefix.isBlank()) {
            prefix = "diamondportal_";
        }
        return prefix + material.name().toLowerCase(Locale.ROOT);
    }
}
