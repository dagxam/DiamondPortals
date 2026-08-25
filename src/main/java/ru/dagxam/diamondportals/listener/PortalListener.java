package ru.dagxam.diamondportals.listener;

import org.bukkit.Axis;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Orientable;
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
import java.util.Locale;
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
        if (!plugin.getConfig().getBoolean("portal.enabled", true)) {
            return;
        }
        if (event.getCause() != BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL) {
            return;
        }

        Block ignitedBlock = event.getBlock();
        Material frame = findFrameMaterial(ignitedBlock);
        if (frame == null) {
            // Это не портал DiamondPortals — ванильная механика продолжает работать.
            return;
        }

        PortalShape shape = PortalShape.find(ignitedBlock, frame,
                plugin.getConfig().getInt("portal.max-size", 23));
        if (shape == null) {
            return;
        }

        // Останавливаем только ванильное появление одного блока портала.
        // Сразу после этого заполняем всю внутреннюю область, как обычный портал Minecraft.
        event.setCancelled(true);
        activate(shape);

        Player player = event.getPlayer();
        if (player != null) {
            player.sendMessage("§bDiamondPortals: §fПортал из блока §e" + russianName(frame)
                    + " §fуспешно активирован.");
        }
    }

    private Material findFrameMaterial(Block ignitionBlock) {
        int maxSize = plugin.getConfig().getInt("portal.max-size", 23);
        for (String value : plugin.getConfig().getStringList("portal.allowed-materials")) {
            Material material = Material.matchMaterial(value);
            if (material == null || !material.isBlock()) {
                plugin.getLogger().warning("Неизвестный материал в настройках порталов: " + value);
                continue;
            }
            if (PortalShape.find(ignitionBlock, material, maxSize) != null) {
                return material;
            }
        }
        return null;
    }

    private void activate(PortalShape shape) {
        Axis axis = shape.axis() == PortalShape.PortalAxis.X ? Axis.X : Axis.Z;
        for (Location location : shape.inside()) {
            setPortalBlock(location.getBlock(), axis);
        }
    }

    private void setPortalBlock(Block block, Axis axis) {
        Orientable portalData = (Orientable) Bukkit.createBlockData(Material.NETHER_PORTAL);
        portalData.setAxis(axis);
        block.setBlockData(portalData, false);
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

        PortalShape shape = findCustomPortalShape(event.getTo());
        if (shape == null) {
            return;
        }

        if (isCustomDimension(event.getTo().getWorld())) {
            ReturnPoint returnPoint = returnPoints.get(uuid);
            if (returnPoint == null) {
                player.sendMessage("§cDiamondPortals: §fНе удалось определить место, откуда вы вошли в портал.");
                return;
            }
            teleportToReturnPoint(player, returnPoint);
            return;
        }

        returnPoints.put(uuid, new ReturnPoint(player.getLocation().clone()));
        teleportToDimension(player, shape.frame());
    }

    private PortalShape findCustomPortalShape(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }

        int maxSize = plugin.getConfig().getInt("portal.max-size", 23);
        for (int dy = -1; dy <= 1; dy++) {
            Block candidate = location.getBlock().getRelative(0, dy, 0);
            if (candidate.getType() != Material.NETHER_PORTAL) {
                continue;
            }

            for (String value : plugin.getConfig().getStringList("portal.allowed-materials")) {
                Material material = Material.matchMaterial(value);
                if (material == null || !material.isBlock()) {
                    continue;
                }

                PortalShape shape = PortalShape.find(candidate, material, maxSize);
                if (shape != null) {
                    return shape;
                }
            }
        }
        return null;
    }

    private void teleportToDimension(Player player, Material frameMaterial) {
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

                    World target = dimensionManager.getOrCreate(frameMaterial);
                    if (target == null) {
                        player.sendMessage("§cDiamondPortals: §fНе удалось создать или загрузить измерение.");
                        return;
                    }

                    Location returnPortal = createReturnPortal(target, frameMaterial);
                    Location targetLocation = returnPortal.clone().add(0.5, 1.0, 2.5);
                    targetLocation.setYaw(player.getLocation().getYaw());
                    targetLocation.setPitch(player.getLocation().getPitch());

                    player.teleport(targetLocation);
                    player.sendMessage("§bDiamondPortals: §fВы прибыли в измерение §e" + russianName(frameMaterial));
                    player.sendMessage("§7Обратный портал находится рядом и вернёт вас точно туда, откуда вы вошли.");
                } finally {
                    releaseTeleportLockLater(uuid);
                }
            }
        }.runTaskLater(plugin, delayTicks);
    }

    private Location createReturnPortal(World world, Material frameMaterial) {
        String key = world.getUID() + ":" + frameMaterial.name();
        int x = 4;
        int z = 0;
        int groundY = world.getHighestBlockYAt(x, z);
        int baseY = Math.max(world.getMinHeight() + 1, groundY + 1);
        Location base = new Location(world, x, baseY, z);

        if (!createdReturnPortals.contains(key) || !hasReturnPortalAt(base, frameMaterial)) {
            for (int dx = -1; dx <= 2; dx++) {
                for (int dy = 0; dy <= 4; dy++) {
                    Block block = world.getBlockAt(x + dx, baseY + dy, z);
                    boolean border = dx == -1 || dx == 2 || dy == 0 || dy == 4;
                    if (border) {
                        block.setType(frameMaterial, false);
                    } else {
                        setPortalBlock(block, Axis.X);
                    }
                }
            }
            createdReturnPortals.add(key);
        }
        return base;
    }

    private boolean hasReturnPortalAt(Location base, Material frameMaterial) {
        World world = base.getWorld();
        return world != null
                && world.getBlockAt(base.getBlockX() - 1, base.getBlockY(), base.getBlockZ()).getType() == frameMaterial
                && world.getBlockAt(base.getBlockX(), base.getBlockY() + 1, base.getBlockZ()).getType() == Material.NETHER_PORTAL;
    }

    private void teleportToReturnPoint(Player player, ReturnPoint returnPoint) {
        UUID uuid = player.getUniqueId();
        if (!teleporting.add(uuid)) {
            return;
        }
        try {
            Location destination = returnPoint.location().clone();
            World world = destination.getWorld();
            if (world == null) {
                player.sendMessage("§cDiamondPortals: §fМир, из которого вы вошли, больше недоступен.");
                return;
            }
            world.loadChunk(destination.getBlockX() >> 4, destination.getBlockZ() >> 4);
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

    private boolean isCustomDimension(World world) {
        if (world == null) {
            return false;
        }
        String prefix = plugin.getConfig().getString("portal.world-prefix", "diamondportal_");
        return world.getName().startsWith(prefix == null ? "diamondportal_" : prefix);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerPortal(PlayerPortalEvent event) {
        // Ванильные порталы не трогаем. Отменяем только переход через нашу рамку,
        // потому что DiamondPortals сам выполняет телепортацию в собственное измерение.
        if (findCustomPortalShape(event.getFrom()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        teleporting.remove(uuid);
        returnPoints.remove(uuid);
    }

    private String russianName(Material material) {
        return switch (material) {
            case DIAMOND_BLOCK -> "алмаза";
            case GOLD_BLOCK -> "золота";
            case IRON_BLOCK -> "железа";
            case EMERALD_BLOCK -> "изумруда";
            case COPPER_BLOCK -> "меди";
            case LAPIS_BLOCK -> "лазурита";
            case REDSTONE_BLOCK -> "редстоуна";
            case AMETHYST_BLOCK -> "аметиста";
            case COAL_BLOCK -> "угля";
            case NETHERITE_BLOCK -> "незерита";
            case QUARTZ_BLOCK -> "кварца";
            default -> material.name().toLowerCase(Locale.ROOT);
        };
    }

    private record ReturnPoint(Location location) {
    }
}
