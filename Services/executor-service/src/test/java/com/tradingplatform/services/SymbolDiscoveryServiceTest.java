package com.tradingplatform.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.tradingplatform.mappers.InstrumentMapper;

@DisplayName("SymbolDiscoveryService Tests")
class SymbolDiscoveryServiceTest {

    @Mock
    private InstrumentMapper instrumentMapper;

    private SymbolDiscoveryService symbolDiscoveryService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        symbolDiscoveryService = new SymbolDiscoveryService(instrumentMapper);
    }

    @Test
    @DisplayName("Test 1: Returns empty set when there are no instruments")
    void testReturnsEmptySetWhenNoInstruments() {
        when(instrumentMapper.findAllSymbols()).thenReturn(new ArrayList<>());
        
        Set<String> symbols = symbolDiscoveryService.getActiveSymbols();
        
        assertTrue(symbols.isEmpty(), "Should return empty set when there are no instruments");
    }

    @Test
    @DisplayName("Test 2: Returns every instrument symbol, held or not")
    void testReturnsEveryInstrumentSymbol() {
        List<String> mockSymbols = new ArrayList<>();
        mockSymbols.add("AAPL");
        mockSymbols.add("MSFT");
        mockSymbols.add("GOOGL");
        
        when(instrumentMapper.findAllSymbols()).thenReturn(mockSymbols);
        
        Set<String> symbols = symbolDiscoveryService.getActiveSymbols();
        
        assertEquals(3, symbols.size(), "Should return all instrument symbols");
        assertTrue(symbols.contains("AAPL"), "Should contain AAPL");
        assertTrue(symbols.contains("MSFT"), "Should contain MSFT");
        assertTrue(symbols.contains("GOOGL"), "Should contain GOOGL");
    }

    @Test
    @DisplayName("Test 3: Handles mapper exception gracefully")
    void testHandlesMapperExceptionGracefully() {
        when(instrumentMapper.findAllSymbols())
            .thenThrow(new RuntimeException("Database connection error"));
        
        // Should not throw, but return empty set
        Set<String> symbols = assertDoesNotThrow(() -> symbolDiscoveryService.getActiveSymbols(),
            "Should handle mapper exceptions gracefully");
        
        assertTrue(symbols.isEmpty(), "Should return empty set on exception");
    }

    @Test
    @DisplayName("Test 4: Returns set (removes duplicates if any)")
    void testReturnsSetRemovesDuplicates() {
        List<String> mockSymbols = new ArrayList<>();
        mockSymbols.add("AAPL");
        mockSymbols.add("MSFT");
        mockSymbols.add("AAPL"); // Duplicate
        
        when(instrumentMapper.findAllSymbols()).thenReturn(mockSymbols);
        
        Set<String> symbols = symbolDiscoveryService.getActiveSymbols();
        
        // Set should handle duplicates naturally
        assertEquals(2, symbols.size(), "Should have 2 unique symbols");
    }

    @Test
    @DisplayName("Test 5: Calls mapper exactly once per invocation")
    void testCallsMapperOncePerInvocation() {
        when(instrumentMapper.findAllSymbols()).thenReturn(new ArrayList<>());
        
        symbolDiscoveryService.getActiveSymbols();
        
        verify(instrumentMapper, times(1)).findAllSymbols();
    }
}
