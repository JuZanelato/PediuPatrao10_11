package com.umc.pediupatrao.dto.dashboard;

import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoEntrega;
import java.time.LocalDate;

/**
 * Filtros comuns a todos os endpoints do dashboard, já validados e com valores padrão aplicados.
 */
public record FiltroDashboard(
        LocalDate dataInicio,
        LocalDate dataFim,
        TipoEntrega tipoEntrega,
        StatusPedido status) {
}
