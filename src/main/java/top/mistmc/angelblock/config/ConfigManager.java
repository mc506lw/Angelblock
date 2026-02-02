package top.mistmc.angelblock.config;

import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.configuration.file.FileConfiguration;
import top.mistmc.angelblock.Angelblock;
import top.mistmc.angelblock.util.ColorUtils;

public class ConfigManager {
    private final Angelblock plugin;
    private FileConfiguration config;
    private Particle.DustOptions dustOptions;

    public ConfigManager(Angelblock plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
        loadParticleSettings();
    }

    private void loadParticleSettings() {
        String colorString = config.getString("particle.color", "#ffffff");
        float size = (float) config.getDouble("particle.size", 0.5);

        Color color = ColorUtils.parseBukkitColor(colorString);
        dustOptions = new Particle.DustOptions(color, size);
    }

    public Particle.DustOptions getDustOptions() {
        return dustOptions;
    }

    public String getItemMaterial() {
        return config.getString("item.material", "STONE");
    }

    public String getItemName() {
        return config.getString("item.name", "&b天使方块");
    }

    public java.util.List<String> getItemLore() {
        return config.getStringList("item.lore");
    }

    public int getMinDistance() {
        return config.getInt("distance.min", 2);
    }

    public int getMaxDistance() {
        return config.getInt("distance.max", 6);
    }

    public int getDefaultDistance() {
        return config.getInt("distance.default", 3);
    }

    public String getMessage(String path) {
        String message = config.getString("messages." + path, "");
        return ColorUtils.colorize(message);
    }

    public String getMessage(String path, String placeholder, String value) {
        String message = config.getString("messages." + path, "");
        if (message != null && placeholder != null && value != null) {
            message = message.replace("{" + placeholder + "}", value);
        }
        return ColorUtils.colorize(message);
    }

    public String getMessage(String path, String placeholder1, String value1, String placeholder2, String value2) {
        String message = config.getString("messages." + path, "");
        if (message != null && placeholder1 != null && value1 != null) {
            message = message.replace("{" + placeholder1 + "}", value1);
        }
        if (message != null && placeholder2 != null && value2 != null) {
            message = message.replace("{" + placeholder2 + "}", value2);
        }
        return ColorUtils.colorize(message);
    }
}
