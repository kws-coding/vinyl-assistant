package dev.vinyl.poc.vision;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ModelPriceTest {

    private final ModelPrice price = new ModelPrice(new BigDecimal("2.00"), new BigDecimal("10.00"));

    @Test
    void costsInputAndOutputSeparately() {
        assertEquals(new BigDecimal("0.048834"), price.cost(14417, 2000));
    }

    @Test
    void zeroTokensCostNothing() {
        assertEquals(0, price.cost(0, 0).signum());
    }
}
