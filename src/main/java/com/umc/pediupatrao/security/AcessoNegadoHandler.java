package com.umc.pediupatrao.security;

import com.umc.pediupatrao.service.AuditoriaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

@Component
public class AcessoNegadoHandler implements AccessDeniedHandler {
    private static final Logger log = LoggerFactory.getLogger(AcessoNegadoHandler.class);

    private final AuditoriaService auditoriaService;

    public AcessoNegadoHandler(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException ex) throws IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String usuario = auth != null ? auth.getName() : "anônimo";
        String perfil = UsuarioLogado.perfil(auth);
        String motivo = ex instanceof CsrfException ? "token CSRF ausente ou inválido" : "perfil sem permissão";
        String uri = request.getRequestURI();

        log.warn("Acesso negado: usuario={} perfil={} {} {} ({})", usuario, perfil, request.getMethod(), uri, motivo);
        try {
            auditoriaService.registrarAcessoNegado(usuario, perfil, request.getMethod(), uri, motivo);
        } catch (RuntimeException e) {
            log.error("Falha ao registrar acesso negado na auditoria", e);
        }

        if (uri.startsWith(request.getContextPath() + "/api/")) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"status\":403,\"erro\":\"Forbidden\",\"mensagem\":\"Acesso negado: "
                    + motivo + ".\"}");
        } else {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
        }
    }
}
