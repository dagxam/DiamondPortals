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

/**
 * Обработчик кастомных порталов.
 *
 * Важный момент: переход обрабатывается именно через PlayerPortalEvent,
 * а не через PlayerMoveEvent. Поэтому ванильный телепорт в Незер не успевает
 * сработать раньше нашего кода.
 */
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

    /** Один клик огнивом внутри рамки 4x5 активирует всю внутреннюю область 2x3. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        if (!plugin.getConfig().getBoolean("portal.enabled", true)
                || event.getCause() != BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL) {
            return;
        }

        PortalShape shape = findFrameAt(event.getBlock());
        if (shape == null) {
            // Не наша рамка: обычный обсидиановый портал работает как в ванильном Minecraft.
            return;
        }

        // Не даём ванили создать один блок. Заполняем сразу весь портал.
        event.setCancelled(true);
        activate(shape);

        Player player = event.getPlayer();
        if (player != null) {
            player.sendMessage("§bDiamondPortals: §fПортал из блока §e"
                    + russianName(shape.frame()) + " §fактивирован.");
        }
    }

    private PortalShape findFrameAt(Block ignitionBlock) {
        for (String value : plugin.getConfig().getStringList("portal.allowed-materials")) {
            Material material = Material.matchMaterial(value);
            if (material == null || !material.isBlock()) {
                continue;
            }
            PortalShape shape = PortalShape.find(ignitionBlock, material);
            if (shape != null) {
                return shape;
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
        block.setBlockData(portalData, true);
    }

    /**
     * Главная точка входа. Кастомный портал всегда отменяет ванильный переход
     * и направляет игрока в измерение, соответствующее материалу рамки.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerPortal(PlayerPortalEvent event) {
        if (!plugin.getConfig().getBoolean("portal.enabled", true)) {
            return;
        }

        PortalShape shape = findCustomPortalShape(event.getFrom());
        if (shape == null) {
            // Не наша рамка, не вмешиваемся в ванильный портал в Незер.
            return;
        }

        // Сначала полностью отменяем ванильный переход в Незер.
        event.setCancelled(true);

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (!teleporting.add(uuid)) {
            return;
        }

        if (isCustomDimension(event.getFrom().getWorld())) {
            ReturnPoint point = returnPoints.get(uuid);
            if (point == null) {
                teleporting.remove(uuid);
                player.sendMessage("§cDiamondPortals: §fНет сохранённой точки возврата.");
                return;
            }
            teleportToReturnPoint(player, uuid, point);
            return;
        }

        // Запоминаем безопасное место рядом с порталом, а не сам блок портала.
        returnPoints.put(uuid, new ReturnPoint(safeReturnLocation(shape, event.getFrom())));
        teleportToDimension(player, uuid, shape.frame());
    }

    private PortalShape findCustomPortalShape(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }

        for (int dy = -1; dy <= 1; dy++) {
            Block candidate = location.getBlock().getRelative(0, dy, 0);
            if (candidate.getType() != Material.NETHER_PORTAL) {
                continue;
            }
            PortalShape shape = findFrameAt(candidate);
            if (shape != null) {
                return shape;
            }
        }
        return null;
    }

    private Location safeReturnLocation(PortalShape shape, Location portalLocation) {
        Location origin = shape.origin().clone();
        Location result;

        if (shape.axis() == PortalShape.PortalAxis.X) {
            // Плоскость X/Y, выходим по оси Z.
            result = origin.add(1.5, 1.0, 2.0);
        } else {
            // Плоскость Z/Y, выходим по оси X.
            result = origin.add(2.0, 1.0, 1.5);
        }

        result.setYaw(portalLocation.getYaw());
        result.setPitch(portalLocation.getPitch());
        return result;
    }

    private void teleportToDimension(Player player, UUID uuid, Material frameMaterial) {
        long delayTicks = Math.max(1L,
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
                        player.sendMessage("§cDiamondPortals: §fНе удалось создать измерение.");
                        return;
                    }

                    Location returnPortal = createReturnPortal(target, frameMaterial);
                    Location targetLocation = returnPortal.clone().add(0.5, 1.0, 2.5);
                    targetLocation.setYaw(player.getLocation().getYaw());
                    targetLocation.setPitch(player.getLocation().getPitch());

                    player.teleport(targetLocation);
                    player.sendMessage("§bDiamondPortals: §fВы прибыли в измерение §e"
                            + russianName(frameMaterial));
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
                for (int dy = 0; dy < PortalShape.OUTER_HEIGHT; dy++) {
                    world.getBlockAt(x + dx, baseY + dy, z).setType(Material.AIR, false);
                }
            }

            for (int dx = -1; dx <= 2; dx++) {
                for (int dy = 0; dy < PortalShape.OUTER_HEIGHT; dy++) {
                    Block block = world.getBlockAt(x + dx, baseY + dy, z);
                    boolean border = dx == -1 || dx == 2
                            || dy == 0 || dy == PortalShape.OUTER_HEIGHT - 1;
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
                && world.getBlockAt(base.getBlockX(), base.getBlockY() + 1, base.getBlockZ()).getType() == Material.NETHER_PORTAL
                && world.getBlockAt(base.getBlockX() + 1, base.getBlockY() + 3, base.getBlockZ()).getType() == Material.NETHER_PORTAL;
    }

    private void teleportToReturnPoint(Player player, UUID uuid, ReturnPoint returnPoint) {
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    if (!player.isOnline()) {
                        return;
                    }

                    Location destination = returnPoint.location().clone();
                    World world = destination.getWorld();
                    if (world == null) {
                        player.sendMessage("§cDiamondPortals: §fМир, из которого вы вошли, больше недоступен.");
                        return;
                    }
                    world.getChunkAt(destination).load();
                    player.teleport(destination);
                    player.sendMessage("§bDiamondPortals: §fВы вернулись обратно.");
                } finally {
                    returnPoints.remove(uuid);
                    releaseTeleportLockLater(uuid);
                }
            }
        }.runTask(plugin);
    }

    private void releaseTeleportLockLater(UUID uuid) {
        new BukkitRunnable() {
            @Override
            public void run() {
                teleporting.remove(uuid);
            }
        }.runTaskLater(plugin, 20L);
    }

    private boolean isCustomDimension(World world) {
        if (world == null) {
            return false;
        }
        String prefix = plugin.getConfig().getString("portal.world-prefix", "diamondportal_");
        return world.getName().startsWith(prefix == null ? "diamondportal_" : prefix);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        teleporting.remove(uuid);
        returnPoints.remove(uuid);
    }

    private String russianName(Material material) {
        return switch (material) {
            case DIAMOND_BLOCK -> "алмазов";
            case GOLD_BLOCK -> "золота";
            case IRON_BLOCK -> "железа";
            case EMERALD_BLOCK -> "изумрудов";
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
