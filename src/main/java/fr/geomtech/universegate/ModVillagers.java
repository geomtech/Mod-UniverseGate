package fr.geomtech.universegate;

import com.google.common.collect.ImmutableSet;
import fr.geomtech.universegate.mixin.PoiTypesAccessor;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public final class ModVillagers {

    public static final ResourceKey<PoiType> PORTAL_KEYBOARD_POI_KEY = ResourceKey.create(
            Registries.POINT_OF_INTEREST_TYPE,
            ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "portal_keyboard")
    );

    public static final ResourceKey<VillagerProfession> ENGINEER_KEY = ResourceKey.create(
            Registries.VILLAGER_PROFESSION,
            ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "engineer")
    );

    public static PoiType PORTAL_KEYBOARD_POI;
    public static VillagerProfession ENGINEER;

    private ModVillagers() {}

    public static void register() {
        if (PORTAL_KEYBOARD_POI != null && ENGINEER != null) return;

        Set<BlockState> keyboardStates = new HashSet<>(ModBlocks.PORTAL_KEYBOARD.getStateDefinition().getPossibleStates());
        keyboardStates.addAll(ModBlocks.PORTAL_NATURAL_KEYBOARD.getStateDefinition().getPossibleStates());

        PORTAL_KEYBOARD_POI = NeoForgeRegistryBootstrap.register(
                BuiltInRegistries.POINT_OF_INTEREST_TYPE.key(),
                PORTAL_KEYBOARD_POI_KEY.location(),
                new PoiType(keyboardStates, 1, 1)
        );

        ENGINEER = NeoForgeRegistryBootstrap.register(
                BuiltInRegistries.VILLAGER_PROFESSION.key(),
                ENGINEER_KEY.location(),
                new VillagerProfession(
                        "engineer",
                        holder -> holder.is(PORTAL_KEYBOARD_POI_KEY),
                        holder -> holder.is(PORTAL_KEYBOARD_POI_KEY),
                        ImmutableSet.of(),
                        ImmutableSet.of(),
                        SoundEvents.VILLAGER_WORK_TOOLSMITH
                )
        );
    }
}
