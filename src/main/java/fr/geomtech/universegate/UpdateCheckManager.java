package fr.geomtech.universegate;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UpdateCheckManager {
    private static final String RELEASES_PAGE_URL = "https://github.com/geomtech/Mod-UniverseGate/releases";
    private static final String RELEASES_API_URL = "https://api.github.com/repos/geomtech/Mod-UniverseGate/releases/latest";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
    private static final long UPDATE_CHECK_INTERVAL_MS = Duration.ofHours(6).toMillis();
    private static final Pattern NUMERIC_PREFIX_PATTERN = Pattern.compile("^\\d+(?:\\.\\d+)*");

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .build();

    private static final Set<UUID> NOTIFIED_PLAYERS = ConcurrentHashMap.newKeySet();

    private static volatile boolean updateAvailable = false;
    private static volatile String latestVersion = "";
    private static volatile String currentVersion = "";
    private static volatile long nextCheckAtMs = Long.MAX_VALUE;

    private UpdateCheckManager() {
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            updateAvailable = false;
            latestVersion = "";
            currentVersion = resolveCurrentVersion();
            nextCheckAtMs = Long.MAX_VALUE;
            NOTIFIED_PLAYERS.clear();
        });

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            nextCheckAtMs = System.currentTimeMillis() + UPDATE_CHECK_INTERVAL_MS;
            checkForUpdatesAsync(server);
        });

        ServerTickEvents.END_SERVER_TICK.register(UpdateCheckManager::runPeriodicCheck);

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> nextCheckAtMs = Long.MAX_VALUE);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> notifyPlayerIfNeeded(handler.player));
    }

    private static void runPeriodicCheck(MinecraftServer server) {
        long now = System.currentTimeMillis();
        if (now < nextCheckAtMs) {
            return;
        }

        nextCheckAtMs = now + UPDATE_CHECK_INTERVAL_MS;
        checkForUpdatesAsync(server);
    }

    private static void checkForUpdatesAsync(MinecraftServer server) {
        CompletableFuture.runAsync(() -> {
            String latest = fetchLatestVersion();
            if (latest.isEmpty()) {
                return;
            }

            if (!isVersionNewer(currentVersion, latest)) {
                return;
            }

            updateAvailable = true;
            latestVersion = latest;

            server.execute(() -> {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    notifyPlayerIfNeeded(player);
                }
            });
        }).exceptionally(throwable -> {
            UniverseGate.LOGGER.warn("Unable to run update check", throwable);
            return null;
        });
    }

    private static String fetchLatestVersion() {
        HttpRequest request = HttpRequest.newBuilder(URI.create(RELEASES_API_URL))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "UniverseGate Update Checker")
                .GET()
                .build();

        HttpResponse<String> response;
        try {
            response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            UniverseGate.LOGGER.warn("Failed to contact GitHub for update check", e);
            return "";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            UniverseGate.LOGGER.warn("Update check interrupted", e);
            return "";
        }

        if (response.statusCode() != 200) {
            UniverseGate.LOGGER.warn("Update check failed with HTTP status {}", response.statusCode());
            return "";
        }

        JsonObject payload;
        try {
            payload = JsonParser.parseString(response.body()).getAsJsonObject();
        } catch (Exception e) {
            UniverseGate.LOGGER.warn("Update check returned invalid JSON", e);
            return "";
        }

        if (!payload.has("tag_name") || payload.get("tag_name").isJsonNull()) {
            UniverseGate.LOGGER.warn("Update check JSON missing tag_name");
            return "";
        }

        return normalizeVersion(payload.get("tag_name").getAsString());
    }

    private static String resolveCurrentVersion() {
        String version = FabricLoader.getInstance()
                .getModContainer(UniverseGate.MOD_ID)
                .map(modContainer -> modContainer.getMetadata().getVersion().getFriendlyString())
                .orElse("0.0.0");
        return normalizeVersion(version);
    }

    private static void notifyPlayerIfNeeded(ServerPlayer player) {
        if (!updateAvailable || latestVersion.isEmpty()) {
            return;
        }

        if (!NOTIFIED_PLAYERS.add(player.getUUID())) {
            return;
        }

        player.displayClientMessage(buildUpdateMessage(), false);
    }

    private static MutableComponent buildUpdateMessage() {
        return Component.translatable("message.universegate.update_available", latestVersion)
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(" "))
                .append(Component.literal(RELEASES_PAGE_URL)
                        .withStyle(style -> style
                                .withColor(ChatFormatting.AQUA)
                                .withUnderlined(true)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, RELEASES_PAGE_URL))));
    }

    private static boolean isVersionNewer(String current, String latest) {
        int[] currentParts = extractNumericParts(current);
        int[] latestParts = extractNumericParts(latest);

        if (currentParts.length == 0 || latestParts.length == 0) {
            return normalizeVersion(latest).compareToIgnoreCase(normalizeVersion(current)) > 0;
        }

        int maxLength = Math.max(currentParts.length, latestParts.length);
        for (int i = 0; i < maxLength; i++) {
            int currentPart = i < currentParts.length ? currentParts[i] : 0;
            int latestPart = i < latestParts.length ? latestParts[i] : 0;

            if (latestPart != currentPart) {
                return latestPart > currentPart;
            }
        }

        return false;
    }

    private static int[] extractNumericParts(String version) {
        Matcher matcher = NUMERIC_PREFIX_PATTERN.matcher(normalizeVersion(version));
        if (!matcher.find()) {
            return new int[0];
        }

        String[] split = matcher.group().split("\\.");
        int[] parsed = new int[split.length];
        for (int i = 0; i < split.length; i++) {
            try {
                parsed[i] = Integer.parseInt(split[i]);
            } catch (NumberFormatException ignored) {
                parsed[i] = 0;
            }
        }

        return parsed;
    }

    private static String normalizeVersion(String version) {
        if (version == null) {
            return "";
        }

        String trimmed = version.trim();
        if (trimmed.startsWith("v") || trimmed.startsWith("V")) {
            return trimmed.substring(1);
        }

        return trimmed;
    }
}
