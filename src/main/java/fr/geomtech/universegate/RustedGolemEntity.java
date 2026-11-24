package fr.geomtech.universegate;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.level.Level;

public class RustedGolemEntity extends IronGolem {
    public RustedGolemEntity(EntityType<? extends IronGolem> type, Level level) { super(type, level); }

    public static AttributeSupplier.Builder createRustedAttributes() {
        return IronGolem.createAttributes()
                .add(Attributes.MAX_HEALTH, 60.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.14D)
                .add(Attributes.ATTACK_DAMAGE, 7.0D);
    }
}
