package fr.geomtech.universegate;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;

public class RiftShadeModel extends HumanoidModel<RiftShadeEntity> {

    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "rift_shade"),
            "main"
    );

    public RiftShadeModel(ModelPart root) {
        super(root);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
        PartDefinition root = mesh.getRoot();

        root.addOrReplaceChild("head",
                CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F)
                        .texOffs(32, 0).addBox(-2.0F, -1.0F, -5.0F, 4.0F, 2.0F, 1.0F),
                PartPose.offset(0.0F, 0.0F, 0.0F)
        );

        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);

        root.addOrReplaceChild("body",
                CubeListBuilder.create()
                        .texOffs(16, 16).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F)
                        .texOffs(16, 32).addBox(-3.0F, 11.0F, -1.5F, 6.0F, 6.0F, 3.0F),
                PartPose.offset(0.0F, 0.0F, 0.0F)
        );

        root.addOrReplaceChild("right_arm",
                CubeListBuilder.create()
                        .texOffs(40, 16).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 14.0F, 4.0F)
                        .texOffs(40, 34).addBox(-2.0F, 11.0F, -1.5F, 3.0F, 4.0F, 3.0F),
                PartPose.offset(-5.0F, 2.0F, 0.0F)
        );

        root.addOrReplaceChild("left_arm",
                CubeListBuilder.create()
                        .texOffs(40, 16).mirror().addBox(-1.0F, -2.0F, -2.0F, 4.0F, 16.0F, 4.0F)
                        .texOffs(40, 34).mirror().addBox(-1.0F, 13.0F, -1.5F, 3.0F, 4.0F, 3.0F),
                PartPose.offset(5.0F, 2.0F, 0.0F)
        );

        root.addOrReplaceChild("right_leg",
                CubeListBuilder.create()
                        .texOffs(0, 16).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 13.0F, 4.0F)
                        .texOffs(0, 33).addBox(-1.0F, 12.0F, -1.0F, 2.0F, 4.0F, 2.0F),
                PartPose.offset(-1.8F, 12.0F, 0.0F)
        );

        root.addOrReplaceChild("left_leg",
                CubeListBuilder.create()
                        .texOffs(0, 16).mirror().addBox(-2.0F, 0.0F, -2.0F, 4.0F, 11.0F, 4.0F)
                        .texOffs(0, 33).mirror().addBox(-1.0F, 10.0F, -1.0F, 2.0F, 6.0F, 2.0F),
                PartPose.offset(1.8F, 12.0F, 0.0F)
        );

        return LayerDefinition.create(mesh, 64, 64);
    }
}
