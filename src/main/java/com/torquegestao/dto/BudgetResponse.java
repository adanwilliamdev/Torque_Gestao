package com.torquegestao.dto;

import com.torquegestao.domain.Budget;
import com.torquegestao.domain.BudgetStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BudgetResponse(
        UUID id,
        String budgetNumber,
        UUID clientId,
        String clientName,
        UUID vehicleId,
        String vehicleInfo,
        UUID serviceOrderId,
        String description,
        BigDecimal totalAmount,
        BudgetStatus status,
        Instant validUntil,
        Instant createdAt) {

    public static BudgetResponse from(Budget b) {
        var v = b.getVehicle();
        return new BudgetResponse(
                b.getId(),
                b.getBudgetNumber(),
                b.getClient().getId(),
                b.getClient().getName(),
                v.getId(),
                v.getBrand() + " " + v.getModel() + " (" + v.getPlate() + ")",
                b.getServiceOrderId(),
                b.getDescription(),
                b.getTotalAmount(),
                b.getEffectiveStatus(),
                b.getValidUntil(),
                b.getCreatedAt());
    }
}
