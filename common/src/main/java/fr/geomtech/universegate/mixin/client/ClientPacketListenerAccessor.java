package fr.geomtech.universegate.mixin.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ClientPacketListener.class)
public interface ClientPacketListenerAccessor {

    @Accessor("level")
    ClientLevel universegate$getLevel();

    @Accessor("level")
    void universegate$setLevel(ClientLevel level);

    @Accessor("levelData")
    ClientLevel.ClientLevelData universegate$getLevelData();

    @Accessor("levelData")
    void universegate$setLevelData(ClientLevel.ClientLevelData levelData);

    @Invoker("updateLevelChunk")
    void universegate$updateLevelChunk(int x, int z, ClientboundLevelChunkPacketData data);

    @Invoker("queueLightRemoval")
    void universegate$queueLightRemoval(ClientboundForgetLevelChunkPacket packet);

    //? if >=1.21.11 {
    /*@Invoker("applyLightData")
    void universegate$applyLightData(int x, int z, ClientboundLightUpdatePacketData data, boolean force);
    *///?} else {
    @Invoker("applyLightData")
    void universegate$applyLightData(int x, int z, ClientboundLightUpdatePacketData data);
    //?}
}
