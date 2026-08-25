package ru.dagxam.diamondportals;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import ru.dagxam.diamondportals.listener.PortalListener;
import ru.dagxam.diamondportals.world.DimensionManager;

public final class DiamondPortalsPlugin extends JavaPlugin {

    private DimensionManager dimensionManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.dimensionManager = new DimensionManager(this);

        getServer().getPluginManager().registerEvents(
                new PortalListener(this, dimensionManager), this
        );

        getLogger().info("DiamondPortals включён.");
        getLogger().info("Кастомные порталы имеют фиксированную рамку 4x5, внутреннюю область 2x3 и активируются обычным огнивом одним кликом.");
    }

    @Override
    public void onDisable() {
        getLogger().info("DiamondPortals выключен.");
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
            sender.sendMessage("§cУ вас нет прав для использования этой команды.");
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            sender.sendMessage("§aНастройки DiamondPortals успешно перезагружены.");
            return true;
        }

        sender.sendMessage("§eИспользование: §f/diamondportals reload");
        return true;
    }
}
