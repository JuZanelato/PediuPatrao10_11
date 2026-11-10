package com.umc.pediupatrao.dto.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Indicadores gerais de vendas do período filtrado (RF01).
 */
public record ResumoVendasResponse(
        LocalDate dataInicio,
        LocalDate dataFim,
        long totalPedidos,
        long pedidosValidos,
        long pedidosFinalizados,
        long pedidosEmAndamento,
        long pedidosCancelados,
        BigDecimal faturamento,
        BigDecimal ticketMedio,
        BigDecimal totalDescontos,
        long itensVendidos,
        BigDecimal taxaCancelamento,
        BigDecimal faturamentoPeriodoAnterior,
        BigDecimal variacaoFaturamento,
        BigDecimal variacaoPedidos) {
}
