package com.tradingplatform.api.services;

import com.tradingplatform.api.mappers.AccountHistoryMapper;
import com.tradingplatform.api.mappers.AccountMapper;
import com.tradingplatform.domain.dto.AccountHistoryResponse;
import com.tradingplatform.domain.enums.AccountStatus;
import com.tradingplatform.domain.exception.AccountNotFoundException;
import com.tradingplatform.domain.exception.InvalidHistoryRequestException;
import com.tradingplatform.domain.model.Account;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountHistoryService Tests")
class AccountHistoryServiceTest {

    private static final Instant OCT_1 = Instant.parse("2026-10-01T00:00:00Z");
    private static final Instant OCT_2 = Instant.parse("2026-10-02T00:00:00Z");

    @Mock
    private AccountMapper accountMapper;

    @Mock
    private AccountHistoryMapper accountHistoryMapper;

    @InjectMocks
    private AccountHistoryService service;

    private final Account account = new Account(
            1L, "ACC-000001", "Priya Menon",
            BigDecimal.valueOf(24500.75), "USD", AccountStatus.ACTIVE,
            0, Instant.now(), Instant.now()
    );

    private final AccountHistoryResponse snapshot = new AccountHistoryResponse(
            1L, Instant.parse("2026-10-01T23:59:59Z"), new BigDecimal("24500.75"), new BigDecimal("90260.75"));

    @Test
    @DisplayName("Should return history for an existing account")
    void returnsHistory() {
        when(accountMapper.findByAccountId(1L)).thenReturn(account);
        when(accountHistoryMapper.findByAccountId(1L, 30)).thenReturn(List.of(snapshot));

        assertEquals(List.of(snapshot), service.getAccountHistory(1L, 30));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 366})
    @DisplayName("Should reject limit outside 1..365 without querying the database")
    void rejectsOutOfRangeLimit(int limit) {
        assertThrows(InvalidHistoryRequestException.class, () -> service.getAccountHistory(1L, limit));
        verify(accountHistoryMapper, never()).findByAccountId(anyLong(), anyInt());
    }

    @Test
    @DisplayName("Should throw AccountNotFoundException for unknown account")
    void unknownAccount() {
        when(accountMapper.findByAccountId(99L)).thenReturn(null);

        assertThrows(AccountNotFoundException.class, () -> service.getAccountHistory(99L, 30));
        assertThrows(AccountNotFoundException.class, () -> service.getAccountHistoryRange(99L, OCT_1, OCT_2));
    }

    @Test
    @DisplayName("Should return history within a valid range")
    void returnsRange() {
        when(accountMapper.findByAccountId(1L)).thenReturn(account);
        when(accountHistoryMapper.findByAccountIdAndDateRange(1L, OCT_1, OCT_2)).thenReturn(List.of(snapshot));

        assertEquals(List.of(snapshot), service.getAccountHistoryRange(1L, OCT_1, OCT_2));
    }

    @Test
    @DisplayName("Should reject a range whose start is not before its end")
    void rejectsInvertedRange() {
        when(accountMapper.findByAccountId(1L)).thenReturn(account);

        assertThrows(InvalidHistoryRequestException.class, () -> service.getAccountHistoryRange(1L, OCT_2, OCT_1));
        assertThrows(InvalidHistoryRequestException.class, () -> service.getAccountHistoryRange(1L, OCT_1, OCT_1));
    }
}
