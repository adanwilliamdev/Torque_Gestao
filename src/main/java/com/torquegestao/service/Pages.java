package com.torquegestao.service;

import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Utilitário de paginação: só aceita ordenar por campos conhecidos (um sort inválido viraria erro 500). */
final class Pages {

    private Pages() {
    }

    static Pageable safe(Pageable requested, Set<String> sortable, Sort fallback) {
        boolean valid = requested.getSort().isSorted()
                && requested.getSort().stream().allMatch(order -> sortable.contains(order.getProperty()));
        return PageRequest.of(
                requested.getPageNumber(), requested.getPageSize(), valid ? requested.getSort() : fallback);
    }
}
