package com.torquegestao.exception;

/**
 * Orçamento vencido. É um erro de negócio (400), mas a transação que o lança <em>não</em> é
 * revertida: a mudança do orçamento para EXPIRADO feita antes do erro precisa ser gravada.
 */
public class BudgetExpiredException extends BusinessException {

    public BudgetExpiredException(String message) {
        super(message);
    }
}
