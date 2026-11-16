package com.parko.identity.service.converter;

import com.parko.domain.lib.model.BalanceAccount;
import com.parko.persistence.core.model.embedded.BalanceAccountEmbedded;

import java.time.LocalDateTime;

public final class BalanceAccountConverter {

    private BalanceAccountConverter() {
    }

    public static BalanceAccountEmbedded toEmbedded(BalanceAccount balanceAccount, LocalDateTime createdAt, LocalDateTime updatedAt) {
        return new BalanceAccountEmbedded(
                balanceAccount.getUserId(),
                balanceAccount.getBalance(),
                createdAt,
                updatedAt
        );
    }
}
