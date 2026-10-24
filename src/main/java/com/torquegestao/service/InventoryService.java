package com.torquegestao.service;

import com.torquegestao.domain.InventoryMovement;
import com.torquegestao.domain.MovementType;
import com.torquegestao.domain.Part;
import com.torquegestao.dto.MovementResponse;
import com.torquegestao.dto.PageResponse;
import com.torquegestao.dto.PartRequest;
import com.torquegestao.dto.PartResponse;
import com.torquegestao.exception.BusinessException;
import com.torquegestao.exception.InsufficientStockException;
import com.torquegestao.exception.NotFoundException;
import com.torquegestao.repository.InventoryMovementRepository;
import com.torquegestao.repository.PartRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Estoque. O saldo só muda por movimentações; a concorrência é protegida por lock otimista
 * ({@code Part.version}): se outra transação alterou a peça no meio do caminho, o commit falha
 * com conflito (HTTP 409) em vez de sobrescrever o saldo.
 */
@Service
@Transactional
public class InventoryService {

    private final PartRepository parts;
    private final InventoryMovementRepository movements;

    public InventoryService(PartRepository parts, InventoryMovementRepository movements) {
        this.parts = parts;
        this.movements = movements;
    }

    public Part getPart(UUID id) {
        return parts.findById(id)
                .orElseThrow(() -> new NotFoundException("Peça não encontrada com ID: " + id));
    }

    @Transactional(readOnly = true)
    public PartResponse getPartResponse(UUID id) {
        return PartResponse.from(getPart(id));
    }

    @Transactional(readOnly = true)
    public List<PartResponse> list() {
        return parts.findByActiveTrueOrderByCreatedAtDesc().stream().map(PartResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<PartResponse> page(String name, Pageable pageable) {
        Pageable safe = Pages.safe(
                pageable, Set.of("name", "code", "stockQuantity", "salePrice", "createdAt"),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.from(parts
                .findByActiveTrueAndNameContainingIgnoreCase(name == null ? "" : name.trim(), safe)
                .map(PartResponse::from));
    }

    @Transactional(readOnly = true)
    public List<PartResponse> listLowStock() {
        return parts.findLowStock().stream().map(PartResponse::from).toList();
    }

    public PartResponse create(PartRequest data, UUID userId) {
        assertCodeFree(data.code().trim());

        Part part = new Part();
        part.setName(data.name().trim());
        part.setCode(data.code().trim());
        part.setManufacturer(Texts.blankToNull(data.manufacturer()));
        part.setPurchasePrice(data.purchasePrice());
        part.setSalePrice(data.salePrice());
        part.setStockQuantity(data.stockQuantity());
        part.setMinimumStock(data.minimumStockOrDefault());
        part.setActive(true);
        parts.saveAndFlush(part);

        record(part, MovementType.ENTRADA, data.stockQuantity(), 0, data.stockQuantity(),
                "Cadastro inicial", null, userId);
        return PartResponse.from(part);
    }

    public PartResponse update(UUID id, PartRequest data) {
        Part part = getPart(id);
        String code = data.code().trim();
        if (!part.getCode().equals(code)) {
            assertCodeFree(code);
        }
        // O estoque só muda por movimentações (entrada/saída), nunca por edição do cadastro.
        part.setName(data.name().trim());
        part.setCode(code);
        part.setManufacturer(Texts.blankToNull(data.manufacturer()));
        part.setPurchasePrice(data.purchasePrice());
        part.setSalePrice(data.salePrice());
        part.setMinimumStock(data.minimumStockOrDefault());
        parts.saveAndFlush(part);
        return PartResponse.from(part);
    }

    public void deactivate(UUID id) {
        getPart(id).setActive(false);
    }

    public void addStock(UUID partId, int quantity, String reason, UUID userId, String referenceId) {
        assertPositive(quantity);
        applyMovement(partId, quantity, MovementType.ENTRADA, reason, userId, referenceId);
    }

    public void removeStock(UUID partId, int quantity, String reason, UUID userId, String referenceId) {
        assertPositive(quantity);
        applyMovement(partId, -quantity, MovementType.SAIDA, reason, userId, referenceId);
    }

    private static void assertPositive(int quantity) {
        if (quantity <= 0) {
            throw new BusinessException("Quantidade deve ser positiva");
        }
    }

    @Transactional(readOnly = true)
    public List<MovementResponse> listMovements(UUID partId) {
        getPart(partId);
        return movements.findByPartIdOrderByCreatedAtDesc(partId).stream()
                .map(MovementResponse::from)
                .toList();
    }

    private void applyMovement(
            UUID partId, int delta, MovementType type, String reason, UUID userId, String referenceId) {
        Part part = getPart(partId);
        int previous = part.getStockQuantity();
        int updated = previous + delta;
        if (updated < 0) {
            throw new InsufficientStockException(previous);
        }
        part.setStockQuantity(updated);
        // O UPDATE da peça carrega "where version = ?": conflito concorrente => 409 no commit.
        parts.saveAndFlush(part);
        record(part, type, Math.abs(delta), previous, updated, reason, referenceId, userId);
    }

    private void record(
            Part part,
            MovementType type,
            int quantity,
            int previous,
            int current,
            String reason,
            String referenceId,
            UUID userId) {
        InventoryMovement movement = new InventoryMovement();
        movement.setPart(part);
        movement.setType(type);
        movement.setQuantity(quantity);
        movement.setPreviousQuantity(previous);
        movement.setCurrentQuantity(current);
        movement.setReason(reason);
        movement.setReferenceId(referenceId);
        movement.setUserId(userId);
        movements.save(movement);
    }

    private void assertCodeFree(String code) {
        if (parts.existsByCode(code)) {
            throw new BusinessException("Já existe uma peça com este código: " + code);
        }
    }
}
