package com.torquegestao.web;

import com.torquegestao.config.AppProperties;
import com.torquegestao.exception.AppException;
import com.torquegestao.security.Access;
import com.torquegestao.security.AppUserDetails;
import com.torquegestao.service.BudgetService;
import com.torquegestao.service.ClientService;
import com.torquegestao.service.VehicleService;
import com.torquegestao.web.form.BudgetForm;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/budgets")
public class BudgetWebController {

    private final BudgetService budgets;
    private final ClientService clients;
    private final VehicleService vehicles;
    private final AppProperties props;

    public BudgetWebController(
            BudgetService budgets, ClientService clients, VehicleService vehicles, AppProperties props) {
        this.budgets = budgets;
        this.clients = clients;
        this.vehicles = vehicles;
        this.props = props;
    }

    @GetMapping
    @PreAuthorize(Access.BUDGET_READ)
    public String list(Model model) {
        model.addAttribute("budgets", budgets.list());
        return "budgets/list";
    }

    @GetMapping("/new")
    @PreAuthorize(Access.BUDGET_CREATE)
    public String create(Model model) {
        model.addAttribute("form", new BudgetForm());
        return formView(model);
    }

    @PostMapping
    @PreAuthorize(Access.BUDGET_CREATE)
    public String save(
            @Valid @ModelAttribute("form") BudgetForm form,
            BindingResult errors,
            Model model,
            RedirectAttributes flash) {
        if (errors.hasErrors()) {
            return formView(model);
        }
        try {
            budgets.create(form.toRequest(props.zoneId()));
        } catch (AppException | DataIntegrityViolationException ex) {
            errors.reject("business", ClientWebController.messageOf(ex));
            return formView(model);
        }
        flash.addFlashAttribute("success", "Orçamento criado com sucesso");
        return "redirect:/budgets";
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize(Access.BUDGET_DECIDE)
    public String approve(
            @PathVariable UUID id, @AuthenticationPrincipal AppUserDetails user, RedirectAttributes flash) {
        budgets.approve(id, user.getId());
        flash.addFlashAttribute("success", "Orçamento aprovado");
        return "redirect:/budgets";
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize(Access.BUDGET_DECIDE)
    public String reject(
            @PathVariable UUID id, @AuthenticationPrincipal AppUserDetails user, RedirectAttributes flash) {
        budgets.reject(id, user.getId());
        flash.addFlashAttribute("success", "Orçamento recusado");
        return "redirect:/budgets";
    }

    private String formView(Model model) {
        model.addAttribute("clients", clients.list());
        model.addAttribute("vehicles", vehicles.list());
        return "budgets/form";
    }
}
