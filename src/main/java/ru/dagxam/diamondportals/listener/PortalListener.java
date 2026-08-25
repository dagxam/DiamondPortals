package ru.dagxam.diamondportals.listener;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
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

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class PortalListener implements Listener {

    private final JavaPlugin plugin;
    private final DimensionManager dimensionManager;
    private final Set<UUID> teleporting = new HashSet<>();

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

        PortalShape shape = PortalShape.find(event.getBlock(), frame, plugin.getConfig().getInt("portal.max-size", 23));
        if (shape == null) {
            return;
        }

        event.setCancelled(true);
        activate(shape);
        event.getBlock().setType(Material.NETHER_PORTAL);

        // Fill every interior block with a portal block, reproducing the
        // familiar Nether portal visual while keeping our custom destination.
        for (Location location : shape.inside()) {
            location.getBlock().setType(Material.NETHER_PORTAL);
        }

        player.sendMessage("§bDiamondPortals: §fportal activated.");
        teleportAfterActivation(player, shape);
    }

    private void activate(PortalShape shape) {
        // The BlockIgniteEvent only gives us the ignited block. The rest of the
        // interior is populated explicitly from the validated shape.
        for (Location location : shape.inside()) {
            location.getBlock().setType(Material.NETHER_PORTAL);
        }
    }

    private void teleportAfterActivation(Player player, PortalShape shape) {
        if (!teleporting.add(player.getUniqueId())) {
            return;
        }

        long delayTicks = Math.max(0L,\ plugin.getConfig().getLong("portal.teleport-delay-seconds", 0L) * 20L);
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    World target = dimensionManager.getOrCreate(shape.frame());
                    if (target == null || !player.isOnline()) {
                        return;
                    }

                    Location targetLocation = target.getSpawnLocation().add(0.5, 0, 0.5);
                    player.teleport(targetLocation);
                    player.sendMessage("§bDiamondPortals: §fWelcome to " + target.getName());
                } finally {
                    teleporting.remove(player.getUniqueId());
                }
            }
        }.runTaskLater(plugin, delayTicks);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerPortal(PlayerPortalEvent event) {
        // We handle custom portal travel ourselves. Cancelling the vanilla
        // portal event prevents Minecraft from trying to route this portal to
        // the Nether automatically.
        if (event.getFrom().getWorld() != null
                && event.getFrom().getWorld().getName().startsWith(
                plugin.getConfig().getString("portal.world-prefix", "diamondportal_"))) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        teleporting.remove(event.getPlayer().getUniqueId());
    }

    private Material configuredFrame() {
        String value = plugin.getConfig().getString("portal.frame-material", "DIAMOND_BLOCK");
        Material material = Material.matchMaterial(value == null ? "DIAMOND_BLOCK" : value);
        return material == null ? Material.DIAMOND_BLOCK : material;
    }
}
