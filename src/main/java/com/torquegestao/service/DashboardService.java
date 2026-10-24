package com.torquegestao.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.torquegestao.config.AppProperties;
import com.torquegestao.domain.ServiceOrderStatus;
import com.torquegestao.dto.DashboardStats;
import com.torquegestao.dto.DashboardStats.MonthlyRevenue;
import com.torquegestao.dto.DashboardStats.MostUsedPart;
import com.torquegestao.dto.DashboardStats.TopMechanic;
import com.torquegestao.repository.ClientRepository;
import com.torquegestao.repository.PartRepository;
import com.torquegestao.repository.ServiceOrderRepository;
import com.torquegestao.repository.VehicleRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private static final Logger log = LoggerFactory.getLogger(DashboardService.class);

    static final String CACHE_KEY = "dashboard:stats";
    static final String[] MONTH_LABELS = {
        "Jan", "Fev", "Mar", "Abr", "Mai", "Jun", "Jul", "Ago", "Set", "Out", "Nov", "Dez"
    };

    private final ClientRepository clients;
    private final VehicleRepository vehicles;
    private final ServiceOrderRepository orders;
    private final PartRepository parts;
    private final CacheService cache;
    private final ObjectMapper mapper;
    private final AppProperties props;

    public DashboardService(
            ClientRepository clients,
            VehicleRepository vehicles,
            ServiceOrderRepository orders,
            PartRepository parts,
            CacheService cache,
            ObjectMapper mapper,
            AppProperties props) {
        this.clients = clients;
        this.vehicles = vehicles;
        this.orders = orders;
        this.parts = parts;
        this.cache = cache;
        this.mapper = mapper;
        this.props = props;
    }

    public DashboardStats getStats() {
        DashboardStats cached = readCache();
        if (cached != null) {
            return cached;
        }

        ZoneId zone = props.zoneId();
        List<MonthlyRevenue> chart = monthlyRevenueChart(zone);
        String currentLabel = MONTH_LABELS[YearMonth.now(zone).getMonthValue() - 1];
        BigDecimal monthlyRevenue = chart.stream()
                .filter(m -> m.month().equals(currentLabel))
                .map(MonthlyRevenue::amount)
                .findFirst()
                .orElse(BigDecimal.ZERO);

        DashboardStats stats = new DashboardStats(
                clients.count(),
                vehicles.count(),
                orders.count(),
                orders.countByStatusIn(EnumSet.of(ServiceOrderStatus.CRIADA, ServiceOrderStatus.EM_DIAGNOSTICO)),
                orders.countByStatusIn(EnumSet.of(ServiceOrderStatus.EM_EXECUCAO)),
                orders.countByStatusIn(EnumSet.of(ServiceOrderStatus.AGUARDANDO_APROVACAO)),
                monthlyRevenue,
                parts.countLowStock(),
                chart,
                topMechanics(),
                mostUsedParts());
        writeCache(stats);
        return stats;
    }

    /**
     * Soma o faturado (OS finalizadas) mês a mês nos últimos 6 meses; meses vazios entram com zero.
     * Os meses seguem o fuso da oficina ({@code app.timezone}), não UTC.
     */
    private List<MonthlyRevenue> monthlyRevenueChart(ZoneId zone) {
        YearMonth now = YearMonth.now(zone);
        List<YearMonth> months = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            months.add(now.minusMonths(i));
        }
        Instant since = months.get(0).atDay(1).atStartOfDay(zone).toInstant();

        Map<YearMonth, BigDecimal> totals = new HashMap<>();
        for (Object[] row : orders.completedRevenueSince(since)) {
            Instant completedAt = (Instant) row[0];
            BigDecimal total = (BigDecimal) row[1];
            YearMonth key = YearMonth.from(completedAt.atZone(zone));
            totals.merge(key, total, BigDecimal::add);
        }

        return months.stream()
                .map(m -> new MonthlyRevenue(
                        MONTH_LABELS[m.getMonthValue() - 1],
                        totals.getOrDefault(m, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)))
                .toList();
    }

    private List<TopMechanic> topMechanics() {
        return orders.topMechanics(PageRequest.of(0, 5)).stream()
                .map(r -> new TopMechanic(r[0].toString(), (String) r[1], ((Number) r[2]).longValue()))
                .toList();
    }

    private List<MostUsedPart> mostUsedParts() {
        return orders.mostUsedParts(PageRequest.of(0, 5)).stream()
                .map(r -> new MostUsedPart(r[0].toString(), (String) r[1], ((Number) r[2]).longValue()))
                .toList();
    }

    private DashboardStats readCache() {
        String raw = cache.get(CACHE_KEY);
        if (raw == null) {
            return null;
        }
        try {
            return mapper.readValue(raw, DashboardStats.class);
        } catch (JsonProcessingException ex) {
            log.warn("Cache do dashboard inválido, recalculando: {}", ex.getMessage());
            return null;
        }
    }

    private void writeCache(DashboardStats stats) {
        try {
            cache.set(CACHE_KEY, mapper.writeValueAsString(stats), props.getDashboardCacheTtlSeconds());
        } catch (JsonProcessingException ex) {
            log.warn("Não foi possível gravar o cache do dashboard: {}", ex.getMessage());
        }
    }
}
