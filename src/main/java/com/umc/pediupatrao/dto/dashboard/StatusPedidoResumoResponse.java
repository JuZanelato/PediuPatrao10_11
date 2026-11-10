package com.umc.pediupatrao.dto.dashboard;

import java.math.BigDecimal;

/**
 * Quantidade de pedidos em cada situação (RF03).
 */
public record StatusPedidoResumoResponse(
        String status,
        String descricao,
        long quantidade,
        BigDecimal percentual) {
}
