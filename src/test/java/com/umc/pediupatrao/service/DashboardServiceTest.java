package com.umc.pediupatrao.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.dto.dashboard.Agrupamento;
import com.umc.pediupatrao.dto.dashboard.FiltroDashboard;
import com.umc.pediupatrao.dto.dashboard.OrdenacaoRanking;
import com.umc.pediupatrao.dto.dashboard.PedidoRecenteResponse;
import com.umc.pediupatrao.dto.dashboard.ProdutoMaisVendidoResponse;
import com.umc.pediupatrao.dto.dashboard.ResumoVendasResponse;
import com.umc.pediupatrao.dto.dashboard.StatusPedidoResumoResponse;
import com.umc.pediupatrao.dto.dashboard.VendasPorCategoriaResponse;
import com.umc.pediupatrao.dto.dashboard.VendasPorHoraResponse;
import com.umc.pediupatrao.dto.dashboard.VendasPorPeriodoResponse;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoEntrega;
import com.umc.pediupatrao.exception.FiltroInvalidoException;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.util.DataHora;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DashboardServiceTest {
    private static final LocalDate HOJE = LocalDate.of(2026, 11, 10);

    private PedidoRepository pedidoRepository;
    private ProdutoRepository produtoRepository;
    private DashboardService service;

    @BeforeEach
    void configurar() {
        pedidoRepository = mock(PedidoRepository.class);
        produtoRepository = mock(ProdutoRepository.class);
        Clock relogio = Clock.fixed(ZonedDateTime.of(HOJE.atTime(15, 0), DataHora.FUSO).toInstant(), DataHora.FUSO);
        service = new DashboardService(pedidoRepository, produtoRepository, relogio);

        when(produtoRepository.findAll()).thenReturn(List.of(
                produto("pz1", "Pizza Calabresa", "Pizza"),
                produto("bb1", "Refrigerante 2L", "Bebida")));
        when(pedidoRepository.buscarPorPeriodoDeEntrada(any(), any())).thenReturn(List.of());
    }

    // ------------------------------------------------------------------ filtros

    @Test
    void filtroSemDatasUsaUltimos30Dias() {
        FiltroDashboard f = service.criarFiltro(null, null, null, null);
        assertEquals(HOJE, f.dataFim());
        assertEquals(HOJE.minusDays(29), f.dataInicio());
    }

    @Test
    void dataInicialPosteriorAFinalGeraErro() {
        FiltroInvalidoException e = assertThrows(FiltroInvalidoException.class,
                () -> service.criarFiltro(HOJE, HOJE.minusDays(1), null, null));
        assertTrue(e.getMessage().contains("data inicial"));
    }

    @Test
    void intervaloMaiorQueUmAnoGeraErro() {
        assertThrows(FiltroInvalidoException.class,
                () -> service.criarFiltro(HOJE.minusDays(400), HOJE, null, null));
    }

    @Test
    void limiteForaDaFaixaGeraErro() {
        FiltroDashboard f = service.criarFiltro(HOJE, HOJE, null, null);
        assertThrows(FiltroInvalidoException.class, () -> service.pedidosRecentes(f, 0));
        assertThrows(FiltroInvalidoException.class, () -> service.produtosMaisVendidos(f, 51, null, null));
    }

    // ------------------------------------------------------------------ RF01

    @Test
    void resumoIgnoraCanceladosNoFaturamentoECalculaIndicadores() {
        periodoAtual(List.of(
                pedido(StatusPedido.FINALIZADO, TipoEntrega.ENTREGA, HOJE.atTime(19, 0), "100.00", "10.00",
                        item("pz1", "Pizza Calabresa", 2, "50.00")),
                pedido(StatusPedido.EM_PREPARACAO, TipoEntrega.RETIRADA, HOJE.atTime(20, 0), "50.00", null,
                        item("bb1", "Refrigerante 2L", 1, "50.00")),
                pedido(StatusPedido.CANCELADO, TipoEntrega.ENTREGA, HOJE.atTime(21, 0), "80.00", null,
                        item("pz1", "Pizza Calabresa", 1, "80.00"))));

        ResumoVendasResponse r = service.resumo(service.criarFiltro(HOJE, HOJE, null, null));

        assertEquals(3, r.totalPedidos());
        assertEquals(2, r.pedidosValidos());
        assertEquals(1, r.pedidosFinalizados());
        assertEquals(1, r.pedidosEmAndamento());
        assertEquals(1, r.pedidosCancelados());
        assertEquals(new BigDecimal("150.00"), r.faturamento());
        assertEquals(new BigDecimal("75.00"), r.ticketMedio());
        assertEquals(new BigDecimal("10.00"), r.totalDescontos());
        assertEquals(3, r.itensVendidos());
        assertEquals(new BigDecimal("33.3"), r.taxaCancelamento());
    }

    @Test
    void resumoCalculaVariacaoEmRelacaoAoPeriodoAnterior() {
        LocalDate ontem = HOJE.minusDays(1);
        when(pedidoRepository.buscarPorPeriodoDeEntrada(eq(HOJE.atStartOfDay()), eq(HOJE.plusDays(1).atStartOfDay())))
                .thenReturn(List.of(pedido(StatusPedido.FINALIZADO, TipoEntrega.ENTREGA, HOJE.atTime(19, 0), "150.00", null)));
        when(pedidoRepository.buscarPorPeriodoDeEntrada(eq(ontem.atStartOfDay()), eq(HOJE.atStartOfDay())))
                .thenReturn(List.of(pedido(StatusPedido.FINALIZADO, TipoEntrega.ENTREGA, ontem.atTime(19, 0), "100.00", null)));

        ResumoVendasResponse r = service.resumo(service.criarFiltro(HOJE, HOJE, null, null));

        assertEquals(new BigDecimal("100.00"), r.faturamentoPeriodoAnterior());
        assertEquals(new BigDecimal("50.0"), r.variacaoFaturamento());
        assertEquals(new BigDecimal("0.0"), r.variacaoPedidos());
    }

    @Test
    void resumoSemPedidosRetornaZerosESemVariacao() {
        ResumoVendasResponse r = service.resumo(service.criarFiltro(HOJE, HOJE, null, null));

        assertEquals(0, r.totalPedidos());
        assertEquals(new BigDecimal("0.00"), r.faturamento());
        assertEquals(new BigDecimal("0.00"), r.ticketMedio());
        assertEquals(new BigDecimal("0.0"), r.taxaCancelamento());
        assertNull(r.variacaoFaturamento());
    }

    @Test
    void resumoTrataValoresNulosComoZero() {
        Pedido semValores = pedido(StatusPedido.RECEBIDO, TipoEntrega.ENTREGA, HOJE.atTime(18, 0), null, null);
        semValores.setItens(null);
        Pedido semData = pedido(StatusPedido.RECEBIDO, TipoEntrega.ENTREGA, null, "99.00", null);
        periodoAtual(listaCom(semValores, semData, null));

        ResumoVendasResponse r = service.resumo(service.criarFiltro(HOJE, HOJE, null, null));

        assertEquals(1, r.totalPedidos());
        assertEquals(new BigDecimal("0.00"), r.faturamento());
        assertEquals(0, r.itensVendidos());
    }

    @Test
    void filtroPorTipoEntregaEStatusEhAplicado() {
        periodoAtual(List.of(
                pedido(StatusPedido.FINALIZADO, TipoEntrega.ENTREGA, HOJE.atTime(19, 0), "100.00", null),
                pedido(StatusPedido.FINALIZADO, TipoEntrega.RETIRADA, HOJE.atTime(19, 30), "40.00", null),
                pedido(StatusPedido.RECEBIDO, TipoEntrega.ENTREGA, HOJE.atTime(20, 0), "60.00", null)));

        ResumoVendasResponse r = service.resumo(
                service.criarFiltro(HOJE, HOJE, TipoEntrega.ENTREGA, StatusPedido.FINALIZADO));

        assertEquals(1, r.totalPedidos());
        assertEquals(new BigDecimal("100.00"), r.faturamento());
    }

    // ------------------------------------------------------------------ RF02

    @Test
    void vendasPorDiaPreencheDiasSemVendaComZero() {
        LocalDate inicio = HOJE.minusDays(2);
        when(pedidoRepository.buscarPorPeriodoDeEntrada(any(), any())).thenReturn(List.of(
                pedido(StatusPedido.FINALIZADO, TipoEntrega.ENTREGA, inicio.atTime(19, 0), "30.00", null),
                pedido(StatusPedido.FINALIZADO, TipoEntrega.ENTREGA, HOJE.atTime(19, 0), "70.00", null),
                pedido(StatusPedido.FINALIZADO, TipoEntrega.ENTREGA, HOJE.atTime(20, 0), "30.00", null)));

        VendasPorPeriodoResponse r = service.vendasPorPeriodo(
                service.criarFiltro(inicio, HOJE, null, null), Agrupamento.DIA);

        assertEquals(3, r.pontos().size());
        assertEquals(new BigDecimal("30.00"), r.pontos().get(0).faturamento());
        assertEquals(0, r.pontos().get(1).quantidadePedidos());
        assertEquals(new BigDecimal("0.00"), r.pontos().get(1).faturamento());
        assertEquals(2, r.pontos().get(2).quantidadePedidos());
        assertEquals(new BigDecimal("50.00"), r.pontos().get(2).ticketMedio());
        assertEquals("10/11", r.pontos().get(2).rotulo());
    }

    @Test
    void vendasPorMesAgrupaNoPrimeiroDiaDoMes() {
        VendasPorPeriodoResponse r = service.vendasPorPeriodo(
                service.criarFiltro(LocalDate.of(2026, 9, 15), HOJE, null, null), Agrupamento.MES);

        assertEquals(Agrupamento.MES, r.agrupamento());
        assertEquals(3, r.pontos().size());
        assertEquals(LocalDate.of(2026, 9, 1), r.pontos().get(0).inicio());
        assertEquals("11/2026", r.pontos().get(2).rotulo());
    }

    @Test
    void vendasPorHoraRetorna24HorasComPico() {
        periodoAtual(List.of(
                pedido(StatusPedido.FINALIZADO, TipoEntrega.ENTREGA, HOJE.atTime(19, 5), "50.00", null),
                pedido(StatusPedido.FINALIZADO, TipoEntrega.ENTREGA, HOJE.atTime(19, 40), "50.00", null),
                pedido(StatusPedido.CANCELADO, TipoEntrega.ENTREGA, HOJE.atTime(19, 50), "50.00", null)));

        List<VendasPorHoraResponse> r = service.vendasPorHora(service.criarFiltro(HOJE, HOJE, null, null));

        assertEquals(24, r.size());
        assertEquals(2, r.get(19).quantidadePedidos());
        assertEquals(new BigDecimal("100.00"), r.get(19).faturamento());
        assertEquals(0, r.get(10).quantidadePedidos());
    }

    @Test
    void vendasPorCategoriaUsaTipoDoProduto() {
        periodoAtual(List.of(pedido(StatusPedido.FINALIZADO, TipoEntrega.ENTREGA, HOJE.atTime(19, 0), "120.00", null,
                item("pz1", "Pizza Calabresa", 2, "45.00"),
                item("bb1", "Refrigerante 2L", 1, "14.00"),
                item("x9", "Produto removido", 1, "16.00"))));

        List<VendasPorCategoriaResponse> r = service.vendasPorCategoria(service.criarFiltro(HOJE, HOJE, null, null));

        assertEquals(3, r.size());
        assertEquals("Pizza", r.get(0).categoria());
        assertEquals(new BigDecimal("90.00"), r.get(0).faturamento());
        assertEquals(new BigDecimal("75.0"), r.get(0).participacao());
        assertTrue(r.stream().anyMatch(c -> c.categoria().equals(DashboardService.SEM_CATEGORIA)));
    }

    // ------------------------------------------------------------------ RF03

    @Test
    void pedidosPorStatusListaTodasAsSituacoes() {
        periodoAtual(List.of(
                pedido(StatusPedido.RECEBIDO, TipoEntrega.ENTREGA, HOJE.atTime(18, 0), "10.00", null),
                pedido(StatusPedido.RECEBIDO, TipoEntrega.ENTREGA, HOJE.atTime(18, 5), "10.00", null),
                pedido(StatusPedido.CANCELADO, TipoEntrega.ENTREGA, HOJE.atTime(18, 10), "10.00", null),
                pedido(StatusPedido.FINALIZADO, TipoEntrega.ENTREGA, HOJE.atTime(18, 15), "10.00", null)));

        List<StatusPedidoResumoResponse> r = service.pedidosPorStatus(service.criarFiltro(HOJE, HOJE, null, null));

        assertEquals(StatusPedido.values().length, r.size());
        StatusPedidoResumoResponse recebido = r.stream().filter(s -> s.status().equals("RECEBIDO")).findFirst().orElseThrow();
        assertEquals(2, recebido.quantidade());
        assertEquals(new BigDecimal("50.0"), recebido.percentual());
        assertEquals("Recebido", recebido.descricao());
    }

    @Test
    void pedidosRecentesOrdenaDoMaisNovoParaOMaisAntigo() {
        periodoAtual(List.of(
                pedido(StatusPedido.RECEBIDO, TipoEntrega.ENTREGA, HOJE.atTime(18, 0), "10.00", null),
                pedido(StatusPedido.RECEBIDO, TipoEntrega.ENTREGA, HOJE.atTime(21, 0), "30.00", null),
                pedido(StatusPedido.RECEBIDO, TipoEntrega.ENTREGA, HOJE.atTime(19, 0), "20.00", null)));

        List<PedidoRecenteResponse> r = service.pedidosRecentes(service.criarFiltro(HOJE, HOJE, null, null), 2);

        assertEquals(2, r.size());
        assertEquals(new BigDecimal("30.00"), r.get(0).valorTotal());
        assertEquals(new BigDecimal("20.00"), r.get(1).valorTotal());
    }

    // ------------------------------------------------------------------ RF04

    @Test
    void rankingPorQuantidadeEFaturamento() {
        periodoAtual(List.of(
                pedido(StatusPedido.FINALIZADO, TipoEntrega.ENTREGA, HOJE.atTime(19, 0), "0", null,
                        item("pz1", "Pizza Calabresa", 1, "60.00"),
                        item("bb1", "Refrigerante 2L", 3, "10.00")),
                pedido(StatusPedido.CANCELADO, TipoEntrega.ENTREGA, HOJE.atTime(19, 30), "0", null,
                        item("pz1", "Pizza Calabresa", 10, "60.00"))));
        FiltroDashboard f = service.criarFiltro(HOJE, HOJE, null, null);

        List<ProdutoMaisVendidoResponse> porQuantidade = service.produtosMaisVendidos(f, 10, OrdenacaoRanking.QUANTIDADE, null);
        List<ProdutoMaisVendidoResponse> porFaturamento = service.produtosMaisVendidos(f, 10, OrdenacaoRanking.FATURAMENTO, null);

        assertEquals("Refrigerante 2L", porQuantidade.get(0).nome());
        assertEquals(3, porQuantidade.get(0).quantidade());
        assertEquals(1, porQuantidade.get(0).posicao());
        assertEquals("Pizza Calabresa", porFaturamento.get(0).nome());
        assertEquals(1, porFaturamento.get(0).quantidade()); // pedido cancelado não conta
        assertEquals(new BigDecimal("66.7"), porFaturamento.get(0).participacao());
    }

    @Test
    void rankingFiltraPorCategoria() {
        periodoAtual(List.of(pedido(StatusPedido.FINALIZADO, TipoEntrega.ENTREGA, HOJE.atTime(19, 0), "0", null,
                item("pz1", "Pizza Calabresa", 1, "60.00"),
                item("bb1", "Refrigerante 2L", 3, "10.00"))));

        List<ProdutoMaisVendidoResponse> r = service.produtosMaisVendidos(
                service.criarFiltro(HOJE, HOJE, null, null), 10, null, "bebida");

        assertEquals(1, r.size());
        assertEquals("Bebida", r.get(0).categoria());
    }

    // ------------------------------------------------------------------ apoio

    private void periodoAtual(List<Pedido> pedidos) {
        when(pedidoRepository.buscarPorPeriodoDeEntrada(eq(HOJE.atStartOfDay()), eq(HOJE.plusDays(1).atStartOfDay())))
                .thenReturn(pedidos);
    }

    private static List<Pedido> listaCom(Pedido... pedidos) {
        List<Pedido> lista = new ArrayList<>();
        for (Pedido p : pedidos) {
            lista.add(p);
        }
        return lista;
    }

    private static Pedido pedido(StatusPedido status, TipoEntrega tipo, LocalDateTime entrada,
                                 String total, String desconto, ItemPedido... itens) {
        Pedido p = new Pedido();
        p.setId("p-" + System.nanoTime());
        p.setClienteNome("Cliente Teste");
        p.setStatus(status);
        p.setTipoEntrega(tipo);
        p.setDataHoraEntrada(entrada);
        p.setValorTotal(total != null ? new BigDecimal(total) : null);
        p.setValorDesconto(desconto != null ? new BigDecimal(desconto) : null);
        p.setItens(new ArrayList<>(List.of(itens)));
        return p;
    }

    private static ItemPedido item(String produtoId, String nome, int quantidade, String preco) {
        return new ItemPedido(produtoId, nome, quantidade, new BigDecimal(preco));
    }

    private static Produto produto(String id, String nome, String tipo) {
        Produto p = new Produto();
        p.setId(id);
        p.setNome(nome);
        p.setTipo(tipo);
        p.setPreco(10.0);
        p.setAtivo(true);
        return p;
    }
}
