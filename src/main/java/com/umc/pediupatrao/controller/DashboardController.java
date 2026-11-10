package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.dto.dashboard.Agrupamento;
import com.umc.pediupatrao.dto.dashboard.FiltroDashboard;
import com.umc.pediupatrao.dto.dashboard.OpcoesFiltroResponse;
import com.umc.pediupatrao.dto.dashboard.OrdenacaoRanking;
import com.umc.pediupatrao.dto.dashboard.PedidoRecenteResponse;
import com.umc.pediupatrao.dto.dashboard.ProdutoMaisVendidoResponse;
import com.umc.pediupatrao.dto.dashboard.ResumoVendasResponse;
import com.umc.pediupatrao.dto.dashboard.StatusPedidoResumoResponse;
import com.umc.pediupatrao.dto.dashboard.VendasPorCategoriaResponse;
import com.umc.pediupatrao.dto.dashboard.VendasPorHoraResponse;
import com.umc.pediupatrao.dto.dashboard.VendasPorPeriodoResponse;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoEntrega;
import com.umc.pediupatrao.service.DashboardService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API do dashboard administrativo. Todos os endpoints são somente leitura (GET), exigem login
 * com perfil ADMIN ou GERENTE e aceitam os filtros comuns:
 * dataInicio, dataFim (yyyy-MM-dd), tipoEntrega (ENTREGA | RETIRADA) e status (StatusPedido).
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /** RF01 — Indicadores gerais de vendas. */
    @GetMapping("/resumo")
    public ResumoVendasResponse resumo(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(required = false) TipoEntrega tipoEntrega,
            @RequestParam(required = false) StatusPedido status) {
        return dashboardService.resumo(filtro(dataInicio, dataFim, tipoEntrega, status));
    }

    /** RF02 — Série temporal de vendas (gráfico de linha/barras). */
    @GetMapping("/vendas")
    public VendasPorPeriodoResponse vendas(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(required = false) TipoEntrega tipoEntrega,
            @RequestParam(required = false) StatusPedido status,
            @RequestParam(required = false) Agrupamento agrupamento) {
        return dashboardService.vendasPorPeriodo(filtro(dataInicio, dataFim, tipoEntrega, status), agrupamento);
    }

    /** RF02 — Pedidos por hora do dia (horários de pico). */
    @GetMapping("/vendas/por-hora")
    public List<VendasPorHoraResponse> vendasPorHora(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(required = false) TipoEntrega tipoEntrega,
            @RequestParam(required = false) StatusPedido status) {
        return dashboardService.vendasPorHora(filtro(dataInicio, dataFim, tipoEntrega, status));
    }

    /** RF02 — Vendas por categoria de produto. */
    @GetMapping("/vendas/por-categoria")
    public List<VendasPorCategoriaResponse> vendasPorCategoria(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(required = false) TipoEntrega tipoEntrega,
            @RequestParam(required = false) StatusPedido status) {
        return dashboardService.vendasPorCategoria(filtro(dataInicio, dataFim, tipoEntrega, status));
    }

    /** RF03 — Quantidade de pedidos por situação. */
    @GetMapping("/pedidos/status")
    public List<StatusPedidoResumoResponse> pedidosPorStatus(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(required = false) TipoEntrega tipoEntrega,
            @RequestParam(required = false) StatusPedido status) {
        return dashboardService.pedidosPorStatus(filtro(dataInicio, dataFim, tipoEntrega, status));
    }

    /** RF03 — Pedidos mais recentes do período. */
    @GetMapping("/pedidos/recentes")
    public List<PedidoRecenteResponse> pedidosRecentes(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(required = false) TipoEntrega tipoEntrega,
            @RequestParam(required = false) StatusPedido status,
            @RequestParam(required = false) Integer limite) {
        return dashboardService.pedidosRecentes(filtro(dataInicio, dataFim, tipoEntrega, status), limite);
    }

    /** RF04 — Ranking de produtos mais vendidos. */
    @GetMapping("/produtos/mais-vendidos")
    public List<ProdutoMaisVendidoResponse> produtosMaisVendidos(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(required = false) TipoEntrega tipoEntrega,
            @RequestParam(required = false) StatusPedido status,
            @RequestParam(required = false) Integer limite,
            @RequestParam(required = false) OrdenacaoRanking ordenarPor,
            @RequestParam(required = false) String categoria) {
        return dashboardService.produtosMaisVendidos(
                filtro(dataInicio, dataFim, tipoEntrega, status), limite, ordenarPor, categoria);
    }

    /** RF05 — Opções disponíveis para os filtros da tela. */
    @GetMapping("/filtros")
    public OpcoesFiltroResponse filtros() {
        return dashboardService.opcoesFiltro();
    }

    private FiltroDashboard filtro(LocalDate dataInicio, LocalDate dataFim, TipoEntrega tipoEntrega,
                                   StatusPedido status) {
        return dashboardService.criarFiltro(dataInicio, dataFim, tipoEntrega, status);
    }
}
