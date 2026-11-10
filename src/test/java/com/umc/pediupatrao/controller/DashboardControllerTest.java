package com.umc.pediupatrao.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.umc.pediupatrao.config.SecurityConfig;
import com.umc.pediupatrao.dto.dashboard.Agrupamento;
import com.umc.pediupatrao.dto.dashboard.FiltroDashboard;
import com.umc.pediupatrao.dto.dashboard.OrdenacaoRanking;
import com.umc.pediupatrao.dto.dashboard.PontoVendasResponse;
import com.umc.pediupatrao.dto.dashboard.ProdutoMaisVendidoResponse;
import com.umc.pediupatrao.dto.dashboard.ResumoVendasResponse;
import com.umc.pediupatrao.dto.dashboard.VendasPorPeriodoResponse;
import com.umc.pediupatrao.entity.TipoEntrega;
import com.umc.pediupatrao.exception.FiltroInvalidoException;
import com.umc.pediupatrao.security.AcessoNegadoHandler;
import com.umc.pediupatrao.service.AuditoriaService;
import com.umc.pediupatrao.service.DashboardService;
import com.umc.pediupatrao.service.UsuarioDetailsService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DashboardController.class)
@Import({SecurityConfig.class, AcessoNegadoHandler.class})
class DashboardControllerTest {
    private static final LocalDate INICIO = LocalDate.of(2026, 11, 1);
    private static final LocalDate FIM = LocalDate.of(2026, 11, 10);
    private static final FiltroDashboard FILTRO = new FiltroDashboard(INICIO, FIM, null, null);

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private DashboardService dashboardService;

    @MockitoBean
    private AuditoriaService auditoriaService;

    @MockitoBean
    private UsuarioDetailsService usuarioDetailsService;

    @BeforeEach
    void configurar() {
        when(dashboardService.criarFiltro(any(), any(), any(), any())).thenReturn(FILTRO);
    }

    // ------------------------------------------------------------------ segurança

    @Test
    void semLoginRecebe401() throws Exception {
        mvc.perform(get("/api/dashboard/resumo")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "atendente01", roles = "ATENDENTE")
    void atendenteNaoAcessaDashboard() throws Exception {
        mvc.perform(get("/api/dashboard/resumo"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminAcessaDashboard() throws Exception {
        when(dashboardService.resumo(FILTRO)).thenReturn(resumo());
        mvc.perform(get("/api/dashboard/resumo")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    void metodosDeEscritaSaoBloqueados() throws Exception {
        mvc.perform(post("/api/dashboard/resumo").with(csrf())).andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ respostas

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    void resumoRetornaIndicadores() throws Exception {
        when(dashboardService.criarFiltro(eq(INICIO), eq(FIM), eq(TipoEntrega.ENTREGA), isNull()))
                .thenReturn(new FiltroDashboard(INICIO, FIM, TipoEntrega.ENTREGA, null));
        when(dashboardService.resumo(any())).thenReturn(resumo());

        mvc.perform(get("/api/dashboard/resumo")
                        .param("dataInicio", "2026-11-01")
                        .param("dataFim", "2026-11-10")
                        .param("tipoEntrega", "ENTREGA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPedidos").value(12))
                .andExpect(jsonPath("$.pedidosCancelados").value(2))
                .andExpect(jsonPath("$.faturamento").value(950.5))
                .andExpect(jsonPath("$.dataInicio").value("2026-11-01"));
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    void vendasAceitaAgrupamento() throws Exception {
        when(dashboardService.vendasPorPeriodo(FILTRO, Agrupamento.SEMANA)).thenReturn(new VendasPorPeriodoResponse(
                Agrupamento.SEMANA,
                List.of(new PontoVendasResponse(INICIO, "Sem. 01/11", 5, new BigDecimal("250.00"), new BigDecimal("50.00")))));

        mvc.perform(get("/api/dashboard/vendas").param("agrupamento", "SEMANA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agrupamento").value("SEMANA"))
                .andExpect(jsonPath("$.pontos[0].rotulo").value("Sem. 01/11"))
                .andExpect(jsonPath("$.pontos[0].quantidadePedidos").value(5));
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    void rankingRepassaParametros() throws Exception {
        when(dashboardService.produtosMaisVendidos(FILTRO, 5, OrdenacaoRanking.FATURAMENTO, "Pizza")).thenReturn(List.of(
                new ProdutoMaisVendidoResponse(1, "pz1", "Pizza Calabresa", "Pizza", 30,
                        new BigDecimal("1647.00"), new BigDecimal("41.2"))));

        mvc.perform(get("/api/dashboard/produtos/mais-vendidos")
                        .param("limite", "5")
                        .param("ordenarPor", "FATURAMENTO")
                        .param("categoria", "Pizza"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].posicao").value(1))
                .andExpect(jsonPath("$[0].nome").value("Pizza Calabresa"));
    }

    // ------------------------------------------------------------------ erros

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    void periodoInvertidoRetorna400ComMensagem() throws Exception {
        when(dashboardService.criarFiltro(eq(FIM), eq(INICIO), isNull(), isNull()))
                .thenThrow(new FiltroInvalidoException("A data inicial não pode ser posterior à data final."));

        mvc.perform(get("/api/dashboard/resumo")
                        .param("dataInicio", "2026-11-10")
                        .param("dataFim", "2026-11-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.mensagem").value("A data inicial não pode ser posterior à data final."));
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    void dataEmFormatoInvalidoRetorna400() throws Exception {
        mvc.perform(get("/api/dashboard/resumo").param("dataInicio", "10/11/2026"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    void statusInexistenteRetorna400() throws Exception {
        mvc.perform(get("/api/dashboard/pedidos/status").param("status", "ENTREGUE"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    void limiteInvalidoRetorna400() throws Exception {
        when(dashboardService.pedidosRecentes(FILTRO, 500))
                .thenThrow(new FiltroInvalidoException("O limite deve estar entre 1 e 50."));

        mvc.perform(get("/api/dashboard/pedidos/recentes").param("limite", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("O limite deve estar entre 1 e 50."));
    }

    private static ResumoVendasResponse resumo() {
        return new ResumoVendasResponse(INICIO, FIM, 12, 10, 8, 2, 2,
                new BigDecimal("950.50"), new BigDecimal("95.05"), new BigDecimal("12.00"), 31,
                new BigDecimal("16.7"), new BigDecimal("800.00"), new BigDecimal("18.8"), new BigDecimal("11.1"));
    }
}
