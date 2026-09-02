package com.tradingplatform.domain;

import com.tradingplatform.domain.enums.AssetClass;
import com.tradingplatform.domain.model.Instrument;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstrumentTest {

    @Test
    void tradableInstrumentIsTradable() {
        Instrument acme = Instrument.tradable(1L, "ACME", "Acme Corp", AssetClass.EQUITY, "USD");
        assertTrue(acme.isTradable());
    }

    @Test
    void delistedInstrumentIsNotTradableButStillResolves() {
        Instrument delisted = Instrument.delisted(2L, "OLDCO", "Old Company Inc", AssetClass.EQUITY, "USD");
        assertFalse(delisted.isTradable());
        assertTrue(delisted.getSymbol().equals("OLDCO"));
    }
}
