package fr.geomtech.universegate;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.ArrayList;
import java.util.List;

/** Initializes legacy registry declarations only while NeoForge is registering their registry. */
final class NeoForgeRegistryBootstrap {
    private static final List<Entry<?>> PENDING = new ArrayList<>();
    private static final ThreadLocal<RegisterEvent> CURRENT_EVENT = new ThreadLocal<>();

    private NeoForgeRegistryBootstrap() {}

    static <T> T register(ResourceKey<? extends Registry<T>> registry, ResourceLocation id, T value) {
        queueOrRegister(new Entry<>(registry, id, value));
        return value;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static <T> T register(Registry<? super T> registry, ResourceLocation id, T value) {
        queueOrRegister(new Entry((ResourceKey) registry.key(), id, value));
        return value;
    }

    private static void queueOrRegister(Entry<?> entry) {
        RegisterEvent event = CURRENT_EVENT.get();
        if (event != null && entry.registry().equals(event.getRegistryKey())) entry.register(event);
        else PENDING.add(entry);
    }

    static void registerEntries(RegisterEvent event) {
        CURRENT_EVENT.set(event);
        try {
            initializeDeclarations(event.getRegistryKey());
            PENDING.removeIf(entry -> {
                if (!entry.registry().equals(event.getRegistryKey())) return false;
                entry.register(event);
                return true;
            });
        } finally {
            CURRENT_EVENT.remove();
        }
    }

    private static void initializeDeclarations(ResourceKey<? extends Registry<?>> key) {
        if (key.equals(BuiltInRegistries.FLUID.key())) initialize("ModFluids");
        else if (key.equals(BuiltInRegistries.BLOCK.key())) initialize("ModBlocks");
        else if (key.equals(BuiltInRegistries.ENTITY_TYPE.key())) initialize("ModEntityTypes");
        else if (key.equals(BuiltInRegistries.ITEM.key())) initialize("ModItems");
        else if (key.equals(BuiltInRegistries.BLOCK_ENTITY_TYPE.key())) initialize("ModBlockEntities");
        else if (key.equals(BuiltInRegistries.MENU.key())) initialize("ModMenuTypes");
        else if (key.equals(BuiltInRegistries.CREATIVE_MODE_TAB.key())) {
            initialize("ModItemGroups");
            ModItemGroups.register();
        }
        else if (key.equals(BuiltInRegistries.FEATURE.key())) initialize("ModFeatures");
        else if (key.equals(BuiltInRegistries.SOUND_EVENT.key())) initialize("ModSounds");
        else if (key.equals(BuiltInRegistries.POINT_OF_INTEREST_TYPE.key())
                || key.equals(BuiltInRegistries.VILLAGER_PROFESSION.key())) {
            initialize("ModVillagers");
            ModVillagers.register();
        }
    }

    private static void initialize(String simpleName) {
        try {
            Class.forName("fr.geomtech.universegate." + simpleName, true, NeoForgeRegistryBootstrap.class.getClassLoader());
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("Unable to initialize registry declarations for " + simpleName, exception);
        }
    }

    private record Entry<T>(ResourceKey<? extends Registry<T>> registry, ResourceLocation id, T value) {
        @SuppressWarnings({"unchecked", "rawtypes"})
        private void register(RegisterEvent event) {
            event.register((ResourceKey) registry, id, () -> value);
        }
    }
}
