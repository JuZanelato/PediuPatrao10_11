package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.dto.UsuarioForm;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.service.AuditoriaService;
import com.umc.pediupatrao.service.ClienteService;
import com.umc.pediupatrao.service.ProdutoService;
import com.umc.pediupatrao.service.UsuarioService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class HomeController {
    private final ClienteService clienteService;
    private final ProdutoService produtoService;
    private final UsuarioService usuarioService;
    private final AuditoriaService auditoriaService;

    public HomeController(ClienteService clienteService, ProdutoService produtoService,
                          UsuarioService usuarioService, AuditoriaService auditoriaService) {
        this.clienteService = clienteService;
        this.produtoService = produtoService;
        this.usuarioService = usuarioService;
        this.auditoriaService = auditoriaService;
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("content", "home :: content");
        return "layout";
    }

    @GetMapping("/usuarios")
    public String listarUsuarios(Model model) {
        model.addAttribute("usuarios", usuarioService.listarTodos());
        model.addAttribute("content", "usuarios/lista :: content");
        return "layout";
    }

    @GetMapping("/usuarios/novo")
    public String novoUsuarioForm(Model model) {
        return formularioUsuario(model, new UsuarioForm());
    }

    @GetMapping("/usuarios/editar/{id}")
    public String editarUsuarioForm(@PathVariable String id, Model model, RedirectAttributes redirectAttributes) {
        return usuarioService.buscarPorId(id)
                .map(u -> formularioUsuario(model, new UsuarioForm(u.getId(), u.getUsername(), u.getRole())))
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("erro", "Usuário não encontrado.");
                    return "redirect:/usuarios";
                });
    }

    @PostMapping("/usuarios/salvar")
    public String salvarUsuario(@ModelAttribute("usuario") UsuarioForm usuario, Model model,
                                RedirectAttributes redirectAttributes) {
        try {
            usuarioService.criar(usuario);
        } catch (RegraNegocioException e) {
            model.addAttribute("erro", e.getMessage());
            usuario.setPassword(null);
            return formularioUsuario(model, usuario);
        }
        redirectAttributes.addFlashAttribute("sucesso", "Usuário criado com sucesso!");
        return "redirect:/usuarios";
    }

    @PostMapping("/usuarios/editar/{id}")
    public String atualizarUsuario(@PathVariable String id, @ModelAttribute("usuario") UsuarioForm usuario,
                                   Model model, RedirectAttributes redirectAttributes) {
        try {
            usuarioService.atualizar(id, usuario);
        } catch (RegraNegocioException | RecursoNaoEncontradoException e) {
            model.addAttribute("erro", e.getMessage());
            usuario.setId(id);
            usuario.setPassword(null);
            return formularioUsuario(model, usuario);
        }
        redirectAttributes.addFlashAttribute("sucesso", "Usuário atualizado com sucesso!");
        return "redirect:/usuarios";
    }

    @PostMapping("/usuarios/deletar/{id}")
    public String deletarUsuario(@PathVariable String id, RedirectAttributes redirectAttributes) {
        try {
            usuarioService.excluir(id);
            redirectAttributes.addFlashAttribute("sucesso", "Usuário removido com sucesso!");
        } catch (RegraNegocioException | RecursoNaoEncontradoException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/usuarios";
    }

    private String formularioUsuario(Model model, UsuarioForm form) {
        model.addAttribute("usuario", form);
        model.addAttribute("perfis", Perfil.values());
        model.addAttribute("content", "usuarios/form :: content");
        return "layout";
    }

    @GetMapping("/pedidos")
    public String pedidos(Model model) {
        model.addAttribute("content", "pedidos :: content");
        return "layout";
    }

    @GetMapping("/produtos")
    public String produtos(Model model) {
        model.addAttribute("produtos", produtoService.listarProdutos());
        model.addAttribute("content", "produtos/lista :: content");
        return "layout";
    }

    @GetMapping("/clientes")
    public String clientes(Model model) {
        model.addAttribute("content", "clientes/lista :: content");
        return "layout";
    }

    @GetMapping("/clientes/novo")
    public String novoClienteForm(Model model) {
        return formularioCliente(model, new Cliente());
    }

    @PostMapping("/clientes/salvar")
    public String salvarCliente(@ModelAttribute Cliente cliente, Model model, RedirectAttributes redirectAttributes) {
        try {
            cliente.setId(null);
            clienteService.cadastrar(cliente);
        } catch (RegraNegocioException e) {
            model.addAttribute("erro", e.getMessage());
            return formularioCliente(model, cliente);
        }
        redirectAttributes.addFlashAttribute("sucesso", "Cliente criado com sucesso!");
        return "redirect:/clientes";
    }

    @GetMapping("/clientes/editar/{id}")
    public String editarClienteForm(@PathVariable String id, Model model, RedirectAttributes redirectAttributes) {
        return clienteService.buscarPorId(id)
                .map(c -> formularioCliente(model, c))
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("erro", "Cliente não encontrado.");
                    return "redirect:/clientes";
                });
    }

    @PostMapping("/clientes/editar/{id}")
    public String atualizarCliente(@PathVariable String id, @ModelAttribute Cliente cliente, Model model,
                                   RedirectAttributes redirectAttributes) {
        try {
            clienteService.atualizar(id, cliente);
        } catch (RegraNegocioException | RecursoNaoEncontradoException e) {
            model.addAttribute("erro", e.getMessage());
            cliente.setId(id);
            return formularioCliente(model, cliente);
        }
        redirectAttributes.addFlashAttribute("sucesso", "Cliente atualizado com sucesso!");
        return "redirect:/clientes";
    }

    private String formularioCliente(Model model, Cliente cliente) {
        model.addAttribute("cliente", cliente);
        model.addAttribute("content", "clientes/form :: content");
        return "layout";
    }

    @GetMapping("/auditoria")
    public String auditoria(@RequestParam(required = false) String entidade,
                            @RequestParam(required = false) String usuario,
                            @RequestParam(required = false) TipoOperacao operacao,
                            @RequestParam(required = false) String entidadeId,
                            Model model) {
        model.addAttribute("registros", auditoriaService.consultar(entidade, usuario, operacao, entidadeId));
        model.addAttribute("operacoes", TipoOperacao.values());
        model.addAttribute("filtroEntidade", entidade);
        model.addAttribute("filtroUsuario", usuario);
        model.addAttribute("filtroOperacao", operacao);
        model.addAttribute("filtroEntidadeId", entidadeId);
        model.addAttribute("content", "auditoria/lista :: content");
        return "layout";
    }

    @GetMapping("/configuracoes")
    public String configuracoes(Model model) {
        model.addAttribute("content", "configuracoes :: content");
        return "layout";
    }

    @GetMapping("/admin")
    public String admin() {
        return "admin";
    }
}
