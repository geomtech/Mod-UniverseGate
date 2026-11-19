package fr.geomtech.universegate;

/**
 * Pure portal energy cost formulas, free of any Minecraft dependency so they
 * can be unit tested without bootstrapping the game.
 */
public final class PortalEnergyCosts {

    public static final int OPEN_BASE_COST = 1200;
    public static final int OPEN_MIN_COST = 900;
    public static final double OPEN_DISTANCE_COST_PER_BLOCK = 0.05D;
    public static final int OPEN_CROSS_DIMENSION_BONUS = 700;
    public static final int OPEN_RIFT_DIMENSION_BONUS = 900;
    public static final int ACTIVE_COST_PER_SECOND = 120;
    public static final int UNSTABLE_COST_BONUS_PERCENT = 50;

    private PortalEnergyCosts() {
    }

    /**
     * Energy (EU) needed to open a portal.
     *
     * @param distanceSquared squared distance between the two portal cores, in blocks
     * @param crossDimension  true if source and target are in different dimensions
     * @param involvesRift    true if source or target is in The Rift
     */
    public static int openCost(double distanceSquared, boolean crossDimension, boolean involvesRift) {
        double distance = Math.sqrt(Math.max(0.0D, distanceSquared));
        int distanceBlocks = (int) Math.ceil(distance);
        int distanceCost = (int) Math.ceil(distanceBlocks * OPEN_DISTANCE_COST_PER_BLOCK);

        int dimensionCost = 0;
        if (crossDimension) {
            dimensionCost += OPEN_CROSS_DIMENSION_BONUS;
        }
        if (involvesRift) {
            dimensionCost += OPEN_RIFT_DIMENSION_BONUS;
        }

        int total = OPEN_BASE_COST + distanceCost + dimensionCost;
        return Math.max(OPEN_MIN_COST, total);
    }

    /** Energy (EU) drained each second while a portal stays open. */
    public static int activeCostPerSecond(boolean unstable) {
        if (!unstable) return ACTIVE_COST_PER_SECOND;

        double factor = 1.0D + (UNSTABLE_COST_BONUS_PERCENT / 100.0D);
        return (int) Math.ceil(ACTIVE_COST_PER_SECOND * factor);
    }
}
