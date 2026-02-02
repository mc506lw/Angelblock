package top.mistmc.angelblock;

import org.bukkit.plugin.java.JavaPlugin;
import top.mistmc.angelblock.command.CommandHandler;
import top.mistmc.angelblock.compat.DominionProtectionHandler;
import top.mistmc.angelblock.compat.HuskClaimsProtectionHandler;
import top.mistmc.angelblock.config.ConfigManager;
import top.mistmc.angelblock.data.BlockDataManager;
import top.mistmc.angelblock.database.DatabaseManager;
import top.mistmc.angelblock.listener.EventListener;
import top.mistmc.angelblock.task.ParticlePreviewTask;
import top.mistmc.angelblock.util.SchedulerWrapper;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Angelblock extends JavaPlugin {
    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private BlockDataManager dataManager;
    private CommandHandler commandHandler;
    private EventListener eventListener;
    private HuskClaimsProtectionHandler huskClaimsProtectionHandler;
    private DominionProtectionHandler dominionProtectionHandler;
    private SchedulerWrapper scheduler;

    private final Map<UUID, Integer> playerDistances = new HashMap<>();
    private final Map<UUID, java.util.List<org.bukkit.Location>> recentPlacements = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();

        scheduler = new SchedulerWrapper(this);

        configManager = new ConfigManager(this);
        configManager.reload();

        databaseManager = new DatabaseManager(this);
        dataManager = new BlockDataManager(this, databaseManager);
        dataManager.load();

        huskClaimsProtectionHandler = new HuskClaimsProtectionHandler(this);
        huskClaimsProtectionHandler.initialize();

        dominionProtectionHandler = new DominionProtectionHandler(this);
        dominionProtectionHandler.initialize();

        commandHandler = new CommandHandler(this, configManager, dataManager, scheduler);
        getCommand("angelblock").setExecutor(commandHandler);
        getCommand("angelblock").setTabCompleter(commandHandler);

        eventListener = new EventListener(this, configManager, dataManager, huskClaimsProtectionHandler,
                playerDistances, recentPlacements);
        getServer().getPluginManager().registerEvents(eventListener, this);

        ParticlePreviewTask particleTask = new ParticlePreviewTask(this, configManager, playerDistances,
                new HashMap<>(), scheduler);
        scheduler.runSyncTimer(particleTask, 0, 5);

        getLogger().info("[AngelBlock] 天使方块插件已启用！");
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.close();
        }
        getLogger().info("[AngelBlock] 天使方块插件已禁用！");
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public BlockDataManager getDataManager() {
        return dataManager;
    }

    public HuskClaimsProtectionHandler getHuskClaimsProtectionHandler() {
        return huskClaimsProtectionHandler;
    }

    public DominionProtectionHandler getDominionProtectionHandler() {
        return dominionProtectionHandler;
    }

    public SchedulerWrapper getScheduler() {
        return scheduler;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
}
