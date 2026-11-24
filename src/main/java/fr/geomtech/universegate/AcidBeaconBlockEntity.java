package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class AcidBeaconBlockEntity extends AcidNeonLampBlockEntity {
    public static final int EFFECT_RADIUS = 64;

    public AcidBeaconBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.ACID_BEACON, pos, state); }

    @Override protected int energyPerTick() { return 4; }
    @Override protected boolean canOperate(Level level, BlockPos pos) { return level.canSeeSky(pos.above()); }

    @Override
    protected void serverTick(Level level, BlockPos pos, BlockState state) {
        super.serverTick(level, pos, state);
        if (getStoredEnergy() <= 0 || !canOperate(level, pos) || level.getGameTime() % 80L != 0L) return;
        for (Player player : level.getEntitiesOfClass(Player.class, new AABB(pos).inflate(EFFECT_RADIUS))) {
            if (player.isSpectator() || player.distanceToSqr(pos.getCenter()) > EFFECT_RADIUS * EFFECT_RADIUS) continue;
            grant(player, MobEffects.MOVEMENT_SPEED, 1);
            grant(player, MobEffects.DIG_SPEED, 1);
            grant(player, MobEffects.DAMAGE_BOOST, 1);
            grant(player, MobEffects.DAMAGE_RESISTANCE, 0);
            grant(player, MobEffects.REGENERATION, 0);
        }
    }

    private static void grant(Player player, Holder<MobEffect> effect, int amplifier) {
        player.addEffect(new MobEffectInstance(effect, 200, amplifier, true, true));
    }
}
