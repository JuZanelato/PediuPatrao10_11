package com.umc.pediupatrao.service;

import com.umc.pediupatrao.dto.dashboard.Agrupamento;
import com.umc.pediupatrao.dto.dashboard.FiltroDashboard;
import com.umc.pediupatrao.dto.dashboard.OpcaoFiltro;
import com.umc.pediupatrao.dto.dashboard.OpcoesFiltroResponse;
import com.umc.pediupatrao.dto.dashboard.OrdenacaoRanking;
import com.umc.pediupatrao.dto.dashboard.PedidoRecenteResponse;
import com.umc.pediupatrao.dto.dashboard.PontoVendasResponse;
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
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Regras de negócio do dashboard administrativo: consolida os pedidos gravados no MongoDB
 * em indicadores, séries para gráficos, situação dos pedidos e rankings de produtos.
 *
 * <p>Convenções adotadas:
 * <ul>
 *   <li>O período considera a data/hora de entrada do pedido (fuso America/Sao_Paulo).</li>
 *   <li>Faturamento = soma do valor total dos pedidos <b>não cancelados</b>.</li>
 *   <li>Valores nulos são tratados como zero e itens sem produto são agrupados como "Sem categoria".</li>
 * </ul>
 */
@Service
public class DashboardService {
    public static final int DIAS_PADRAO = 30;
    public static final int INTERVALO_MAXIMO_DIAS = 366;
    public static final int LIMITE_MAXIMO = 50;
    public static final String SEM_CATEGORIA = "Sem categoria";

    private static final BigDecimal CEM = new BigDecimal("100");
    private static final DateTimeFormatter FORMATO_DIA = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter FORMATO_MES = DateTimeFormatter.ofPattern("MM/yyyy");

    private final PedidoRepository pedidoRepository;
    private final ProdutoRepository produtoRepository;
    private final Clock relogio;

    @Autowired
    public DashboardService(PedidoRepository pedidoRepository, ProdutoRepository produtoRepository) {
        this(pedidoRepository, produtoRepository, Clock.system(DataHora.FUSO));
    }

    DashboardService(PedidoRepository pedidoRepository, ProdutoRepository produtoRepository, Clock relogio) {
        this.pedidoRepository = pedidoRepository;
        this.produtoRepository = produtoRepository;
        this.relogio = relogio;
    }

    // ------------------------------------------------------------------ filtros

    /**
     * Valida os parâmetros recebidos e aplica os valores padrão (últimos 30 dias).
     */
    public FiltroDashboard criarFiltro(LocalDate dataInicio, LocalDate dataFim,
                                       TipoEntrega tipoEntrega, StatusPedido status) {
        LocalDate hoje = LocalDate.now(relogio);
        LocalDate fim = dataFim != null ? dataFim : hoje;
        LocalDate inicio = dataInicio != null ? dataInicio : fim.minusDays(DIAS_PADRAO - 1L);

        if (inicio.isAfter(fim)) {
            throw new FiltroInvalidoException("A data inicial não pode ser posterior à data final.");
        }
        long dias = ChronoUnit.DAYS.between(inicio, fim) + 1;
        if (dias > INTERVALO_MAXIMO_DIAS) {
            throw new FiltroInvalidoException(
                    "O intervalo máximo de consulta é de " + INTERVALO_MAXIMO_DIAS + " dias.");
        }
        return new FiltroDashboard(inicio, fim, tipoEntrega, status);
    }

    public OpcoesFiltroResponse opcoesFiltro() {
        List<OpcaoFiltro> status = Arrays.stream(StatusPedido.values())
                .map(s -> new OpcaoFiltro(s.name(), s.getDescricao()))
                .toList();
        List<OpcaoFiltro> tipos = Arrays.stream(TipoEntrega.values())
                .map(t -> new OpcaoFiltro(t.name(), t.getDescricao()))
                .toList();
        List<OpcaoFiltro> agrupamentos = List.of(
                new OpcaoFiltro(Agrupamento.DIA.name(), "Por dia"),
                new OpcaoFiltro(Agrupamento.SEMANA.name(), "Por semana"),
                new OpcaoFiltro(Agrupamento.MES.name(), "Por mês"));
        TreeSet<String> categorias = new TreeSet<>();
        for (Produto p : produtoRepository.findAll()) {
            categorias.add(categoriaDe(p));
        }
        FiltroDashboard padrao = criarFiltro(null, null, null, null);
        return new OpcoesFiltroResponse(status, tipos, agrupamentos, new ArrayList<>(categorias),
                padrao.dataInicio(), padrao.dataFim(), INTERVALO_MAXIMO_DIAS);
    }

    // ------------------------------------------------------------------ RF01

    public ResumoVendasResponse resumo(FiltroDashboard filtro) {
        List<Pedido> pedidos = buscarPedidos(filtro);

        long total = pedidos.size();
        long cancelados = pedidos.stream().filter(p -> p.getStatus() == StatusPedido.CANCELADO).count();
        long finalizados = pedidos.stream().filter(p -> p.getStatus() == StatusPedido.FINALIZADO).count();
        long emAndamento = pedidos.stream()
                .filter(p -> p.getStatus() != null && !p.getStatus().isEncerrado())
                .count();
        List<Pedido> validos = validos(pedidos);
        BigDecimal faturamento = somarValorTotal(validos);
        BigDecimal descontos = validos.stream()
                .map(p -> valor(p.getValorDesconto()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long itens = validos.stream()
                .flatMap(p -> itensDe(p).stream())
                .mapToLong(i -> Math.max(i.getQuantidade(), 0))
                .sum();

        // Período anterior de mesmo tamanho, para indicar tendência.
        long dias = ChronoUnit.DAYS.between(filtro.dataInicio(), filtro.dataFim()) + 1;
        FiltroDashboard anterior = new FiltroDashboard(
                filtro.dataInicio().minusDays(dias), filtro.dataInicio().minusDays(1),
                filtro.tipoEntrega(), filtro.status());
        List<Pedido> validosAnteriores = validos(buscarPedidos(anterior));
        BigDecimal faturamentoAnterior = somarValorTotal(validosAnteriores);

        return new ResumoVendasResponse(
                filtro.dataInicio(),
                filtro.dataFim(),
                total,
                validos.size(),
                finalizados,
                emAndamento,
                cancelados,
                dinheiro(faturamento),
                dinheiro(dividir(faturamento, validos.size())),
                dinheiro(descontos),
                itens,
                percentual(cancelados, total),
                dinheiro(faturamentoAnterior),
                variacao(faturamento, faturamentoAnterior),
                variacao(BigDecimal.valueOf(validos.size()), BigDecimal.valueOf(validosAnteriores.size())));
    }

    // ------------------------------------------------------------------ RF02

    public VendasPorPeriodoResponse vendasPorPeriodo(FiltroDashboard filtro, Agrupamento agrupamento) {
        Agrupamento grupo = agrupamento != null ? agrupamento : Agrupamento.DIA;
        List<Pedido> validos = validos(buscarPedidos(filtro));

        // Cria todos os períodos do intervalo com zero, para o gráfico não ter "buracos".
        Map<LocalDate, List<Pedido>> porPeriodo = new LinkedHashMap<>();
        LocalDate cursor = inicioDoPeriodo(filtro.dataInicio(), grupo);
        while (!cursor.isAfter(filtro.dataFim())) {
            porPeriodo.put(cursor, new ArrayList<>());
            cursor = proximoPeriodo(cursor, grupo);
        }
        for (Pedido p : validos) {
            LocalDate chave = inicioDoPeriodo(p.getDataHoraEntrada().toLocalDate(), grupo);
            porPeriodo.computeIfAbsent(chave, k -> new ArrayList<>()).add(p);
        }

        List<PontoVendasResponse> pontos = new ArrayList<>();
        porPeriodo.forEach((inicio, lista) -> {
            BigDecimal soma = somarValorTotal(lista);
            pontos.add(new PontoVendasResponse(inicio, rotulo(inicio, grupo), lista.size(),
                    dinheiro(soma), dinheiro(dividir(soma, lista.size()))));
        });
        return new VendasPorPeriodoResponse(grupo, pontos);
    }

    public List<VendasPorHoraResponse> vendasPorHora(FiltroDashboard filtro) {
        long[] quantidades = new long[24];
        BigDecimal[] valores = new BigDecimal[24];
        Arrays.fill(valores, BigDecimal.ZERO);
        for (Pedido p : validos(buscarPedidos(filtro))) {
            int hora = p.getDataHoraEntrada().getHour();
            quantidades[hora]++;
            valores[hora] = valores[hora].add(valor(p.getValorTotal()));
        }
        List<VendasPorHoraResponse> resposta = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            resposta.add(new VendasPorHoraResponse(h, quantidades[h], dinheiro(valores[h])));
        }
        return resposta;
    }

    public List<VendasPorCategoriaResponse> vendasPorCategoria(FiltroDashboard filtro) {
        Map<String, String> categorias = categoriasPorProduto();
        Map<String, long[]> quantidades = new HashMap<>();
        Map<String, BigDecimal> valores = new HashMap<>();

        for (Pedido p : validos(buscarPedidos(filtro))) {
            for (ItemPedido item : itensDe(p)) {
                String categoria = categorias.getOrDefault(item.getProdutoId(), SEM_CATEGORIA);
                quantidades.computeIfAbsent(categoria, k -> new long[1])[0] += Math.max(item.getQuantidade(), 0);
                valores.merge(categoria, subtotalDe(item), BigDecimal::add);
            }
        }
        BigDecimal total = valores.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return valores.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .map(e -> new VendasPorCategoriaResponse(e.getKey(), quantidades.get(e.getKey())[0],
                        dinheiro(e.getValue()), participacao(e.getValue(), total)))
                .toList();
    }

    // ------------------------------------------------------------------ RF03

    public List<StatusPedidoResumoResponse> pedidosPorStatus(FiltroDashboard filtro) {
        List<Pedido> pedidos = buscarPedidos(filtro);
        Map<StatusPedido, Long> contagem = pedidos.stream()
                .filter(p -> p.getStatus() != null)
                .collect(Collectors.groupingBy(Pedido::getStatus, Collectors.counting()));
        long total = pedidos.size();
        return Arrays.stream(StatusPedido.values())
                .map(s -> {
                    long qtd = contagem.getOrDefault(s, 0L);
                    return new StatusPedidoResumoResponse(s.name(), s.getDescricao(), qtd, percentual(qtd, total));
                })
                .toList();
    }

    public List<PedidoRecenteResponse> pedidosRecentes(FiltroDashboard filtro, Integer limite) {
        int max = validarLimite(limite, 10);
        return buscarPedidos(filtro).stream()
                .sorted(Comparator.comparing(Pedido::getDataHoraEntrada).reversed())
                .limit(max)
                .map(p -> new PedidoRecenteResponse(
                        p.getId(),
                        p.getClienteNome() != null ? p.getClienteNome() : "Cliente não informado",
                        p.getTipoEntrega() != null ? p.getTipoEntrega().getDescricao() : null,
                        p.getStatus() != null ? p.getStatus().name() : null,
                        p.getStatusDescricao(),
                        itensDe(p).stream().mapToInt(i -> Math.max(i.getQuantidade(), 0)).sum(),
                        dinheiro(valor(p.getValorTotal())),
                        p.getDataHoraEntrada()))
                .toList();
    }

    // ------------------------------------------------------------------ RF04

    public List<ProdutoMaisVendidoResponse> produtosMaisVendidos(FiltroDashboard filtro, Integer limite,
                                                                 OrdenacaoRanking ordenarPor, String categoria) {
        int max = validarLimite(limite, 10);
        OrdenacaoRanking ordem = ordenarPor != null ? ordenarPor : OrdenacaoRanking.QUANTIDADE;
        String categoriaFiltro = categoria == null || categoria.isBlank() ? null : categoria.trim();
        Map<String, String> categorias = categoriasPorProduto();

        Map<String, AcumuladoProduto> acumulados = new LinkedHashMap<>();
        for (Pedido p : validos(buscarPedidos(filtro))) {
            for (ItemPedido item : itensDe(p)) {
                String cat = categorias.getOrDefault(item.getProdutoId(), SEM_CATEGORIA);
                if (categoriaFiltro != null && !cat.equalsIgnoreCase(categoriaFiltro)) {
                    continue;
                }
                String chave = item.getProdutoId() != null ? item.getProdutoId() : "nome:" + item.getNomeProduto();
                AcumuladoProduto acc = acumulados.computeIfAbsent(chave, k -> new AcumuladoProduto(
                        item.getProdutoId(),
                        item.getNomeProduto() != null ? item.getNomeProduto() : "Produto sem nome",
                        cat));
                acc.quantidade += Math.max(item.getQuantidade(), 0);
                acc.faturamento = acc.faturamento.add(subtotalDe(item));
            }
        }

        Comparator<AcumuladoProduto> comparador = ordem == OrdenacaoRanking.FATURAMENTO
                ? Comparator.comparing((AcumuladoProduto a) -> a.faturamento).reversed()
                        .thenComparing(a -> a.quantidade, Comparator.reverseOrder())
                : Comparator.comparingLong((AcumuladoProduto a) -> a.quantidade).reversed()
                        .thenComparing(a -> a.faturamento, Comparator.reverseOrder());
        comparador = comparador.thenComparing(a -> a.nome);

        BigDecimal total = acumulados.values().stream().map(a -> a.faturamento)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<AcumuladoProduto> ordenados = acumulados.values().stream().sorted(comparador).limit(max).toList();

        List<ProdutoMaisVendidoResponse> ranking = new ArrayList<>();
        for (int i = 0; i < ordenados.size(); i++) {
            AcumuladoProduto a = ordenados.get(i);
            ranking.add(new ProdutoMaisVendidoResponse(i + 1, a.produtoId, a.nome, a.categoria, a.quantidade,
                    dinheiro(a.faturamento), participacao(a.faturamento, total)));
        }
        return ranking;
    }

    // ------------------------------------------------------------------ apoio

    private List<Pedido> buscarPedidos(FiltroDashboard filtro) {
        LocalDateTime inicio = filtro.dataInicio().atStartOfDay();
        LocalDateTime fim = filtro.dataFim().plusDays(1).atStartOfDay();
        List<Pedido> pedidos = pedidoRepository.buscarPorPeriodoDeEntrada(inicio, fim);
        if (pedidos == null) {
            return List.of();
        }
        return pedidos.stream()
                .filter(Objects::nonNull)
                .filter(p -> p.getDataHoraEntrada() != null)
                .filter(p -> filtro.tipoEntrega() == null || filtro.tipoEntrega() == p.getTipoEntrega())
                .filter(p -> filtro.status() == null || filtro.status() == p.getStatus())
                .toList();
    }

    private static List<Pedido> validos(List<Pedido> pedidos) {
        return pedidos.stream().filter(p -> p.getStatus() != StatusPedido.CANCELADO).toList();
    }

    private static List<ItemPedido> itensDe(Pedido p) {
        return p.getItens() == null ? List.of() : p.getItens().stream().filter(Objects::nonNull).toList();
    }

    private static BigDecimal subtotalDe(ItemPedido item) {
        if (item.getSubtotal() != null) {
            return item.getSubtotal();
        }
        return valor(item.getPrecoUnitario()).multiply(BigDecimal.valueOf(Math.max(item.getQuantidade(), 0)));
    }

    private Map<String, String> categoriasPorProduto() {
        Map<String, String> mapa = new HashMap<>();
        for (Produto p : produtoRepository.findAll()) {
            if (p != null && p.getId() != null) {
                mapa.put(p.getId(), categoriaDe(p));
            }
        }
        return mapa;
    }

    private static String categoriaDe(Produto p) {
        return p == null || p.getTipo() == null || p.getTipo().isBlank() ? SEM_CATEGORIA : p.getTipo().trim();
    }

    private static int validarLimite(Integer limite, int padrao) {
        if (limite == null) {
            return padrao;
        }
        if (limite < 1 || limite > LIMITE_MAXIMO) {
            throw new FiltroInvalidoException("O limite deve estar entre 1 e " + LIMITE_MAXIMO + ".");
        }
        return limite;
    }

    private static LocalDate inicioDoPeriodo(LocalDate data, Agrupamento grupo) {
        return switch (grupo) {
            case DIA -> data;
            case SEMANA -> data.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MES -> data.withDayOfMonth(1);
        };
    }

    private static LocalDate proximoPeriodo(LocalDate data, Agrupamento grupo) {
        return switch (grupo) {
            case DIA -> data.plusDays(1);
            case SEMANA -> data.plusWeeks(1);
            case MES -> data.plusMonths(1);
        };
    }

    private static String rotulo(LocalDate inicio, Agrupamento grupo) {
        return switch (grupo) {
            case DIA -> inicio.format(FORMATO_DIA);
            case SEMANA -> "Sem. " + inicio.format(FORMATO_DIA);
            case MES -> inicio.format(FORMATO_MES);
        };
    }

    private static BigDecimal somarValorTotal(List<Pedido> pedidos) {
        return pedidos.stream().map(p -> valor(p.getValorTotal())).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal valor(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private static BigDecimal dinheiro(BigDecimal v) {
        return valor(v).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal dividir(BigDecimal valor, long quantidade) {
        return quantidade == 0 ? BigDecimal.ZERO : valor.divide(BigDecimal.valueOf(quantidade), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal percentual(long parte, long total) {
        if (total == 0) {
            return BigDecimal.ZERO.setScale(1);
        }
        return BigDecimal.valueOf(parte).multiply(CEM).divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP);
    }

    private static BigDecimal participacao(BigDecimal parte, BigDecimal total) {
        if (total.signum() == 0) {
            return BigDecimal.ZERO.setScale(1);
        }
        return parte.multiply(CEM).divide(total, 1, RoundingMode.HALF_UP);
    }

    /** Variação percentual em relação ao período anterior; nula quando não há base de comparação. */
    private static BigDecimal variacao(BigDecimal atual, BigDecimal anterior) {
        if (anterior == null || anterior.signum() == 0) {
            return null;
        }
        return atual.subtract(anterior).multiply(CEM).divide(anterior, 1, RoundingMode.HALF_UP);
    }

    private static final class AcumuladoProduto {
        private final String produtoId;
        private final String nome;
        private final String categoria;
        private long quantidade;
        private BigDecimal faturamento = BigDecimal.ZERO;

        private AcumuladoProduto(String produtoId, String nome, String categoria) {
            this.produtoId = produtoId;
            this.nome = nome;
            this.categoria = categoria;
        }
    }
}
