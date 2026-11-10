package com.umc.pediupatrao.entity;

import java.util.EnumSet;
import java.util.Set;

public enum StatusPedido {
    RECEBIDO("Recebido"),
    EM_PREPARACAO("Em preparação"),
    PRONTO("Pronto"),
    SAIU_PARA_ENTREGA("Saiu para entrega"),
    RETIRADO("Retirado"),
    FINALIZADO("Finalizado"),
    CANCELADO("Cancelado");

    private final String descricao;

    StatusPedido(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }

    public Set<StatusPedido> proximosPermitidos(TipoEntrega tipoEntrega) {
        return switch (this) {
            case RECEBIDO -> EnumSet.of(EM_PREPARACAO);
            case EM_PREPARACAO -> EnumSet.of(PRONTO);
            case PRONTO -> tipoEntrega == TipoEntrega.RETIRADA
                    ? EnumSet.of(RETIRADO)
                    : EnumSet.of(SAIU_PARA_ENTREGA);
            case SAIU_PARA_ENTREGA, RETIRADO -> EnumSet.of(FINALIZADO);
            case FINALIZADO, CANCELADO -> EnumSet.noneOf(StatusPedido.class);
        };
    }

    public boolean podeAvancarPara(StatusPedido destino, TipoEntrega tipoEntrega) {
        return destino != null && proximosPermitidos(tipoEntrega).contains(destino);
    }

    public boolean antesDaSaida() {
        return this == RECEBIDO || this == EM_PREPARACAO || this == PRONTO;
    }

    public boolean permiteCancelamento() {
        return antesDaSaida();
    }

    public boolean permiteDesconto() {
        return antesDaSaida();
    }

    public boolean isSaida() {
        return this == SAIU_PARA_ENTREGA || this == RETIRADO;
    }

    public boolean isEncerrado() {
        return this == FINALIZADO || this == CANCELADO;
    }
}
