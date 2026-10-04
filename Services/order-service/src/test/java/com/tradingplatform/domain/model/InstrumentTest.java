package com.tradingplatform.domain.model;

import com.tradingplatform.domain.enums.AssetClass;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstrumentTest {

    @Test
    void tradableInstrumentIsTradableAndDelistedInstrumentIsNotButStillResolves() {
        Instrument acme = Instrument.tradable(1L, "ACME", "Acme Corp", AssetClass.EQUITY, "USD");
        Instrument delisted = Instrument.delisted(2L, "OLDCO", "Old Company Inc", AssetClass.EQUITY, "USD");

        assertAll(
            () -> assertTrue(acme.isTradable(), "a tradable instrument reports itself as tradable"),
            () -> assertFalse(delisted.isTradable(), "a delisted instrument reports itself as not tradable"),
            () -> assertEquals("OLDCO", delisted.getSymbol(), "a delisted instrument still resolves its symbol")
        );
    }
}