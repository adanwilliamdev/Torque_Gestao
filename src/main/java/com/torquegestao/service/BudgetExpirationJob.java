package com.torquegestao.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Marca como EXPIRADO os orçamentos pendentes cuja validade passou. A operação é idempotente
 * (um UPDATE condicional), então várias instâncias rodando ao mesmo tempo não causam problema.
 * Entre uma execução e outra, a leitura e a decisão já tratam o orçamento vencido como expirado.
 */
@Component
public class BudgetExpirationJob {

    private static final Logger log = LoggerFactory.getLogger(BudgetExpirationJob.class);

    private final BudgetService budgets;

    public BudgetExpirationJob(BudgetService budgets) {
        this.budgets = budgets;
    }

    @Scheduled(
            fixedDelayString = "${app.budget-expiration-interval:PT15M}",
            initialDelayString = "PT30S")
    public void expireOverdueBudgets() {
        try {
            int expired = budgets.expireOverdue();
            if (expired > 0) {
                log.info("{} orçamento(s) pendente(s) vencido(s) marcado(s) como expirado(s)", expired);
            }
        } catch (RuntimeException ex) {
            // Uma falha (ex.: banco indisponível) não pode derrubar o agendador: tenta de novo no próximo ciclo.
            log.warn("Falha ao expirar orçamentos vencidos: {}", ex.getMessage());
        }
    }
}
