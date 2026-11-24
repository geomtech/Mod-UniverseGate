package fr.geomtech.universegate.mixin.client;

import fr.geomtech.universegate.seamless.client.ClientRemoteWorldManager;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if <1.21.11 {
import net.minecraft.world.entity.player.Player;
//?}

/**
 * Strictly isolates remote seamless {@link ClientLevel} instances from the shared
 * {@code ParticleEngine} and {@code SoundManager} so particles and sounds from dimension B never
 * leak into dimension A at overlapping coordinates.
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelIsolationMixin {

    @Inject(method = "addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V", at = @At("HEAD"), cancellable = true)
    private void universegate$blockRemoteParticle1(ParticleOptions options, double x, double y, double z,
                                                   double xd, double yd, double zd, CallbackInfo ci) {
        if (ClientRemoteWorldManager.isRemoteLevel((ClientLevel) (Object) this)) {
            ci.cancel();
        }
    }

    //? if >=1.21.11 {
    /*@Inject(method = "addParticle(Lnet/minecraft/core/particles/ParticleOptions;ZZDDDDDD)V", at = @At("HEAD"), cancellable = true)
    private void universegate$blockRemoteParticle2(ParticleOptions options, boolean overrideLimiter, boolean decreaseShown,
                                                   double x, double y, double z, double xd, double yd, double zd, CallbackInfo ci) {
        if (ClientRemoteWorldManager.isRemoteLevel((ClientLevel) (Object) this)) {
            ci.cancel();
        }
    }
    *///?} else {
    @Inject(method = "addParticle(Lnet/minecraft/core/particles/ParticleOptions;ZDDDDDD)V", at = @At("HEAD"), cancellable = true)
    private void universegate$blockRemoteParticle2(ParticleOptions options, boolean overrideLimiter,
                                                   double x, double y, double z, double xd, double yd, double zd, CallbackInfo ci) {
        if (ClientRemoteWorldManager.isRemoteLevel((ClientLevel) (Object) this)) {
            ci.cancel();
        }
    }
    //?}

    @Inject(method = "addAlwaysVisibleParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V", at = @At("HEAD"), cancellable = true)
    private void universegate$blockRemoteAlwaysParticle1(ParticleOptions options, double x, double y, double z,
                                                         double xd, double yd, double zd, CallbackInfo ci) {
        if (ClientRemoteWorldManager.isRemoteLevel((ClientLevel) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "addAlwaysVisibleParticle(Lnet/minecraft/core/particles/ParticleOptions;ZDDDDDD)V", at = @At("HEAD"), cancellable = true)
    private void universegate$blockRemoteAlwaysParticle2(ParticleOptions options, boolean overrideLimiter,
                                                         double x, double y, double z, double xd, double yd, double zd, CallbackInfo ci) {
        if (ClientRemoteWorldManager.isRemoteLevel((ClientLevel) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "playLocalSound(DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZ)V", at = @At("HEAD"), cancellable = true)
    private void universegate$blockRemoteLocalSound1(double x, double y, double z, SoundEvent sound, SoundSource source,
                                                     float volume, float pitch, boolean distanceDelay, CallbackInfo ci) {
        if (ClientRemoteWorldManager.isRemoteLevel((ClientLevel) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "playLocalSound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V", at = @At("HEAD"), cancellable = true)
    private void universegate$blockRemoteLocalSound2(Entity entity, SoundEvent sound, SoundSource source,
                                                     float volume, float pitch, CallbackInfo ci) {
        if (ClientRemoteWorldManager.isRemoteLevel((ClientLevel) (Object) this)) {
            ci.cancel();
        }
    }

    //? if >=1.21.11 {
    /*@Inject(method = "playSeededSound(Lnet/minecraft/world/entity/Entity;DDDLnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V", at = @At("HEAD"), cancellable = true)
    private void universegate$blockRemoteSeededSound1(@Nullable Entity player, double x, double y, double z,
                                                      Holder<SoundEvent> sound, SoundSource source, float volume, float pitch, long seed, CallbackInfo ci) {
        if (ClientRemoteWorldManager.isRemoteLevel((ClientLevel) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "playSeededSound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V", at = @At("HEAD"), cancellable = true)
    private void universegate$blockRemoteSeededSound2(@Nullable Entity player, Entity entity,
                                                      Holder<SoundEvent> sound, SoundSource source, float volume, float pitch, long seed, CallbackInfo ci) {
        if (ClientRemoteWorldManager.isRemoteLevel((ClientLevel) (Object) this)) {
            ci.cancel();
        }
    }
    *///?} else {
    @Inject(method = "playSeededSound(Lnet/minecraft/world/entity/player/Player;DDDLnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V", at = @At("HEAD"), cancellable = true)
    private void universegate$blockRemoteSeededSound1(@Nullable Player player, double x, double y, double z,
                                                      Holder<SoundEvent> sound, SoundSource source, float volume, float pitch, long seed, CallbackInfo ci) {
        if (ClientRemoteWorldManager.isRemoteLevel((ClientLevel) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "playSeededSound(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V", at = @At("HEAD"), cancellable = true)
    private void universegate$blockRemoteSeededSound2(@Nullable Player player, Entity entity,
                                                      Holder<SoundEvent> sound, SoundSource source, float volume, float pitch, long seed, CallbackInfo ci) {
        if (ClientRemoteWorldManager.isRemoteLevel((ClientLevel) (Object) this)) {
            ci.cancel();
        }
    }
    //?}

    // Prevent remote ClientLevel instances (which share minecraft.levelRenderer) from dirtying
    // active-world chunk sections or leaking block-breaking overlays/global events into mc.levelRenderer.
    @Inject(method = "sendBlockUpdated", at = @At("HEAD"), cancellable = true)
    private void universegate$blockRemoteSendBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState, int flags, CallbackInfo ci) {
        if (ClientRemoteWorldManager.isRemoteLevel((ClientLevel) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "setBlocksDirty", at = @At("HEAD"), cancellable = true)
    private void universegate$blockRemoteSetBlocksDirty(BlockPos pos, BlockState oldState, BlockState newState, CallbackInfo ci) {
        if (ClientRemoteWorldManager.isRemoteLevel((ClientLevel) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "setSectionDirtyWithNeighbors", at = @At("HEAD"), cancellable = true)
    private void universegate$blockRemoteSetSectionDirty(int sectionX, int sectionY, int sectionZ, CallbackInfo ci) {
        if (ClientRemoteWorldManager.isRemoteLevel((ClientLevel) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "destroyBlockProgress", at = @At("HEAD"), cancellable = true)
    private void universegate$blockRemoteDestroyProgress(int breakerId, BlockPos pos, int progress, CallbackInfo ci) {
        if (ClientRemoteWorldManager.isRemoteLevel((ClientLevel) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "globalLevelEvent", at = @At("HEAD"), cancellable = true)
    private void universegate$blockRemoteGlobalLevelEvent(int id, BlockPos pos, int data, CallbackInfo ci) {
        if (ClientRemoteWorldManager.isRemoteLevel((ClientLevel) (Object) this)) {
            ci.cancel();
        }
    }
}
