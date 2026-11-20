package fr.geomtech.universegate;

/**
 * Rules for exchanging energy with other mods (NeoForge energy capability / Team Reborn Energy on
 * Fabric), e.g. Stellaris cables and generators. Pure logic, no Minecraft classes, so it can be tested.
 *
 * <p>1 EU of UniverseGate = 1 FE (Forge/NeoForge Energy) = 1 E (Team Reborn Energy).
 */
public final class ExternalEnergyTransfer {

    /** Maximum amount a single block can push into or pull from a condenser in one call (one tick). */
    public static final int MAX_TRANSFER_PER_CALL = 1000;

    private ExternalEnergyTransfer() {}

    /** How much can be inserted: limited by the request, the free space and the per-call limit. */
    public static int insertable(long requested, int stored, int capacity) {
        if (requested <= 0L) return 0;
        long free = Math.max(0L, (long) capacity - Math.max(0, stored));
        return (int) Math.min(Math.min(requested, free), MAX_TRANSFER_PER_CALL);
    }

    /** How much can be extracted: limited by the request, the stored energy and the per-call limit. */
    public static int extractable(long requested, int stored) {
        if (requested <= 0L) return 0;
        return (int) Math.min(Math.min(requested, Math.max(0, stored)), MAX_TRANSFER_PER_CALL);
    }
}
