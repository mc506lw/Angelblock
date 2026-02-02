package top.mistmc.angelblock.util;

import org.bukkit.Location;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;

public class SchedulerWrapper {
    private final Plugin plugin;
    private final boolean isFolia;
    private Object regionScheduler;
    private Object globalScheduler;
    private Object asyncScheduler;

    public SchedulerWrapper(Plugin plugin) {
        this.plugin = plugin;
        this.isFolia = checkFolia();
        if (isFolia) {
            initializeFoliaSchedulers();
        }
    }

    private boolean checkFolia() {
        try {
            Method getRegionScheduler = plugin.getServer().getClass().getMethod("getRegionScheduler");
            getRegionScheduler.invoke(plugin.getServer());
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private void initializeFoliaSchedulers() {
        try {
            Method getRegionScheduler = plugin.getServer().getClass().getMethod("getRegionScheduler");
            regionScheduler = getRegionScheduler.invoke(plugin.getServer());

            try {
                Method getGlobalScheduler = plugin.getServer().getClass().getMethod("getGlobalScheduler");
                globalScheduler = getGlobalScheduler.invoke(plugin.getServer());
            } catch (NoSuchMethodException e) {
                plugin.getLogger().warning("[AngelBlock] 未找到 getGlobalScheduler 方法，将使用 RegionScheduler");
            }

            try {
                Method getAsyncScheduler = plugin.getServer().getClass().getMethod("getAsyncScheduler");
                asyncScheduler = getAsyncScheduler.invoke(plugin.getServer());
            } catch (NoSuchMethodException e) {
                plugin.getLogger().warning("[AngelBlock] 未找到 getAsyncScheduler 方法");
            }
        } catch (Exception e) {
            plugin.getLogger()
                    .severe("[AngelBlock] 初始化 Folia 调度器失败: " + e.getClass().getName() + " - " + e.getMessage());
            e.printStackTrace();
        }
    }

    public BukkitTask runAtLocation(Location location, Runnable task) {
        if (isFolia && regionScheduler != null) {
            return runAtLocationFolia(location, task);
        } else {
            return runSync(task);
        }
    }

    public BukkitTask runAtLocationLater(Location location, Runnable task, long delay) {
        if (isFolia && regionScheduler != null) {
            return runAtLocationLaterFolia(location, task, delay);
        } else {
            return runSyncLater(task, delay);
        }
    }

    public BukkitTask runAtLocationTimer(Location location, Runnable task, long delay, long period) {
        if (isFolia && regionScheduler != null) {
            return runAtLocationTimerFolia(location, task, delay, period);
        } else {
            return runSyncTimer(task, delay, period);
        }
    }

    public BukkitTask runSync(Runnable task) {
        if (isFolia) {
            if (globalScheduler != null) {
                return runGlobalFolia(task);
            } else {
                plugin.getLogger().warning("[AngelBlock] GlobalScheduler 未初始化，尝试使用 RegionScheduler");
                return runRegionFolia(task);
            }
        } else {
            return plugin.getServer().getScheduler().runTask(plugin, task);
        }
    }

    public BukkitTask runSyncLater(Runnable task, long delay) {
        if (isFolia && globalScheduler != null) {
            return runGlobalLaterFolia(task, delay);
        } else {
            return plugin.getServer().getScheduler().runTaskLater(plugin, task, delay);
        }
    }

    public BukkitTask runSyncTimer(Runnable task, long delay, long period) {
        if (isFolia) {
            if (globalScheduler != null) {
                return runGlobalTimerFolia(task, delay, period);
            } else {
                return runRegionTimerFolia(task, delay, period);
            }
        } else {
            return plugin.getServer().getScheduler().runTaskTimer(plugin, task, delay, period);
        }
    }

    public void runAsync(Runnable task) {
        if (isFolia && asyncScheduler != null) {
            runAsyncFolia(task);
        } else {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, task);
        }
    }

    public void runAsyncLater(Runnable task, long delay) {
        if (isFolia && asyncScheduler != null) {
            runAsyncLaterFolia(task, delay);
        } else {
            plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, task, delay);
        }
    }

    private BukkitTask runAtLocationFolia(Location location, Runnable task) {
        try {
            Method execute = regionScheduler.getClass().getMethod("execute", Plugin.class, Location.class,
                    Runnable.class);
            Object scheduledTask = execute.invoke(regionScheduler, plugin, location, task);
            return new FoliaTaskWrapper(scheduledTask);
        } catch (Exception e) {
            plugin.getLogger().warning("[AngelBlock] Folia 区域调度失败: " + e.getMessage());
            return null;
        }
    }

    private BukkitTask runRegionFolia(Runnable task) {
        try {
            Method execute = regionScheduler.getClass().getMethod("execute", Plugin.class, Runnable.class);
            Object scheduledTask = execute.invoke(regionScheduler, plugin, task);
            return new FoliaTaskWrapper(scheduledTask);
        } catch (NoSuchMethodException e) {
            plugin.getLogger().warning("[AngelBlock] 未找到 execute(Runnable) 方法，尝试使用 execute(Location, Runnable)");
            try {
                Location spawnLoc = plugin.getServer().getWorlds().get(0).getSpawnLocation();
                return runAtLocationFolia(spawnLoc, task);
            } catch (Exception ex) {
                plugin.getLogger().severe("[AngelBlock] 无法执行区域调度: " + ex.getMessage());
                return null;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[AngelBlock] Folia 区域调度失败: " + e.getMessage());
            return null;
        }
    }

    private BukkitTask runAtLocationLaterFolia(Location location, Runnable task, long delay) {
        try {
            Method execute = regionScheduler.getClass().getMethod("runDelayed", Plugin.class, Location.class,
                    java.util.function.Consumer.class, long.class);

            Object scheduledTask = execute.invoke(regionScheduler, plugin, location,
                    new java.util.function.Consumer<Object>() {
                        @Override
                        public void accept(Object scheduledTask) {
                            task.run();
                        }
                    }, delay);

            return new FoliaTaskWrapper(scheduledTask);
        } catch (NoSuchMethodException e) {
            return runAtLocationLaterFoliaWithRunnable(location, task, delay);
        } catch (Exception e) {
            plugin.getLogger().warning("[AngelBlock] Folia 区域延迟调度失败: " + e.getMessage());
            return null;
        }
    }

    private BukkitTask runAtLocationLaterFoliaWithRunnable(Location location, Runnable task, long delay) {
        try {
            Method execute = regionScheduler.getClass().getMethod("runDelayed", Plugin.class, Location.class,
                    Runnable.class, long.class);
            Object scheduledTask = execute.invoke(regionScheduler, plugin, location, task, delay);
            return new FoliaTaskWrapper(scheduledTask);
        } catch (Exception e) {
            plugin.getLogger().warning("[AngelBlock] Folia 区域延迟调度失败: " + e.getMessage());
            return null;
        }
    }

    private BukkitTask runAtLocationTimerFolia(Location location, Runnable task, long delay, long period) {
        try {
            Method execute = regionScheduler.getClass().getMethod("runAtFixedRate", Plugin.class, Location.class,
                    Runnable.class, long.class, long.class);
            Object scheduledTask = execute.invoke(regionScheduler, plugin, location, task, delay, period);
            return new FoliaTaskWrapper(scheduledTask);
        } catch (Exception e) {
            plugin.getLogger().warning("[AngelBlock] Folia 区域定时调度失败: " + e.getMessage());
            return null;
        }
    }

    private BukkitTask runRegionTimerFolia(Runnable task, long delay, long period) {
        try {
            Location location = plugin.getServer().getWorlds().get(0).getSpawnLocation();
            long adjustedDelay = Math.max(1, delay);

            Method execute = regionScheduler.getClass().getMethod("runAtFixedRate", Plugin.class, Location.class,
                    java.util.function.Consumer.class, long.class, long.class);

            Object scheduledTask = execute.invoke(regionScheduler, plugin, location,
                    new java.util.function.Consumer<Object>() {
                        @Override
                        public void accept(Object scheduledTask) {
                            task.run();
                        }
                    }, adjustedDelay, period);

            return new FoliaTaskWrapper(scheduledTask);
        } catch (NoSuchMethodException e) {
            plugin.getLogger().warning("[AngelBlock] 未找到 Consumer<ScheduledTask> 方法，尝试使用 Runnable");
            return runRegionTimerFoliaWithRunnable(task, delay, period);
        } catch (Exception e) {
            plugin.getLogger().warning("[AngelBlock] Folia 区域定时调度失败: " + e.getMessage());
            return null;
        }
    }

    private BukkitTask runRegionTimerFoliaWithRunnable(Runnable task, long delay, long period) {
        try {
            Location location = plugin.getServer().getWorlds().get(0).getSpawnLocation();
            long adjustedDelay = Math.max(1, delay);

            Method execute = regionScheduler.getClass().getMethod("runAtFixedRate", Plugin.class, Location.class,
                    Runnable.class, long.class, long.class);
            Object scheduledTask = execute.invoke(regionScheduler, plugin, location, task, adjustedDelay, period);
            return new FoliaTaskWrapper(scheduledTask);
        } catch (Exception e) {
            plugin.getLogger().warning("[AngelBlock] Folia 区域定时调度失败: " + e.getMessage());
            return null;
        }
    }

    private BukkitTask runGlobalFolia(Runnable task) {
        try {
            Method execute = globalScheduler.getClass().getMethod("execute", Plugin.class, Runnable.class);
            Object scheduledTask = execute.invoke(globalScheduler, plugin, task);
            return new FoliaTaskWrapper(scheduledTask);
        } catch (Exception e) {
            plugin.getLogger().warning("[AngelBlock] Folia 全局调度失败: " + e.getMessage());
            return null;
        }
    }

    private BukkitTask runGlobalLaterFolia(Runnable task, long delay) {
        try {
            Method execute = globalScheduler.getClass().getMethod("runDelayed", Plugin.class, Runnable.class,
                    long.class);
            Object scheduledTask = execute.invoke(globalScheduler, plugin, task, delay);
            return new FoliaTaskWrapper(scheduledTask);
        } catch (Exception e) {
            plugin.getLogger().warning("[AngelBlock] Folia 全局延迟调度失败: " + e.getMessage());
            return null;
        }
    }

    private BukkitTask runGlobalTimerFolia(Runnable task, long delay, long period) {
        try {
            Method execute = globalScheduler.getClass().getMethod("runAtFixedRate", Plugin.class, Runnable.class,
                    long.class, long.class);
            Object scheduledTask = execute.invoke(globalScheduler, plugin, task, delay, period);
            return new FoliaTaskWrapper(scheduledTask);
        } catch (Exception e) {
            plugin.getLogger().warning("[AngelBlock] Folia 全局定时调度失败: " + e.getMessage());
            return null;
        }
    }

    private void runAsyncFolia(Runnable task) {
        try {
            Method execute = asyncScheduler.getClass().getMethod("runNow", Plugin.class, Runnable.class);
            execute.invoke(asyncScheduler, plugin, task);
        } catch (Exception e) {
            plugin.getLogger().warning("[AngelBlock] Folia 异步调度失败: " + e.getMessage());
        }
    }

    private void runAsyncLaterFolia(Runnable task, long delay) {
        try {
            Method execute = asyncScheduler.getClass().getMethod("runDelayed", Plugin.class, Runnable.class,
                    long.class);
            execute.invoke(asyncScheduler, plugin, task, delay);
        } catch (Exception e) {
            plugin.getLogger().warning("[AngelBlock] Folia 异步延迟调度失败: " + e.getMessage());
        }
    }

    public boolean isFolia() {
        return isFolia;
    }

    private static class FoliaTaskWrapper implements BukkitTask {
        private final Object foliaTask;

        FoliaTaskWrapper(Object foliaTask) {
            this.foliaTask = foliaTask;
        }

        @Override
        public int getTaskId() {
            try {
                Method getId = foliaTask.getClass().getMethod("getId");
                return (int) getId.invoke(foliaTask);
            } catch (Exception e) {
                return -1;
            }
        }

        @Override
        public Plugin getOwner() {
            try {
                Method getOwner = foliaTask.getClass().getMethod("getOwningPlugin");
                return (Plugin) getOwner.invoke(foliaTask);
            } catch (Exception e) {
                return null;
            }
        }

        @Override
        public boolean isSync() {
            return true;
        }

        @Override
        public boolean isCancelled() {
            try {
                Method isCancelled = foliaTask.getClass().getMethod("isCancelled");
                return (boolean) isCancelled.invoke(foliaTask);
            } catch (Exception e) {
                return true;
            }
        }

        @Override
        public void cancel() {
            try {
                Method cancel = foliaTask.getClass().getMethod("cancel");
                cancel.invoke(foliaTask);
            } catch (Exception e) {
            }
        }
    }
}