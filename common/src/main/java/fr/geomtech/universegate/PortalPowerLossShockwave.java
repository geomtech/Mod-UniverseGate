package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.TintedGlassBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Onde de choc déclenchée lorsqu'un portail en panne d'énergie arrive au bout de son compte à rebours
 * d'effondrement : brise uniquement les blocs de verre et vitres dans le rayon du souffle, repousse les
 * entités proches et projette vapeur et étincelles.
 */
public final class PortalPowerLossShockwave {

    static final int GLASS_SHATTER_RADIUS = 9;
    static final double ENTITY_PUSH_RADIUS = 10.0D;
    static final double ENTITY_DAMAGE_RADIUS = 5.5D;
    static final float MAX_BLAST_DAMAGE = 3.0F;
    static final double MAX_KNOCKBACK = 1.4D;
    private static final int MAX_FULL_SHATTER_SOUNDS = 14;

    private static final TagKey<Block> COMMON_GLASS_BLOCKS = TagKey.create(
            Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("c", "glass_blocks"));
    private static final TagKey<Block> COMMON_GLASS_PANES = TagKey.create(
            Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("c", "glass_panes"));
    private static final TagKey<Block> FORGE_GLASS_BLOCKS = TagKey.create(
            Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("forge", "glass"));
    private static final TagKey<Block> FORGE_GLASS_PANES = TagKey.create(
            Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("forge", "glass_panes"));

    private PortalPowerLossShockwave() {}

    public static void trigger(ServerLevel level, BlockPos corePos) {
        double cx = corePos.getX() + 0.5D;
        double cy = corePos.getY() + 1.0D + PortalFrameDetector.INNER_HEIGHT * 0.5D;
        double cz = corePos.getZ() + 0.5D;
        Vec3 center = new Vec3(cx, cy, cz);

        playShockwaveSounds(level, corePos, cx, cy, cz);
        spawnShockwaveParticles(level, cx, cy, cz);
        shatterGlassInRadius(level, center);
        pushAndHarmEntities(level, center);
    }

    static boolean isShatterableGlass(BlockState state) {
        if (state.isAir()) return false;
        Block block = state.getBlock();
        if (block == Blocks.GLASS
                || block == Blocks.GLASS_PANE
                || block instanceof StainedGlassBlock
                || block instanceof StainedGlassPaneBlock
                || block instanceof TintedGlassBlock) {
            return true;
        }
        return state.is(COMMON_GLASS_BLOCKS)
                || state.is(COMMON_GLASS_PANES)
                || state.is(FORGE_GLASS_BLOCKS)
                || state.is(FORGE_GLASS_PANES);
    }

    private static void playShockwaveSounds(ServerLevel level, BlockPos corePos, double cx, double cy, double cz) {
        level.playSound(null, cx, cy, cz, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 1.8F, 0.72F);
        ModSounds.playAt(level, corePos, ModSounds.PORTAL_ERROR, 1.2F, 0.65F);
        ModSounds.playAt(level, corePos, ModSounds.PORTAL_VENT, 1.2F, 0.75F);
    }

    private static void spawnShockwaveParticles(ServerLevel level, double cx, double cy, double cz) {
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, cx, cy, cz, 2, 0.6D, 0.8D, 0.6D, 0.0D);

        int rays = 32;
        for (int i = 0; i < rays; i++) {
            double theta = (Mth.TWO_PI * i) / rays;
            double cos = Math.cos(theta);
            double sin = Math.sin(theta);
            double vy = (i % 4 - 1.5D) * 0.14D;

            // Onde radiale de vapeur / fumée
            level.sendParticles(ParticleTypes.CLOUD, cx, cy, cz, 0, cos, vy + 0.08D, sin, 0.55D);
            if ((i & 1) == 0) {
                level.sendParticles(ParticleTypes.POOF, cx, cy, cz, 0, cos, vy + 0.12D, sin, 0.42D);
            }
            // Gerbe d'étincelles électriques
            level.sendParticles(ModParticles.SPARK, cx, cy, cz, 0, cos, vy + 0.15D, sin, 0.65D);
        }
    }

    private static void shatterGlassInRadius(ServerLevel level, Vec3 center) {
        BlockPos origin = BlockPos.containing(center);
        double maxDistSqr = (double) GLASS_SHATTER_RADIUS * GLASS_SHATTER_RADIUS;
        int fullShatters = 0;
        int brokenCount = 0;

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dy = -GLASS_SHATTER_RADIUS; dy <= GLASS_SHATTER_RADIUS; dy++) {
            for (int dx = -GLASS_SHATTER_RADIUS; dx <= GLASS_SHATTER_RADIUS; dx++) {
                for (int dz = -GLASS_SHATTER_RADIUS; dz <= GLASS_SHATTER_RADIUS; dz++) {
                    double distSqr = dx * dx + dy * dy + dz * dz;
                    if (distSqr > maxDistSqr) continue;

                    pos.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    if (!level.isLoaded(pos)) continue;

                    BlockState state = level.getBlockState(pos);
                    if (!isShatterableGlass(state)) continue;

                    brokenCount++;
                    if (fullShatters < MAX_FULL_SHATTER_SOUNDS) {
                        level.destroyBlock(pos, false);
                        fullShatters++;
                    } else {
                        level.removeBlock(pos, false);
                        if ((brokenCount & 3) == 0) {
                            level.sendParticles(
                                    new BlockParticleOption(ParticleTypes.BLOCK, state),
                                    pos.getX() + 0.5D,
                                    pos.getY() + 0.5D,
                                    pos.getZ() + 0.5D,
                                    6,
                                    0.25D,
                                    0.25D,
                                    0.25D,
                                    0.12D
                            );
                        }
                    }
                }
            }
        }
    }

    private static void pushAndHarmEntities(ServerLevel level, Vec3 center) {
        AABB box = new AABB(
                center.x - ENTITY_PUSH_RADIUS,
                center.y - ENTITY_PUSH_RADIUS,
                center.z - ENTITY_PUSH_RADIUS,
                center.x + ENTITY_PUSH_RADIUS,
                center.y + ENTITY_PUSH_RADIUS,
                center.z + ENTITY_PUSH_RADIUS
        );

        for (Entity entity : level.getEntities((Entity) null, box, e -> e.isAlive() && !e.isSpectator())) {
            Vec3 delta = entity.getBoundingBox().getCenter().subtract(center);
            double dist = delta.length();
            if (dist > ENTITY_PUSH_RADIUS) continue;

            Vec3 dir = dist > 1.0E-4D ? delta.scale(1.0D / dist) : new Vec3(0.0D, 0.35D, 1.0D).normalize();
            double falloff = Mth.clamp(1.0D - (dist / ENTITY_PUSH_RADIUS), 0.0D, 1.0D);

            boolean immuneCreativePlayer = entity instanceof Player player && player.getAbilities().instabuild;
            if (!immuneCreativePlayer) {
                double pushX = dir.x * MAX_KNOCKBACK * falloff;
                double pushY = Math.max(0.22D, dir.y * 0.65D + 0.32D) * falloff;
                double pushZ = dir.z * MAX_KNOCKBACK * falloff;
                entity.setDeltaMovement(entity.getDeltaMovement().add(pushX, pushY, pushZ));
                entity.hasImpulse = true;
                entity.hurtMarked = true;

                if (entity instanceof LivingEntity living && dist <= ENTITY_DAMAGE_RADIUS) {
                    float damageFalloff = (float) Mth.clamp(1.0D - (dist / ENTITY_DAMAGE_RADIUS), 0.0D, 1.0D);
                    float damage = MAX_BLAST_DAMAGE * damageFalloff;
                    if (damage >= 0.5F) {
                        living.hurt(level.damageSources().explosion(null), damage);
                    }
                }
            }
        }
    }
}
