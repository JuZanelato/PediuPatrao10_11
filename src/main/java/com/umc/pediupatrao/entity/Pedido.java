package com.umc.pediupatrao.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

@Document(collection = "pedidos")
public class Pedido {
    @Id
    private String id;

    @Version
    private Long versao;

    private String clienteId;
    private String clienteNome;

    private TipoEntrega tipoEntrega;
    private String observacao;

    private List<ItemPedido> itens = new ArrayList<>();

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal percentualDesconto = BigDecimal.ZERO;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal valorDesconto = BigDecimal.ZERO;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal valorTotal = BigDecimal.ZERO;

    private String descontoAplicadoPor;
    private LocalDateTime dataHoraDesconto;

    private StatusPedido status;
    private List<HistoricoStatus> historico = new ArrayList<>();

    private LocalDateTime dataHoraEntrada;
    private String responsavelEntrada;

    private LocalDateTime dataHoraSaida;
    private String responsavelSaida;

    private LocalDateTime dataHoraFinalizacao;
    private String responsavelFinalizacao;

    private LocalDateTime dataHoraCancelamento;
    private String responsavelCancelamento;
    private String justificativaCancelamento;

    @Transient
    public String getStatusDescricao() {
        return status != null ? status.getDescricao() : null;
    }

    @Transient
    public Set<StatusPedido> getProximosStatus() {
        return status != null ? status.proximosPermitidos(tipoEntrega) : Set.of();
    }

    @Transient
    public boolean isPodeCancelar() {
        return status != null && status.permiteCancelamento();
    }

    @Transient
    public boolean isPodeAplicarDesconto() {
        return status != null && status.permiteDesconto();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Long getVersao() { return versao; }
    public void setVersao(Long versao) { this.versao = versao; }

    public String getClienteId() { return clienteId; }
    public void setClienteId(String clienteId) { this.clienteId = clienteId; }

    public String getClienteNome() { return clienteNome; }
    public void setClienteNome(String clienteNome) { this.clienteNome = clienteNome; }

    public TipoEntrega getTipoEntrega() { return tipoEntrega; }
    public void setTipoEntrega(TipoEntrega tipoEntrega) { this.tipoEntrega = tipoEntrega; }

    public String getObservacao() { return observacao; }
    public void setObservacao(String observacao) { this.observacao = observacao; }

    public List<ItemPedido> getItens() { return itens; }
    public void setItens(List<ItemPedido> itens) { this.itens = itens; }

    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }

    public BigDecimal getPercentualDesconto() { return percentualDesconto; }
    public void setPercentualDesconto(BigDecimal percentualDesconto) { this.percentualDesconto = percentualDesconto; }

    public BigDecimal getValorDesconto() { return valorDesconto; }
    public void setValorDesconto(BigDecimal valorDesconto) { this.valorDesconto = valorDesconto; }

    public BigDecimal getValorTotal() { return valorTotal; }
    public void setValorTotal(BigDecimal valorTotal) { this.valorTotal = valorTotal; }

    public String getDescontoAplicadoPor() { return descontoAplicadoPor; }
    public void setDescontoAplicadoPor(String descontoAplicadoPor) { this.descontoAplicadoPor = descontoAplicadoPor; }

    public LocalDateTime getDataHoraDesconto() { return dataHoraDesconto; }
    public void setDataHoraDesconto(LocalDateTime dataHoraDesconto) { this.dataHoraDesconto = dataHoraDesconto; }

    public StatusPedido getStatus() { return status; }
    public void setStatus(StatusPedido status) { this.status = status; }

    public List<HistoricoStatus> getHistorico() { return historico; }
    public void setHistorico(List<HistoricoStatus> historico) { this.historico = historico; }

    public LocalDateTime getDataHoraEntrada() { return dataHoraEntrada; }
    public void setDataHoraEntrada(LocalDateTime dataHoraEntrada) { this.dataHoraEntrada = dataHoraEntrada; }

    public String getResponsavelEntrada() { return responsavelEntrada; }
    public void setResponsavelEntrada(String responsavelEntrada) { this.responsavelEntrada = responsavelEntrada; }

    public LocalDateTime getDataHoraSaida() { return dataHoraSaida; }
    public void setDataHoraSaida(LocalDateTime dataHoraSaida) { this.dataHoraSaida = dataHoraSaida; }

    public String getResponsavelSaida() { return responsavelSaida; }
    public void setResponsavelSaida(String responsavelSaida) { this.responsavelSaida = responsavelSaida; }

    public LocalDateTime getDataHoraFinalizacao() { return dataHoraFinalizacao; }
    public void setDataHoraFinalizacao(LocalDateTime dataHoraFinalizacao) { this.dataHoraFinalizacao = dataHoraFinalizacao; }

    public String getResponsavelFinalizacao() { return responsavelFinalizacao; }
    public void setResponsavelFinalizacao(String responsavelFinalizacao) { this.responsavelFinalizacao = responsavelFinalizacao; }

    public LocalDateTime getDataHoraCancelamento() { return dataHoraCancelamento; }
    public void setDataHoraCancelamento(LocalDateTime dataHoraCancelamento) { this.dataHoraCancelamento = dataHoraCancelamento; }

    public String getResponsavelCancelamento() { return responsavelCancelamento; }
    public void setResponsavelCancelamento(String responsavelCancelamento) { this.responsavelCancelamento = responsavelCancelamento; }

    public String getJustificativaCancelamento() { return justificativaCancelamento; }
    public void setJustificativaCancelamento(String justificativaCancelamento) { this.justificativaCancelamento = justificativaCancelamento; }
}
