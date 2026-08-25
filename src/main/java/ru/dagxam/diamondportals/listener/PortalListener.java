package ru.dagxam.diamondportals.listener;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import ru.dagxam.diamondportals.portal.PortalShape;
import ru.dagxam.diamondportals.world.DimensionManager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PortalListener implements Listener {

    private final JavaPlugin plugin;
    private final DimensionManager dimensionManager;
    private final Set<UUID> teleporting = new HashSet<>();
    private final Map<UUID, ReturnPoint> returnPoints = new HashMap<>();
    private final Set<String> createdReturnPortals = new HashSet<>();

    public PortalListener(JavaPlugin plugin, DimensionManager dimensionManager) {
        this.plugin = plugin;
        this.dimensionManager = dimensionManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        if (!(event.getIgniter() instanceof Player player)) {
            return;
        }
        if (event.getCause() != BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL
                && event.getCause() != BlockIgniteEvent.IgniteCause.FIREBALL) {
            return;
        }

        Material frame = configuredFrame();
        if (!frame.isBlock()) {
            return;
        }

        PortalShape shape = PortalShape.find(event.getBlock(), frame,
                plugin.getConfig().getInt("portal.max-size", 23));
        if (shape == null) {
            return;
        }

        event.setCancelled(true);
        activate(shape);
        player.sendMessage("§bDiamondPortals: §fПортал успешно активирован.");
    }

    private void activate(PortalShape shape) {
        for (Location location : shape.inside()) {
            location.getBlock().setType(Material.NETHER_PORTAL);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null || !changedBlock(event.getFrom(), event.getTo())) {
            return;
        }

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (teleporting.contains(uuid)) {
            return;
        }

        Location location = event.getTo();
        if (!isInsideCustomPortal(location)) {
            return;
        }

        if (isCustomDimension(location.getWorld())) {
            ReturnPoint returnPoint = returnPoints.get(uuid);
            if (returnPoint == null) {
                player.sendMessage("§cDiamondPortals: §fНе удалось определить место, откуда вы вошли в портал.");
                return;
            }

            teleportToReturnPoint(player, returnPoint);
            return;
        }

        Material frame = configuredFrame();
        PortalShape shape = PortalShape.find(location.getBlock(), frame,
                plugin.getConfig().getInt("portal.max-size", 23));
        if (shape == null) {
            return;
        }

        returnPoints.put(uuid, new ReturnPoint(player.getLocation().clone()));
        teleportToDimension(player, shape);
    }

    private void teleportToDimension(Player player, PortalShape shape) {
        UUID uuid = player.getUniqueId();
        if (!teleporting.add(uuid)) {
            return;
        }

        long delayTicks = Math.max(0L,
                plugin.getConfig().getLong("portal.teleport-delay-seconds", 0L) * 20L);

        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    if (!player.isOnline()) {
                        return;
                    }

                    World target = dimensionManager.getOrCreate(shape.frame());
                    if (target == null) {
                        player.sendMessage("§cDiamondPortals: §fНе удалось создать или загрузить измерение.");
                        return;
                    }

                    Location returnPortal = createReturnPortal(target, shape.frame());
                    Location targetLocation = returnPortal.clone().add(0.5, 1.0, 2.5);
                    targetLocation.setYaw(player.getLocation().getYaw());
                    targetLocation.setPitch(player.getLocation().getPitch());

                    player.teleport(targetLocation);
                    player.sendMessage("§bDiamondPortals: §fВы прибыли в измерение §e" + target.getName());
                    player.sendMessage("§7Обратный портал находится рядом. Он вернёт вас точно туда, откуда вы вошли.");
                } finally {
                    releaseTeleportLockLater(uuid);
                }
            }
        }.runTaskLater(plugin, delayTicks);
    }

    private Location createReturnPortal(World world, Material frameMaterial) {
        String key = world.getUID().toString();
        int x = 4;
        int z = 0;
        int groundY = world.getHighestBlockYAt(x, z);
        int baseY = Math.max(world.getMinHeight() + 1, groundY + 1);
        Location base = new Location(world, x, baseY, z);

        // Портал строится один раз в каждом измерении рядом с точкой появления.
        // Рамка 4x5, как минимальный стандартный портал в Ад.
        if (!createdReturnPortals.contains(key) || !hasReturnPortalAt(base, frameMaterial)) {
            for (int dx = -1; dx <= 2; dx++) {
                for (int dy = 0; dy <= 4; dy++) {
                    Block block = world.getBlockAt(x + dx, baseY + dy, z);
                    boolean border = dx == -1 || dx == 2 || dy == 0 || dy == 4;
                    block.setType(border ? frameMaterial : Material.NETHER_PORTAL);
                }
            }
            createdReturnPortals.add(key);
        }

        return base;
    }

    private boolean hasReturnPortalAt(Location base, Material frameMaterial) {
        World world = base.getWorld();
        if (world == null) {
            return false;
        }
        return world.getBlockAt(base.getBlockX() - 1, base.getBlockY(), base.getBlockZ()).getType() == frameMaterial
                && world.getBlockAt(base.getBlockX(), base.getBlockY() + 1, base.getBlockZ()).getType() == Material.NETHER_PORTAL;
    }

    private void teleportToReturnPoint(Player player, ReturnPoint returnPoint) {
        UUID uuid = player.getUniqueId();
        if (!teleporting.add(uuid)) {
            return;
        }

        try {
            World returnWorld = returnPoint.location().getWorld();
            if (returnWorld == null) {
                player.sendMessage("§cDiamondPortals: §fМир, из которого вы вошли, больше недоступен.");
                return;
            }

            int chunkX = returnPoint.location().getBlockX() >> 4;
            int chunkZ = returnPoint.location().getBlockZ() >> 4;
            if (!returnWorld.isChunkLoaded(chunkX, chunkZ)) {
                returnWorld.loadChunk(chunkX, chunkZ);
            }

            Location destination = returnPoint.location().clone();
            destination.setYaw(player.getLocation().getYaw());
            destination.setPitch(player.getLocation().getPitch());
            player.teleport(destination);
            player.sendMessage("§bDiamondPortals: §fВы вернулись точно в то место, откуда вошли в измерение.");
        } finally {
            returnPoints.remove(uuid);
            releaseTeleportLockLater(uuid);
        }
    }

    private void releaseTeleportLockLater(UUID uuid) {
        new BukkitRunnable() {
            @Override
            public void run() {
                teleporting.remove(uuid);
            }
        }.runTaskLater(plugin, 20L);
    }

    private boolean changedBlock(Location from, Location to) {
        return from.getBlockX() != to.getBlockX()
                || from.getBlockY() != to.getBlockY()
                || from.getBlockZ() != to.getBlockZ();
    }

    private boolean isInsideCustomPortal(Location location) {
        return location.getBlock().getType() == Material.NETHER_PORTAL
                || location.clone().add(0, 1, 0).getBlock().getType() == Material.NETHER_PORTAL;
    }

    private boolean isCustomDimension(World world) {
        if (world == null) {
            return false;
        }
        String prefix = plugin.getConfig().getString("portal.world-prefix", "diamondportal_");
        return world.getName().startsWith(prefix == null ? "diamondportal_" : prefix);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerPortal(PlayerPortalEvent event) {
        // Полностью отключаем стандартную маршрутизацию Minecraft в Нижний мир.
        // Все переходы обрабатывает этот плагин через PlayerMoveEvent.
        event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        teleporting.remove(uuid);
        returnPoints.remove(uuid);
    }

    private Material configuredFrame() {
        String value = plugin.getConfig().getString("portal.frame-material", "DIAMOND_BLOCK");
        Material material = Material.matchMaterial(value == null ? "DIAMOND_BLOCK" : value);
        return material == null ? Material.DIAMOND_BLOCK : material;
    }

    private record ReturnPoint(Location location) {
    }
}
