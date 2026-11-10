package com.umc.pediupatrao.dto.dashboard;

import java.util.List;

/**
 * Série temporal de vendas agrupada por dia, semana ou mês (RF02).
 */
public record VendasPorPeriodoResponse(
        Agrupamento agrupamento,
        List<PontoVendasResponse> pontos) {
}
