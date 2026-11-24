package fr.geomtech.universegate;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.IronGolemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.IronGolem;

public class RustedGolemRenderer extends IronGolemRenderer {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "textures/entity/rusted_golem.png");
    public RustedGolemRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override public ResourceLocation getTextureLocation(IronGolem entity) { return TEXTURE; }
}
