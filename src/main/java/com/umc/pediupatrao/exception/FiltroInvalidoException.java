package com.umc.pediupatrao.exception;

/**
 * Lançada quando os parâmetros de consulta do dashboard são inválidos
 * (período invertido, intervalo muito longo, limite fora da faixa etc.).
 */
public class FiltroInvalidoException extends RuntimeException {
    public FiltroInvalidoException(String mensagem) {
        super(mensagem);
    }
}
