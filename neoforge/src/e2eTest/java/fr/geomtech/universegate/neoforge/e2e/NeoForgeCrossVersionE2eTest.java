package fr.geomtech.universegate.neoforge.e2e;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.geomtech.universegate.e2e.harness.SeamlessContractProbe;
import fr.geomtech.universegate.neoforge.NeoForgeClientPlatformHelper;
import fr.geomtech.universegate.neoforge.NeoForgePlatformHelper;
import fr.geomtech.universegate.platform.ClientPlatformHelper;
import fr.geomtech.universegate.platform.ClientServices;
import fr.geomtech.universegate.platform.PlatformHelper;
import fr.geomtech.universegate.platform.Services;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("cross-loader")
@Tag("neoforge")
@DisplayName("NeoForge Cross-Version Baseline E2E")
class NeoForgeCrossVersionE2eTest {

    //? if >=1.21.11 {
    /*private static final String EXPECTED_VERSION_SUFFIX = "+1.21.11";
    private static final String EXPECTED_MC_RANGE = "[1.21.11,1.21.12)";
    *///?} else {
    private static final String EXPECTED_VERSION_SUFFIX = "+1.21.1";
    private static final String EXPECTED_MC_RANGE = "[1.21.1,1.21.2)";
    //?}

    @Test
    @DisplayName("ServiceLoader resolves NeoForgePlatformHelper and NeoForgeClientPlatformHelper without loader boot")
    void serviceLoaderResolvesNeoForgePlatformProviders() {
        List<PlatformHelper> platformProviders = ServiceLoader.load(
                PlatformHelper.class, NeoForgeCrossVersionE2eTest.class.getClassLoader()
        ).stream().map(ServiceLoader.Provider::get).toList();
        assertEquals(1, platformProviders.size(), "Expected exactly one PlatformHelper provider");
        assertInstanceOf(NeoForgePlatformHelper.class, platformProviders.getFirst());

        List<ClientPlatformHelper> clientProviders = ServiceLoader.load(
                ClientPlatformHelper.class, NeoForgeCrossVersionE2eTest.class.getClassLoader()
        ).stream().map(ServiceLoader.Provider::get).toList();
        assertEquals(1, clientProviders.size(), "Expected exactly one ClientPlatformHelper provider");
        assertInstanceOf(NeoForgeClientPlatformHelper.class, clientProviders.getFirst());

        assertInstanceOf(NeoForgePlatformHelper.class, Services.PLATFORM);
        assertEquals("neoforge", Services.PLATFORM.getLoaderName());
        assertTrue(Services.PLATFORM.autoRegistersPoiBlockStates());
        assertInstanceOf(NeoForgeClientPlatformHelper.class, ClientServices.CLIENT);

        //? if >=1.21.11 {
        /*Class<?> beFactory = SeamlessContractProbe.requireClass(
                "fr.geomtech.universegate.platform.PlatformHelper$BlockEntityFactory", false
        );
        Class<?> blockClass = SeamlessContractProbe.requireClass("net.minecraft.world.level.block.Block", false);
        Class<?> beTypeClass = SeamlessContractProbe.requireClass(
                "net.minecraft.world.level.block.entity.BlockEntityType", false
        );
        assertNotNull(SeamlessContractProbe.requireInstanceMethod(
                PlatformHelper.class, "createBlockEntityType", beTypeClass, beFactory, blockClass
        ));
        *///?}
    }

    @Test
    @DisplayName("Expanded META-INF/neoforge.mods.toml and @Mod entrypoints are valid across MC versions")
    void neoforgeModsTomlIsValidAndExpanded() throws IOException, ClassNotFoundException {
        CommentedConfig config;
        try (InputStream stream = NeoForgeCrossVersionE2eTest.class.getClassLoader()
                .getResourceAsStream("META-INF/neoforge.mods.toml")) {
            assertNotNull(stream, "Missing classpath resource: META-INF/neoforge.mods.toml");
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                config = TomlFormat.instance().createParser().parse(reader);
            }
        }

        assertEquals("javafml", config.<String>get("modLoader"));
        assertEquals("[4,)", config.<String>get("loaderVersion"));
        assertEquals("CC0-1.0", config.<String>get("license"));

        List<? extends UnmodifiableConfig> mods = config.get("mods");
        assertNotNull(mods);
        UnmodifiableConfig modEntry = mods.stream()
                .filter(entry -> "universegate".equals(entry.get("modId")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing modId='universegate' in neoforge.mods.toml"));

        String version = modEntry.get("version");
        assertNotNull(version);
        assertFalse(version.contains("${"), "Unexpanded version placeholder in neoforge.mods.toml: " + version);
        assertTrue(version.endsWith(EXPECTED_VERSION_SUFFIX),
                "Expected version ending with " + EXPECTED_VERSION_SUFFIX + " but got " + version);

        List<? extends UnmodifiableConfig> mixins = config.get("mixins");
        assertNotNull(mixins);
        List<String> mixinConfigs = mixins.stream()
                .map(entry -> entry.<String>get("config"))
                .toList();
        assertTrue(mixinConfigs.contains("universegate.mixins.json"),
                "Missing universegate.mixins.json in neoforge.mods.toml");
        assertTrue(mixinConfigs.contains("universegate.client.mixins.json"),
                "Missing universegate.client.mixins.json in neoforge.mods.toml");

        List<? extends UnmodifiableConfig> deps = config.get(List.of("dependencies", "universegate"));
        assertNotNull(deps);

        UnmodifiableConfig neoforgeDep = findDep(deps, "neoforge");
        assertEquals("required", neoforgeDep.get("type"));
        String neoforgeRange = neoforgeDep.get("versionRange");
        assertNotNull(neoforgeRange);
        assertFalse(neoforgeRange.contains("${"), "Unexpanded neoforge_version placeholder: " + neoforgeRange);
        assertTrue(neoforgeRange.startsWith("["), "Unexpected neoforge versionRange: " + neoforgeRange);

        UnmodifiableConfig mcDep = findDep(deps, "minecraft");
        assertEquals("required", mcDep.get("type"));
        assertEquals(EXPECTED_MC_RANGE, mcDep.get("versionRange"));

        UnmodifiableConfig stellarisDep = findDep(deps, "stellaris");
        assertEquals("optional", stellarisDep.get("type"));

        ClassLoader cl = NeoForgeCrossVersionE2eTest.class.getClassLoader();
        for (String entrypoint : List.of(
                "fr.geomtech.universegate.neoforge.UniverseGateNeoForge",
                "fr.geomtech.universegate.neoforge.UniverseGateNeoForgeClient"
        )) {
            Class<?> cls = Class.forName(entrypoint, false, cl);
            assertNotNull(cls.getDeclaredMethods());
        }
    }

    @Test
    @DisplayName("All mixins in universegate.mixins.json and universegate.client.mixins.json resolve on NeoForge classpath")
    void allDeclaredMixinClassesLoadAndResolveSignatures() throws IOException, ClassNotFoundException {
        verifyMixinConfig("universegate.mixins.json", "fr.geomtech.universegate.mixin");
        verifyMixinConfig("universegate.client.mixins.json", "fr.geomtech.universegate.mixin.client");
    }

    private static UnmodifiableConfig findDep(List<? extends UnmodifiableConfig> deps, String modId) {
        return deps.stream()
                .filter(entry -> modId.equals(entry.get("modId")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing dependency '" + modId + "' in neoforge.mods.toml"));
    }

    private static void verifyMixinConfig(String resourceName, String expectedPackage)
            throws IOException, ClassNotFoundException {
        JsonObject json = readJsonResource(resourceName);
        assertTrue(json.get("required").getAsBoolean(), resourceName + " must set required=true");
        assertEquals(expectedPackage, json.get("package").getAsString());
        assertEquals("JAVA_21", json.get("compatibilityLevel").getAsString());
        assertEquals(1, json.getAsJsonObject("injectors").get("defaultRequire").getAsInt());

        List<String> mixinEntries = new ArrayList<>();
        addEntries(json, "mixins", mixinEntries);
        addEntries(json, "client", mixinEntries);
        addEntries(json, "server", mixinEntries);
        assertFalse(mixinEntries.isEmpty(), "Expected at least one mixin entry in " + resourceName);

        ClassLoader cl = NeoForgeCrossVersionE2eTest.class.getClassLoader();
        for (String entry : mixinEntries) {
            String fqcn = expectedPackage + "." + entry;
            Class<?> cls = Class.forName(fqcn, false, cl);
            assertNotNull(cls.getDeclaredMethods(), "Failed to resolve methods for " + fqcn);
            assertNotNull(cls.getDeclaredFields(), "Failed to resolve fields for " + fqcn);
        }
    }

    private static void addEntries(JsonObject json, String key, List<String> target) {
        if (!json.has(key)) {
            return;
        }
        for (JsonElement element : json.getAsJsonArray(key)) {
            target.add(element.getAsString());
        }
    }

    private static JsonObject readJsonResource(String resourcePath) throws IOException {
        try (InputStream stream = NeoForgeCrossVersionE2eTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
            assertNotNull(stream, "Missing classpath resource: " + resourcePath);
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        }
    }
}
