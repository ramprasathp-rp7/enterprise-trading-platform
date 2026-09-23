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

import com.tradingplatform.mappers.PositionMapper;

@DisplayName("SymbolDiscoveryService Tests")
class SymbolDiscoveryServiceTest {

    @Mock
    private PositionMapper positionMapper;

    private SymbolDiscoveryService symbolDiscoveryService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        symbolDiscoveryService = new SymbolDiscoveryService(positionMapper);
    }

    @Test
    @DisplayName("Test 1: Returns empty set when no active positions")
    void testReturnsEmptySetWhenNoActivePositions() {
        when(positionMapper.getDistinctSymbolsWithQuantity()).thenReturn(new ArrayList<>());
        
        Set<String> symbols = symbolDiscoveryService.getActiveSymbols();
        
        assertTrue(symbols.isEmpty(), "Should return empty set when no active positions");
    }

    @Test
    @DisplayName("Test 2: Returns symbols when active positions exist")
    void testReturnsSymbolsWhenActivePositionsExist() {
        List<String> mockSymbols = new ArrayList<>();
        mockSymbols.add("AAPL");
        mockSymbols.add("MSFT");
        mockSymbols.add("GOOGL");
        
        when(positionMapper.getDistinctSymbolsWithQuantity()).thenReturn(mockSymbols);
        
        Set<String> symbols = symbolDiscoveryService.getActiveSymbols();
        
        assertEquals(3, symbols.size(), "Should return all active symbols");
        assertTrue(symbols.contains("AAPL"), "Should contain AAPL");
        assertTrue(symbols.contains("MSFT"), "Should contain MSFT");
        assertTrue(symbols.contains("GOOGL"), "Should contain GOOGL");
    }

    @Test
    @DisplayName("Test 3: Handles mapper exception gracefully")
    void testHandlesMapperExceptionGracefully() {
        when(positionMapper.getDistinctSymbolsWithQuantity())
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
        
        when(positionMapper.getDistinctSymbolsWithQuantity()).thenReturn(mockSymbols);
        
        Set<String> symbols = symbolDiscoveryService.getActiveSymbols();
        
        // Set should handle duplicates naturally
        assertEquals(2, symbols.size(), "Should have 2 unique symbols");
    }

    @Test
    @DisplayName("Test 5: Calls mapper exactly once per invocation")
    void testCallsMapperOncePerInvocation() {
        when(positionMapper.getDistinctSymbolsWithQuantity()).thenReturn(new ArrayList<>());
        
        symbolDiscoveryService.getActiveSymbols();
        
        verify(positionMapper, times(1)).getDistinctSymbolsWithQuantity();
    }
}
