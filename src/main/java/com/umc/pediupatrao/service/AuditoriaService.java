package com.umc.pediupatrao.service;

import com.umc.pediupatrao.dto.AuditoriaView;
import com.umc.pediupatrao.entity.AlteracaoCampo;
import com.umc.pediupatrao.entity.Auditoria;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.repository.AuditoriaRepository;
import com.umc.pediupatrao.security.UsuarioLogado;
import com.umc.pediupatrao.util.DataHora;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class AuditoriaService {
    private static final Logger log = LoggerFactory.getLogger(AuditoriaService.class);

    private static final Set<String> CAMPOS_CREDENCIAIS = Set.of("password", "senha", "token", "_csrf");

    private static final Set<String> CAMPOS_MASCARADOS = Set.of("telefone");

    private static final int LIMITE_CONSULTA = 500;

    private final AuditoriaRepository auditoriaRepository;
    private final MongoTemplate mongoTemplate;

    public AuditoriaService(AuditoriaRepository auditoriaRepository, MongoTemplate mongoTemplate) {
        this.auditoriaRepository = auditoriaRepository;
        this.mongoTemplate = mongoTemplate;
    }

    public Auditoria registrar(TipoOperacao operacao, String entidade, String entidadeId,
                               List<AlteracaoCampo> alteracoes, String justificativa) {
        return registrar(operacao, entidade, entidadeId, alteracoes, justificativa, null);
    }

    public Auditoria registrar(TipoOperacao operacao, String entidade, String entidadeId,
                               List<AlteracaoCampo> alteracoes, String justificativa, String detalhe) {
        Auditoria registro = new Auditoria(
                DataHora.agora(),
                UsuarioLogado.username(),
                UsuarioLogado.perfil(),
                operacao,
                entidade,
                entidadeId,
                protegerCredenciais(alteracoes),
                justificativa,
                detalhe);
        Auditoria salvo = auditoriaRepository.save(registro);

        log.info("Auditoria: {} {} id={} por {}", operacao, entidade, entidadeId, registro.getUsuario());
        return salvo;
    }

    public void registrarAcessoNegado(String usuario, String perfil, String metodo, String uri, String motivo) {
        Auditoria registro = new Auditoria(
                DataHora.agora(),
                usuario,
                perfil,
                TipoOperacao.ACESSO_NEGADO,
                "Sistema",
                null,
                List.of(),
                null,
                metodo + " " + uri + (motivo != null ? " — " + motivo : ""));
        auditoriaRepository.save(registro);
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public List<AuditoriaView> consultar(String entidade, String usuario, TipoOperacao operacao, String entidadeId) {
        Query query = new Query();
        if (temTexto(entidade)) {
            query.addCriteria(Criteria.where("entidade").is(entidade.trim()));
        }
        if (temTexto(usuario)) {
            query.addCriteria(Criteria.where("usuario").is(usuario.trim()));
        }
        if (operacao != null) {
            query.addCriteria(Criteria.where("operacao").is(operacao));
        }
        if (temTexto(entidadeId)) {
            query.addCriteria(Criteria.where("entidadeId").is(entidadeId.trim()));
        }
        query.with(Sort.by(Sort.Direction.DESC, "dataHora")).limit(LIMITE_CONSULTA);

        return mongoTemplate.find(query, Auditoria.class).stream()
                .map(AuditoriaService::paraView)
                .toList();
    }

    public static List<AlteracaoCampo> diferencas(Map<String, String> antes, Map<String, String> depois) {
        Set<String> campos = new LinkedHashSet<>();
        if (antes != null) campos.addAll(antes.keySet());
        if (depois != null) campos.addAll(depois.keySet());

        List<AlteracaoCampo> alteracoes = new ArrayList<>();
        for (String campo : campos) {
            String anterior = normalizar(antes != null ? antes.get(campo) : null);
            String novo = normalizar(depois != null ? depois.get(campo) : null);
            if (!Objects.equals(anterior, novo)) {
                alteracoes.add(new AlteracaoCampo(campo, anterior, novo));
            }
        }
        return alteracoes;
    }

    public static String mascararTelefone(String telefone) {
        if (telefone == null) return null;
        String digitos = telefone.replaceAll("\\D", "");
        if (digitos.length() <= 4) return "****";
        return "***-" + digitos.substring(digitos.length() - 4);
    }

    private static List<AlteracaoCampo> protegerCredenciais(List<AlteracaoCampo> alteracoes) {
        if (alteracoes == null) return List.of();
        return alteracoes.stream()
                .map(a -> CAMPOS_CREDENCIAIS.contains(a.getCampo().toLowerCase())
                        ? new AlteracaoCampo(a.getCampo(), "[protegido]", "[alterada]")
                        : a)
                .toList();
    }

    private static AuditoriaView paraView(Auditoria a) {
        List<AlteracaoCampo> alteracoes = a.getAlteracoes() == null ? List.of() : a.getAlteracoes().stream()
                .map(c -> CAMPOS_MASCARADOS.contains(c.getCampo())
                        ? new AlteracaoCampo(c.getCampo(), mascararTelefone(c.getValorAnterior()),
                                mascararTelefone(c.getValorNovo()))
                        : c)
                .toList();
        return new AuditoriaView(
                a.getId(), a.getDataHora(), a.getUsuario(), a.getPerfil(),
                a.getOperacao() != null ? a.getOperacao().name() : null,
                a.getOperacao() != null ? a.getOperacao().getDescricao() : null,
                a.getEntidade(), a.getEntidadeId(), alteracoes, a.getJustificativa(), a.getDetalhe());
    }

    private static String normalizar(String valor) {
        return (valor == null || valor.isBlank()) ? null : valor.trim();
    }

    private static boolean temTexto(String s) {
        return s != null && !s.isBlank();
    }
}
