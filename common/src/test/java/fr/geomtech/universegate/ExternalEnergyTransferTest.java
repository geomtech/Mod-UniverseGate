package fr.geomtech.universegate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExternalEnergyTransferTest {

    private static final int CAP = EnergyCondenserBlockEntity.CAPACITY;
    private static final int LIMIT = ExternalEnergyTransfer.MAX_TRANSFER_PER_CALL;

    @Test
    void insertIsLimitedByRequestFreeSpaceAndRate() {
        assertEquals(100, ExternalEnergyTransfer.insertable(100, 0, CAP));
        assertEquals(LIMIT, ExternalEnergyTransfer.insertable(Long.MAX_VALUE, 0, CAP));
        assertEquals(50, ExternalEnergyTransfer.insertable(500, CAP - 50, CAP));
        assertEquals(0, ExternalEnergyTransfer.insertable(500, CAP, CAP));
    }

    @Test
    void extractIsLimitedByRequestStoredAndRate() {
        assertEquals(100, ExternalEnergyTransfer.extractable(100, CAP));
        assertEquals(LIMIT, ExternalEnergyTransfer.extractable(Long.MAX_VALUE, CAP));
        assertEquals(30, ExternalEnergyTransfer.extractable(500, 30));
        assertEquals(0, ExternalEnergyTransfer.extractable(500, 0));
    }

    @Test
    void nonPositiveRequestsMoveNothing() {
        assertEquals(0, ExternalEnergyTransfer.insertable(0, 0, CAP));
        assertEquals(0, ExternalEnergyTransfer.insertable(-5, 0, CAP));
        assertEquals(0, ExternalEnergyTransfer.extractable(0, CAP));
        assertEquals(0, ExternalEnergyTransfer.extractable(-5, CAP));
    }
}
