package top.mistmc.angelblock.compat;

import net.william278.huskclaims.api.HuskClaimsAPI;
import net.william278.huskclaims.libraries.cloplib.operation.OperationType;
import net.william278.huskclaims.position.Position;
import net.william278.huskclaims.user.OnlineUser;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import top.mistmc.angelblock.Angelblock;

public class HuskClaimsProtectionHandler {
    private final Angelblock plugin;
    private HuskClaimsAPI huskClaims;
    private boolean enabled;

    public HuskClaimsProtectionHandler(Angelblock plugin) {
        this.plugin = plugin;
        this.enabled = false;
    }

    public void initialize() {
        Plugin huskPlugin = plugin.getServer().getPluginManager().getPlugin("HuskClaims");
        if (huskPlugin != null && huskPlugin.isEnabled()) {
            try {
                huskClaims = HuskClaimsAPI.getInstance();
                enabled = true;
                plugin.getLogger().info("[AngelBlock] HuskClaims 领地保护已启用");
            } catch (Exception e) {
                plugin.getLogger().warning("[AngelBlock] HuskClaims 初始化失败: " + e.getMessage());
                enabled = false;
            }
        } else {
            enabled = false;
        }
    }

    public boolean canPlace(Player player, Location location) {
        if (!enabled || huskClaims == null) {
            return true;
        }

        if (player.hasPermission("huskclaims.bypass")) {
            return true;
        }

        try {
            Position position = huskClaims.getPosition(
                    location.getX(),
                    location.getY(),
                    location.getZ(),
                    huskClaims.getWorld(location.getWorld().getName()));
            OnlineUser user = huskClaims.getOnlineUser(player.getUniqueId());
            return huskClaims.isOperationAllowed(user, OperationType.BLOCK_PLACE, position);
        } catch (Exception e) {
            plugin.getLogger().warning("[AngelBlock] 检查放置权限时出错: " + e.getMessage());
            return true;
        }
    }

    public boolean canBreak(Player player, Location location) {
        if (!enabled || huskClaims == null) {
            return true;
        }

        if (player.hasPermission("huskclaims.bypass")) {
            return true;
        }

        try {
            Position position = huskClaims.getPosition(
                    location.getX(),
                    location.getY(),
                    location.getZ(),
                    huskClaims.getWorld(location.getWorld().getName()));
            OnlineUser user = huskClaims.getOnlineUser(player.getUniqueId());
            return huskClaims.isOperationAllowed(user, OperationType.BLOCK_BREAK, position);
        } catch (Exception e) {
            plugin.getLogger().warning("[AngelBlock] 检查破坏权限时出错: " + e.getMessage());
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