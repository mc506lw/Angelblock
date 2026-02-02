package top.mistmc.angelblock.command;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import top.mistmc.angelblock.Angelblock;
import top.mistmc.angelblock.config.ConfigManager;
import top.mistmc.angelblock.data.BlockDataManager;
import top.mistmc.angelblock.util.ColorUtils;
import top.mistmc.angelblock.util.SchedulerWrapper;
import top.mistmc.angelblock.util.Utils;

import java.util.*;

public class CommandHandler implements CommandExecutor, TabCompleter {
    private final ConfigManager configManager;
    private final BlockDataManager dataManager;
    private final SchedulerWrapper scheduler;
    private final Map<UUID, Long> removeallConfirmations = new HashMap<>();
    private final Map<UUID, String> removeallTargets = new HashMap<>();

    public CommandHandler(Angelblock plugin, ConfigManager configManager, BlockDataManager dataManager,
            SchedulerWrapper scheduler) {
        this.configManager = configManager;
        this.dataManager = dataManager;
        this.scheduler = scheduler;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0) {
            if (args[0].equalsIgnoreCase("reload")) {
                return handleReload(sender);
            } else if (args[0].equalsIgnoreCase("removeall")) {
                return handleRemoveAll(sender, args);
            }
        }

        return handleGive(sender, args);
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("angelblock.reload")) {
            Utils.sendMessage(sender, configManager.getMessage("no-permission"));
            return true;
        }
        configManager.reload();
        dataManager.load();
        Utils.sendMessage(sender, configManager.getMessage("reload-success"));
        return true;
    }

    private boolean handleRemoveAll(CommandSender sender, String[] args) {
        if (args.length > 1) {
            if (args[1].equalsIgnoreCase("confirm")) {
                if (!(sender instanceof Player)) {
                    Utils.sendMessage(sender, configManager.getMessage("player-only-command"));
                    return true;
                }

                Player player = (Player) sender;
                Long lastConfirm = removeallConfirmations.get(player.getUniqueId());
                if (lastConfirm != null && System.currentTimeMillis() - lastConfirm < 10000) {
                    String targetName = removeallTargets.get(player.getUniqueId());
                    if (targetName != null) {
                        removeAllAngelBlocks(player, targetName);
                    } else {
                        removeAllAngelBlocks(player, null);
                    }
                    removeallConfirmations.remove(player.getUniqueId());
                    removeallTargets.remove(player.getUniqueId());
                    return true;
                }
            } else {
                if (!sender.hasPermission("angelblock.removeall.others")) {
                    Utils.sendMessage(sender, configManager.getMessage("no-permission"));
                    return true;
                }

                String targetName = args[1];
                if (sender instanceof Player) {
                    Player player = (Player) sender;
                    removeallTargets.put(player.getUniqueId(), targetName);
                    removeallConfirmations.put(player.getUniqueId(), System.currentTimeMillis());
                    String displayName = getSelectorDisplayName(targetName, player);
                    Utils.sendMessage(sender,
                            configManager.getMessage("removeall-confirm-target", "target", displayName));
                } else {
                    removeAllAngelBlocks(null, targetName);
                }
                return true;
            }
        }

        if (sender instanceof Player) {
            Player player = (Player) sender;
            removeallTargets.put(player.getUniqueId(), null);
            removeallConfirmations.put(player.getUniqueId(), System.currentTimeMillis());
            Utils.sendMessage(sender, configManager.getMessage("removeall-confirm"));
        } else {
            Utils.sendMessage(sender, configManager.getMessage("player-only-command"));
        }
        return true;
    }

    private boolean handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("angelblock.give")) {
            Utils.sendMessage(sender, configManager.getMessage("no-permission"));
            return true;
        }

        Player target = args.length > 0
                ? Bukkit.getPlayer(args[0])
                : (sender instanceof Player ? (Player) sender : null);

        if (target == null) {
            Utils.sendMessage(sender, configManager.getMessage("player-not-found"));
            return true;
        }

        target.getInventory().addItem(createAngelBlock());
        Utils.sendMessage(sender, configManager.getMessage("give-success", "player", target.getName()));
        return true;
    }

    private void removeAllAngelBlocks(Player executor, String targetName) {
        List<UUID> targetUUIDs = new ArrayList<>();

        if (targetName == null) {
            if (executor == null) {
                return;
            }
            targetUUIDs.add(executor.getUniqueId());
        } else {
            if (targetName.startsWith("@")) {
                targetUUIDs.addAll(getTargetUUIDs(targetName, executor));
            } else {
                Player target = Bukkit.getPlayer(targetName);
                if (target != null) {
                    targetUUIDs.add(target.getUniqueId());
                }
            }
        }

        if (targetUUIDs.isEmpty()) {
            if (executor != null) {
                Utils.sendMessage(executor, configManager.getMessage("player-not-found"));
            }
            return;
        }

        Map<UUID, List<Location>> blocksByOwner = dataManager.getBlocksByOwner();
        if (blocksByOwner.isEmpty()) {
            if (executor != null) {
                Utils.sendMessage(executor, configManager.getMessage("removeall-error"));
            }
            return;
        }

        final int[] totalRemoved = { 0 };
        for (UUID targetUUID : targetUUIDs) {
            List<Location> locations = blocksByOwner.get(targetUUID);
            if (locations == null || locations.isEmpty()) {
                continue;
            }

            for (Location loc : locations) {
                scheduler.runAtLocation(loc, () -> {
                    Chunk chunk = loc.getChunk();
                    if (!chunk.isLoaded()) {
                        chunk.load();
                    }
                    loc.getBlock().setType(Material.AIR);
                });
            }

            Player owner = Bukkit.getPlayer(targetUUID);
            if (owner != null && owner.isOnline() && owner.getGameMode() != org.bukkit.GameMode.CREATIVE) {
                ItemStack newBlock = createAngelBlock();
                HashMap<Integer, ItemStack> leftover = owner.getInventory().addItem(newBlock);

                if (!leftover.isEmpty()) {
                    for (ItemStack item : leftover.values()) {
                        owner.getWorld().dropItem(owner.getLocation(), item);
                    }
                }
            }

            if (executor != null && owner != null && owner.isOnline()
                    && !owner.getUniqueId().equals(executor.getUniqueId())) {
                Utils.sendMessage(owner,
                        configManager.getMessage("admin-removed-your-block", "admin", executor.getName()));
            }

            totalRemoved[0] += locations.size();
        }

        for (UUID targetUUID : targetUUIDs) {
            dataManager.removeBlocksByOwner(targetUUID);
        }

        if (executor != null) {
            if (targetUUIDs.size() == 1) {
                UUID singleTarget = targetUUIDs.get(0);
                Player targetPlayer = Bukkit.getPlayer(singleTarget);
                String targetDisplayName = targetPlayer != null ? targetPlayer.getName() : "未知玩家";
                Utils.sendMessage(executor,
                        configManager.getMessage("removeall-success-single", "target", targetDisplayName, "count",
                                String.valueOf(totalRemoved[0])));
            } else {
                Utils.sendMessage(executor,
                        configManager.getMessage("removeall-success", "count", String.valueOf(totalRemoved[0])));
            }
        }
    }

    private List<UUID> getTargetUUIDs(String selector, Player executor) {
        List<UUID> uuids = new ArrayList<>();

        switch (selector) {
            case "@a":
                for (Player player : Bukkit.getOnlinePlayers()) {
                    uuids.add(player.getUniqueId());
                }
                break;
            case "@p":
                if (executor != null) {
                    uuids.add(executor.getUniqueId());
                }
                break;
            case "@r":
                List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
                if (!players.isEmpty()) {
                    Player randomPlayer = players.get((int) (Math.random() * players.size()));
                    uuids.add(randomPlayer.getUniqueId());
                }
                break;
            default:
                break;
        }

        return uuids;
    }

    private String getSelectorDisplayName(String selector, Player executor) {
        switch (selector) {
            case "@a":
                return "所有玩家";
            case "@p":
                return executor != null ? executor.getName() : "自己";
            case "@r":
                return "随机玩家";
            default:
                return selector;
        }
    }

    private ItemStack createAngelBlock() {
        Material material = Material.matchMaterial(configManager.getItemMaterial());
        ItemStack item = new ItemStack(material != null ? material : Material.STONE);

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtils.colorize(configManager.getItemName()));

            List<String> lore = new ArrayList<>();
            for (String line : configManager.getItemLore()) {
                lore.add(ColorUtils.colorize(line));
            }
            meta.setLore(lore);

            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String input = args[0].toLowerCase();

            if ("reload".startsWith(input) && sender.hasPermission("angelblock.reload")) {
                completions.add("reload");
            }
            if ("removeall".startsWith(input) && sender.hasPermission("angelblock.removeall")) {
                completions.add("removeall");
            }
            if ("give".startsWith(input) && sender.hasPermission("angelblock.give")) {
                completions.add("give");
            }
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("give") && sender.hasPermission("angelblock.give")) {
                String input = args[1].toLowerCase();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getName().toLowerCase().startsWith(input)) {
                        completions.add(player.getName());
                    }
                }
            } else if (args[0].equalsIgnoreCase("removeall")) {
                String input = args[1].toLowerCase();

                if ("confirm".startsWith(input) && sender.hasPermission("angelblock.removeall")) {
                    completions.add("confirm");
                }

                if (sender.hasPermission("angelblock.removeall.others")) {
                    if ("@a".startsWith(input)) {
                        completions.add("@a");
                    }
                    if ("@p".startsWith(input)) {
                        completions.add("@p");
                    }
                    if ("@r".startsWith(input)) {
                        completions.add("@r");
                    }

                    for (Player player : Bukkit.getOnlinePlayers()) {
                        if (player.getName().toLowerCase().startsWith(input)) {
                            completions.add(player.getName());
                        }
                    }
                }
            }
        }

        return completions;
    }
}
