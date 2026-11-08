package com.torquegestao.dto;

import com.torquegestao.domain.Payment;
import com.torquegestao.domain.PaymentMethod;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        UUID serviceOrderId,
        BigDecimal amount,
        PaymentMethod method,
        Instant paidAt,
        String notes,
        String createdByName) {

    public static PaymentResponse from(Payment p) {
        var by = p.getCreatedBy();
        return new PaymentResponse(
                p.getId(),
                p.getServiceOrder().getId(),
                p.getAmount(),
                p.getMethod(),
                p.getPaidAt(),
                p.getNotes(),
                by == null ? null : by.getName());
    }
}
