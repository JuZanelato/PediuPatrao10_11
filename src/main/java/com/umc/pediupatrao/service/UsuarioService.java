package com.umc.pediupatrao.service;

import com.umc.pediupatrao.dto.UsuarioForm;
import com.umc.pediupatrao.entity.AlteracaoCampo;
import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.UsuarioRepository;
import com.umc.pediupatrao.security.UsuarioLogado;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@PreAuthorize("hasRole('ADMIN')")
public class UsuarioService {
    static final String ENTIDADE = "Usuario";
    private static final int TAMANHO_MINIMO_SENHA = 6;

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoriaService;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
                          AuditoriaService auditoriaService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditoriaService = auditoriaService;
    }

    public Optional<Usuario> buscarPorId(String id) {
        return usuarioRepository.findById(id);
    }

    public List<Usuario> listarTodos() {
        return usuarioRepository.findAll();
    }

    public Usuario criar(UsuarioForm form) {
        String username = validarUsername(form.getUsername());
        Perfil perfil = validarPerfil(form.getRole());
        if (usuarioRepository.existsByUsername(username)) {
            throw new RegraNegocioException("Já existe um usuário com esse username.");
        }
        validarSenha(form.getPassword());

        Usuario usuario = new Usuario();
        usuario.setUsername(username);
        usuario.setRole(perfil.name());
        usuario.setPassword(passwordEncoder.encode(form.getPassword()));
        Usuario salvo = usuarioRepository.save(usuario);

        auditoriaService.registrar(TipoOperacao.INCLUSAO_USUARIO, ENTIDADE, salvo.getId(),
                AuditoriaService.diferencas(Map.of(), retrato(salvo)), null);
        return salvo;
    }

    public Usuario atualizar(String id, UsuarioForm form) {
        Usuario existente = usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));
        String username = validarUsername(form.getUsername());
        Perfil novoPerfil = validarPerfil(form.getRole());
        boolean ehOProprioUsuario = existente.getUsername().equals(UsuarioLogado.username());

        if (ehOProprioUsuario && !novoPerfil.name().equals(existente.getRole())) {
            throw new RegraNegocioException("Você não pode alterar o seu próprio perfil.");
        }
        if (ehOProprioUsuario && !username.equals(existente.getUsername())) {
            throw new RegraNegocioException("Você não pode alterar o seu próprio username.");
        }
        if (!username.equals(existente.getUsername()) && usuarioRepository.existsByUsername(username)) {
            throw new RegraNegocioException("Já existe um usuário com esse username.");
        }
        if (Perfil.ADMIN.name().equals(existente.getRole()) && novoPerfil != Perfil.ADMIN) {
            garantirOutroAdmin();
        }

        Map<String, String> antes = retrato(existente);
        existente.setUsername(username);
        existente.setRole(novoPerfil.name());

        List<AlteracaoCampo> alteracoes = new ArrayList<>(AuditoriaService.diferencas(antes, retrato(existente)));
        if (form.getPassword() != null && !form.getPassword().isBlank()) {
            validarSenha(form.getPassword());
            existente.setPassword(passwordEncoder.encode(form.getPassword()));
            alteracoes.add(new AlteracaoCampo("senha", "[protegido]", "[alterada]"));
        }

        Usuario salvo = usuarioRepository.save(existente);
        if (!alteracoes.isEmpty()) {
            auditoriaService.registrar(TipoOperacao.ALTERACAO_USUARIO, ENTIDADE, salvo.getId(), alteracoes, null);
        }
        return salvo;
    }

    public void excluir(String id) {
        Usuario existente = usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));
        if (existente.getUsername().equals(UsuarioLogado.username())) {
            throw new RegraNegocioException("Você não pode excluir o seu próprio usuário.");
        }
        if (Perfil.ADMIN.name().equals(existente.getRole())) {
            garantirOutroAdmin();
        }
        usuarioRepository.delete(existente);
        auditoriaService.registrar(TipoOperacao.EXCLUSAO_USUARIO, ENTIDADE, id,
                AuditoriaService.diferencas(retrato(existente), Map.of()), null);
    }

    private void garantirOutroAdmin() {
        if (usuarioRepository.countByRole(Perfil.ADMIN.name()) <= 1) {
            throw new RegraNegocioException("O sistema precisa manter pelo menos um ADMIN.");
        }
    }

    private static Map<String, String> retrato(Usuario u) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("username", u.getUsername());
        m.put("perfil", u.getRole());
        return m;
    }

    private static String validarUsername(String username) {
        if (username == null || !username.trim().matches("[A-Za-z0-9._-]{3,30}")) {
            throw new RegraNegocioException(
                    "Username inválido: use de 3 a 30 caracteres (letras, números, ponto, hífen ou _).");
        }
        return username.trim();
    }

    private static Perfil validarPerfil(String role) {
        return Perfil.de(role).orElseThrow(() ->
                new RegraNegocioException("Perfil inválido. Valores aceitos: ADMIN, GERENTE ou ATENDENTE."));
    }

    private static void validarSenha(String senha) {
        if (senha == null || senha.length() < TAMANHO_MINIMO_SENHA) {
            throw new RegraNegocioException("A senha deve ter pelo menos " + TAMANHO_MINIMO_SENHA + " caracteres.");
        }
    }
}
