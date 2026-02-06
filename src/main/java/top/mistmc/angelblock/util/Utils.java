package top.mistmc.angelblock.util;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import top.mistmc.angelblock.data.BlockDataManager;

public class Utils {

    public static String colorize(String message) {
        return ColorUtils.colorize(message);
    }

    public static void sendMessage(CommandSender sender, String message) {
        if (message != null && !message.isEmpty()) {
            sender.sendMessage(message);
        }
    }

    public static void sendActionBar(Player player, String message) {
        if (message != null && !message.isEmpty()) {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
        }
    }

    public static boolean isAngelBlock(Block block, String materialName, BlockDataManager dataManager) {
        Material material = Material.matchMaterial(materialName);
        if (material == null || block.getType() != material) {
            return false;
        }
        // 检查数据库中是否存在该方块的记录
        return dataManager.getBlockOwner(block.getLocation()) != null;
    }

    public static boolean isAngelBlock(ItemStack item, String itemName) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return false;
        }

        String coloredItemName = colorize(itemName);
        String displayName = meta.getDisplayName();

        return displayName.equalsIgnoreCase(coloredItemName);
    }

    public static String formatLocation(Location loc) {
        return String.format("世界: %s, X: %d, Y: %d, Z: %d",
                loc.getWorld().getName(),
                loc.getBlockX(),
                loc.getBlockY(),
                loc.getBlockZ());
    }

    public static void removeItemFromHand(Player player, ItemStack item) {
        if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            if (item.getAmount() > 1) {
                item.setAmount(item.getAmount() - 1);
            } else {
                player.getInventory().setItemInMainHand(null);
            }
        }
    }

    public static void giveItem(Player player, ItemStack item) {
        if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            java.util.HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);

            if (!leftover.isEmpty()) {
                for (ItemStack leftoverItem : leftover.values()) {
                    player.getWorld().dropItem(player.getLocation(), leftoverItem);
                }
            }
        }
    }
}
