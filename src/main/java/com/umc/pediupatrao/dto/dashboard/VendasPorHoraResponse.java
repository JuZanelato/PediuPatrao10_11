package com.umc.pediupatrao.dto.dashboard;

import java.math.BigDecimal;

/**
 * Pedidos e faturamento por hora do dia, para identificar horários de pico (RF02).
 */
public record VendasPorHoraResponse(
        int hora,
        long quantidadePedidos,
        BigDecimal faturamento) {
}
