package com.umc.pediupatrao.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.entity.AlteracaoCampo;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.repository.ClienteRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ClienteServiceTest {
    private Cliente cliente(String logradouro) {
        Cliente c = new Cliente();
        c.setId("c1");
        c.setNome("Maria");
        c.setTelefone("(11) 98765-4321");
        c.setLogradouro(logradouro);
        return c;
    }

    @Test
    @SuppressWarnings("unchecked")
    void alteracaoDeEnderecoEhAuditadaComValorAnteriorENovo() {
        ClienteRepository repo = mock(ClienteRepository.class);
        AuditoriaService auditoria = mock(AuditoriaService.class);
        when(repo.findById("c1")).thenReturn(Optional.of(cliente("Rua das Flores")));
        when(repo.save(any(Cliente.class))).thenAnswer(inv -> inv.getArgument(0));

        new ClienteService(repo, auditoria).atualizar("c1", cliente("Av. Brasil"));

        ArgumentCaptor<List<AlteracaoCampo>> captor = ArgumentCaptor.forClass(List.class);
        verify(auditoria).registrar(eq(TipoOperacao.ALTERACAO_CLIENTE), eq("Cliente"), eq("c1"),
                captor.capture(), isNull());
        List<AlteracaoCampo> alteracoes = captor.getValue();
        assertEquals(1, alteracoes.size());
        assertEquals("logradouro", alteracoes.get(0).getCampo());
        assertEquals("Rua das Flores", alteracoes.get(0).getValorAnterior());
        assertEquals("Av. Brasil", alteracoes.get(0).getValorNovo());
    }

    @Test
    void semAlteracaoNaoGeraAuditoria() {
        ClienteRepository repo = mock(ClienteRepository.class);
        AuditoriaService auditoria = mock(AuditoriaService.class);
        when(repo.findById("c1")).thenReturn(Optional.of(cliente("Rua A")));

        new ClienteService(repo, auditoria).atualizar("c1", cliente("Rua A"));

        verify(auditoria, never()).registrar(any(), anyString(), anyString(), anyList(), any());
    }
}
