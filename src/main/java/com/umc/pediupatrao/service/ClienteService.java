package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.AlteracaoCampo;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.ClienteRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class ClienteService {
    static final String ENTIDADE = "Cliente";

    private final ClienteRepository clienteRepository;
    private final AuditoriaService auditoriaService;

    public ClienteService(ClienteRepository clienteRepository, AuditoriaService auditoriaService) {
        this.clienteRepository = clienteRepository;
        this.auditoriaService = auditoriaService;
    }

    @PreAuthorize("hasAnyRole('ATENDENTE','GERENTE')")
    public List<Cliente> listarClientes() {
        return clienteRepository.findAll();
    }

    @PreAuthorize("hasAnyRole('ATENDENTE','GERENTE')")
    public Optional<Cliente> buscarPorId(String id) {
        return clienteRepository.findById(id);
    }

    @PreAuthorize("hasRole('ATENDENTE')")
    public Cliente cadastrar(Cliente dados) {
        validar(dados);
        Cliente novo = new Cliente();
        novo.copiarDadosCadastrais(dados);
        Cliente salvo = clienteRepository.save(novo);

        List<AlteracaoCampo> campos = AuditoriaService.diferencas(Map.of(), salvo.paraMapaAuditoria());
        auditoriaService.registrar(TipoOperacao.INCLUSAO_CLIENTE, ENTIDADE, salvo.getId(), campos, null);
        return salvo;
    }

    @PreAuthorize("hasRole('ATENDENTE')")
    public Cliente atualizar(String id, Cliente dados) {
        validar(dados);
        Cliente existente = clienteRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente não encontrado."));

        Map<String, String> antes = existente.paraMapaAuditoria();
        existente.copiarDadosCadastrais(dados);
        List<AlteracaoCampo> alteracoes = AuditoriaService.diferencas(antes, existente.paraMapaAuditoria());

        if (alteracoes.isEmpty()) {
            return existente;
        }
        Cliente salvo = clienteRepository.save(existente);
        auditoriaService.registrar(TipoOperacao.ALTERACAO_CLIENTE, ENTIDADE, salvo.getId(), alteracoes, null);
        return salvo;
    }

    @PreAuthorize("hasRole('GERENTE')")
    public void excluir(String id) {
        Cliente existente = clienteRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente não encontrado."));
        clienteRepository.delete(existente);
        List<AlteracaoCampo> campos = AuditoriaService.diferencas(existente.paraMapaAuditoria(), Map.of());
        auditoriaService.registrar(TipoOperacao.EXCLUSAO_CLIENTE, ENTIDADE, id, campos, null);
    }

    private void validar(Cliente c) {
        if (c == null || c.getNome() == null || c.getNome().isBlank()) {
            throw new RegraNegocioException("O nome do cliente é obrigatório.");
        }
        if (c.getTelefone() == null || c.getTelefone().replaceAll("\\D", "").length() < 10) {
            throw new RegraNegocioException("Informe um telefone válido com DDD.");
        }
        if (c.getEstado() != null && !c.getEstado().isBlank() && c.getEstado().trim().length() != 2) {
            throw new RegraNegocioException("O estado deve ter 2 letras (ex.: SP).");
        }
    }
}
