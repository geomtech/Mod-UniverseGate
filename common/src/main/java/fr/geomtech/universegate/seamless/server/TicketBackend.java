package fr.geomtech.universegate.seamless.server;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Abstraction over Minecraft chunk ticket acquisition and release so
 * {@link RemoteChunkTicketController} can be unit-tested without booting a server.
 */
public interface TicketBackend {

    void addTicket(ResourceKey<Level> dim, int chunkX, int chunkZ, int radius);

    void removeTicket(ResourceKey<Level> dim, int chunkX, int chunkZ, int radius);
}
