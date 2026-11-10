package com.umc.pediupatrao.entity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StatusPedidoTest {
    @Test
    void fluxoNormalDeEntrega() {
        assertTrue(StatusPedido.RECEBIDO.podeAvancarPara(StatusPedido.EM_PREPARACAO, TipoEntrega.ENTREGA));
        assertTrue(StatusPedido.EM_PREPARACAO.podeAvancarPara(StatusPedido.PRONTO, TipoEntrega.ENTREGA));
        assertTrue(StatusPedido.PRONTO.podeAvancarPara(StatusPedido.SAIU_PARA_ENTREGA, TipoEntrega.ENTREGA));
        assertTrue(StatusPedido.SAIU_PARA_ENTREGA.podeAvancarPara(StatusPedido.FINALIZADO, TipoEntrega.ENTREGA));
    }

    @Test
    void retiradaNaoPodeSairParaEntrega() {
        assertTrue(StatusPedido.PRONTO.podeAvancarPara(StatusPedido.RETIRADO, TipoEntrega.RETIRADA));
        assertFalse(StatusPedido.PRONTO.podeAvancarPara(StatusPedido.SAIU_PARA_ENTREGA, TipoEntrega.RETIRADA));
    }

    @Test
    void naoPermitePularEtapas() {
        assertFalse(StatusPedido.EM_PREPARACAO.podeAvancarPara(StatusPedido.FINALIZADO, TipoEntrega.ENTREGA));
        assertFalse(StatusPedido.RECEBIDO.podeAvancarPara(StatusPedido.PRONTO, TipoEntrega.ENTREGA));
        assertFalse(StatusPedido.PRONTO.podeAvancarPara(StatusPedido.RECEBIDO, TipoEntrega.ENTREGA));
    }

    @Test
    void canceladoEFinalizadoSaoEstadosFinais() {
        for (StatusPedido destino : StatusPedido.values()) {
            assertFalse(StatusPedido.CANCELADO.podeAvancarPara(destino, TipoEntrega.ENTREGA));
            assertFalse(StatusPedido.FINALIZADO.podeAvancarPara(destino, TipoEntrega.ENTREGA));
        }
    }

    @Test
    void cancelamentoEDescontoSomenteAntesDaSaida() {
        assertTrue(StatusPedido.PRONTO.permiteCancelamento());
        assertTrue(StatusPedido.PRONTO.permiteDesconto());
        assertFalse(StatusPedido.SAIU_PARA_ENTREGA.permiteCancelamento());
        assertFalse(StatusPedido.RETIRADO.permiteDesconto());
    }
}
