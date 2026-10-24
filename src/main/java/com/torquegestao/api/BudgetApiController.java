package com.torquegestao.api;

import com.torquegestao.domain.BudgetStatus;
import com.torquegestao.dto.BudgetRequest;
import com.torquegestao.dto.BudgetResponse;
import com.torquegestao.security.Access;
import com.torquegestao.security.AppUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.torquegestao.service.BudgetService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Orçamentos")
@RestController
@RequestMapping("/api/budgets")
public class BudgetApiController {

    private final BudgetService budgets;

    public BudgetApiController(BudgetService budgets) {
        this.budgets = budgets;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Access.BUDGET_CREATE)
    public BudgetResponse create(@Valid @RequestBody BudgetRequest data) {
        return budgets.create(data);
    }

    @GetMapping
    @PreAuthorize(Access.BUDGET_READ)
    public List<BudgetResponse> list() {
        return budgets.list();
    }

    @GetMapping("/client/{clientId}")
    @PreAuthorize(Access.BUDGET_READ)
    public List<BudgetResponse> listByClient(@PathVariable UUID clientId) {
        return budgets.listByClient(clientId);
    }

    @GetMapping("/status/{status}")
    @PreAuthorize(Access.BUDGET_READ)
    public List<BudgetResponse> listByStatus(@PathVariable BudgetStatus status) {
        return budgets.listByStatus(status);
    }

    @GetMapping("/{id}")
    @PreAuthorize(Access.BUDGET_READ)
    public BudgetResponse get(@PathVariable UUID id) {
        return budgets.getResponse(id);
    }

    @PatchMapping("/{id}/approve")
    @PreAuthorize(Access.BUDGET_DECIDE)
    public BudgetResponse approve(@PathVariable UUID id, @AuthenticationPrincipal AppUserDetails user) {
        return budgets.approve(id, user.getId());
    }

    @PatchMapping("/{id}/reject")
    @PreAuthorize(Access.BUDGET_DECIDE)
    public BudgetResponse reject(@PathVariable UUID id, @AuthenticationPrincipal AppUserDetails user) {
        return budgets.reject(id, user.getId());
    }
}
