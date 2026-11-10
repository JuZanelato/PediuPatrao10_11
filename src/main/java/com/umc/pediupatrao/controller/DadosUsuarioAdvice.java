package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.security.UsuarioLogado;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice(assignableTypes = HomeController.class)
public class DadosUsuarioAdvice {
    @ModelAttribute("usuarioLogado")
    public String usuarioLogado(Authentication authentication) {
        return authentication != null ? authentication.getName() : null;
    }

    @ModelAttribute("perfilLogado")
    public String perfilLogado(Authentication authentication) {
        return UsuarioLogado.perfil(authentication);
    }
}
