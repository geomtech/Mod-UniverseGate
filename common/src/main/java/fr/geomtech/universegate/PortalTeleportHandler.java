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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import fr.geomtech.universegate.UniverseGateDimensions;
import fr.geomtech.universegate.PortalRiftHelper;
import fr.geomtech.universegate.seamless.math.PortalCrossingMath;
import fr.geomtech.universegate.seamless.math.PortalTransform;
import fr.geomtech.universegate.seamless.net.SeamlessNetwork;
import fr.geomtech.universegate.seamless.net.SeamlessSwitchHintPayload;
import fr.geomtech.universegate.seamless.physics.PortalCrossingTracker;

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
    private static final double PORTAL_FIELD_CLEARANCE = 0.22D;
    private static final double PORTAL_MINECART_EXTRA_OFFSET = 0.18D;

    private static final double PURSUING_MOB_SEARCH_RADIUS_XZ = 8.0D;
    private static final double PURSUING_MOB_SEARCH_RADIUS_Y = 5.0D;
    private static final double PURSUING_MOB_MAX_PORTAL_DISTANCE_SQR = 49.0D;

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
        Entity root = entity.isPassenger() ? entity.getRootVehicle() : entity;
        if (root == null || !root.isAlive()) return;
        BlockPos corePos = findCoreNear(sourceLevel, fieldPos);
        if (corePos != null && sourceLevel.getBlockEntity(corePos) instanceof PortalCoreBlockEntity core)
            tryTeleport(root, fieldPos, core);
    }

    static void tryTeleport(Entity entity, BlockPos fieldPos, PortalCoreBlockEntity core) {
        if (!(entity.level() instanceof ServerLevel sourceLevel) || !core.isActive()) return;
        if (!entity.isAlive()) return;
        if (entity.isPassenger()) {
            Entity root = entity.getRootVehicle();
            if (root == null || root == entity || !root.isAlive()) return;
            entity = root;
        }

        if (!PortalCrossingTracker.shouldCrossPortal(sourceLevel, entity, core)) {
            return;
        }

        long now = sourceLevel.getGameTime();
        UUID entityId = entity.getUUID();

        boolean alignYawToVelocity = shouldAlignYawToVelocity(entity);
        Vec3 preservedVelocity = entity.getDeltaMovement();
        float preservedYaw = entity.getYRot();
        float preservedPitch = entity.getXRot();
        Vec3 velocityAfterTeleport = preservedVelocity;
        float yawAfterTeleport = preservedYaw;
        float pitchAfterTeleport = preservedPitch;
        List<UUID> pursuingMobIds = List.of();

        BlockPos corePos = core.getBlockPos();
        if (!core.isOutboundTravelEnabled()) {
            // Portals are now bidirectional; still refresh the initiator side's activity timer.
            keepPortalOpenFromDestinationContact(sourceLevel, core, now);
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

        ServerLevel targetLevel = sourceLevel.getServer().getLevel(targetEntry.dim());
        if (targetLevel == null) {
            PortalConnectionManager.forceCloseOneSide(sourceLevel, corePos);
            return;
        }

        // charger chunk destination (core)
        targetLevel.getChunk(targetEntry.pos());

        // 4) point d’arrivée (orienté selon le portail avec conservation continue de vitesse/angle)
        double x = targetEntry.pos().getX() + 0.5;
        double y = targetEntry.pos().getY() + 1.0;
        double z = targetEntry.pos().getZ() + 0.5;
        float yaw = preservedYaw;
        float pitch = preservedPitch;
        PortalTransform portalTransform = null;

        var sourceMatch = PortalFrameDetector.find(sourceLevel, corePos);
        var targetMatch = PortalFrameDetector.find(targetLevel, targetEntry.pos());
        if (sourceMatch.isPresent() && targetMatch.isPresent()) {
            Direction sourceFrontNormal = PortalOrientationHelper.resolveFrontNormal(sourceLevel, corePos, sourceMatch.get().right());
            Direction sourceNormal = resolveSourceInwardNormal(sourceMatch.get(), corePos, entity, sourceFrontNormal);
            // Only allow entering a portal from its FRONT face (controller side).
            if (sourceNormal != sourceFrontNormal.getOpposite()) {
                return;
            }
            // Always exit on the destination portal's FRONT face (controller side) using the unified helper
            Direction exitNormal = PortalOrientationHelper.resolveFrontNormal(targetLevel, targetEntry.pos(), targetMatch.get().right());

            portalTransform = new PortalTransform(corePos, sourceNormal, targetEntry.pos(), exitNormal);
            Direction sourceRight = portalTransform.sourceRight();
            Direction targetRight = portalTransform.targetRight();

            double lateralOffset = lateralOffsetFromCore(corePos, entity.position(), sourceRight);
            double verticalOffset = entity.getY() - (corePos.getY() + 1.0D);
            double lateralLimit = Math.max(0.0D, PORTAL_INNER_HALF_WIDTH - (entity.getBbWidth() * 0.5D) - PORTAL_POSITION_EPSILON);
            double verticalMax = Math.max(0.0D, PORTAL_INNER_HEIGHT - entity.getBbHeight() - PORTAL_POSITION_EPSILON);

            double clampedLateral = clamp(lateralOffset, -lateralLimit, lateralLimit);
            double clampedVertical = clamp(verticalOffset, 0.0D, verticalMax);
            double exitOffset = Math.max(PORTAL_MIN_EXIT_OFFSET, (entity.getBbWidth() * 0.5D) + PORTAL_FIELD_CLEARANCE);
            if (entity instanceof AbstractMinecart) {
                exitOffset += PORTAL_MINECART_EXTRA_OFFSET;
            }

            x = targetEntry.pos().getX() + 0.5 + targetRight.getStepX() * clampedLateral + exitNormal.getStepX() * exitOffset;
            y = targetEntry.pos().getY() + 1.0 + clampedVertical;
            z = targetEntry.pos().getZ() + 0.5 + targetRight.getStepZ() * clampedLateral + exitNormal.getStepZ() * exitOffset;

            velocityAfterTeleport = portalTransform.transformVelocity(preservedVelocity);
            yawAfterTeleport = alignYawToVelocity
                    ? PortalCrossingMath.yawFromVelocity(velocityAfterTeleport, portalTransform.transformYaw(preservedYaw))
                    : portalTransform.transformYaw(preservedYaw);
            pitchAfterTeleport = alignYawToVelocity
                    ? PortalCrossingMath.pitchFromVelocity(velocityAfterTeleport, preservedPitch)
                    : preservedPitch;
            yaw = yawAfterTeleport;
            pitch = pitchAfterTeleport;
        }

        ServerPlayer primaryPlayer = entity instanceof ServerPlayer sp ? sp : null;
        if (primaryPlayer == null) {
            for (Entity passenger : entity.getIndirectPassengers()) {
                if (passenger instanceof ServerPlayer sp) {
                    primaryPlayer = sp;
                    break;
                }
            }
        }
        if (primaryPlayer != null) {
            pursuingMobIds = collectPursuingMobIds(sourceLevel, corePos, primaryPlayer);
        }

        // Capture passenger hierarchy before teleporting so vehicles + riders cross together cleanly
        PassengerNode passengerTree = captureAndDetachPassengers(entity);

        boolean crossDimension = sourceLevel != targetLevel;
        if (crossDimension) {
            sendSeamlessSwitchHints(passengerTree, targetEntry.dim(), targetEntry.pos(), x, y, z, yaw, pitch, velocityAfterTeleport, portalTransform);
        }

        ModSounds.playAt(sourceLevel, corePos, ModSounds.PORTAL_ENTITY_GOING_THROUGH, 0.9F, 1.0F);
        // Apply transformed velocity BEFORE cross-dimension teleportTo() so Entity#restoreFrom copies
        // the transformed velocity into the newly spawned destination entity BEFORE ChunkMap#addEntity
        // builds ServerEntity and emits ClientboundAddEntityPacket (prevents dropped items/projectiles
        // from flying sideways in the remote preview until ServerEntity's next 20-tick sync).
        if (!(entity instanceof ServerPlayer)) {
            entity.setDeltaMovement(velocityAfterTeleport);
        }
        Entity teleportedRoot = teleportSingleEntity(entity, targetLevel, x, y, z, yaw, pitch);
        if (teleportedRoot == null) {
            if (!(entity instanceof ServerPlayer)) {
                entity.setDeltaMovement(preservedVelocity);
            }
            return;
        }

        applyPostTeleportKinematics(teleportedRoot, velocityAfterTeleport, yawAfterTeleport, pitchAfterTeleport);
        long destGameTime = targetLevel.getGameTime();
        PortalCrossingTracker.recordCrossingCompleted(teleportedRoot.getUUID(), targetLevel.dimension(), targetEntry.pos(), destGameTime);

        List<Entity> allTeleported = new ArrayList<>();
        allTeleported.add(teleportedRoot);
        teleportAndRemountChildren(passengerTree, teleportedRoot, targetLevel, x, y, z, velocityAfterTeleport, portalTransform, targetEntry.pos(), destGameTime, allTeleported);

        for (Entity moved : allTeleported) {
            if (moved instanceof Mob teleportedMob) {
                PortalPursuitTracker.onMobTeleported(targetLevel, teleportedMob);
            }
        }

        ModSounds.playAt(targetLevel, targetEntry.pos(), ModSounds.PORTAL_ENTITY_GOING_THROUGH, 0.9F, 1.05F);
        if (primaryPlayer != null) {
            core.onPlayerPassedThrough(now);
        } else {
            core.onEntityPassed(now);
        }
        if (targetLevel.getBlockEntity(targetEntry.pos()) instanceof PortalCoreBlockEntity targetCore) {
            targetCore.onEntityPassed(targetLevel.getGameTime());
        }

        // Apply instability damage/debuffs to the post-teleport LivingEntity instance(s) in targetLevel
        // (fixing stale pre-teleport entity reference in cross-dimension transfers).
        for (Entity moved : allTeleported) {
            if (!(moved instanceof LivingEntity livingEntity)) continue;
            if (unstableDamageRoll) {
                livingEntity.hurt(targetLevel.damageSources().magic(), UNSTABLE_DAMAGE_AMOUNT);
                instabilityTriggered = true;
            }
            if (instabilityTriggered) {
                applyUnstableDebuffs(livingEntity);
            }
        }

        if (primaryPlayer != null && !pursuingMobIds.isEmpty()) {
            markPursuingMobsForPortalFollow(sourceLevel, fieldPos, primaryPlayer, pursuingMobIds);
        }

        if (isRift) {
            PortalRiftHelper.handleRiftArrival(targetLevel, targetEntry.pos());
        }
    }

    private static PassengerNode captureAndDetachPassengers(Entity root) {
        List<Entity> direct = new ArrayList<>(root.getPassengers());
        List<PassengerNode> children = new ArrayList<>(direct.size());
        for (Entity passenger : direct) {
            PassengerNode childNode = captureAndDetachPassengers(passenger);
            passenger.stopRiding();
            children.add(childNode);
        }
        return new PassengerNode(root, root.getYRot(), root.getXRot(), children);
    }

    private static void sendSeamlessSwitchHints(PassengerNode node,
                                                net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> targetDim,
                                                BlockPos targetCorePos,
                                                double x,
                                                double y,
                                                double z,
                                                float rootYaw,
                                                float rootPitch,
                                                Vec3 velocity,
                                                @org.jetbrains.annotations.Nullable PortalTransform transform) {
        if (node.entity() instanceof ServerPlayer player) {
            float playerYaw = transform != null ? transform.transformYaw(node.originalYaw()) : rootYaw;
            SeamlessNetwork.sendToPlayer(
                    player,
                    new SeamlessSwitchHintPayload(targetDim, targetCorePos, x, y, z, playerYaw, node.originalPitch(), velocity)
            );
        }
        for (PassengerNode child : node.children()) {
            sendSeamlessSwitchHints(child, targetDim, targetCorePos, x, y, z, rootYaw, rootPitch, velocity, transform);
        }
    }

    private static @org.jetbrains.annotations.Nullable Entity teleportSingleEntity(Entity entity,
                                                                                   ServerLevel targetLevel,
                                                                                   double x,
                                                                                   double y,
                                                                                   double z,
                                                                                   float yaw,
                                                                                   float pitch) {
        UUID entityId = entity.getUUID();
        boolean teleported;
        if (entity instanceof ServerPlayer player) {
            //? if >=1.21.11 {
            /*teleported = player.teleportTo(targetLevel, x, y, z, Set.<RelativeMovement>of(), yaw, pitch, true);
            *///?} else {
            teleported = player.teleportTo(targetLevel, x, y, z, Set.<RelativeMovement>of(), yaw, pitch);
            //?}
        } else {
            //? if >=1.21.11 {
            /*teleported = entity.teleportTo(targetLevel, x, y, z, Set.<RelativeMovement>of(), yaw, pitch, true);
            *///?} else {
            teleported = entity.teleportTo(targetLevel, x, y, z, Set.<RelativeMovement>of(), yaw, pitch);
            //?}
        }
        if (!teleported) {
            return null;
        }
        Entity resolved = targetLevel.getEntity(entityId);
        if (resolved == null && entity.level() == targetLevel) {
            resolved = entity;
        }
        return resolved;
    }

    private static void teleportAndRemountChildren(PassengerNode parentNode,
                                                   Entity teleportedParent,
                                                   ServerLevel targetLevel,
                                                   double x,
                                                   double y,
                                                   double z,
                                                   Vec3 velocity,
                                                   @org.jetbrains.annotations.Nullable PortalTransform transform,
                                                   BlockPos targetCorePos,
                                                   long destGameTime,
                                                   List<Entity> allTeleported) {
        for (PassengerNode child : parentNode.children()) {
            float childYaw = transform != null ? transform.transformYaw(child.originalYaw()) : child.originalYaw();
            float childPitch = child.originalPitch();
            if (!(child.entity() instanceof ServerPlayer)) {
                child.entity().setDeltaMovement(velocity);
            }
            Entity movedChild = teleportSingleEntity(child.entity(), targetLevel, x, y, z, childYaw, childPitch);
            if (movedChild == null) {
                continue;
            }
            applyPostTeleportKinematics(movedChild, velocity, childYaw, childPitch);
            PortalCrossingTracker.recordCrossingCompleted(movedChild.getUUID(), targetLevel.dimension(), targetCorePos, destGameTime);
            //? if >=1.21.11 {
            /*movedChild.startRiding(teleportedParent, true, true);
            *///?} else {
            movedChild.startRiding(teleportedParent, true);
            //?}
            allTeleported.add(movedChild);
            teleportAndRemountChildren(child, movedChild, targetLevel, x, y, z, velocity, transform, targetCorePos, destGameTime, allTeleported);
        }
    }

    private static void applyPostTeleportKinematics(Entity entity, Vec3 velocity, float yaw, float pitch) {
        entity.setDeltaMovement(velocity);
        entity.setYRot(yaw);
        entity.setXRot(pitch);
        if (entity instanceof LivingEntity living) {
            living.setYHeadRot(yaw);
            living.setYBodyRot(yaw);
        }
        entity.hasImpulse = true;
        if (!(entity instanceof ServerPlayer)) {
            // Forces ServerEntity#sendChanges to broadcast ClientboundSetEntityMotionPacket even
            // when tickCount == 0 (newly created cross-dimension entity in 1.21.1 and 1.21.11).
            entity.hurtMarked = true;
        }
    }

    private record PassengerNode(Entity entity, float originalYaw, float originalPitch, List<PassengerNode> children) {}

    private static void showBlockedDirectionMessage(ServerPlayer player) {
        player.displayClientMessage(
                Component.translatable("message.universegate.blocked_direction").withStyle(ChatFormatting.RED),
                true
        );
    }

    private static void keepPortalOpenFromDestinationContact(ServerLevel destinationLevel,
                                                             PortalCoreBlockEntity destinationCore,
                                                             long now) {
        destinationCore.onEntityPassed(now);

        UUID sourcePortalId = destinationCore.getTargetPortalId();
        UUID connectionId = destinationCore.getConnectionId();
        if (sourcePortalId == null || connectionId == null) return;

        PortalRegistrySavedData registry = PortalRegistrySavedData.get(destinationLevel.getServer());
        PortalRegistrySavedData.PortalEntry sourceEntry = registry.get(sourcePortalId);
        if (sourceEntry == null) return;

        ServerLevel sourceLevel = destinationLevel.getServer().getLevel(sourceEntry.dim());
        if (sourceLevel == null || !sourceLevel.hasChunkAt(sourceEntry.pos())) return;
        if (!(sourceLevel.getBlockEntity(sourceEntry.pos()) instanceof PortalCoreBlockEntity sourceCore)) return;
        if (!sourceCore.isActive()) return;
        if (!connectionId.equals(sourceCore.getConnectionId())) return;

        sourceCore.onEntityPassed(sourceLevel.getGameTime());
    }

    private static Direction resolveSourceInwardNormal(PortalFrameDetector.FrameMatch match,
                                                       BlockPos corePos,
                                                       Entity entity,
                                                       Direction sourceFrontNormal) {
        // 1) Exact approach side tracked by PortalCrossingTracker prior to crossing the plane
        Direction tracked = PortalCrossingTracker.getLastInwardNormal(entity.getUUID(), corePos);
        if (tracked != null) {
            return tracked;
        }

        Direction positiveNormal = match.right().getAxis() == Direction.Axis.X ? Direction.SOUTH : Direction.EAST;

        // 2) Actual server tick displacement (xo/zo), which works for ServerPlayer even when deltaMovement == 0
        double tickDelta = (entity.getX() - entity.xo) * positiveNormal.getStepX()
                + (entity.getZ() - entity.zo) * positiveNormal.getStepZ();
        if (Math.abs(tickDelta) > 0.001D) {
            return tickDelta > 0.0D ? positiveNormal : positiveNormal.getOpposite();
        }

        // 3) Entity velocity (projectiles, minecarts, mobs)
        double axisVelocity = entity.getDeltaMovement().x * positiveNormal.getStepX()
                + entity.getDeltaMovement().z * positiveNormal.getStepZ();
        if (Math.abs(axisVelocity) > 0.001D) {
            return axisVelocity > 0.0D ? positiveNormal : positiveNormal.getOpposite();
        }

        // 4) Position side relative to portal center (pointing INTO the portal from the entity's side)
        double signedOffset = (entity.getX() - (corePos.getX() + 0.5D)) * positiveNormal.getStepX()
                + (entity.getZ() - (corePos.getZ() + 0.5D)) * positiveNormal.getStepZ();
        if (Math.abs(signedOffset) > 0.05D) {
            return signedOffset > 0.0D ? positiveNormal.getOpposite() : positiveNormal;
        }

        // 5) Default: entering from the portal's front (controller) face
        return sourceFrontNormal.getOpposite();
    }

    private static BlockPos findCoreNear(ServerLevel level, BlockPos pos) {
        BlockPos nearestActiveCore = null;
        double nearestActiveDistance = Double.MAX_VALUE;
        BlockPos nearestCore = null;
        double nearestCoreDistance = Double.MAX_VALUE;

        for (int dy = -CORE_SEARCH_Y; dy <= CORE_SEARCH_Y; dy++) {
            for (int dx = -CORE_SEARCH_XZ; dx <= CORE_SEARCH_XZ; dx++) {
                for (int dz = -CORE_SEARCH_XZ; dz <= CORE_SEARCH_XZ; dz++) {
                    BlockPos p = pos.offset(dx, dy, dz);
                    if (!level.getBlockState(p).is(ModBlocks.PORTAL_CORE)) {
                        continue;
                    }

                    double distance = p.distSqr(pos);
                    if (distance < nearestCoreDistance) {
                        nearestCoreDistance = distance;
                        nearestCore = p;
                    }

                    if (distance < nearestActiveDistance
                            && level.getBlockEntity(p) instanceof PortalCoreBlockEntity core
                            && core.isActive()) {
                        nearestActiveDistance = distance;
                        nearestActiveCore = p;
                    }
                }
            }
        }

        if (nearestActiveCore != null) {
            return nearestActiveCore;
        }
        return nearestCore;
    }

    private static PortalKeyboardBlockEntity findKeyboardNear(ServerLevel level, BlockPos corePos, int r) {
        for (int dy = -KEYBOARD_SEARCH_Y; dy <= KEYBOARD_SEARCH_Y; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos p = corePos.offset(dx, dy, dz);
                    var state = level.getBlockState(p);
                    if (!state.is(ModBlocks.PORTAL_KEYBOARD) && !state.is(ModBlocks.PORTAL_NATURAL_KEYBOARD)) continue;
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

    private static List<UUID> collectPursuingMobIds(ServerLevel level, BlockPos corePos, ServerPlayer player) {
        Vec3 portalCenter = new Vec3(corePos.getX() + 0.5D, corePos.getY() + 1.0D, corePos.getZ() + 0.5D);
        AABB searchBox = AABB.ofSize(
                portalCenter,
                PURSUING_MOB_SEARCH_RADIUS_XZ * 2.0D,
                PURSUING_MOB_SEARCH_RADIUS_Y * 2.0D,
                PURSUING_MOB_SEARCH_RADIUS_XZ * 2.0D
        );

        List<Mob> candidates = level.getEntitiesOfClass(
                Mob.class,
                searchBox,
                mob -> isPursuingPlayerNearPortal(mob, player, portalCenter)
        );
        if (candidates.isEmpty()) {
            return List.of();
        }

        candidates.sort((first, second) -> Double.compare(
                first.distanceToSqr(portalCenter.x, portalCenter.y, portalCenter.z),
                second.distanceToSqr(portalCenter.x, portalCenter.y, portalCenter.z)
        ));

        List<UUID> ids = new ArrayList<>(candidates.size());
        for (int i = 0; i < candidates.size(); i++) {
            ids.add(candidates.get(i).getUUID());
        }
        return ids;
    }

    private static boolean isPursuingPlayerNearPortal(Mob mob, ServerPlayer player, Vec3 portalCenter) {
        if (!mob.isAlive() || mob.isPassenger()) return false;
        if (mob.getTarget() != player) return false;
        return mob.distanceToSqr(portalCenter.x, portalCenter.y, portalCenter.z) <= PURSUING_MOB_MAX_PORTAL_DISTANCE_SQR;
    }

    private static void markPursuingMobsForPortalFollow(ServerLevel sourceLevel,
                                                        BlockPos fieldPos,
                                                        ServerPlayer player,
                                                        List<UUID> pursuingMobIds) {
        for (UUID mobId : pursuingMobIds) {
            Entity candidate = sourceLevel.getEntity(mobId);
            if (!(candidate instanceof Mob mob)) continue;
            if (!mob.isAlive()) continue;
            if (mob.getTarget() != player) continue;
            PortalPursuitTracker.trackMobTowardPortalField(sourceLevel, mob, fieldPos, player.getUUID());
        }
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

    private static boolean shouldAlignYawToVelocity(Entity entity) {
        return entity instanceof Projectile
                || entity instanceof AbstractArrow
                || entity instanceof AbstractMinecart
                || entity instanceof ThrownEnderpearl;
    }

}
