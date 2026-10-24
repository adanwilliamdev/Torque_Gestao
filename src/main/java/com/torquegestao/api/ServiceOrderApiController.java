package com.torquegestao.api;

import com.torquegestao.domain.ServiceOrderStatus;
import com.torquegestao.dto.LaborAmountRequest;
import com.torquegestao.dto.PageResponse;
import com.torquegestao.dto.ServiceOrderItemRequest;
import com.torquegestao.dto.ServiceOrderRequest;
import com.torquegestao.dto.ServiceOrderResponse;
import com.torquegestao.dto.StatusUpdateRequest;
import com.torquegestao.security.Access;
import com.torquegestao.security.AppUserDetails;
import com.torquegestao.service.ServiceOrderService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * MECHANIC só é liberado em GET /{id}, PATCH /{id}/status e nos itens/mão de obra; a restrição
 * "somente a OS atribuída a ele" é aplicada em {@code ServiceOrderService.assertCanAccess}.
 */
@Tag(name = "Ordens de serviço")
@RestController
@RequestMapping("/api/service-orders")
public class ServiceOrderApiController {

    private final ServiceOrderService orders;

    public ServiceOrderApiController(ServiceOrderService orders) {
        this.orders = orders;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Access.ORDER_CREATE)
    public ServiceOrderResponse create(@Valid @RequestBody ServiceOrderRequest data) {
        return orders.create(data);
    }

    @GetMapping
    @PreAuthorize(Access.ORDER_STAFF)
    public PageResponse<ServiceOrderResponse> list(
            @RequestParam(required = false) ServiceOrderStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return orders.page(status, pageable);
    }

    /** A listagem geral é negada ao MECHANIC; esta devolve só as ordens atribuídas a ele. */
    @GetMapping("/mine")
    @PreAuthorize(Access.MECHANIC_ONLY)
    public List<ServiceOrderResponse> listMine(@AuthenticationPrincipal AppUserDetails user) {
        return orders.listForMechanic(user);
    }

    @GetMapping("/client/{clientId}")
    @PreAuthorize(Access.ORDER_STAFF)
    public List<ServiceOrderResponse> listByClient(@PathVariable UUID clientId) {
        return orders.listByClient(clientId);
    }

    @GetMapping("/vehicle/{vehicleId}")
    @PreAuthorize(Access.ORDER_STAFF)
    public List<ServiceOrderResponse> listByVehicle(@PathVariable UUID vehicleId) {
        return orders.listByVehicle(vehicleId);
    }

    @GetMapping("/status/{status}")
    @PreAuthorize(Access.ORDER_STAFF)
    public List<ServiceOrderResponse> listByStatus(@PathVariable ServiceOrderStatus status) {
        return orders.listByStatus(status);
    }

    @GetMapping("/{id}")
    @PreAuthorize(Access.ORDER_VIEW)
    public ServiceOrderResponse get(@PathVariable UUID id, @AuthenticationPrincipal AppUserDetails user) {
        return orders.get(id, user);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize(Access.ORDER_STATUS)
    public ServiceOrderResponse updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody StatusUpdateRequest data,
            @AuthenticationPrincipal AppUserDetails user) {
        return orders.updateStatus(id, data, user);
    }

    @PatchMapping("/{id}/mechanic")
    @PreAuthorize(Access.ORDER_STAFF)
    public ServiceOrderResponse assignMechanic(@PathVariable UUID id, @RequestParam UUID mechanicId) {
        return orders.assignMechanic(id, mechanicId);
    }

    /** Registra o uso de uma peça na OS: baixa o estoque e soma ao total faturado. */
    @PostMapping("/{id}/items")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Access.ORDER_BILLING)
    public ServiceOrderResponse addItem(
            @PathVariable UUID id,
            @Valid @RequestBody ServiceOrderItemRequest data,
            @AuthenticationPrincipal AppUserDetails user) {
        return orders.addItem(id, data, user);
    }

    /** Remove uma peça lançada na OS e estorna a quantidade ao estoque. */
    @DeleteMapping("/{id}/items/{itemId}")
    @PreAuthorize(Access.ORDER_BILLING)
    public ServiceOrderResponse removeItem(
            @PathVariable UUID id, @PathVariable UUID itemId, @AuthenticationPrincipal AppUserDetails user) {
        return orders.removeItem(id, itemId, user);
    }

    @PatchMapping("/{id}/labor")
    @PreAuthorize(Access.ORDER_BILLING)
    public ServiceOrderResponse updateLabor(
            @PathVariable UUID id,
            @Valid @RequestBody LaborAmountRequest data,
            @AuthenticationPrincipal AppUserDetails user) {
        return orders.updateLaborAmount(id, data.laborAmount(), user);
    }
}
