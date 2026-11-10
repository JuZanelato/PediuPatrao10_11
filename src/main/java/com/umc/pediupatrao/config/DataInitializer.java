package com.umc.pediupatrao.config;

import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer {
    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Bean
    CommandLineRunner initDatabase(UsuarioRepository usuarios, ProdutoRepository produtos,
                                   PasswordEncoder encoder,
                                   @Value("${app.seguranca.senha-inicial}") String senhaInicial) {
        return args -> {
            criarUsuarioSeNaoExistir(usuarios, encoder, "admin", Perfil.ADMIN, senhaInicial);
            criarUsuarioSeNaoExistir(usuarios, encoder, "gerente01", Perfil.GERENTE, senhaInicial);
            criarUsuarioSeNaoExistir(usuarios, encoder, "atendente01", Perfil.ATENDENTE, senhaInicial);

            for (Usuario u : usuarios.findAll()) {
                if (Perfil.de(u.getRole()).isEmpty()) {
                    Perfil novo = "admin".equals(u.getUsername()) ? Perfil.ADMIN : Perfil.ATENDENTE;
                    u.setRole(novo.name());
                    usuarios.save(u);
                    log.info("Perfil do usuário '{}' migrado para {}", u.getUsername(), novo);
                }
            }

            if (produtos.count() == 0) {
                produtos.save(produto("Pizza Calabresa", "Pizza", 54.90));
                produtos.save(produto("Pizza Margherita", "Pizza", 52.00));
                produtos.save(produto("Pizza Portuguesa", "Pizza", 58.50));
                produtos.save(produto("Esfiha de Carne", "Esfiha", 6.50));
                produtos.save(produto("Refrigerante 2L", "Bebida", 14.00));
                log.info("Produtos de exemplo cadastrados.");
            }
        };
    }

    private static void criarUsuarioSeNaoExistir(UsuarioRepository repo, PasswordEncoder encoder,
                                                 String username, Perfil perfil, String senha) {
        if (repo.findByUsername(username).isEmpty()) {
            Usuario u = new Usuario();
            u.setUsername(username);
            u.setPassword(encoder.encode(senha));
            u.setRole(perfil.name());
            repo.save(u);
            log.info("Usuário de demonstração criado: {} ({})", username, perfil);
        }
    }

    private static Produto produto(String nome, String tipo, double preco) {
        Produto p = new Produto();
        p.setNome(nome);
        p.setTipo(tipo);
        p.setPreco(preco);
        p.setAtivo(true);
        return p;
    }
}
