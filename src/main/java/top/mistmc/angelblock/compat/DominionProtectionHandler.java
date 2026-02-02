package top.mistmc.angelblock.compat;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import top.mistmc.angelblock.Angelblock;

import java.lang.reflect.Method;

public class DominionProtectionHandler {
    private final Angelblock plugin;
    private Object dominionAPI;
    private boolean enabled;
    private Method canBuildMethod;
    private Method canBreakMethod;

    public DominionProtectionHandler(Angelblock plugin) {
        this.plugin = plugin;
        this.enabled = false;
    }

    public void initialize() {
        Plugin dominionPlugin = plugin.getServer().getPluginManager().getPlugin("Dominion");
        if (dominionPlugin != null && dominionPlugin.isEnabled()) {
            try {
                Class<?> dominionAPIClass = Class.forName("cn.lunadeer.dominion.api.DominionAPI");
                Method getInstanceMethod = dominionAPIClass.getMethod("getInstance");
                dominionAPI = getInstanceMethod.invoke(null);

                try {
                    canBuildMethod = dominionAPIClass.getMethod("canBuild", Player.class, Location.class);
                } catch (NoSuchMethodException e) {
                    canBuildMethod = dominionAPIClass.getMethod("canPlace", Player.class, Location.class);
                }

                try {
                    canBreakMethod = dominionAPIClass.getMethod("canBreak", Player.class, Location.class);
                } catch (NoSuchMethodException e) {
                    canBreakMethod = canBuildMethod;
                }

                enabled = true;
                plugin.getLogger().info("[AngelBlock] Dominion 领地保护已启用");
            } catch (ClassNotFoundException e) {
                plugin.getLogger().warning("[AngelBlock] Dominion API 类未找到: " + e.getMessage());
                enabled = false;
            } catch (NoSuchMethodException e) {
                plugin.getLogger().warning("[AngelBlock] Dominion API 方法未找到: " + e.getMessage());
                enabled = false;
            } catch (Exception e) {
                plugin.getLogger().warning("[AngelBlock] Dominion 初始化失败: " + e.getMessage());
                enabled = false;
            }
        } else {
            enabled = false;
        }
    }

    public boolean canPlace(Player player, Location location) {
        if (!enabled || dominionAPI == null || canBuildMethod == null) {
            return true;
        }

        if (player.hasPermission("dominion.bypass")) {
            return true;
        }

        try {
            Object result = canBuildMethod.invoke(dominionAPI, player, location);
            if (result instanceof Boolean) {
                return (Boolean) result;
            }
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("[AngelBlock] 检查 Dominion 放置权限时出错: " + e.getMessage());
            return true;
        }
    }

    public boolean canBreak(Player player, Location location) {
        if (!enabled || dominionAPI == null || canBreakMethod == null) {
            return true;
        }

        if (player.hasPermission("dominion.bypass")) {
            return true;
        }

        try {
            Object result = canBreakMethod.invoke(dominionAPI, player, location);
            if (result instanceof Boolean) {
                return (Boolean) result;
            }
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("[AngelBlock] 检查 Dominion 破坏权限时出错: " + e.getMessage());
            return true;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void reload() {
        initialize();
    }
}