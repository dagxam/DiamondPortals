package ru.dagxam.diamondportals.listener;

import org.bukkit.Axis;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Orientable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import ru.dagxam.diamondportals.portal.PortalShape;
import ru.dagxam.diamondportals.world.DimensionManager;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Обработчик кастомных порталов.
 *
 * Оптимизация: активные внутренние блоки порталов кэшируются. Поэтому при
 * обычном перемещении игрока не приходится каждый раз перебирать все материалы
 * и повторно проверять всю рамку 4x5.
 */
public final class PortalListener implements Listener {

    private final JavaPlugin plugin;
    private final DimensionManager dimensionManager;
    private final Set<UUID> teleporting = new HashSet<>();
    private final Map<UUID, ReturnPoint> returnPoints = new HashMap<>();
    private final Set<String> createdReturnPortals = new HashSet<>();
    private final Map<PortalBlockKey, PortalShape> portalCache = new HashMap<>();
    private final Set<Material> allowedMaterials = EnumSet.noneOf(Material.class);

    public PortalListener(JavaPlugin plugin, DimensionManager dimensionManager) {
        this.plugin = plugin;
        this.dimensionManager = dimensionManager;
        reloadSettings();
    }

    /** Обновляет кэш разрешённых материалов после /diamondportals reload. */
    public void reloadSettings() {
        allowedMaterials.clear();
        for (String value : plugin.getConfig().getStringList("portal.allowed-materials")) {
            Material material = Material.matchMaterial(value);
            if (material != null && material.isBlock()) {
                allowedMaterials.add(material);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (!isEnabled()
                || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getHand() != EquipmentSlot.HAND
                || event.getItem() == null
                || event.getItem().getType() != Material.FLINT_AND_STEEL
                || event.getClickedBlock() == null) {
            return;
        }

        PortalShape shape = findFrameNearClick(event.getClickedBlock(), event.getBlockFace());
        if (shape == null) {
            return;
        }

        event.setCancelled(true);
        activate(shape);
        damageFlintAndSteel(event.getPlayer());
        event.getPlayer().sendMessage("§bDiamondPortals: §fПортал из блока §e"
                + russianName(shape.frame()) + " §fактивирован.");
    }

    /** Запасной перехват для серверов, где используется BlockIgniteEvent. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onIgnite(BlockIgniteEvent event) {
        if (!isEnabled() || event.getCause() != BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL) {
            return;
        }

        PortalShape shape = findFrameNear(event.getBlock());
        if (shape == null) {
            return;
        }

        event.setCancelled(true);
        activate(shape);
    }

    private boolean isEnabled() {
        return plugin.getConfig().getBoolean("portal.enabled", true);
    }

    private PortalShape findFrameNearClick(Block clicked, BlockFace face) {
        PortalShape shape = findFrameNear(clicked);
        if (shape != null) {
            return shape;
        }
        return face == null ? null : findFrameNear(clicked.getRelative(face));
    }

    /** Ищет фиксированную 4x5 рамку только во время активации или резервного поиска. */
    private PortalShape findFrameNear(Block center) {
        if (center == null || allowedMaterials.isEmpty()) {
            return null;
        }

        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -3; dy <= 1; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    PortalShape shape = findFrameAt(center.getRelative(dx, dy, dz));
                    if (shape != null) {
                        return shape;
                    }
                }
            }
        }
        return null;
    }

    private PortalShape findFrameAt(Block block) {
        for (Material material : allowedMaterials) {
            PortalShape shape = PortalShape.find(block, material);
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
        cachePortal(shape);
    }

    private void setPortalBlock(Block block, Axis axis) {
        Orientable data = (Orientable) Bukkit.createBlockData(Material.NETHER_PORTAL);
        data.setAxis(axis);
        block.setBlockData(data, false);
    }

    private void cachePortal(PortalShape shape) {
        for (Location location : shape.inside()) {
            portalCache.put(PortalBlockKey.of(location), shape);
        }
    }

    private void damageFlintAndSteel(Player player) {
        if (player.getGameMode().name().equals("CREATIVE")) {
            return;
        }
        var item = player.getInventory().getItemInMainHand();
        if (item.getType() != Material.FLINT_AND_STEEL
                || !(item.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable damageable)) {
            return;
        }
        int damage = damageable.getDamage() + 1;
        if (damage >= item.getType().getMaxDurability()) {
            player.getInventory().setItemInMainHand(null);
            return;
        }
        damageable.setDamage(damage);
        item.setItemMeta(damageable);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!isEnabled() || event.getTo() == null || sameBlock(event.getFrom(), event.getTo())) {
            return;
        }

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (teleporting.contains(uuid)) {
            return;
        }

        PortalShape shape = findCustomPortalShape(event.getTo());
        if (shape != null) {
            beginPortalTeleport(player, event.getTo(), shape);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerPortal(PlayerPortalEvent event) {
        if (!isEnabled()) {
            return;
        }

        PortalShape shape = findCustomPortalShape(event.getFrom());
        if (shape == null) {
            return;
        }

        event.setCancelled(true);
        UUID uuid = event.getPlayer().getUniqueId();
        if (!teleporting.contains(uuid)) {
            beginPortalTeleport(event.getPlayer(), event.getFrom(), shape);
        }
    }

    private PortalShape findCustomPortalShape(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }

        Block base = location.getBlock();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 2; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Block candidate = base.getRelative(dx, dy, dz);
                    if (candidate.getType() != Material.NETHER_PORTAL) {
                        continue;
                    }

                    PortalBlockKey key = PortalBlockKey.of(candidate);
                    PortalShape cached = portalCache.get(key);
                    if (cached != null) {
                        return cached;
                    }

                    PortalShape shape = findFrameNear(candidate);
                    if (shape != null) {
                        cachePortal(shape);
                        return shape;
                    }
                }
            }
        }
        return null;
    }

    private void beginPortalTeleport(Player player, Location from, PortalShape shape) {
        UUID uuid = player.getUniqueId();
        if (!teleporting.add(uuid)) {
            return;
        }

        if (isCustomDimension(from.getWorld())) {
            ReturnPoint point = returnPoints.get(uuid);
            if (point == null) {
                teleporting.remove(uuid);
                player.sendMessage("§cDiamondPortals: §fНет сохранённой точки возврата.");
                return;
            }
            teleportToReturnPoint(player, uuid, point);
            return;
        }

        returnPoints.put(uuid, new ReturnPoint(safeReturnLocation(shape, from)));
        teleportToDimension(player, uuid, shape.frame());
    }

    private Location safeReturnLocation(PortalShape shape, Location portalLocation) {
        Location origin = shape.origin().clone();
        Location result = shape.axis() == PortalShape.PortalAxis.X
                ? origin.add(1.5, 1.0, 2.0)
                : origin.add(2.0, 1.0, 1.5);
        result.setYaw(portalLocation.getYaw());
        result.setPitch(portalLocation.getPitch());
        return result;
    }

    private void teleportToDimension(Player player, UUID uuid, Material frameMaterial) {
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
                    target.getChunkAt(targetLocation).load();
                    player.teleport(targetLocation);
                    player.sendMessage("§bDiamondPortals: §fВы прибыли в измерение §e"
                            + russianName(frameMaterial));
                } finally {
                    releaseTeleportLockLater(uuid);
                }
            }
        }.runTask(plugin);
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
                    boolean border = dx == -1 || dx == 2 || dy == 0 || dy == PortalShape.OUTER_HEIGHT - 1;
                    if (border) {
                        block.setType(frameMaterial, false);
                    } else {
                        setPortalBlock(block, Axis.X);
                    }
                }
            }
            createdReturnPortals.add(key);
        }

        PortalShape shape = PortalShape.find(world.getBlockAt(x, baseY + 1, z), frameMaterial);
        if (shape != null) {
            cachePortal(shape);
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
        return world.getName().startsWith(prefix == null || prefix.isBlank() ? "diamondportal_" : prefix);
    }

    private static boolean sameBlock(Location first, Location second) {
        return first.getWorld() == second.getWorld()
                && first.getBlockX() == second.getBlockX()
                && first.getBlockY() == second.getBlockY()
                && first.getBlockZ() == second.getBlockZ();
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

    private record PortalBlockKey(UUID worldId, int x, int y, int z) {
        private static PortalBlockKey of(Location location) {
            World world = location.getWorld();
            if (world == null) {
                throw new IllegalArgumentException("Portal location has no world");
            }
            return new PortalBlockKey(world.getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        }

        private static PortalBlockKey of(Block block) {
            return new PortalBlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }
    }
}
