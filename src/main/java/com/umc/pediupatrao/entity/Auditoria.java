package com.umc.pediupatrao.entity;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "auditoria")
public class Auditoria {
    @Id
    private String id;

    @Indexed
    private LocalDateTime dataHora;

    @Indexed
    private String usuario;
    private String perfil;

    @Indexed
    private TipoOperacao operacao;

    @Indexed
    private String entidade;
    private String entidadeId;

    private List<AlteracaoCampo> alteracoes;
    private String justificativa;
    private String detalhe;

    protected Auditoria() {
    }

    public Auditoria(LocalDateTime dataHora, String usuario, String perfil, TipoOperacao operacao,
                     String entidade, String entidadeId, List<AlteracaoCampo> alteracoes,
                     String justificativa, String detalhe) {
        this.dataHora = dataHora;
        this.usuario = usuario;
        this.perfil = perfil;
        this.operacao = operacao;
        this.entidade = entidade;
        this.entidadeId = entidadeId;
        this.alteracoes = alteracoes == null ? List.of() : List.copyOf(alteracoes);
        this.justificativa = justificativa;
        this.detalhe = detalhe;
    }

    public String getId() { return id; }
    public LocalDateTime getDataHora() { return dataHora; }
    public String getUsuario() { return usuario; }
    public String getPerfil() { return perfil; }
    public TipoOperacao getOperacao() { return operacao; }
    public String getEntidade() { return entidade; }
    public String getEntidadeId() { return entidadeId; }
    public List<AlteracaoCampo> getAlteracoes() { return alteracoes; }
    public String getJustificativa() { return justificativa; }
    public String getDetalhe() { return detalhe; }
}
