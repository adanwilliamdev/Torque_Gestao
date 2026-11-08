package com.torquegestao.dto;

import com.torquegestao.domain.ServiceOrderStatus;
import com.torquegestao.domain.ServiceOrderStatusHistory;
import java.time.Instant;
import java.util.UUID;

/** Uma linha do histórico de status. {@code fromStatus} é nulo na abertura da OS. */
public record StatusHistoryResponse(
        UUID id,
        ServiceOrderStatus fromStatus,
        ServiceOrderStatus toStatus,
        UUID changedById,
        String changedByName,
        Instant changedAt) {

    public static StatusHistoryResponse from(ServiceOrderStatusHistory h) {
        var by = h.getChangedBy();
        return new StatusHistoryResponse(
                h.getId(),
                h.getFromStatus(),
                h.getToStatus(),
                by == null ? null : by.getId(),
                by == null ? null : by.getName(),
                h.getChangedAt());
    }
}
