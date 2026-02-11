package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record PortalInfo(UUID id, String name, ResourceLocation dimId, BlockPos corePos) { }
