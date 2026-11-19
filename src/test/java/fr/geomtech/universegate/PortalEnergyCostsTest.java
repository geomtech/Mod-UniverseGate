package fr.geomtech.universegate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortalEnergyCostsTest {

    private static double sq(double distance) {
        return distance * distance;
    }

    @Nested
    @DisplayName("Coût d'ouverture selon la distance")
    class OpenCostByDistance {

        @Test
        void portalAtSamePositionCostsBaseOnly() {
            assertEquals(1200, PortalEnergyCosts.openCost(0.0D, false, false));
        }

        @ParameterizedTest(name = "{0} blocs -> {1} EU")
        @CsvSource({
                "1,        1201",
                "10,       1201",
                "20,       1201",
                "21,       1202",
                "100,      1205",
                "1000,     1250",
                "10000,    1700",
                "30000000, 1501200"
        })
        void addsOneEnergyUnitPerTwentyBlocksRoundedUp(int distanceBlocks, int expectedCost) {
            assertEquals(expectedCost, PortalEnergyCosts.openCost(sq(distanceBlocks), false, false));
        }

        @Test
        void fractionalDistanceIsRoundedUpToTheNextBlock() {
            // Diagonal of 1x1 => sqrt(2) ~ 1.41 blocks, counted as 2 blocks => ceil(0.1) = 1 EU.
            assertEquals(1201, PortalEnergyCosts.openCost(2.0D, false, false));
            // 20.5 blocks counts as 21 blocks => ceil(1.05) = 2 EU.
            assertEquals(1202, PortalEnergyCosts.openCost(sq(20.5D), false, false));
        }

        @Test
        void costNeverDecreasesWithDistance() {
            int previous = PortalEnergyCosts.openCost(0.0D, false, false);
            for (int d = 1; d <= 5000; d++) {
                int cost = PortalEnergyCosts.openCost(sq(d), false, false);
                assertTrue(cost >= previous, "cost decreased at distance " + d);
                previous = cost;
            }
        }

        @Test
        void negativeSquaredDistanceIsTreatedAsZero() {
            assertEquals(1200, PortalEnergyCosts.openCost(-4.0D, false, false));
        }

        @Test
        void costIsNeverBelowMinimum() {
            assertTrue(PortalEnergyCosts.openCost(0.0D, false, false) >= PortalEnergyCosts.OPEN_MIN_COST);
        }
    }

    @Nested
    @DisplayName("Bonus de dimension")
    class DimensionBonuses {

        @Test
        void crossDimensionAddsBonus() {
            assertEquals(1900, PortalEnergyCosts.openCost(0.0D, true, false));
        }

        @Test
        void riftAddsBonusEvenInsideTheSameDimension() {
            assertEquals(2100, PortalEnergyCosts.openCost(0.0D, false, true));
        }

        @Test
        void crossDimensionToRiftStacksBothBonuses() {
            assertEquals(2800, PortalEnergyCosts.openCost(0.0D, true, true));
        }

        @Test
        void distanceAndDimensionBonusesAdd() {
            assertEquals(1200 + 5 + 700 + 900, PortalEnergyCosts.openCost(sq(100), true, true));
        }
    }

    @Nested
    @DisplayName("Coût de maintien du portail ouvert")
    class ActiveCost {

        @Test
        void stablePortalCostsBaseRate() {
            assertEquals(120, PortalEnergyCosts.activeCostPerSecond(false));
        }

        @Test
        void unstablePortalCostsFiftyPercentMore() {
            assertEquals(180, PortalEnergyCosts.activeCostPerSecond(true));
        }
    }
}
