package fr.geomtech.universegate.fabric.e2e;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.geomtech.universegate.e2e.harness.SeamlessContractProbe;
import fr.geomtech.universegate.fabric.FabricClientPlatformHelper;
import fr.geomtech.universegate.fabric.FabricPlatformHelper;
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
@Tag("fabric")
@DisplayName("Fabric Cross-Version Baseline E2E")
class FabricCrossVersionE2eTest {

    //? if >=1.21.11 {
    /*private static final String EXPECTED_VERSION_SUFFIX = "+1.21.11";
    private static final String EXPECTED_MC_DEPENDENCY = "~1.21.11";
    *///?} else {
    private static final String EXPECTED_VERSION_SUFFIX = "+1.21.1";
    private static final String EXPECTED_MC_DEPENDENCY = "~1.21.1";
    //?}

    @Test
    @DisplayName("ServiceLoader resolves FabricPlatformHelper and FabricClientPlatformHelper without loader boot")
    void serviceLoaderResolvesFabricPlatformProviders() {
        List<PlatformHelper> platformProviders = ServiceLoader.load(
                PlatformHelper.class, FabricCrossVersionE2eTest.class.getClassLoader()
        ).stream().map(ServiceLoader.Provider::get).toList();
        assertEquals(1, platformProviders.size(), "Expected exactly one PlatformHelper provider");
        assertInstanceOf(FabricPlatformHelper.class, platformProviders.getFirst());

        List<ClientPlatformHelper> clientProviders = ServiceLoader.load(
                ClientPlatformHelper.class, FabricCrossVersionE2eTest.class.getClassLoader()
        ).stream().map(ServiceLoader.Provider::get).toList();
        assertEquals(1, clientProviders.size(), "Expected exactly one ClientPlatformHelper provider");
        assertInstanceOf(FabricClientPlatformHelper.class, clientProviders.getFirst());

        assertInstanceOf(FabricPlatformHelper.class, Services.PLATFORM);
        assertEquals("fabric", Services.PLATFORM.getLoaderName());
        assertFalse(Services.PLATFORM.autoRegistersPoiBlockStates());
        assertInstanceOf(FabricClientPlatformHelper.class, ClientServices.CLIENT);

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
    @DisplayName("Expanded fabric.mod.json has valid metadata, entrypoints, mixins, and MC version constraints")
    void fabricModJsonIsValidAndExpanded() throws IOException, ClassNotFoundException {
        JsonObject root = readJsonResource("fabric.mod.json");

        assertEquals(1, root.get("schemaVersion").getAsInt());
        assertEquals("universegate", root.get("id").getAsString());
        assertEquals("*", root.get("environment").getAsString());

        String version = root.get("version").getAsString();
        assertFalse(version.contains("${"), "Unexpanded version placeholder in fabric.mod.json: " + version);
        assertTrue(version.endsWith(EXPECTED_VERSION_SUFFIX),
                "Expected version ending with " + EXPECTED_VERSION_SUFFIX + " but got " + version);

        JsonObject entrypoints = root.getAsJsonObject("entrypoints");
        List<String> mainEntrypoints = toStringList(entrypoints.getAsJsonArray("main"));
        List<String> clientEntrypoints = toStringList(entrypoints.getAsJsonArray("client"));
        assertTrue(mainEntrypoints.contains("fr.geomtech.universegate.fabric.UniverseGateFabric"));
        assertTrue(clientEntrypoints.contains("fr.geomtech.universegate.fabric.UniverseGateFabricClient"));

        ClassLoader cl = FabricCrossVersionE2eTest.class.getClassLoader();
        for (String entrypoint : mainEntrypoints) {
            Class<?> cls = Class.forName(entrypoint, false, cl);
            assertNotNull(cls.getDeclaredMethods());
        }
        for (String entrypoint : clientEntrypoints) {
            Class<?> cls = Class.forName(entrypoint, false, cl);
            assertNotNull(cls.getDeclaredMethods());
        }

        JsonArray mixins = root.getAsJsonArray("mixins");
        boolean foundCommonMixinConfig = false;
        boolean foundClientMixinConfig = false;
        for (JsonElement element : mixins) {
            if (element.isJsonPrimitive() && "universegate.mixins.json".equals(element.getAsString())) {
                foundCommonMixinConfig = true;
            } else if (element.isJsonObject()) {
                JsonObject obj = element.getAsJsonObject();
                if ("universegate.client.mixins.json".equals(obj.get("config").getAsString())
                        && "client".equals(obj.get("environment").getAsString())) {
                    foundClientMixinConfig = true;
                }
            }
        }
        assertTrue(foundCommonMixinConfig, "Missing universegate.mixins.json in fabric.mod.json");
        assertTrue(foundClientMixinConfig, "Missing client-scoped universegate.client.mixins.json in fabric.mod.json");

        JsonObject depends = root.getAsJsonObject("depends");
        assertEquals(EXPECTED_MC_DEPENDENCY, depends.get("minecraft").getAsString());
        assertEquals(">=21", depends.get("java").getAsString());
        assertEquals("*", depends.get("fabric-api").getAsString());
        String fabricLoaderDep = depends.get("fabricloader").getAsString();
        assertFalse(fabricLoaderDep.contains("${"), "Unexpanded fabricloader placeholder: " + fabricLoaderDep);
        assertTrue(fabricLoaderDep.startsWith(">="), "Unexpected fabricloader constraint: " + fabricLoaderDep);
    }

    @Test
    @DisplayName("All mixins in universegate.mixins.json and universegate.client.mixins.json resolve on Fabric classpath")
    void allDeclaredMixinClassesLoadAndResolveSignatures() throws IOException, ClassNotFoundException {
        verifyMixinConfig("universegate.mixins.json", "fr.geomtech.universegate.mixin");
        verifyMixinConfig("universegate.client.mixins.json", "fr.geomtech.universegate.mixin.client");
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

        ClassLoader cl = FabricCrossVersionE2eTest.class.getClassLoader();
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

    private static List<String> toStringList(JsonArray array) {
        List<String> result = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            result.add(element.getAsString());
        }
        return result;
    }

    private static JsonObject readJsonResource(String resourcePath) throws IOException {
        try (InputStream stream = FabricCrossVersionE2eTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
            assertNotNull(stream, "Missing classpath resource: " + resourcePath);
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        }
    }
}
