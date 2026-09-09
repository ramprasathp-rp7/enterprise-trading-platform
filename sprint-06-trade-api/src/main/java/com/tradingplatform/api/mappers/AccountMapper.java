package com.tradingplatform.api.mappers;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Select;

import com.tradingplatform.domain.dto.AccountResponse;
import com.tradingplatform.domain.dto.BalanceResponse;
import com.tradingplatform.domain.dto.OrderHistoryEntry;
import com.tradingplatform.domain.dto.PositionResponse;
import com.tradingplatform.domain.enums.OrderStatus;

interface AccountMapper {
    @Select("SELECT id, account_id, holder_name, cash_balance, status, version, last_updated FROM accounts where id=#{id}")
    AccountResponse findByAccountId(int id);
}




