package com.umc.pediupatrao.dto.dashboard;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Resumo de um pedido para a lista de pedidos recentes do painel (RF03).
 */
public record PedidoRecenteResponse(
        String id,
        String clienteNome,
        String tipoEntrega,
        String status,
        String statusDescricao,
        int quantidadeItens,
        BigDecimal valorTotal,
        LocalDateTime dataHoraEntrada) {
}
