package com.umc.pediupatrao.entity;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "clientes")
public class Cliente {
    @Id
    private String id;
    private String nome;
    private String telefone;

    private String cep;
    private String logradouro;
    private String numero;
    private String complemento;
    private String bairro;
    private String cidade;
    private String estado;

    private String endereco;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public String getTelefone() { return telefone; }
    public void setTelefone(String telefone) { this.telefone = telefone; }

    public String getCep() { return cep; }
    public void setCep(String cep) { this.cep = cep; }

    public String getLogradouro() { return logradouro; }
    public void setLogradouro(String logradouro) { this.logradouro = logradouro; }

    public String getNumero() { return numero; }
    public void setNumero(String numero) { this.numero = numero; }

    public String getComplemento() { return complemento; }
    public void setComplemento(String complemento) { this.complemento = complemento; }

    public String getBairro() { return bairro; }
    public void setBairro(String bairro) { this.bairro = bairro; }

    public String getCidade() { return cidade; }
    public void setCidade(String cidade) { this.cidade = cidade; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public String getEndereco() { return endereco; }
    public void setEndereco(String endereco) { this.endereco = endereco; }

    public Map<String, String> paraMapaAuditoria() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("nome", nome);
        m.put("telefone", telefone);
        m.put("cep", cep);
        m.put("logradouro", logradouro);
        m.put("numero", numero);
        m.put("complemento", complemento);
        m.put("bairro", bairro);
        m.put("cidade", cidade);
        m.put("estado", estado);
        return m;
    }

    public void copiarDadosCadastrais(Cliente origem) {
        this.nome = origem.getNome();
        this.telefone = origem.getTelefone();
        this.cep = origem.getCep();
        this.logradouro = origem.getLogradouro();
        this.numero = origem.getNumero();
        this.complemento = origem.getComplemento();
        this.bairro = origem.getBairro();
        this.cidade = origem.getCidade();
        this.estado = origem.getEstado();
    }
}
