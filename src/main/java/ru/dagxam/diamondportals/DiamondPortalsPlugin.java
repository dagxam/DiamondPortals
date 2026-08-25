package ru.dagxam.diamondportals;

import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import ru.dagxam.diamondportals.listener.PortalListener;
import ru.dagxam.diamondportals.world.BlockWorldGenerator;
import ru.dagxam.diamondportals.world.DimensionManager;

public final class DiamondPortalsPlugin extends JavaPlugin {

    private DimensionManager dimensionManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        BlockWorldGenerator generator = new BlockWorldGenerator();
        this.dimensionManager = new DimensionManager(this, generator);

        getServer().getPluginManager().registerEvents(
                new PortalListener(this, dimensionManager), this
        );

        getLogger().info("DiamondPortals enabled.");
        getLogger().info("Diamond portals use DIAMOND_BLOCK frames and flint & steel activation.");
    }

    @Override
    public void onDisable() {
        getLogger().info("DiamondPortals disabled.");
    }

    public DimensionManager getDimensionManager() {
        return dimensionManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("diamondportals")) {
            return false;
        }

        if (!sender.hasPermission("diamondportals.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            sender.sendMessage("§aDiamondPortals config reloaded.");
            return true;
        }

        sender.sendMessage("§e/diamondportals reload");
        return true;
    }
}
