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

    public static boolean isAngelBlock(Block block, String materialName) {
        Material material = Material.matchMaterial(materialName);
        return material != null && block.getType() == material;
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
