package com.tradingplatform.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EnumTest {

    @Test
    void errorCodeRoundTripsThroughItsCode() {
        for (ErrorCode code : ErrorCode.values()) {
            assertEquals(code, ErrorCode.fromCode(code.code()));
            assertEquals(code.code(), code.toString());
        }
        assertEquals("PREF-422", ErrorCode.INVALID_PREFERENCE.code());
    }

    @Test
    void unknownErrorCodeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> ErrorCode.fromCode("NOPE-000"));
    }

    @Test
    void onlyNewOrderStatusIsNonTerminal() {
        assertFalse(OrderStatus.NEW.isTerminal());
        assertTrue(OrderStatus.FILLED.isTerminal());
        assertTrue(OrderStatus.REJECTED.isTerminal());
        assertTrue(OrderStatus.CANCELLED.isTerminal());
    }
}
