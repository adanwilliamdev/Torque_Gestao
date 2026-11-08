package com.torquegestao.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "budgets")
public class Budget extends AuditedEntity {

    @Column(name = "budget_number", nullable = false, unique = true)
    private String budgetNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    /** Uma OS pode ter vários orçamentos ao longo do tempo, mas só um ativo (índice parcial no banco). */
    @Column(name = "service_order_id")
    private UUID serviceOrderId;

    private String description;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BudgetStatus status = BudgetStatus.PENDENTE;

    @Column(name = "valid_until")
    private Instant validUntil;

    /** Pendente e com a validade já vencida em {@code now}. */
    public boolean isExpiredAt(Instant now) {
        return status == BudgetStatus.PENDENTE && validUntil != null && validUntil.isBefore(now);
    }

    /** Status exibido: um pendente vencido já aparece como expirado, mesmo antes da rotina gravar. */
    public BudgetStatus getEffectiveStatus() {
        return isExpiredAt(Instant.now()) ? BudgetStatus.EXPIRADO : status;
    }
}
