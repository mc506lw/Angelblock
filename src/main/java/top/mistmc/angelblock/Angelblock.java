package top.mistmc.angelblock;

import net.william278.huskclaims.api.HuskClaimsAPI;
import net.william278.huskclaims.libraries.cloplib.operation.OperationType;
import net.william278.huskclaims.libraries.cloplib.operation.OperationTypeRegistry;
import net.william278.huskclaims.position.Position;
import net.william278.huskclaims.user.OnlineUser;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.bukkit.FluidCollisionMode;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class Angelblock extends JavaPlugin implements Listener, TabExecutor {

    private final Map<UUID, Integer> playerDistances = new HashMap<>();
    private final Map<UUID, Location> lastPreviewLocations = new HashMap<>();
    private final Map<UUID, Long> removeallConfirmations = new HashMap<>();
    private Particle.DustOptions dustOptions;
    private FileConfiguration config;
    private final Map<UUID, Integer> playerMaxBlocksCache = new HashMap<>();
    private final Map<UUID, List<Location>> recentPlacements = new HashMap<>();
    private File blocksFile;
    private FileConfiguration blocksConfig;
    private HuskClaimsAPI huskClaims;
    private OperationType releaseMonOpType;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadPluginConfig();
        loadBlocksFile(); // 确保在启用时加载blocks.yml
        getServer().getPluginManager().registerEvents(this, this);

        // 启动粒子预览任务
        new ParticlePreviewTask().runTaskTimer(this, 0, 5);

        // 初始化 HuskClaims
        Plugin huskPlugin = Bukkit.getServer().getPluginManager().getPlugin("HuskClaims");
        if (huskPlugin != null && huskPlugin.isEnabled()) {
            huskClaims = HuskClaimsAPI.getInstance();
            getLogger().info("HuskClaims 已加载，领地保护已启用");
        } else {
            getLogger().warning("HuskClaims 未找到，领地保护已禁用");
        }
    }

    private void reloadPluginConfig() {
        reloadConfig();
        config = getConfig();
        loadParticleSettings();
    }

    private void loadParticleSettings() {
        String[] rgb = config.getString("particle.color", "255,255,255").split(",");
        int r = Math.min(255, Math.max(0, Integer.parseInt(rgb[0].trim())));
        int g = Math.min(255, Math.max(0, Integer.parseInt(rgb[1].trim())));
        int b = Math.min(255, Math.max(0, Integer.parseInt(rgb[2].trim())));
        float size = (float) config.getDouble("particle.size", 0.5);

        dustOptions = new Particle.DustOptions(Color.fromRGB(r, g, b), size);
    }

    private void loadBlocksFile() {
        blocksFile = new File(getDataFolder(), "blocks.yml");

        // 确保文件存在
        if (!blocksFile.exists()) {
            try {
                // 创建插件数据文件夹（如果不存在）
                if (!blocksFile.getParentFile().exists()) {
                    blocksFile.getParentFile().mkdirs();
                }

                // 创建空文件
                blocksFile.createNewFile();
                getLogger().info("已创建 blocks.yml 文件");

                // 如果需要初始内容，可以在这里添加
                blocksConfig = YamlConfiguration.loadConfiguration(blocksFile);
                blocksConfig.createSection("blocks"); // 创建空的方块存储区
                saveBlocksFile(); // 保存初始文件
            } catch (IOException e) {
                getLogger().severe("创建 blocks.yml 文件失败: " + e.getMessage());
            }
        } else {
            // 如果文件已存在，正常加载
            blocksConfig = YamlConfiguration.loadConfiguration(blocksFile);
        }
    }

    private void saveBlocksFile() {
        // 关键修复：添加null检查
        if (blocksConfig == null) {
            getLogger().warning("无法保存blocks.yml：配置未初始化");
            return;
        }

        try {
            blocksConfig.save(blocksFile);
        } catch (IOException e) {
            getLogger().severe("保存 blocks.yml 失败: " + e.getMessage());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0) {
            if (args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("angelblock.reload")) {
                    sendMessage(sender, "messages.no-permission");
                    return true;
                }
                reloadPluginConfig();
                loadBlocksFile(); // 重新加载blocks.yml
                sendMessage(sender, "messages.reload-success");
                return true;
            } else if (args[0].equalsIgnoreCase("removeall")) {
                if (!sender.hasPermission("angelblock.removeall")) {
                    sendMessage(sender, "messages.no-permission");
                    return true;
                }

                // 确认机制
                if (args.length > 1 && args[1].equalsIgnoreCase("confirm")) {
                    if (!(sender instanceof Player)) {
                        sendMessage(sender, "messages.player-only-command");
                        return true;
                    }

                    Player player = (Player) sender;
                    Long lastConfirm = removeallConfirmations.get(player.getUniqueId());
                    if (lastConfirm != null && System.currentTimeMillis() - lastConfirm < 10000) {
                        removeAllAngelBlocks(player);
                        removeallConfirmations.remove(player.getUniqueId());
                        return true;
                    }
                }

                if (sender instanceof Player) {
                    Player player = (Player) sender;
                    removeallConfirmations.put(player.getUniqueId(), System.currentTimeMillis());
                    sendMessage(sender, "messages.removeall-confirm");
                } else {
                    sendMessage(sender, "messages.player-only-command");
                }
                return true;
            }
        }

        if (!sender.hasPermission("angelblock.give")) {
            sendMessage(sender, "messages.no-permission");
            return true;
        }

        Player target = args.length > 0
                ? Bukkit.getPlayer(args[0])
                : (sender instanceof Player ? (Player) sender : null);

        if (target == null) {
            sendMessage(sender, "messages.player-not-found");
            return true;
        }

        target.getInventory().addItem(createAngelBlock());
        sendMessage(sender, "messages.give-success", "player", target.getName());
        return true;
    }

    // 添加领地权限检查方法
    private boolean canPlaceAt(Player player, Location location) {
        if (huskClaims == null) return true;

        if (player.hasPermission("huskclaims.bypass")) {
            return true;
        }

        Position position = huskClaims.getPosition(
                location.getX(),
                location.getY(),
                location.getZ(),
                huskClaims.getWorld(location.getWorld().getName())
        );
        OnlineUser user = huskClaims.getOnlineUser(player.getUniqueId());
        return huskClaims.isOperationAllowed(user, OperationType.BLOCK_PLACE, position);
    }

    private boolean canBreakAt(Player player, Location location) {
        if (huskClaims == null) return true;

        // 管理员权限覆盖
        if (player.hasPermission("huskclaims.bypass")) {
            return true;
        }

        Position position = huskClaims.getPosition(
                location.getX(),
                location.getY(),
                location.getZ(),
                huskClaims.getWorld(location.getWorld().getName())
        );
        OnlineUser user = huskClaims.getOnlineUser(player.getUniqueId());
        return huskClaims.isOperationAllowed(user, OperationType.BLOCK_BREAK, position);
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

            // 玩家名称补全
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(input)) {
                    completions.add(player.getName());
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("removeall")) {
            if ("confirm".startsWith(args[1].toLowerCase()) && sender.hasPermission("angelblock.removeall")) {
                completions.add("confirm");
            }
        }

        return completions;
    }

    private ItemStack createAngelBlock() {
        Material material = Material.matchMaterial(config.getString("item.material", "STONE"));
        ItemStack item = new ItemStack(Objects.requireNonNullElse(material, Material.STONE));

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(colorize(config.getString("item.name", "&b天使方块")));

            List<String> lore = new ArrayList<>();
            for (String line : config.getStringList("item.lore")) {
                lore.add(colorize(line));
            }
            meta.setLore(lore);

            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        // 左键点击破坏天使方块
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            Block clickedBlock = event.getClickedBlock();
            if (clickedBlock != null && isAngelBlock(clickedBlock)) {
                event.setCancelled(true);
                removeAngelBlock(clickedBlock, player);
                return;
            }
        }

        // 右键放置天使方块（需要检查手持物品）
        if ((event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.RIGHT_CLICK_AIR)
                && isAngelBlock(player.getInventory().getItemInMainHand())) {

            event.setCancelled(true);

            if (!player.hasPermission("angelblock.use")) {
                sendMessage(player, "messages.no-permission");
                return;
            }

            int distance = playerDistances.getOrDefault(player.getUniqueId(),
                    config.getInt("distance.default", 3));

            Location eyeLocation = player.getEyeLocation();
            Vector direction = eyeLocation.getDirection().normalize();
            Location targetLocation = eyeLocation.clone().add(direction.multiply(distance));

            Block targetBlock = targetLocation.getBlock();
            if (!targetBlock.getType().isAir() && !targetBlock.isLiquid()) {
                sendMessage(player, "messages.cant-place-here");
                return;
            }

            placeAngelBlock(player, targetBlock);
        }
    }

    private void placeAngelBlock(Player player, Block block) {
        if (!canPlaceAt(player, block.getLocation())) {
            sendMessage(player, "messages.cant-place-in-claim");
            return;
        }
        // 检查玩家是否超过上限
        UUID playerId = player.getUniqueId();
        int maxBlocks = getMaxBlocks(player);
        int currentBlocks = getPlayerBlockCount(playerId);

        if (currentBlocks >= maxBlocks) {
            sendMessage(player, "messages.max-blocks-reached", "max", String.valueOf(maxBlocks));
            return;
        }

        // 直接放置方块
        Material material = Material.matchMaterial(
                config.getString("item.material", "STONE"));
        block.setType(Objects.requireNonNullElse(material, Material.STONE));

        // 保存到YAML文件（记录玩家UUID）
        saveBlockLocation(block.getLocation(), player.getUniqueId());

        // 记录放置操作用于后续验证
        trackRecentPlacement(player, block.getLocation());

        // 只在成功放置后消耗物品
        if (player.getGameMode() != GameMode.CREATIVE) {
            ItemStack item = player.getInventory().getItemInMainHand();
            if (isAngelBlock(item)) {
                if (item.getAmount() > 1) {
                    item.setAmount(item.getAmount() - 1);
                } else {
                    player.getInventory().setItemInMainHand(null);
                }
            }
        }
    }

    private void trackRecentPlacement(Player player, Location location) {
        UUID playerId = player.getUniqueId();
        List<Location> placements = recentPlacements.getOrDefault(playerId, new ArrayList<>());
        placements.add(location);
        recentPlacements.put(playerId, placements);

        // 设置1秒后检查方块是否还在
        Bukkit.getScheduler().runTaskLater(this, () -> {
            verifyPlacement(player, location);
        }, 20); // 20 ticks = 1秒
    }

    private void verifyPlacement(Player player, Location location) {
        UUID playerId = player.getUniqueId();

        // 检查这个位置是否还在最近放置列表中
        if (!recentPlacements.containsKey(playerId) ||
                !recentPlacements.get(playerId).contains(location)) {
            return;
        }

        // 检查方块是否还存在
        Block block = location.getBlock();
        if (!isAngelBlock(block)) {
            // 方块被领地保护移除了
            removeRecentPlacement(playerId, location);

            // 移除YAML记录
            removeBlockLocation(location);

            // 补偿玩家一个天使方块
            if (player.isOnline() && player.getGameMode() != GameMode.CREATIVE) {
                ItemStack newBlock = createAngelBlock();
                HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(newBlock);

                if (!leftover.isEmpty()) {
                    for (ItemStack item : leftover.values()) {
                        player.getWorld().dropItem(player.getLocation(), item);
                    }
                }
                sendMessage(player, "messages.block-removed-by-protection");
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
            sendMessage(player, "messages.cant-break-in-claim");
            return;
        }
        // 移除最近放置记录
        removeRecentPlacement(player.getUniqueId(), block.getLocation());

        // 获取方块所属玩家
        UUID ownerId = getBlockOwner(block);
        if (ownerId == null) {
            sendMessage(player, "messages.corrupted-block");
            return;
        }

        // 检查破坏权限
        if (!player.getUniqueId().equals(ownerId) && !player.hasPermission("angelblock.admin")) {
            String ownerName = Bukkit.getOfflinePlayer(ownerId).getName();
            sendMessage(player, "messages.cant-break-others", "owner", ownerName != null ? ownerName : "未知玩家");
            return;
        }

        // 如果是管理员破坏他人方块，通知所有者
        if (player.hasPermission("angelblock.admin") && !player.getUniqueId().equals(ownerId)) {
            Player owner = Bukkit.getPlayer(ownerId);
            if (owner != null && owner.isOnline()) {
                sendMessage(owner, "messages.admin-removed-your-block",
                        "admin", player.getName());
            }
        }

        // 播放破坏效果
        player.playSound(block.getLocation(), Sound.BLOCK_STONE_BREAK, 1.0f, 1.0f);

        // 移除方块
        block.setType(Material.AIR);
        removeBlockLocation(block.getLocation());
        sendMessage(player, "messages.block-removed");

        // 给所有者新的天使方块（非创造模式）
        if (player.getGameMode() != GameMode.CREATIVE && player.getUniqueId().equals(ownerId)) {
            ItemStack newBlock = createAngelBlock();
            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(newBlock);

            if (!leftover.isEmpty()) {
                for (ItemStack item : leftover.values()) {
                    player.getWorld().dropItem(player.getLocation(), item);
                }
            }
        }
    }

    private String formatLocation(Location loc) {
        return String.format("世界: %s, X: %d, Y: %d, Z: %d",
                loc.getWorld().getName(),
                loc.getBlockX(),
                loc.getBlockY(),
                loc.getBlockZ());
    }

    private UUID getBlockOwner(Block block) {
        ConfigurationSection blocksSection = blocksConfig.getConfigurationSection("blocks");
        if (blocksSection != null) {
            for (String key : blocksSection.getKeys(false)) {
                ConfigurationSection blockSection = blocksSection.getConfigurationSection(key);
                if (blockSection != null) {
                    String world = blockSection.getString("world");
                    int x = blockSection.getInt("x");
                    int y = blockSection.getInt("y");
                    int z = blockSection.getInt("z");

                    if (block.getWorld().getName().equals(world) &&
                            block.getX() == x &&
                            block.getY() == y &&
                            block.getZ() == z) {
                        return UUID.fromString(blockSection.getString("player"));
                    }
                }
            }
        }
        return null;
    }

    private boolean canBuildHere(Player player, Location location) {
        // 简化权限检查，实际使用时应集成领地插件
        return player.hasPermission("worldguard.region.bypass." + location.getWorld().getName()) ||
                player.hasPermission("plots.build." + location.getWorld().getName());
    }

    private void removeAllAngelBlocks(Player player) {
        boolean isAdmin = player.hasPermission("angelblock.admin");
        UUID playerId = player.getUniqueId();

        ConfigurationSection blocksSection = blocksConfig.getConfigurationSection("blocks");
        if (blocksSection == null) {
            sendMessage(player, "messages.removeall-error");
            return;
        }

        List<Location> blockLocations = new ArrayList<>();
        Map<String, ConfigurationSection> toRemove = new HashMap<>();

        for (String key : blocksSection.getKeys(false)) {
            ConfigurationSection blockSection = blocksSection.getConfigurationSection(key);
            if (blockSection != null) {
                String world = blockSection.getString("world");
                int x = blockSection.getInt("x");
                int y = blockSection.getInt("y");
                int z = blockSection.getInt("z");
                UUID ownerUUID = UUID.fromString(blockSection.getString("player"));

                if (isAdmin || playerId.equals(ownerUUID)) {
                    World worldObj = Bukkit.getWorld(world);
                    if (worldObj != null) {
                        blockLocations.add(new Location(worldObj, x, y, z));
                        toRemove.put(key, blockSection);
                    }
                }
            }
        }

        // 从世界中移除方块
        int removedCount = 0;
        for (Location loc : blockLocations) {
            Block block = loc.getBlock();
            if (isAngelBlock(block)) {
                block.setType(Material.AIR);
                removedCount++;

                // 移除最近放置记录
                UUID ownerId = getBlockOwner(block);
                if (ownerId != null) {
                    removeRecentPlacement(ownerId, loc);
                }
            }
        }

        // 从YAML文件中移除记录
        for (String key : toRemove.keySet()) {
            blocksSection.set(key, null);
        }

        saveBlocksFile();

        // 给予玩家所有移除的方块（非创造模式）
        if (player.getGameMode() != GameMode.CREATIVE && removedCount > 0) {
            ItemStack angelBlock = createAngelBlock();

            // 分批给予（避免一次给太多）
            int remaining = removedCount;
            while (remaining > 0) {
                int amount = Math.min(remaining, angelBlock.getMaxStackSize());
                ItemStack toGive = angelBlock.clone();
                toGive.setAmount(amount);

                HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(toGive);

                // 背包满时掉落
                for (ItemStack item : leftover.values()) {
                    player.getWorld().dropItem(player.getLocation(), item);
                }

                remaining -= amount;
            }
        }

        sendMessage(player, "messages.removeall-success", "count", String.valueOf(removedCount));
        getLogger().info(player.getName() + " 移除了 " + removedCount + " 个天使方块");
    }

    private int getPlayerBlockCount(UUID playerId) {
        ConfigurationSection blocksSection = blocksConfig.getConfigurationSection("blocks");
        if (blocksSection == null) {
            return 0;
        }

        int count = 0;
        for (String key : blocksSection.getKeys(false)) {
            ConfigurationSection blockSection = blocksSection.getConfigurationSection(key);
            if (blockSection != null) {
                UUID ownerUUID = UUID.fromString(blockSection.getString("player"));
                if (playerId.equals(ownerUUID)) {
                    count++;
                }
            }
        }
        return count;
    }

    private int getMaxBlocks(Player player) {
        // 使用缓存提高性能
        UUID playerId = player.getUniqueId();
        if (playerMaxBlocksCache.containsKey(playerId)) {
            return playerMaxBlocksCache.get(playerId);
        }

        int maxBlocks = 64; // 默认64个

        // 检查玩家权限
        for (int i = 1; i <= 1024; i *= 2) {
            if (player.hasPermission("angelblock.max." + i)) {
                maxBlocks = i;
                break;
            }
        }

        // 检查更高的限额（从128到2048）
        if (maxBlocks == 64) {
            for (int i = 128; i <= 2048; i += 128) {
                if (player.hasPermission("angelblock.max." + i)) {
                    maxBlocks = i;
                    break;
                }
            }
        }

        // 检查无限权限
        if (player.hasPermission("angelblock.max.unlimited")) {
            maxBlocks = Integer.MAX_VALUE;
        }

        // 更新缓存
        playerMaxBlocksCache.put(playerId, maxBlocks);
        return maxBlocks;
    }

    @EventHandler
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!player.isSneaking() || !isAngelBlock(player.getInventory().getItemInMainHand())) {
            return;
        }

        event.setCancelled(true);

        // 检测滚轮方向（使用F键交换）
        adjustPlacementDistance(player, 1);
    }

    @EventHandler
    public void onItemHeldChange(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (!player.isSneaking() || !isAngelBlock(player.getInventory().getItemInMainHand())) {
            return;
        }

        int previousSlot = event.getPreviousSlot();
        int newSlot = event.getNewSlot();

        // 修复方向计算逻辑
        int direction;
        if ((previousSlot == 0 && newSlot == 8) ||
                (previousSlot > newSlot && !(previousSlot == 8 && newSlot == 0))) {
            // 向左滚动：减少间距
            direction = -1;
        } else {
            // 向右滚动：增加间距
            direction = 1;
        }

        // 调整距离
        adjustPlacementDistance(player, direction);

        // 重置物品栏位置（保持玩家当前选择的物品不变）
        Bukkit.getScheduler().runTaskLater(this, () -> {
            player.getInventory().setHeldItemSlot(previousSlot);
        }, 1);
    }

    private void adjustPlacementDistance(Player player, int direction) {
        UUID uuid = player.getUniqueId();
        int current = playerDistances.getOrDefault(uuid,
                config.getInt("distance.default", 3));

        int min = config.getInt("distance.min", 1);
        int max = config.getInt("distance.max", 10);

        int newDistance = current + direction;
        if (newDistance < min) newDistance = min;
        if (newDistance > max) newDistance = max;

        playerDistances.put(uuid, newDistance);
        sendActionBar(player, "messages.placement-distance", "distance", String.valueOf(newDistance));
    }

    private void sendActionBar(Player player, String configPath, String placeholder, String value) {
        String message = getMessage(configPath, placeholder, value);
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
    }

    private void sendMessage(CommandSender sender, String configPath) {
        sender.sendMessage(getMessage(configPath));
    }

    private void sendMessage(CommandSender sender, String configPath, String placeholder, String value) {
        sender.sendMessage(getMessage(configPath, placeholder, value));
    }

    private String getMessage(String configPath) {
        return colorize(config.getString(configPath, "&cError: Missing message"));
    }

    private String getMessage(String configPath, String placeholder, String value) {
        return getMessage(configPath).replace("{" + placeholder + "}", value);
    }

    private boolean isAngelBlock(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;

        String configName = colorize(config.getString("item.name", "&b天使方块"));

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        if (!meta.hasLore()) return false; // need to delete

        System.out.println(Arrays.toString(meta.getLore().toArray()));
        for (String lore : config.getStringList("item.lore")) {
            if (!meta.hasLore() || !meta.getLore().contains(colorize(lore))) {
                return false;
            }
        }

        return meta.hasDisplayName() && meta.getDisplayName().equals(configName);
    }

    private boolean isAngelBlock(Block block) {
        ConfigurationSection blocksSection = blocksConfig.getConfigurationSection("blocks");
        if (blocksSection != null) {
            for (String key : blocksSection.getKeys(false)) {
                ConfigurationSection blockSection = blocksSection.getConfigurationSection(key);
                if (blockSection != null) {
                    String world = blockSection.getString("world");
                    int x = blockSection.getInt("x");
                    int y = blockSection.getInt("y");
                    int z = blockSection.getInt("z");

                    if (block.getWorld().getName().equals(world) &&
                            block.getX() == x &&
                            block.getY() == y &&
                            block.getZ() == z) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void saveBlockLocation(Location loc, UUID playerId) {
        // 确保blocks部分存在
        ConfigurationSection blocksSection = blocksConfig.getConfigurationSection("blocks");
        if (blocksSection == null) {
            blocksSection = blocksConfig.createSection("blocks");
        }

        String key = UUID.randomUUID().toString();
        ConfigurationSection blockSection = blocksSection.createSection(key);

        blockSection.set("world", loc.getWorld().getName());
        blockSection.set("x", loc.getBlockX());
        blockSection.set("y", loc.getBlockY());
        blockSection.set("z", loc.getBlockZ());
        blockSection.set("player", playerId.toString());

        saveBlocksFile();
    }

    private void removeBlockLocation(Location loc) {
        ConfigurationSection blocksSection = blocksConfig.getConfigurationSection("blocks");
        if (blocksSection != null) {
            for (String key : blocksSection.getKeys(false)) {
                ConfigurationSection blockSection = blocksSection.getConfigurationSection(key);
                if (blockSection != null) {
                    String world = blockSection.getString("world");
                    int x = blockSection.getInt("x");
                    int y = blockSection.getInt("y");
                    int z = blockSection.getInt("z");

                    if (loc.getWorld().getName().equals(world) &&
                            loc.getBlockX() == x &&
                            loc.getBlockY() == y &&
                            loc.getBlockZ() == z) {
                        blocksSection.set(key, null);
                        break;
                    }
                }
            }
        }
        saveBlocksFile();
    }

    private class ParticlePreviewTask extends BukkitRunnable {
        @Override
        public void run() {
            for (Player player : Bukkit.getOnlinePlayers()) {
                ItemStack item = player.getInventory().getItemInMainHand();
                if (!isAngelBlock(item)) {
                    // 清除上一次的预览
                    if (lastPreviewLocations.containsKey(player.getUniqueId())) {
                        lastPreviewLocations.remove(player.getUniqueId());
                    }
                    continue;
                }

                int distance = playerDistances.getOrDefault(player.getUniqueId(),
                        config.getInt("distance.default", 3));

                Location eyeLoc = player.getEyeLocation();
                RayTraceResult result = player.getWorld().rayTraceBlocks(
                        eyeLoc, eyeLoc.getDirection(), distance,
                        FluidCollisionMode.NEVER, true);

                Location previewLoc = null;
                if (result != null && result.getHitBlock() != null) {
                    previewLoc = result.getHitBlock().getLocation();
                } else {
                    Vector direction = eyeLoc.getDirection().normalize();
                    Location calculated = eyeLoc.clone().add(direction.multiply(distance));
                    previewLoc = new Location(
                            calculated.getWorld(),
                            calculated.getBlockX(),
                            calculated.getBlockY(),
                            calculated.getBlockZ()
                    );
                }

                drawBlockOutline(player, previewLoc);
                lastPreviewLocations.put(player.getUniqueId(), previewLoc);
            }
        }
    }

    private void drawBlockOutline(Player player, Location location) {
        World world = location.getWorld();
        double x = location.getX();
        double y = location.getY();
        double z = location.getZ();

        // 绘制方块轮廓
        for (double offset = 0; offset <= 1; offset += 0.2) {
            // 底部边缘
            spawnParticle(player, world, x + offset, y, z);
            spawnParticle(player, world, x, y, z + offset);
            spawnParticle(player, world, x + 1, y, z + offset);
            spawnParticle(player, world, x + offset, y, z + 1);

            // 顶部边缘
            spawnParticle(player, world, x + offset, y + 1, z);
            spawnParticle(player, world, x, y + 1, z + offset);
            spawnParticle(player, world, x + 1, y + 1, z + offset);
            spawnParticle(player, world, x + offset, y + 1, z + 1);

            // 垂直边缘
            spawnParticle(player, world, x, y + offset, z);
            spawnParticle(player, world, x + 1, y + offset, z);
            spawnParticle(player, world, x, y + offset, z + 1);
            spawnParticle(player, world, x + 1, y + offset, z + 1);
        }
    }

    private void spawnParticle(Player player, World world, double x, double y, double z) {
        if (player.getWorld().equals(world)) {
            // 使用 DUST 粒子类型
            player.spawnParticle(Particle.DUST, x, y, z, 1, dustOptions);
        }
    }

    private String colorize(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    @Override
    public void onDisable() {
        // 确保在关闭前保存数据
        saveBlocksFile();
    }
}