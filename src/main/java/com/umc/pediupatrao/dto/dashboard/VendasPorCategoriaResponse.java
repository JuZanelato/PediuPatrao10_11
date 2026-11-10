package com.umc.pediupatrao.dto.dashboard;

import java.math.BigDecimal;

/**
 * Itens vendidos e faturamento por categoria de produto (RF02).
 */
public record VendasPorCategoriaResponse(
        String categoria,
        long quantidade,
        BigDecimal faturamento,
        BigDecimal participacao) {
}
