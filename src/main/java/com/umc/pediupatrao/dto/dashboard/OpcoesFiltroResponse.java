package com.umc.pediupatrao.dto.dashboard;

import java.time.LocalDate;
import java.util.List;

/**
 * Valores disponíveis para os filtros do dashboard (RF05).
 */
public record OpcoesFiltroResponse(
        List<OpcaoFiltro> status,
        List<OpcaoFiltro> tiposEntrega,
        List<OpcaoFiltro> agrupamentos,
        List<String> categorias,
        LocalDate dataInicioPadrao,
        LocalDate dataFimPadrao,
        int intervaloMaximoDias) {
}
