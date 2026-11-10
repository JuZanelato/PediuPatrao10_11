package com.umc.pediupatrao.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.umc.pediupatrao.config.SecurityConfig;
import com.umc.pediupatrao.security.AcessoNegadoHandler;
import com.umc.pediupatrao.service.AuditoriaService;
import com.umc.pediupatrao.service.PedidoService;
import com.umc.pediupatrao.service.UsuarioDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PedidoController.class)
@Import({SecurityConfig.class, AcessoNegadoHandler.class})
class PedidoControllerSecurityTest {
    private static final String CANCELAR = "{\"justificativa\":\"Cliente desistiu do pedido\"}";
    private static final String DESCONTO = "{\"percentual\":10}";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PedidoService pedidoService;

    @MockitoBean
    private AuditoriaService auditoriaService;

    @MockitoBean
    private UsuarioDetailsService usuarioDetailsService;

    @Test
    void semLoginRecebe401() throws Exception {
        mvc.perform(get("/api/pedidos")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "atendente01", roles = "ATENDENTE")
    void atendenteNaoCancela() throws Exception {
        mvc.perform(post("/api/pedidos/p1/cancelar").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(CANCELAR))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminNaoAplicaDesconto() throws Exception {
        mvc.perform(post("/api/pedidos/p1/desconto").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(DESCONTO))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminNaoRegistraPedido() throws Exception {
        mvc.perform(post("/api/pedidos").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    void gerenteCancela() throws Exception {
        mvc.perform(post("/api/pedidos/p1/cancelar").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(CANCELAR))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    void semTokenCsrfEhBloqueado() throws Exception {
        mvc.perform(post("/api/pedidos/p1/cancelar")
                        .contentType(MediaType.APPLICATION_JSON).content(CANCELAR))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminConsultaPedidos() throws Exception {
        mvc.perform(get("/api/pedidos")).andExpect(status().isOk());
    }
}
