package fr.geomtech.universegate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.phys.Vec3;

import fr.geomtech.universegate.UniverseGateDimensions;
import fr.geomtech.universegate.PortalRiftHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class PortalTeleportHandler {

    // --- réglages ---
    private static final int CORE_SEARCH_XZ = 3;
    private static final int CORE_SEARCH_Y = 5;

    private static final int KEYBOARD_RADIUS = 8;
    private static final int KEYBOARD_SEARCH_Y = 2;

    private static final double PORTAL_INNER_HALF_WIDTH = PortalFrameDetector.INNER_WIDTH / 2.0D;
    private static final double PORTAL_INNER_HEIGHT = PortalFrameDetector.INNER_HEIGHT;
    private static final double PORTAL_POSITION_EPSILON = 0.01D;
    private static final double PORTAL_MIN_EXIT_OFFSET = 0.35D;

    private static final float UNSTABLE_RIFT_CHANCE = 0.05F;
    private static final float UNSTABLE_DAMAGE_CHANCE = 0.40F;
    private static final float UNSTABLE_RANDOM_PORTAL_CHANCE = 0.10F;
    private static final float UNSTABLE_DAMAGE_AMOUNT = 8.0F; // 4 hearts
    private static final int UNSTABLE_NAUSEA_DURATION_TICKS = 20 * 3;
    private static final int UNSTABLE_SLOWNESS_DURATION_TICKS = 20 * 5;
    private static final int UNSTABLE_SLOWNESS_AMPLIFIER = 2; // slowness III

    private PortalTeleportHandler() {}

    /** Appelé par PortalFieldBlock quand une entité touche le champ. */
    public static void tryTeleport(Entity entity, BlockPos fieldPos) {
        if (!(entity.level() instanceof ServerLevel sourceLevel)) return;
        if (!entity.isAlive()) return;
        if (entity.isPassenger()) return;

        long now = sourceLevel.getGameTime();
        UUID entityId = entity.getUUID();
        boolean preserveProjectileMomentum = shouldPreserveProjectileMomentum(entity);
        Vec3 preservedVelocity = entity.getDeltaMovement();
        float preservedYaw = entity.getYRot();
        float preservedPitch = entity.getXRot();
        Vec3 projectileVelocityAfterTeleport = preservedVelocity;
        float projectileYawAfterTeleport = preservedYaw;
        float projectilePitchAfterTeleport = preservedPitch;

        // 1) trouver le core associé au champ
        BlockPos corePos = findCoreNear(sourceLevel, fieldPos);
        if (corePos == null) return;

        if (!(sourceLevel.getBlockEntity(corePos) instanceof PortalCoreBlockEntity core)) return;
        if (!core.isActive()) return;
        if (!core.isOutboundTravelEnabled()) {
            if (entity instanceof ServerPlayer player) {
                showBlockedDirectionMessage(player);
            }
            return;
        }

        UUID targetId = core.getTargetPortalId();
        if (targetId == null) return;

        // 2) resolve destination (cross-dimension)
        PortalRegistrySavedData registry = PortalRegistrySavedData.get(sourceLevel.getServer());
        PortalRegistrySavedData.PortalEntry targetEntry = registry.get(targetId);
        if (targetEntry == null) {
            // destination inexistante => fermeture
            PortalConnectionManager.forceCloseOneSide(sourceLevel, corePos);
            return;
        }

        boolean unstableDamageRoll = false;
        boolean instabilityTriggered = false;
        if (core.isVortexUnstable(now)) {
            boolean redirectToRift = sourceLevel.random.nextFloat() < UNSTABLE_RIFT_CHANCE;
            boolean redirectToRandomPortal = sourceLevel.random.nextFloat() < UNSTABLE_RANDOM_PORTAL_CHANCE;
            unstableDamageRoll = sourceLevel.random.nextFloat() < UNSTABLE_DAMAGE_CHANCE;

            if (redirectToRandomPortal) {
                PortalRegistrySavedData.PortalEntry randomEntry = pickRandomPortalDestination(
                        sourceLevel.getServer(),
                        registry,
                        sourceLevel.random,
                        core.getPortalId(),
                        targetEntry.id()
                );
                if (randomEntry != null) {
                    targetEntry = randomEntry;
                    instabilityTriggered = true;
                }
            }

            if (redirectToRift) {
                PortalRegistrySavedData.PortalEntry riftEntry =
                        findRiftPortalDestination(sourceLevel.getServer(), registry, core.getPortalId());
                if (riftEntry != null) {
                    targetEntry = riftEntry;
                    instabilityTriggered = true;
                }
            }
        }

        boolean isRift = targetEntry.dim().equals(UniverseGateDimensions.RIFT);

        boolean consumeFuel = !isRift && entity instanceof ServerPlayer;

        if (consumeFuel) {
            // 3) consommer 1 rift ash dans le keyboard source
            PortalKeyboardBlockEntity keyboard = findKeyboardNear(sourceLevel, corePos, KEYBOARD_RADIUS);
            if (keyboard == null || !keyboard.consumeOneFuel()) {
                // plus de carburant => fermeture immédiate
                PortalConnectionManager.forceCloseOneSide(sourceLevel, corePos);
                return;
            }
        }

        ServerLevel targetLevel = sourceLevel.getServer().getLevel(targetEntry.dim());
        if (targetLevel == null) {
            PortalConnectionManager.forceCloseOneSide(sourceLevel, corePos);
            return;
        }

        // charger chunk destination (core)
        targetLevel.getChunk(targetEntry.pos());

        // 4) point d’arrivée (orienté selon le portail)
        double x = targetEntry.pos().getX() + 0.5;
        double y = targetEntry.pos().getY() + 1.0;
        double z = targetEntry.pos().getZ() + 0.5;
        float yaw = preserveProjectileMomentum ? preservedYaw : entity.getYRot();
        float pitch = preserveProjectileMomentum ? preservedPitch : entity.getXRot();

        var sourceMatch = PortalFrameDetector.find(sourceLevel, corePos);
        var targetMatch = PortalFrameDetector.find(targetLevel, targetEntry.pos());
        if (sourceMatch.isPresent() && targetMatch.isPresent()) {
            int sideSign = sideSignFromEntry(sourceMatch.get(), corePos, entity);
            Direction sourceNormal = normalFromMatch(sourceMatch.get(), sideSign);
            Direction sourceSurfaceRight = sourceNormal.getCounterClockWise();
            PortalKeyboardBlockEntity targetKeyboard = findKeyboardNear(targetLevel, targetEntry.pos(), KEYBOARD_RADIUS);
            Direction exitNormal = targetKeyboard != null
                    ? normalFromKeyboard(targetMatch.get(), targetEntry.pos(), targetKeyboard.getBlockPos(), sideSign)
                    : normalFromMatch(targetMatch.get(), sideSign);
            Direction targetSurfaceRight = exitNormal.getCounterClockWise();

            if (preserveProjectileMomentum) {
                projectileVelocityAfterTeleport = transformVelocityThroughPortal(
                        preservedVelocity,
                        sourceSurfaceRight,
                        sourceNormal,
                        targetSurfaceRight,
                        exitNormal
                );
                projectileYawAfterTeleport = yawFromVelocity(projectileVelocityAfterTeleport, preservedYaw);
                projectilePitchAfterTeleport = pitchFromVelocity(projectileVelocityAfterTeleport, preservedPitch);
            }

            double lateralOffset = lateralOffsetFromCore(corePos, entity.position(), sourceSurfaceRight);
            double verticalOffset = entity.getY() - (corePos.getY() + 1.0D);
            double lateralLimit = Math.max(0.0D, PORTAL_INNER_HALF_WIDTH - (entity.getBbWidth() * 0.5D) - PORTAL_POSITION_EPSILON);
            double verticalMax = Math.max(0.0D, PORTAL_INNER_HEIGHT - entity.getBbHeight() - PORTAL_POSITION_EPSILON);

            double clampedLateral = clamp(lateralOffset, -lateralLimit, lateralLimit);
            double mirroredLateral = -clampedLateral;
            double clampedVertical = clamp(verticalOffset, 0.0D, verticalMax);
            double exitOffset = Math.max(PORTAL_MIN_EXIT_OFFSET, (entity.getBbWidth() * 0.5D) + 0.05D);

            x = targetEntry.pos().getX() + 0.5 + targetSurfaceRight.getStepX() * mirroredLateral;
            y = targetEntry.pos().getY() + 1.0 + clampedVertical;
            z = targetEntry.pos().getZ() + 0.5 + targetSurfaceRight.getStepZ() * mirroredLateral;

            x += exitNormal.getStepX() * exitOffset;
            z += exitNormal.getStepZ() * exitOffset;
            if (!preserveProjectileMomentum) {
                yaw = exitNormal.toYRot();
            }
        }

        ModSounds.playAt(sourceLevel, corePos, ModSounds.PORTAL_ENTITY_GOING_THROUGH, 0.9F, 1.0F);
        boolean teleported;
        if (entity instanceof ServerPlayer player) {
            player.teleportTo(targetLevel, x, y, z, yaw, player.getXRot());
            teleported = true;
        } else {
            teleported = entity.teleportTo(targetLevel, x, y, z, Set.<RelativeMovement>of(), yaw, pitch);
        }
        if (!teleported) return;

        if (preserveProjectileMomentum) {
            Entity teleportedEntity = targetLevel.getEntity(entityId);
            if (teleportedEntity == null && entity.level() == targetLevel) {
                teleportedEntity = entity;
            }
            if (teleportedEntity != null) {
                teleportedEntity.setDeltaMovement(projectileVelocityAfterTeleport);
                teleportedEntity.setYRot(projectileYawAfterTeleport);
                teleportedEntity.setXRot(projectilePitchAfterTeleport);
            }
        }

        ModSounds.playAt(targetLevel, targetEntry.pos(), ModSounds.PORTAL_ENTITY_GOING_THROUGH, 0.9F, 1.05F);
        core.onEntityPassed(now);
        if (targetLevel.getBlockEntity(targetEntry.pos()) instanceof PortalCoreBlockEntity targetCore) {
            targetCore.onEntityPassed(targetLevel.getGameTime());
        }

        LivingEntity livingEntity = entity instanceof LivingEntity living ? living : null;
        if (unstableDamageRoll && livingEntity != null) {
            livingEntity.hurt(targetLevel.damageSources().magic(), UNSTABLE_DAMAGE_AMOUNT);
            instabilityTriggered = true;
        }
        if (instabilityTriggered && livingEntity != null) {
            applyUnstableDebuffs(livingEntity);
        }

        if (isRift) {
            PortalRiftHelper.handleRiftArrival(targetLevel, targetEntry.pos());
        }
    }

    private static void showBlockedDirectionMessage(ServerPlayer player) {
        player.displayClientMessage(
                Component.translatable("message.universegate.blocked_direction").withStyle(ChatFormatting.RED),
                true
        );
    }

    private static int sideSignFromEntry(PortalFrameDetector.FrameMatch match, BlockPos corePos, Entity entity) {
        double axisVelocity = match.right() == Direction.EAST
                ? entity.getDeltaMovement().z
                : entity.getDeltaMovement().x;

        if (Math.abs(axisVelocity) > 0.001D) {
            return axisVelocity >= 0 ? 1 : -1;
        }

        double centerX = corePos.getX() + 0.5;
        double centerZ = corePos.getZ() + 0.5;
        if (match.right() == Direction.EAST) {
            return entity.getZ() >= centerZ ? 1 : -1;
        }
        return entity.getX() >= centerX ? 1 : -1;
    }

    private static Direction normalFromMatch(PortalFrameDetector.FrameMatch match, int sideSign) {
        if (match.right() == Direction.EAST) {
            return sideSign >= 0 ? Direction.SOUTH : Direction.NORTH;
        }
        return sideSign >= 0 ? Direction.EAST : Direction.WEST;
    }

    private static Direction normalFromKeyboard(PortalFrameDetector.FrameMatch match,
                                                BlockPos corePos,
                                                BlockPos keyboardPos,
                                                int fallbackSign) {
        if (match.right() == Direction.EAST) {
            int dz = Integer.compare(keyboardPos.getZ(), corePos.getZ());
            if (dz == 0) return normalFromMatch(match, fallbackSign);
            return dz > 0 ? Direction.SOUTH : Direction.NORTH;
        }

        int dx = Integer.compare(keyboardPos.getX(), corePos.getX());
        if (dx == 0) return normalFromMatch(match, fallbackSign);
        return dx > 0 ? Direction.EAST : Direction.WEST;
    }

    private static BlockPos findCoreNear(ServerLevel level, BlockPos pos) {
        for (int dy = -CORE_SEARCH_Y; dy <= CORE_SEARCH_Y; dy++) {
            for (int dx = -CORE_SEARCH_XZ; dx <= CORE_SEARCH_XZ; dx++) {
                for (int dz = -CORE_SEARCH_XZ; dz <= CORE_SEARCH_XZ; dz++) {
                    BlockPos p = pos.offset(dx, dy, dz);
                    if (level.getBlockState(p).is(ModBlocks.PORTAL_CORE)) {
                        return p;
                    }
                }
            }
        }
        return null;
    }

    private static PortalKeyboardBlockEntity findKeyboardNear(ServerLevel level, BlockPos corePos, int r) {
        for (int dy = -KEYBOARD_SEARCH_Y; dy <= KEYBOARD_SEARCH_Y; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos p = corePos.offset(dx, dy, dz);
                    if (!level.getBlockState(p).is(ModBlocks.PORTAL_KEYBOARD)) continue;
                    if (level.getBlockEntity(p) instanceof PortalKeyboardBlockEntity kb) return kb;
                }
            }
        }
        return null;
    }

    private static PortalRegistrySavedData.PortalEntry findRiftPortalDestination(MinecraftServer server,
                                                                                  PortalRegistrySavedData registry,
                                                                                  UUID sourcePortalId) {
        for (PortalRegistrySavedData.PortalEntry entry : registry.listAll()) {
            if (!entry.dim().equals(UniverseGateDimensions.RIFT)) continue;
            if (sourcePortalId != null && sourcePortalId.equals(entry.id())) continue;
            if (!isPortalEntryUsable(server, entry)) continue;
            return entry;
        }
        return null;
    }

    private static PortalRegistrySavedData.PortalEntry pickRandomPortalDestination(MinecraftServer server,
                                                                                    PortalRegistrySavedData registry,
                                                                                    RandomSource random,
                                                                                    UUID sourcePortalId,
                                                                                    UUID currentTargetId) {
        List<PortalRegistrySavedData.PortalEntry> candidates = new ArrayList<>();
        for (PortalRegistrySavedData.PortalEntry entry : registry.listAll()) {
            if (sourcePortalId != null && sourcePortalId.equals(entry.id())) continue;
            if (currentTargetId != null && currentTargetId.equals(entry.id())) continue;
            if (!isPortalEntryUsable(server, entry)) continue;
            candidates.add(entry);
        }

        if (candidates.isEmpty()) return null;
        return candidates.get(random.nextInt(candidates.size()));
    }

    private static boolean isPortalEntryUsable(MinecraftServer server, PortalRegistrySavedData.PortalEntry entry) {
        ServerLevel level = server.getLevel(entry.dim());
        if (level == null) return false;
        level.getChunk(entry.pos());
        return level.getBlockEntity(entry.pos()) instanceof PortalCoreBlockEntity;
    }

    private static void applyUnstableDebuffs(LivingEntity living) {
        living.addEffect(new MobEffectInstance(MobEffects.CONFUSION, UNSTABLE_NAUSEA_DURATION_TICKS, 0));
        living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, UNSTABLE_SLOWNESS_DURATION_TICKS, UNSTABLE_SLOWNESS_AMPLIFIER));
    }

    private static double lateralOffsetFromCore(BlockPos corePos, Vec3 entityPos, Direction lateralDirection) {
        double dx = entityPos.x - (corePos.getX() + 0.5D);
        double dz = entityPos.z - (corePos.getZ() + 0.5D);
        return dx * lateralDirection.getStepX() + dz * lateralDirection.getStepZ();
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static Vec3 transformVelocityThroughPortal(Vec3 velocity,
                                                       Direction sourceSurfaceRight,
                                                       Direction sourceNormal,
                                                       Direction targetSurfaceRight,
                                                       Direction targetNormal) {
        double sourceLateral = velocity.x * sourceSurfaceRight.getStepX() + velocity.z * sourceSurfaceRight.getStepZ();
        double sourceForward = velocity.x * sourceNormal.getStepX() + velocity.z * sourceNormal.getStepZ();

        double targetX = (-sourceLateral) * targetSurfaceRight.getStepX() + sourceForward * targetNormal.getStepX();
        double targetY = velocity.y;
        double targetZ = (-sourceLateral) * targetSurfaceRight.getStepZ() + sourceForward * targetNormal.getStepZ();
        return new Vec3(targetX, targetY, targetZ);
    }

    private static float yawFromVelocity(Vec3 velocity, float fallbackYaw) {
        double horizontalLengthSqr = velocity.x * velocity.x + velocity.z * velocity.z;
        if (horizontalLengthSqr < 1.0E-7D) return fallbackYaw;
        return (float) (Math.atan2(velocity.z, velocity.x) * (180.0D / Math.PI)) - 90.0F;
    }

    private static float pitchFromVelocity(Vec3 velocity, float fallbackPitch) {
        double horizontalLength = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        if (horizontalLength < 1.0E-7D && Math.abs(velocity.y) < 1.0E-7D) return fallbackPitch;
        return (float) (-(Math.atan2(velocity.y, horizontalLength) * (180.0D / Math.PI)));
    }

    private static boolean shouldPreserveProjectileMomentum(Entity entity) {
        return entity instanceof AbstractArrow;
    }

}
