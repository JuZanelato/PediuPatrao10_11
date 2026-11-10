package com.umc.pediupatrao.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.dto.ItemPedidoRequest;
import com.umc.pediupatrao.dto.NovoPedidoRequest;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoEntrega;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class PedidoServiceTest {
    private PedidoRepository pedidoRepository;
    private ClienteRepository clienteRepository;
    private ProdutoRepository produtoRepository;
    private AuditoriaService auditoriaService;
    private PedidoService service;

    @BeforeEach
    void configurar() {
        pedidoRepository = mock(PedidoRepository.class);
        clienteRepository = mock(ClienteRepository.class);
        produtoRepository = mock(ProdutoRepository.class);
        auditoriaService = mock(AuditoriaService.class);
        service = new PedidoService(pedidoRepository, clienteRepository, produtoRepository, auditoriaService);

        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> {
            Pedido p = inv.getArgument(0);
            if (p.getId() == null) p.setId("p1");
            return p;
        });

        Cliente cliente = new Cliente();
        cliente.setId("c1");
        cliente.setNome("Maria");
        when(clienteRepository.findById("c1")).thenReturn(Optional.of(cliente));

        Produto pizza = new Produto();
        pizza.setId("pz");
        pizza.setNome("Pizza Calabresa");
        pizza.setPreco(50.0);
        pizza.setAtivo(true);
        when(produtoRepository.findById("pz")).thenReturn(Optional.of(pizza));

        logarComo("atendente01", "ATENDENTE");
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    private static void logarComo(String usuario, String perfil) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                usuario, null, List.of(new SimpleGrantedAuthority("ROLE_" + perfil))));
    }

    private Pedido pedidoComStatus(StatusPedido status) {
        Pedido p = service.criarPedido(new NovoPedidoRequest("c1", TipoEntrega.ENTREGA,
                List.of(new ItemPedidoRequest("pz", 2)), null));
        p.setStatus(status);
        when(pedidoRepository.findById("p1")).thenReturn(Optional.of(p));
        return p;
    }

    @Test
    void criaPedidoCalculandoTotalNoServidorERegistrandoEntrada() {
        Pedido p = service.criarPedido(new NovoPedidoRequest("c1", TipoEntrega.ENTREGA,
                List.of(new ItemPedidoRequest("pz", 3)), "sem cebola"));

        assertEquals(new BigDecimal("150.00"), p.getSubtotal());
        assertEquals(new BigDecimal("150.00"), p.getValorTotal());
        assertEquals(StatusPedido.RECEBIDO, p.getStatus());
        assertEquals("atendente01", p.getResponsavelEntrada());
        assertNotNull(p.getDataHoraEntrada());
        verify(auditoriaService).registrar(eq(TipoOperacao.INCLUSAO_PEDIDO), eq("Pedido"), eq("p1"), anyList(), isNull());
    }

    @Test
    void naoPermiteTransicaoInvalida() {
        pedidoComStatus(StatusPedido.EM_PREPARACAO);
        assertThrows(RegraNegocioException.class, () -> service.atualizarStatus("p1", StatusPedido.FINALIZADO));
    }

    @Test
    void saidaRegistraDataHoraEResponsavel() {
        pedidoComStatus(StatusPedido.PRONTO);
        Pedido p = service.atualizarStatus("p1", StatusPedido.SAIU_PARA_ENTREGA);
        assertEquals("atendente01", p.getResponsavelSaida());
        assertNotNull(p.getDataHoraSaida());
    }

    @Test
    void naoAlteraStatusDePedidoCancelado() {
        pedidoComStatus(StatusPedido.CANCELADO);
        assertThrows(RegraNegocioException.class, () -> service.atualizarStatus("p1", StatusPedido.EM_PREPARACAO));
    }

    @Test
    void descontoLimitadoA20PorCento() {
        logarComo("gerente01", "GERENTE");
        pedidoComStatus(StatusPedido.RECEBIDO);
        assertThrows(RegraNegocioException.class,
                () -> service.aplicarDesconto("p1", new BigDecimal("20.01"), null));

        Pedido p = service.aplicarDesconto("p1", new BigDecimal("10"), "cliente fiel");
        assertEquals(new BigDecimal("10.00"), p.getValorDesconto());
        assertEquals(new BigDecimal("90.00"), p.getValorTotal());
        assertEquals("gerente01", p.getDescontoAplicadoPor());
        verify(auditoriaService).registrar(eq(TipoOperacao.DESCONTO_PEDIDO), eq("Pedido"), eq("p1"),
                anyList(), eq("cliente fiel"));
    }

    @Test
    void descontoNaoPermitidoAposSaida() {
        logarComo("gerente01", "GERENTE");
        pedidoComStatus(StatusPedido.SAIU_PARA_ENTREGA);
        assertThrows(RegraNegocioException.class,
                () -> service.aplicarDesconto("p1", new BigDecimal("5"), null));
    }

    @Test
    void cancelamentoExigeJustificativa() {
        logarComo("gerente01", "GERENTE");
        pedidoComStatus(StatusPedido.EM_PREPARACAO);
        assertThrows(RegraNegocioException.class, () -> service.cancelar("p1", "curta"));
        verify(auditoriaService, never()).registrar(eq(TipoOperacao.CANCELAMENTO_PEDIDO), anyString(),
                anyString(), anyList(), anyString());
    }

    @Test
    void cancelamentoPreservaDadosERegistraAuditoria() {
        logarComo("gerente01", "GERENTE");
        pedidoComStatus(StatusPedido.PRONTO);
        Pedido p = service.cancelar("p1", "Cliente desistiu por telefone");

        assertEquals(StatusPedido.CANCELADO, p.getStatus());
        assertEquals(new BigDecimal("100.00"), p.getValorTotal());
        assertEquals("gerente01", p.getResponsavelCancelamento());
        verify(auditoriaService).registrar(eq(TipoOperacao.CANCELAMENTO_PEDIDO), eq("Pedido"), eq("p1"),
                anyList(), eq("Cliente desistiu por telefone"));
    }

    @Test
    void naoCancelaAposSaida() {
        logarComo("gerente01", "GERENTE");
        pedidoComStatus(StatusPedido.RETIRADO);
        assertThrows(RegraNegocioException.class, () -> service.cancelar("p1", "Justificativa suficiente"));
    }
}
