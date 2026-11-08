package com.torquegestao.dto;

import com.torquegestao.domain.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record PaymentRequest(
        @NotNull(message = "Valor do pagamento é obrigatório")
        @DecimalMin(value = "0", inclusive = false, message = "Valor do pagamento deve ser maior que zero")
        @Digits(integer = 10, fraction = 2, message = "Valor do pagamento inválido") BigDecimal amount,
        @NotNull(message = "Forma de pagamento é obrigatória") PaymentMethod method,
        @Size(max = 255, message = "Observação deve ter no máximo 255 caracteres") String notes) {
}
