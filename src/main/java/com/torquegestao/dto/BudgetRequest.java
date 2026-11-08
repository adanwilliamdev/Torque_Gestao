package com.torquegestao.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BudgetRequest(
        @NotNull(message = "Cliente é obrigatório") UUID clientId,
        @NotNull(message = "Veículo é obrigatório") UUID vehicleId,
        UUID serviceOrderId,
        String description,
        /** Opcional quando há OS vinculada (o valor vem do total da OS); obrigatório nos demais casos. */
        @DecimalMin(value = "0", inclusive = false, message = "Valor total deve ser maior que zero")
        @Digits(integer = 10, fraction = 2, message = "Valor total inválido") BigDecimal totalAmount,
        Instant validUntil) {
}
