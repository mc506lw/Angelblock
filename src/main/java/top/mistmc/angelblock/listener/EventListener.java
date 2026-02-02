package top.mistmc.angelblock.listener;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import top.mistmc.angelblock.Angelblock;
import top.mistmc.angelblock.compat.DominionProtectionHandler;
import top.mistmc.angelblock.compat.HuskClaimsProtectionHandler;
import top.mistmc.angelblock.config.ConfigManager;
import top.mistmc.angelblock.data.BlockDataManager;
import top.mistmc.angelblock.util.SchedulerWrapper;
import top.mistmc.angelblock.util.Utils;

import java.util.*;

public class EventListener implements Listener {
    private final ConfigManager configManager;
    private final BlockDataManager dataManager;
    private final HuskClaimsProtectionHandler huskClaimsProtectionHandler;
    private final DominionProtectionHandler dominionProtectionHandler;
    private final SchedulerWrapper scheduler;
    private final Map<UUID, Integer> playerDistances;
    private final Map<UUID, List<Location>> recentPlacements;
    private final Map<UUID, Integer> lastHeldSlots;

    public EventListener(Angelblock plugin, ConfigManager configManager, BlockDataManager dataManager,
            HuskClaimsProtectionHandler huskClaimsProtectionHandler, Map<UUID, Integer> playerDistances,
            Map<UUID, List<Location>> recentPlacements) {
        this.configManager = configManager;
        this.dataManager = dataManager;
        this.huskClaimsProtectionHandler = huskClaimsProtectionHandler;
        this.dominionProtectionHandler = plugin.getDominionProtectionHandler();
        this.scheduler = plugin.getScheduler();
        this.playerDistances = playerDistances;
        this.recentPlacements = recentPlacements;
        this.lastHeldSlots = new HashMap<>();
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            Block clickedBlock = event.getClickedBlock();
            if (clickedBlock != null && Utils.isAngelBlock(clickedBlock, configManager.getItemMaterial())) {
                event.setCancelled(true);
                removeAngelBlock(clickedBlock, player);
                return;
            }
        }

        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if ((event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.RIGHT_CLICK_AIR)
                && Utils.isAngelBlock(itemInHand, configManager.getItemName())) {

            event.setCancelled(true);

            if (!player.hasPermission("angelblock.use")) {
                Utils.sendMessage(player, configManager.getMessage("no-permission"));
                return;
            }

            int distance = playerDistances.getOrDefault(player.getUniqueId(),
                    configManager.getDefaultDistance());

            Location eyeLocation = player.getEyeLocation();
            org.bukkit.util.Vector direction = eyeLocation.getDirection().normalize();
            Location targetLocation = eyeLocation.clone().add(direction.multiply(distance));

            Block targetBlock = targetLocation.getBlock();
            if (!targetBlock.getType().isAir() && !targetBlock.isLiquid()) {
                Utils.sendMessage(player, configManager.getMessage("cant-place-here"));
                return;
            }

            placeAngelBlock(player, targetBlock);
        }
    }

    @EventHandler
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        ItemStack currentItem = player.getInventory().getItem(event.getPreviousSlot());

        if (player.isSneaking()) {
            boolean isAngelBlock = Utils.isAngelBlock(currentItem, configManager.getItemName());
            if (isAngelBlock) {
                int currentDistance = playerDistances.getOrDefault(player.getUniqueId(),
                        configManager.getDefaultDistance());
                int newDistance = currentDistance;

                int previousSlot = event.getPreviousSlot();
                int newSlot = event.getNewSlot();

                boolean isScrollingRight = (newSlot == (previousSlot + 1) % 9) ||
                        (previousSlot == 8 && newSlot == 0);

                if (isScrollingRight) {
                    newDistance = currentDistance + 1;
                    if (newDistance > configManager.getMaxDistance()) {
                        newDistance = configManager.getMinDistance();
                    }
                } else {
                    newDistance = currentDistance - 1;
                    if (newDistance < configManager.getMinDistance()) {
                        newDistance = configManager.getMaxDistance();
                    }
                }

                playerDistances.put(player.getUniqueId(), newDistance);
                Utils.sendActionBar(player,
                        configManager.getMessage("placement-distance", "distance", String.valueOf(newDistance)));

                player.getInventory().setHeldItemSlot(event.getPreviousSlot());
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        playerDistances.remove(playerId);
        recentPlacements.remove(playerId);
        lastHeldSlots.remove(playerId);
    }

    private void placeAngelBlock(Player player, Block block) {
        if (!canPlaceAt(player, block.getLocation())) {
            Utils.sendMessage(player, configManager.getMessage("cant-place-in-claim"));
            return;
        }

        UUID playerId = player.getUniqueId();
        int maxBlocks = getMaxBlocks(player);
        int currentBlocks = dataManager.getPlayerBlockCount(playerId);

        if (currentBlocks >= maxBlocks) {
            Utils.sendMessage(player, configManager.getMessage("max-blocks-reached", "max", String.valueOf(maxBlocks)));
            return;
        }

        Material material = Material.matchMaterial(configManager.getItemMaterial());
        block.setType(material != null ? material : Material.STONE);

        dataManager.saveBlockLocation(block.getLocation(), playerId);
        trackRecentPlacement(player, block.getLocation());

        ItemStack item = player.getInventory().getItemInMainHand();
        Utils.removeItemFromHand(player, item);
    }

    private void trackRecentPlacement(Player player, Location location) {
        UUID playerId = player.getUniqueId();
        List<Location> placements = recentPlacements.getOrDefault(playerId, new ArrayList<>());
        placements.add(location);
        recentPlacements.put(playerId, placements);

        scheduler.runAtLocationLater(location, () -> {
            verifyPlacement(player, location);
        }, 20);
    }

    private void verifyPlacement(Player player, Location location) {
        UUID playerId = player.getUniqueId();

        if (!recentPlacements.containsKey(playerId) ||
                !recentPlacements.get(playerId).contains(location)) {
            return;
        }

        Block block = location.getBlock();
        if (!Utils.isAngelBlock(block, configManager.getItemMaterial())) {
            removeRecentPlacement(playerId, location);
            dataManager.removeBlockLocation(location);

            if (player.isOnline() && player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
                ItemStack newBlock = createAngelBlock();
                Utils.giveItem(player, newBlock);
                Utils.sendMessage(player, configManager.getMessage("block-removed-by-protection"));
            }
        }
    }

    private void removeRecentPlacement(UUID playerId, Location location) {
        if (recentPlacements.containsKey(playerId)) {
            List<Location> list = recentPlacements.get(playerId);
            list.remove(location);
            if (list.isEmpty()) {
                recentPlacements.remove(playerId);
            }
        }
    }

    private void removeAngelBlock(Block block, Player player) {
        if (!canBreakAt(player, block.getLocation())) {
            Utils.sendMessage(player, configManager.getMessage("cant-break-in-claim"));
            return;
        }

        removeRecentPlacement(player.getUniqueId(), block.getLocation());

        UUID ownerId = dataManager.getBlockOwner(block.getLocation());
        if (ownerId == null) {
            Utils.sendMessage(player, configManager.getMessage("corrupted-block"));
            return;
        }

        if (!player.getUniqueId().equals(ownerId) && !player.hasPermission("angelblock.admin")) {
            String ownerName = Bukkit.getOfflinePlayer(ownerId).getName();
            Utils.sendMessage(player,
                    configManager.getMessage("cant-break-others", "owner", ownerName != null ? ownerName : "未知玩家"));
            return;
        }

        if (player.hasPermission("angelblock.admin") && !player.getUniqueId().equals(ownerId)) {
            Player owner = Bukkit.getPlayer(ownerId);
            if (owner != null && owner.isOnline()) {
                Utils.sendMessage(owner,
                        configManager.getMessage("admin-removed-your-block", "admin", player.getName()));
            }
        }

        player.playSound(block.getLocation(), Sound.BLOCK_STONE_BREAK, 1.0f, 1.0f);

        block.setType(Material.AIR);
        dataManager.removeBlockLocation(block.getLocation());
        Utils.sendMessage(player, configManager.getMessage("block-removed"));

        if (player.getGameMode() != org.bukkit.GameMode.CREATIVE && player.getUniqueId().equals(ownerId)) {
            ItemStack newBlock = createAngelBlock();
            Utils.giveItem(player, newBlock);
        }
    }

    private boolean canPlaceAt(Player player, Location location) {
        if (dominionProtectionHandler != null && dominionProtectionHandler.isEnabled()) {
            return dominionProtectionHandler.canPlace(player, location);
        }
        return huskClaimsProtectionHandler.canPlace(player, location);
    }

    private boolean canBreakAt(Player player, Location location) {
        if (dominionProtectionHandler != null && dominionProtectionHandler.isEnabled()) {
            return dominionProtectionHandler.canBreak(player, location);
        }
        return huskClaimsProtectionHandler.canBreak(player, location);
    }

    private int getMaxBlocks(Player player) {
        if (player.hasPermission("angelblock.max.unlimited")) {
            return Integer.MAX_VALUE;
        }

        if (player.hasPermission("angelblock.max.*")) {
            return Integer.MAX_VALUE;
        }

        int maxBlocks = 32;

        for (org.bukkit.permissions.PermissionAttachmentInfo attachment : player.getEffectivePermissions()) {
            String permission = attachment.getPermission();
            if (permission.startsWith("angelblock.max.")) {
                String numberStr = permission.substring("angelblock.max.".length());
                try {
                    int number = Integer.parseInt(numberStr);
                    if (number > maxBlocks) {
                        maxBlocks = number;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }

        return maxBlocks;
    }

    private ItemStack createAngelBlock() {
        Material material = Material.matchMaterial(configManager.getItemMaterial());
        ItemStack item = new ItemStack(material != null ? material : Material.STONE);

        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Utils.colorize(configManager.getItemName()));

            List<String> lore = new ArrayList<>();
            for (String line : configManager.getItemLore()) {
                lore.add(Utils.colorize(line));
            }
            meta.setLore(lore);

            item.setItemMeta(meta);
        }
        return item;
    }
}
