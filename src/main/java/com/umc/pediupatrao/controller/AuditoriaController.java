package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.dto.AuditoriaView;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.service.AuditoriaService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auditoria")
public class AuditoriaController {
    private final AuditoriaService auditoriaService;

    public AuditoriaController(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    @GetMapping
    public List<AuditoriaView> consultar(@RequestParam(required = false) String entidade,
                                         @RequestParam(required = false) String usuario,
                                         @RequestParam(required = false) TipoOperacao operacao,
                                         @RequestParam(required = false) String entidadeId) {
        return auditoriaService.consultar(entidade, usuario, operacao, entidadeId);
    }
}
