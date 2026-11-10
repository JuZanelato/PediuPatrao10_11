package com.umc.pediupatrao.dto;

import com.umc.pediupatrao.entity.AlteracaoCampo;
import java.time.LocalDateTime;
import java.util.List;

public class AuditoriaView {
    private final String id;
    private final LocalDateTime dataHora;
    private final String usuario;
    private final String perfil;
    private final String operacao;
    private final String operacaoDescricao;
    private final String entidade;
    private final String entidadeId;
    private final List<AlteracaoCampo> alteracoes;
    private final String justificativa;
    private final String detalhe;

    public AuditoriaView(String id, LocalDateTime dataHora, String usuario, String perfil,
                         String operacao, String operacaoDescricao, String entidade, String entidadeId,
                         List<AlteracaoCampo> alteracoes, String justificativa, String detalhe) {
        this.id = id;
        this.dataHora = dataHora;
        this.usuario = usuario;
        this.perfil = perfil;
        this.operacao = operacao;
        this.operacaoDescricao = operacaoDescricao;
        this.entidade = entidade;
        this.entidadeId = entidadeId;
        this.alteracoes = alteracoes;
        this.justificativa = justificativa;
        this.detalhe = detalhe;
    }

    public String getId() { return id; }
    public LocalDateTime getDataHora() { return dataHora; }
    public String getUsuario() { return usuario; }
    public String getPerfil() { return perfil; }
    public String getOperacao() { return operacao; }
    public String getOperacaoDescricao() { return operacaoDescricao; }
    public String getEntidade() { return entidade; }
    public String getEntidadeId() { return entidadeId; }
    public List<AlteracaoCampo> getAlteracoes() { return alteracoes; }
    public String getJustificativa() { return justificativa; }
    public String getDetalhe() { return detalhe; }
}
