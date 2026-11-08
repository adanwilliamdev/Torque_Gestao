package com.torquegestao.web.form;

import com.torquegestao.domain.PaymentMethod;
import com.torquegestao.dto.PaymentRequest;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PaymentForm {

    @NotNull(message = "Valor do pagamento é obrigatório")
    @DecimalMin(value = "0", inclusive = false, message = "Valor do pagamento deve ser maior que zero")
    @Digits(integer = 10, fraction = 2, message = "Valor do pagamento inválido")
    private BigDecimal amount;

    @NotNull(message = "Selecione a forma de pagamento")
    private PaymentMethod method = PaymentMethod.PIX;

    @Size(max = 255, message = "Observação deve ter no máximo 255 caracteres")
    private String notes;

    public PaymentRequest toRequest() {
        return new PaymentRequest(amount, method, notes);
    }
}
