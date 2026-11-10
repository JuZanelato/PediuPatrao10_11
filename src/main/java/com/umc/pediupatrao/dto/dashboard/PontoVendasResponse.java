package com.umc.pediupatrao.dto.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Um ponto da série temporal de vendas (RF02).
 */
public record PontoVendasResponse(
        LocalDate inicio,
        String rotulo,
        long quantidadePedidos,
        BigDecimal faturamento,
        BigDecimal ticketMedio) {
}
