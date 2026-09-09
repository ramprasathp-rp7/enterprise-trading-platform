package com.tradingplatform.api.mappers;

import org.apache.ibatis.annotations.Select;

import com.tradingplatform.domain.dto.BalanceResponse;


interface BalanceMapper {
    @Select("SELECT id, cash_balance, currency, last_updated FROM accounts where id=#{id}")
    BalanceResponse findBalanceByAccountId(int id);
}

