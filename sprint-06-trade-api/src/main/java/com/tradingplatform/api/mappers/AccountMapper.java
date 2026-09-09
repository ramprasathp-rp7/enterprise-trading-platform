package com.tradingplatform.api.mappers;

import org.apache.ibatis.annotations.Select;

import com.tradingplatform.domain.dto.AccountResponse;


interface AccountMapper {
    @Select("SELECT id, account_id, holder_name, cash_balance, status, version, last_updated FROM accounts where id=#{id}")
    AccountResponse findByAccountId(int id);
}




