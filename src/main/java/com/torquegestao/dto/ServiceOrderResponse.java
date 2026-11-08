package com.torquegestao.dto;

import com.torquegestao.domain.PaymentStatus;
import com.torquegestao.domain.ServiceOrder;
import com.torquegestao.domain.ServiceOrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ServiceOrderResponse(
        UUID id,
        String orderNumber,
        UUID clientId,
        String clientName,
        UUID vehicleId,
        String vehicleInfo,
        UUID mechanicId,
        String mechanicName,
        String reportedProblem,
        String diagnosis,
        ServiceOrderStatus status,
        BigDecimal laborAmount,
        BigDecimal partsAmount,
        BigDecimal totalAmount,
        BigDecimal paidAmount,
        BigDecimal balanceAmount,
        PaymentStatus paymentStatus,
        List<ServiceOrderItemResponse> items,
        Instant startedAt,
        Instant completedAt,
        Instant createdAt) {

    public static ServiceOrderResponse from(ServiceOrder o) {
        var v = o.getVehicle();
        var m = o.getMechanic();
        return new ServiceOrderResponse(
                o.getId(),
                o.getOrderNumber(),
                o.getClient().getId(),
                o.getClient().getName(),
                v.getId(),
                v.getBrand() + " " + v.getModel() + " (" + v.getPlate() + ")",
                m == null ? null : m.getId(),
                m == null ? null : m.getName(),
                o.getReportedProblem(),
                o.getDiagnosis(),
                o.getStatus(),
                o.getLaborAmount(),
                o.getPartsAmount(),
                o.getTotalAmount(),
                o.getPaidAmount(),
                o.getBalanceAmount(),
                o.getPaymentStatus(),
                o.getItems().stream().map(ServiceOrderItemResponse::from).toList(),
                o.getStartedAt(),
                o.getCompletedAt(),
                o.getCreatedAt());
    }
}
