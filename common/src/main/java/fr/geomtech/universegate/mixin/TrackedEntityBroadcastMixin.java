package fr.geomtech.universegate.mixin;

import fr.geomtech.universegate.seamless.server.ServerPortalStreamManager;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if >=1.21.11 {
/*import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Predicate;
*///?}

@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public abstract class TrackedEntityBroadcastMixin {

    @Shadow
    @Final
    Entity entity;

    @Shadow
    @Final
    ServerEntity serverEntity;

    //? if >=1.21.11 {
    /*@Inject(method = "sendToTrackingPlayers", at = @At("HEAD"))
    private void universegate$onSendToTrackingPlayers(Packet<? super ClientGamePacketListener> packet, CallbackInfo ci) {
        if (this.entity.level() instanceof ServerLevel serverLevel) {
            ServerPortalStreamManager.onTrackedEntityPacket(serverLevel, this.entity, this.serverEntity, packet);
        }
    }

    @Inject(method = "sendToTrackingPlayersFiltered", at = @At("HEAD"))
    private void universegate$onSendToTrackingPlayersFiltered(Packet<? super ClientGamePacketListener> packet,
                                                              Predicate<ServerPlayer> predicate,
                                                              CallbackInfo ci) {
        if (this.entity.level() instanceof ServerLevel serverLevel) {
            ServerPortalStreamManager.onTrackedEntityPacket(serverLevel, this.entity, this.serverEntity, packet);
        }
    }
    *///?} else {
    @Inject(method = "broadcast", at = @At("HEAD"))
    private void universegate$onBroadcast(Packet<?> packet, CallbackInfo ci) {
        if (this.entity.level() instanceof ServerLevel serverLevel) {
            ServerPortalStreamManager.onTrackedEntityPacket(serverLevel, this.entity, this.serverEntity, packet);
        }
    }
    //?}
}
