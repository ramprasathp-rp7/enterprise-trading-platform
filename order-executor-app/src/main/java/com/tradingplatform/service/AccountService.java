package com.tradingplatform.service;

import java.math.BigDecimal;

public interface AccountService {
    boolean isSuspended(long accountId);
    BigDecimal getAvailableCash(long accountId);
}
