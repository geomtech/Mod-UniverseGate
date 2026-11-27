package fr.geomtech.universegate.seamless.client;

import fr.geomtech.universegate.UniverseGate;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Per-profile setting for the client-side portal view. */
public final class PortalViewOptions {
    private static final String KEY = "livePortalView";
    private static final Path FILE = Minecraft.getInstance().gameDirectory.toPath().resolve("config/universegate-client.properties");
    private static boolean enabled = load();

    private PortalViewOptions() {}

    public static boolean enabled() {
        return enabled;
    }

    public static void toggle() {
        enabled = !enabled;
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, KEY + "=" + enabled + "\n");
        } catch (IOException e) {
            UniverseGate.LOGGER.warn("Could not save the live portal view setting", e);
        }
    }

    private static boolean load() {
        if (Files.notExists(FILE)) return true;
        Properties settings = new Properties();
        try (var input = Files.newInputStream(FILE)) {
            settings.load(input);
            return !"false".equalsIgnoreCase(settings.getProperty(KEY));
        } catch (IOException | IllegalArgumentException e) {
            UniverseGate.LOGGER.warn("Could not load the live portal view setting", e);
            return true;
        }
    }
}
