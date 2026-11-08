package com.torquegestao.domain;

/** Situação financeira de uma OS (independente do status operacional). */
public enum PaymentStatus {
    PENDENTE("Pagamento pendente"),
    PARCIAL("Pago parcialmente"),
    PAGA("Paga");

    private final String label;

    PaymentStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
