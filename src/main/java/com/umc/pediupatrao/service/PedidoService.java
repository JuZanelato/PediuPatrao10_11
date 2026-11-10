package com.umc.pediupatrao.service;

import com.umc.pediupatrao.dto.ItemPedidoRequest;
import com.umc.pediupatrao.dto.NovoPedidoRequest;
import com.umc.pediupatrao.entity.AlteracaoCampo;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.HistoricoStatus;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.security.UsuarioLogado;
import com.umc.pediupatrao.util.DataHora;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class PedidoService {
    static final String ENTIDADE = "Pedido";

    public static final BigDecimal DESCONTO_MAXIMO = new BigDecimal("20");
    private static final BigDecimal CEM = new BigDecimal("100");
    private static final int QUANTIDADE_MAXIMA_ITEM = 50;
    private static final int TAMANHO_MINIMO_JUSTIFICATIVA = 10;

    private final PedidoRepository pedidoRepository;
    private final ClienteRepository clienteRepository;
    private final ProdutoRepository produtoRepository;
    private final AuditoriaService auditoriaService;

    public PedidoService(PedidoRepository pedidoRepository, ClienteRepository clienteRepository,
                         ProdutoRepository produtoRepository, AuditoriaService auditoriaService) {
        this.pedidoRepository = pedidoRepository;
        this.clienteRepository = clienteRepository;
        this.produtoRepository = produtoRepository;
        this.auditoriaService = auditoriaService;
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE','ATENDENTE')")
    public List<Pedido> listarPedidos() {
        return pedidoRepository.findAllByOrderByDataHoraEntradaDesc();
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE','ATENDENTE')")
    public Pedido buscarPorId(String id) {
        return pedidoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pedido não encontrado."));
    }

    @PreAuthorize("hasAnyRole('ATENDENTE','GERENTE')")
    public Pedido criarPedido(NovoPedidoRequest dados) {
        if (dados == null || dados.tipoEntrega() == null) {
            throw new RegraNegocioException("Informe se o pedido é para entrega ou retirada.");
        }
        Cliente cliente = clienteRepository.findById(dados.clienteId())
                .orElseThrow(() -> new RegraNegocioException("Cliente não encontrado."));

        String usuario = UsuarioLogado.username();
        LocalDateTime agora = DataHora.agora();

        Pedido pedido = new Pedido();
        pedido.setClienteId(cliente.getId());
        pedido.setClienteNome(cliente.getNome());
        pedido.setTipoEntrega(dados.tipoEntrega());
        pedido.setObservacao(dados.observacao());
        pedido.setItens(montarItens(dados.itens()));
        pedido.setPercentualDesconto(BigDecimal.ZERO);
        recalcularValores(pedido);

        pedido.setStatus(StatusPedido.RECEBIDO);
        pedido.setDataHoraEntrada(agora);
        pedido.setResponsavelEntrada(usuario);
        pedido.getHistorico().add(new HistoricoStatus(StatusPedido.RECEBIDO, agora, usuario));

        Pedido salvo = pedidoRepository.save(pedido);

        List<AlteracaoCampo> campos = List.of(
                new AlteracaoCampo("cliente", null, salvo.getClienteNome()),
                new AlteracaoCampo("tipoEntrega", null, salvo.getTipoEntrega().name()),
                new AlteracaoCampo("itens", null, descreverItens(salvo.getItens())),
                new AlteracaoCampo("valorTotal", null, formatar(salvo.getValorTotal())),
                new AlteracaoCampo("status", null, StatusPedido.RECEBIDO.name()),
                new AlteracaoCampo("dataHoraEntrada", null, agora.toString()),
                new AlteracaoCampo("responsavelEntrada", null, usuario));
        auditoriaService.registrar(TipoOperacao.INCLUSAO_PEDIDO, ENTIDADE, salvo.getId(), campos, null);
        return salvo;
    }

    @PreAuthorize("hasAnyRole('ATENDENTE','GERENTE')")
    public Pedido atualizarItens(String id, List<ItemPedidoRequest> novosItens) {
        Pedido pedido = buscarPedido(id);
        if (pedido.getStatus() != StatusPedido.RECEBIDO) {
            throw new RegraNegocioException("Os itens só podem ser alterados enquanto o pedido está RECEBIDO.");
        }
        String itensAntes = descreverItens(pedido.getItens());
        String subtotalAntes = formatar(pedido.getSubtotal());
        String totalAntes = formatar(pedido.getValorTotal());

        pedido.setItens(montarItens(novosItens));
        recalcularValores(pedido);
        Pedido salvo = pedidoRepository.save(pedido);

        List<AlteracaoCampo> campos = AuditoriaService.diferencas(
                Map.of("itens", itensAntes, "subtotal", subtotalAntes, "valorTotal", totalAntes),
                Map.of("itens", descreverItens(salvo.getItens()),
                        "subtotal", formatar(salvo.getSubtotal()),
                        "valorTotal", formatar(salvo.getValorTotal())));
        auditoriaService.registrar(TipoOperacao.ALTERACAO_PEDIDO, ENTIDADE, salvo.getId(), campos, null);
        return salvo;
    }

    @PreAuthorize("hasAnyRole('ATENDENTE','GERENTE')")
    public Pedido atualizarStatus(String id, StatusPedido novoStatus) {
        if (novoStatus == null) {
            throw new RegraNegocioException("Informe o novo status.");
        }
        Pedido pedido = buscarPedido(id);
        StatusPedido atual = pedido.getStatus();

        if (atual == StatusPedido.CANCELADO) {
            throw new RegraNegocioException("Pedido cancelado não pode ter o status alterado.");
        }
        if (novoStatus == StatusPedido.CANCELADO) {
            throw new RegraNegocioException("Para cancelar utilize a operação de cancelamento (exclusiva do GERENTE).");
        }
        if (!atual.podeAvancarPara(novoStatus, pedido.getTipoEntrega())) {
            String permitidos = atual.proximosPermitidos(pedido.getTipoEntrega()).stream()
                    .map(Enum::name).collect(Collectors.joining(", "));
            throw new RegraNegocioException("Transição inválida: " + atual + " → " + novoStatus
                    + (permitidos.isEmpty() ? ". O pedido já está encerrado." : ". Permitido: " + permitidos + "."));
        }

        if (novoStatus == StatusPedido.FINALIZADO
                && (pedido.getDataHoraSaida() == null || pedido.getResponsavelSaida() == null)) {
            throw new RegraNegocioException("Não é possível finalizar um pedido sem registro de saída.");
        }

        String usuario = UsuarioLogado.username();
        LocalDateTime agora = DataHora.agora();
        List<AlteracaoCampo> campos = new ArrayList<>();
        campos.add(new AlteracaoCampo("status", atual.name(), novoStatus.name()));

        pedido.setStatus(novoStatus);
        pedido.getHistorico().add(new HistoricoStatus(novoStatus, agora, usuario));

        if (novoStatus.isSaida()) {
            pedido.setDataHoraSaida(agora);
            pedido.setResponsavelSaida(usuario);
            campos.add(new AlteracaoCampo("dataHoraSaida", null, agora.toString()));
            campos.add(new AlteracaoCampo("responsavelSaida", null, usuario));
        }
        if (novoStatus == StatusPedido.FINALIZADO) {
            pedido.setDataHoraFinalizacao(agora);
            pedido.setResponsavelFinalizacao(usuario);
            campos.add(new AlteracaoCampo("dataHoraFinalizacao", null, agora.toString()));
        }

        Pedido salvo = pedidoRepository.save(pedido);
        auditoriaService.registrar(TipoOperacao.MUDANCA_STATUS_PEDIDO, ENTIDADE, salvo.getId(), campos, null);
        return salvo;
    }

    @PreAuthorize("hasRole('GERENTE')")
    public Pedido cancelar(String id, String justificativa) {
        String motivo = justificativa == null ? "" : justificativa.trim();
        if (motivo.length() < TAMANHO_MINIMO_JUSTIFICATIVA) {
            throw new RegraNegocioException("A justificativa do cancelamento é obrigatória (mínimo de "
                    + TAMANHO_MINIMO_JUSTIFICATIVA + " caracteres).");
        }
        Pedido pedido = buscarPedido(id);
        StatusPedido atual = pedido.getStatus();
        if (atual == StatusPedido.CANCELADO) {
            throw new RegraNegocioException("O pedido já está cancelado.");
        }
        if (!atual.permiteCancelamento()) {
            throw new RegraNegocioException("Não é possível cancelar: o pedido já saiu para entrega/retirada ("
                    + atual + ").");
        }

        String usuario = UsuarioLogado.username();
        LocalDateTime agora = DataHora.agora();

        pedido.setStatus(StatusPedido.CANCELADO);
        pedido.setDataHoraCancelamento(agora);
        pedido.setResponsavelCancelamento(usuario);
        pedido.setJustificativaCancelamento(motivo);
        pedido.getHistorico().add(new HistoricoStatus(StatusPedido.CANCELADO, agora, usuario));
        Pedido salvo = pedidoRepository.save(pedido);

        List<AlteracaoCampo> campos = List.of(
                new AlteracaoCampo("status", atual.name(), StatusPedido.CANCELADO.name()),
                new AlteracaoCampo("dataHoraCancelamento", null, agora.toString()),
                new AlteracaoCampo("responsavelCancelamento", null, usuario));
        auditoriaService.registrar(TipoOperacao.CANCELAMENTO_PEDIDO, ENTIDADE, salvo.getId(), campos, motivo);
        return salvo;
    }

    @PreAuthorize("hasRole('GERENTE')")
    public Pedido aplicarDesconto(String id, BigDecimal percentual, String motivo) {
        if (percentual == null || percentual.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RegraNegocioException("O percentual de desconto deve ser maior que zero.");
        }
        if (percentual.compareTo(DESCONTO_MAXIMO) > 0) {
            throw new RegraNegocioException("O desconto máximo permitido é de " + DESCONTO_MAXIMO + "%.");
        }
        Pedido pedido = buscarPedido(id);
        if (pedido.getStatus() == StatusPedido.CANCELADO) {
            throw new RegraNegocioException("Pedido cancelado não pode receber desconto.");
        }
        if (!pedido.getStatus().permiteDesconto()) {
            throw new RegraNegocioException("O desconto só pode ser aplicado antes da saída para entrega/retirada.");
        }

        String pctAntes = formatar(pedido.getPercentualDesconto());
        String descontoAntes = formatar(pedido.getValorDesconto());
        String totalAntes = formatar(pedido.getValorTotal());

        pedido.setPercentualDesconto(percentual.setScale(2, RoundingMode.HALF_UP));
        recalcularValores(pedido);
        pedido.setDescontoAplicadoPor(UsuarioLogado.username());
        pedido.setDataHoraDesconto(DataHora.agora());
        Pedido salvo = pedidoRepository.save(pedido);

        List<AlteracaoCampo> campos = List.of(
                new AlteracaoCampo("percentualDesconto", pctAntes, formatar(salvo.getPercentualDesconto())),
                new AlteracaoCampo("valorDesconto", descontoAntes, formatar(salvo.getValorDesconto())),
                new AlteracaoCampo("valorTotal", totalAntes, formatar(salvo.getValorTotal())));
        String justificativa = (motivo == null || motivo.isBlank()) ? null : motivo.trim();
        auditoriaService.registrar(TipoOperacao.DESCONTO_PEDIDO, ENTIDADE, salvo.getId(), campos, justificativa);
        return salvo;
    }

    static void recalcularValores(Pedido pedido) {
        BigDecimal subtotal = pedido.getItens().stream()
                .map(ItemPedido::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal percentual = pedido.getPercentualDesconto() == null
                ? BigDecimal.ZERO : pedido.getPercentualDesconto();
        BigDecimal valorDesconto = subtotal.multiply(percentual)
                .divide(CEM, 2, RoundingMode.HALF_UP);

        pedido.setSubtotal(subtotal);
        pedido.setValorDesconto(valorDesconto);
        pedido.setValorTotal(subtotal.subtract(valorDesconto));
    }

    private List<ItemPedido> montarItens(List<ItemPedidoRequest> itens) {
        if (itens == null || itens.isEmpty()) {
            throw new RegraNegocioException("O pedido precisa de pelo menos um item.");
        }
        List<ItemPedido> resultado = new ArrayList<>();
        for (ItemPedidoRequest req : itens) {
            if (req == null || req.produtoId() == null || req.produtoId().isBlank()) {
                throw new RegraNegocioException("Item sem produto informado.");
            }
            if (req.quantidade() < 1 || req.quantidade() > QUANTIDADE_MAXIMA_ITEM) {
                throw new RegraNegocioException("Quantidade inválida (de 1 a " + QUANTIDADE_MAXIMA_ITEM + ").");
            }
            Produto produto = produtoRepository.findById(req.produtoId())
                    .orElseThrow(() -> new RegraNegocioException("Produto não encontrado: " + req.produtoId()));
            if (Boolean.FALSE.equals(produto.getAtivo())) {
                throw new RegraNegocioException("Produto inativo: " + produto.getNome());
            }
            if (produto.getPreco() == null || produto.getPreco() <= 0) {
                throw new RegraNegocioException("Produto sem preço válido: " + produto.getNome());
            }
            BigDecimal preco = BigDecimal.valueOf(produto.getPreco()).setScale(2, RoundingMode.HALF_UP);
            resultado.add(new ItemPedido(produto.getId(), produto.getNome(), req.quantidade(), preco));
        }
        return resultado;
    }

    private Pedido buscarPedido(String id) {
        return pedidoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pedido não encontrado."));
    }

    private static String descreverItens(List<ItemPedido> itens) {
        return itens.stream().map(ItemPedido::toString).collect(Collectors.joining("; "));
    }

    private static String formatar(BigDecimal valor) {
        return valor == null ? null : valor.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
