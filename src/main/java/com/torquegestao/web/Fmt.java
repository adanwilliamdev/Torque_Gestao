package com.torquegestao.web;

import com.torquegestao.config.AppProperties;
import com.torquegestao.domain.BudgetStatus;
import com.torquegestao.domain.PaymentStatus;
import com.torquegestao.domain.ServiceOrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

/** Disponível nos templates como {@code @fmt}. */
@Component("fmt")
public class Fmt {

    private final ZoneId zone;

    public Fmt(AppProperties props) {
        this.zone = props.zoneId();
    }

    public String currency(BigDecimal value) {
        return Formats.currency(value);
    }

    public String date(Instant value) {
        return Formats.date(value, zone);
    }

    public String dateTime(Instant value) {
        return Formats.dateTime(value, zone);
    }

    public String cpf(String value) {
        return Formats.cpf(value);
    }

    public String phone(String value) {
        return Formats.phone(value);
    }

    public String km(Integer value) {
        return Formats.kilometers(value);
    }

    public String dash(String value) {
        return Formats.orDash(value);
    }

    /** Cor do badge de uma OS. */
    public String orderTone(ServiceOrderStatus status) {
        return switch (status) {
            case CRIADA -> "neutral";
            case EM_DIAGNOSTICO, EM_EXECUCAO -> "steel";
            case AGUARDANDO_APROVACAO -> "amber";
            case APROVADA -> "amber-soft";
            case FINALIZADA -> "moss";
            case CANCELADA -> "rust";
        };
    }

    /** Cor do badge de um orçamento. */
    public String budgetTone(BudgetStatus status) {
        return switch (status) {
            case PENDENTE -> "amber";
            case APROVADO -> "moss";
            case RECUSADO -> "rust";
            case EXPIRADO -> "neutral";
        };
    }

    /** Cor do badge da situação financeira de uma OS. */
    public String paymentTone(PaymentStatus status) {
        return switch (status) {
            case PENDENTE -> "amber";
            case PARCIAL -> "amber-soft";
            case PAGA -> "moss";
        };
    }
}
