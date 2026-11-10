package com.umc.pediupatrao.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.entity.AlteracaoCampo;
import com.umc.pediupatrao.entity.Auditoria;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.repository.AuditoriaRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class AuditoriaServiceTest {
    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void diferencasRetornaSomenteCamposAlterados() {
        Map<String, String> antes = new LinkedHashMap<>();
        antes.put("logradouro", "Rua das Flores");
        antes.put("numero", "10");
        Map<String, String> depois = new LinkedHashMap<>();
        depois.put("logradouro", "Av. Brasil");
        depois.put("numero", "10");

        List<AlteracaoCampo> alteracoes = AuditoriaService.diferencas(antes, depois);

        assertEquals(1, alteracoes.size());
        assertEquals("logradouro", alteracoes.get(0).getCampo());
        assertEquals("Rua das Flores", alteracoes.get(0).getValorAnterior());
        assertEquals("Av. Brasil", alteracoes.get(0).getValorNovo());
    }

    @Test
    void mascaraTelefone() {
        assertEquals("***-4321", AuditoriaService.mascararTelefone("(11) 98765-4321"));
    }

    @Test
    void registraUsuarioPerfilENaoGravaSenha() {
        AuditoriaRepository repo = mock(AuditoriaRepository.class);
        when(repo.save(any(Auditoria.class))).thenAnswer(inv -> inv.getArgument(0));
        AuditoriaService service = new AuditoriaService(repo, mock(MongoTemplate.class));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        service.registrar(TipoOperacao.ALTERACAO_USUARIO, "Usuario", "u1",
                List.of(new AlteracaoCampo("senha", "segredo-antigo", "segredo-novo")), null);

        ArgumentCaptor<Auditoria> captor = ArgumentCaptor.forClass(Auditoria.class);
        verify(repo).save(captor.capture());
        Auditoria salvo = captor.getValue();
        assertEquals("admin", salvo.getUsuario());
        assertEquals("ADMIN", salvo.getPerfil());
        assertTrue(salvo.getDataHora() != null);
        assertEquals("[protegido]", salvo.getAlteracoes().get(0).getValorAnterior());
        assertEquals("[alterada]", salvo.getAlteracoes().get(0).getValorNovo());
    }
}
