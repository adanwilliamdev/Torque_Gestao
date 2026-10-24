package com.torquegestao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.torquegestao.dto.PartRequest;
import com.torquegestao.exception.InsufficientStockException;
import com.torquegestao.service.InventoryService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

/**
 * Prova a garantia central do estoque: baixas simultâneas nunca geram saldo negativo nem "perdem"
 * atualizações. Cada thread usa a sua própria transação (como requisições HTTP concorrentes).
 */
class StockConcurrencyIT extends AbstractIntegrationTest {

    @Autowired InventoryService inventory;

    private UUID novaPeca(int stock) {
        var request = new PartRequest(
                "Pastilha", "P-" + UUID.randomUUID(), null,
                new BigDecimal("10.00"), new BigDecimal("20.00"), stock, 1);
        return inventory.create(request, null).id();
    }

    /** Dispara {@code threads} baixas de 1 unidade ao mesmo tempo; devolve quantas tiveram sucesso. */
    private int baixasSimultaneas(UUID partId, int threads) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CyclicBarrier largada = new CyclicBarrier(threads);
        AtomicInteger sucessos = new AtomicInteger();
        List<Future<?>> tarefas = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            tarefas.add(pool.submit(() -> {
                largada.await(10, TimeUnit.SECONDS);
                try {
                    inventory.removeStock(partId, 1, "concorrência", null, null);
                    sucessos.incrementAndGet();
                } catch (InsufficientStockException | ObjectOptimisticLockingFailureException esperado) {
                    // perdeu a corrida: saldo esgotado ou conflito de versão (HTTP 400/409)
                }
                return null;
            }));
        }
        for (Future<?> tarefa : tarefas) {
            tarefa.get(30, TimeUnit.SECONDS); // propaga qualquer exceção inesperada
        }
        pool.shutdown();
        return sucessos.get();
    }

    @Test
    void umaUnicaUnidadeSoPodeSerBaixadaUmaVez() throws Exception {
        UUID partId = novaPeca(1);

        int sucessos = baixasSimultaneas(partId, 8);

        assertEquals(1, sucessos, "apenas uma baixa pode vencer");
        assertEquals(0, inventory.getPartResponse(partId).stockQuantity());
    }

    @Test
    void saldoFinalSempreBateComAsBaixasQueTiveramSucesso() throws Exception {
        int inicial = 5;
        UUID partId = novaPeca(inicial);

        int sucessos = baixasSimultaneas(partId, 12);

        int saldo = inventory.getPartResponse(partId).stockQuantity();
        assertTrue(saldo >= 0, "saldo nunca pode ficar negativo");
        assertEquals(inicial - sucessos, saldo, "nenhuma atualização pode ser perdida");
        // cadastro inicial + uma movimentação por baixa bem-sucedida
        assertEquals(1 + sucessos, inventory.listMovements(partId).size());
    }
}
