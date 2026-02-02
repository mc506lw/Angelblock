package top.mistmc.angelblock.data;

import org.bukkit.Location;
import top.mistmc.angelblock.Angelblock;
import top.mistmc.angelblock.database.DatabaseManager;

import java.sql.*;
import java.util.*;

public class BlockDataManager {
    private final Angelblock plugin;
    private final DatabaseManager databaseManager;

    public BlockDataManager(Angelblock plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    public void load() {
        databaseManager.initialize();
    }

    public void save() {
    }

    public void saveBlockLocation(Location loc, UUID playerId) {
        String sql = "INSERT OR REPLACE INTO angel_blocks (world, x, y, z, player_uuid) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, loc.getWorld().getName());
            stmt.setInt(2, loc.getBlockX());
            stmt.setInt(3, loc.getBlockY());
            stmt.setInt(4, loc.getBlockZ());
            stmt.setString(5, playerId.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("[AngelBlock] 保存方块位置失败: " + e.getMessage());
        }
    }

    public void removeBlockLocation(Location loc) {
        String sql = "DELETE FROM angel_blocks WHERE world = ? AND x = ? AND y = ? AND z = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, loc.getWorld().getName());
            stmt.setInt(2, loc.getBlockX());
            stmt.setInt(3, loc.getBlockY());
            stmt.setInt(4, loc.getBlockZ());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("[AngelBlock] 移除方块位置失败: " + e.getMessage());
        }
    }

    public UUID getBlockOwner(Location loc) {
        String sql = "SELECT player_uuid FROM angel_blocks WHERE world = ? AND x = ? AND y = ? AND z = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, loc.getWorld().getName());
            stmt.setInt(2, loc.getBlockX());
            stmt.setInt(3, loc.getBlockY());
            stmt.setInt(4, loc.getBlockZ());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return UUID.fromString(rs.getString("player_uuid"));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[AngelBlock] 获取方块所有者失败: " + e.getMessage());
        }
        return null;
    }

    public int getPlayerBlockCount(UUID playerId) {
        String sql = "SELECT COUNT(*) FROM angel_blocks WHERE player_uuid = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerId.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[AngelBlock] 获取玩家方块数量失败: " + e.getMessage());
        }
        return 0;
    }

    public List<Location> getAllBlockLocations() {
        List<Location> locations = new ArrayList<>();
        String sql = "SELECT world, x, y, z FROM angel_blocks";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                String world = rs.getString("world");
                int x = rs.getInt("x");
                int y = rs.getInt("y");
                int z = rs.getInt("z");

                org.bukkit.World worldObj = plugin.getServer().getWorld(world);
                if (worldObj != null) {
                    locations.add(new Location(worldObj, x, y, z));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[AngelBlock] 获取所有方块位置失败: " + e.getMessage());
        }
        return locations;
    }

    public int removeAllBlocks() {
        String sql = "DELETE FROM angel_blocks";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            return stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("[AngelBlock] 移除所有方块失败: " + e.getMessage());
            return 0;
        }
    }

    public int removeBlocksByOwner(UUID ownerUUID) {
        String sql = "DELETE FROM angel_blocks WHERE player_uuid = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, ownerUUID.toString());
            return stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("[AngelBlock] 移除指定玩家的方块失败: " + e.getMessage());
            return 0;
        }
    }

    public Map<UUID, List<Location>> getBlocksByOwner() {
        Map<UUID, List<Location>> result = new HashMap<>();
        String sql = "SELECT player_uuid, world, x, y, z FROM angel_blocks";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                UUID ownerUUID = UUID.fromString(rs.getString("player_uuid"));
                String world = rs.getString("world");
                int x = rs.getInt("x");
                int y = rs.getInt("y");
                int z = rs.getInt("z");

                org.bukkit.World worldObj = plugin.getServer().getWorld(world);
                if (worldObj != null) {
                    result.computeIfAbsent(ownerUUID, k -> new ArrayList<>())
                            .add(new Location(worldObj, x, y, z));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[AngelBlock] 按所有者获取方块失败: " + e.getMessage());
        }
        return result;
    }
}
