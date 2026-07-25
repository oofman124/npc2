package npc2.npc2;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Text-editable npc2 settings loaded once when the game starts. */
public final class Npc2Config {
    private static final Path PATH = FabricLoader.getInstance().getConfigDir()
            .resolve("npc2")
            .resolve("npc2.properties");
    private static Npc2Config current = defaults();

    public final boolean startupMessage;
    public final boolean debugHud;
    public final boolean debugPathRendering;
    public final double debugMaxDistance;
    public final int playerRetaliationTicks;
    public final int resourceSearchBlockBudget;
    public final int resourceSurveyBlockBudget;

    private Npc2Config(boolean startupMessage, boolean debugHud, boolean debugPathRendering,
                       double debugMaxDistance, int playerRetaliationTicks,
                       int resourceSearchBlockBudget, int resourceSurveyBlockBudget) {
        this.startupMessage = startupMessage;
        this.debugHud = debugHud;
        this.debugPathRendering = debugPathRendering;
        this.debugMaxDistance = debugMaxDistance;
        this.playerRetaliationTicks = playerRetaliationTicks;
        this.resourceSearchBlockBudget = resourceSearchBlockBudget;
        this.resourceSurveyBlockBudget = resourceSurveyBlockBudget;
    }

    public static void load() {
        Properties properties = defaultProperties();
        if (Files.exists(PATH)) {
            try (InputStream input = Files.newInputStream(PATH)) {
                properties.load(input);
            } catch (IOException exception) {
                Npc2.LOGGER.warn("Could not read npc2 config {}; using defaults", PATH, exception);
            }
        }

        current = new Npc2Config(
                booleanValue(properties, "startup_message", true),
                booleanValue(properties, "debug_hud", true),
                booleanValue(properties, "debug_path_rendering", true),
                doubleValue(properties, "debug_max_distance", 128.0D, 16.0D, 512.0D),
                intValue(properties, "player_retaliation_ticks", 600, 0, 72_000),
                intValue(properties, "resource_search_block_budget", 8192, 256, 65_536),
                intValue(properties, "resource_survey_block_budget", 256, 16, 4096)
        );
        writeNormalizedConfig();
        Npc2.LOGGER.info("Loaded npc2 config from {}", PATH);
    }

    public static Npc2Config get() {
        return current;
    }

    public static Path path() {
        return PATH;
    }

    private static Npc2Config defaults() {
        return new Npc2Config(true, true, true, 128.0D, 600, 8192, 256);
    }

    private static Properties defaultProperties() {
        Npc2Config defaults = defaults();
        Properties properties = new Properties();
        properties.setProperty("startup_message", Boolean.toString(defaults.startupMessage));
        properties.setProperty("debug_hud", Boolean.toString(defaults.debugHud));
        properties.setProperty("debug_path_rendering", Boolean.toString(defaults.debugPathRendering));
        properties.setProperty("debug_max_distance", Double.toString(defaults.debugMaxDistance));
        properties.setProperty("player_retaliation_ticks", Integer.toString(defaults.playerRetaliationTicks));
        properties.setProperty("resource_search_block_budget", Integer.toString(defaults.resourceSearchBlockBudget));
        properties.setProperty("resource_survey_block_budget", Integer.toString(defaults.resourceSurveyBlockBudget));
        return properties;
    }

    private static void writeNormalizedConfig() {
        Properties properties = defaultProperties();
        Npc2Config config = current;
        properties.setProperty("startup_message", Boolean.toString(config.startupMessage));
        properties.setProperty("debug_hud", Boolean.toString(config.debugHud));
        properties.setProperty("debug_path_rendering", Boolean.toString(config.debugPathRendering));
        properties.setProperty("debug_max_distance", Double.toString(config.debugMaxDistance));
        properties.setProperty("player_retaliation_ticks", Integer.toString(config.playerRetaliationTicks));
        properties.setProperty("resource_search_block_budget", Integer.toString(config.resourceSearchBlockBudget));
        properties.setProperty("resource_survey_block_budget", Integer.toString(config.resourceSurveyBlockBudget));
        try {
            Files.createDirectories(PATH.getParent());
            try (OutputStream output = Files.newOutputStream(PATH)) {
                properties.store(output,
                        "npc2 configuration - edit while Minecraft is closed; restart to apply");
            }
        } catch (IOException exception) {
            Npc2.LOGGER.warn("Could not write npc2 config {}", PATH, exception);
        }
    }

    private static boolean booleanValue(Properties properties, String key, boolean fallback) {
        String value = properties.getProperty(key);
        if ("true".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;
        Npc2.LOGGER.warn("Invalid npc2 config value {}={}; using {}", key, value, fallback);
        return fallback;
    }

    private static int intValue(Properties properties, String key, int fallback, int minimum, int maximum) {
        try {
            return Math.clamp(Integer.parseInt(properties.getProperty(key)), minimum, maximum);
        } catch (NumberFormatException exception) {
            Npc2.LOGGER.warn("Invalid npc2 config value {}={}; using {}", key, properties.getProperty(key), fallback);
            return fallback;
        }
    }

    private static double doubleValue(Properties properties, String key, double fallback,
                                      double minimum, double maximum) {
        try {
            double value = Double.parseDouble(properties.getProperty(key));
            if (!Double.isFinite(value)) throw new NumberFormatException("non-finite value");
            return Math.clamp(value, minimum, maximum);
        } catch (NumberFormatException exception) {
            Npc2.LOGGER.warn("Invalid npc2 config value {}={}; using {}", key, properties.getProperty(key), fallback);
            return fallback;
        }
    }
}
