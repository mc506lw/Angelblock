package top.mistmc.angelblock.task;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import top.mistmc.angelblock.Angelblock;
import top.mistmc.angelblock.config.ConfigManager;
import top.mistmc.angelblock.util.SchedulerWrapper;
import top.mistmc.angelblock.util.Utils;

import java.util.Map;
import java.util.UUID;

public class ParticlePreviewTask implements Runnable {
    private final ConfigManager configManager;
    private final Map<UUID, Integer> playerDistances;
    private final Map<UUID, Location> lastPreviewLocations;
    private final SchedulerWrapper scheduler;

    public ParticlePreviewTask(Angelblock plugin, ConfigManager configManager,
            Map<UUID, Integer> playerDistances,
            Map<UUID, Location> lastPreviewLocations,
            SchedulerWrapper scheduler) {
        this.configManager = configManager;
        this.playerDistances = playerDistances;
        this.lastPreviewLocations = lastPreviewLocations;
        this.scheduler = scheduler;
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (scheduler.isFolia()) {
                ItemStack item = player.getInventory().getItemInMainHand();
                String configName = configManager.getItemName();
                boolean isAngelBlock = Utils.isAngelBlock(item, configName);

                if (!isAngelBlock) {
                    if (lastPreviewLocations.containsKey(player.getUniqueId())) {
                        lastPreviewLocations.remove(player.getUniqueId());
                    }
                    continue;
                }

                int distance = playerDistances.getOrDefault(player.getUniqueId(),
                        configManager.getDefaultDistance());

                Location eyeLoc = player.getEyeLocation();
                RayTraceResult result = player.getWorld().rayTraceBlocks(
                        eyeLoc, eyeLoc.getDirection(), distance,
                        org.bukkit.FluidCollisionMode.NEVER, true);

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
                            calculated.getBlockZ());
                }

                Location finalPreviewLoc = previewLoc;
                scheduler.runAtLocation(player.getLocation(), () -> {
                    drawBlockOutline(player, finalPreviewLoc);
                    lastPreviewLocations.put(player.getUniqueId(), finalPreviewLoc);

                    int finalDistance = distance;
                    Utils.sendActionBar(player,
                            configManager.getMessage("placement-distance", "distance", String.valueOf(finalDistance)));
                });
            } else {
                handlePlayer(player);
            }
        }
    }

    private void handlePlayer(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        String configName = configManager.getItemName();
        boolean isAngelBlock = Utils.isAngelBlock(item, configName);

        if (!isAngelBlock) {
            if (lastPreviewLocations.containsKey(player.getUniqueId())) {
                lastPreviewLocations.remove(player.getUniqueId());
            }
            return;
        }

        int distance = playerDistances.getOrDefault(player.getUniqueId(),
                configManager.getDefaultDistance());

        Location eyeLoc = player.getEyeLocation();
        RayTraceResult result = player.getWorld().rayTraceBlocks(
                eyeLoc, eyeLoc.getDirection(), distance,
                org.bukkit.FluidCollisionMode.NEVER, true);

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
                    calculated.getBlockZ());
        }

        drawBlockOutline(player, previewLoc);
        lastPreviewLocations.put(player.getUniqueId(), previewLoc);

        int finalDistance = distance;
        Utils.sendActionBar(player,
                configManager.getMessage("placement-distance", "distance", String.valueOf(finalDistance)));
    }

    private void drawBlockOutline(Player player, Location location) {
        World world = location.getWorld();
        double x = location.getX();
        double y = location.getY();
        double z = location.getZ();

        for (double offset = 0; offset <= 1; offset += 0.2) {
            spawnParticle(player, world, x + offset, y, z);
            spawnParticle(player, world, x, y, z + offset);
            spawnParticle(player, world, x + 1, y, z + offset);
            spawnParticle(player, world, x + offset, y, z + 1);

            spawnParticle(player, world, x + offset, y + 1, z);
            spawnParticle(player, world, x, y + 1, z + offset);
            spawnParticle(player, world, x + 1, y + 1, z + offset);
            spawnParticle(player, world, x + offset, y + 1, z + 1);

            spawnParticle(player, world, x, y + offset, z);
            spawnParticle(player, world, x + 1, y + offset, z);
            spawnParticle(player, world, x, y + offset, z + 1);
            spawnParticle(player, world, x + 1, y + offset, z + 1);
        }
    }

    private void spawnParticle(Player player, World world, double x, double y, double z) {
        if (player.getWorld().equals(world)) {
            player.spawnParticle(Particle.DUST, x, y, z, 1, configManager.getDustOptions());
        }
    }
}
