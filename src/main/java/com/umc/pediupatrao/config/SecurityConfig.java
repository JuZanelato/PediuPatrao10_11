package com.umc.pediupatrao.config;

import com.umc.pediupatrao.security.AcessoNegadoHandler;
import com.umc.pediupatrao.service.UsuarioDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.LinkedHashMap;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.DelegatingAuthenticationEntryPoint;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    private static final String ADMIN = "ADMIN";
    private static final String GERENTE = "GERENTE";
    private static final String ATENDENTE = "ATENDENTE";

    private final UsuarioDetailsService usuarioDetailsService;
    private final AcessoNegadoHandler acessoNegadoHandler;

    public SecurityConfig(UsuarioDetailsService usuarioDetailsService, AcessoNegadoHandler acessoNegadoHandler) {
        this.usuarioDetailsService = usuarioDetailsService;
        this.acessoNegadoHandler = acessoNegadoHandler;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(usuarioDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        RequestMatcher rotasApi = request -> request.getRequestURI().startsWith(request.getContextPath() + "/api/");
        LinkedHashMap<RequestMatcher, AuthenticationEntryPoint> pontosDeEntrada = new LinkedHashMap<>();
        pontosDeEntrada.put(rotasApi, new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED));
        DelegatingAuthenticationEntryPoint entryPoint = new DelegatingAuthenticationEntryPoint(pontosDeEntrada);
        entryPoint.setDefaultEntryPoint(new LoginUrlAuthenticationEntryPoint("/login"));

        http

                .csrf(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth

                        .requestMatchers("/login", "/error", "/css/**", "/dist/**", "/plugins/**", "/js/**", "/images/**").permitAll()

                        .requestMatchers("/usuarios/**", "/api/usuarios/**").hasRole(ADMIN)
                        .requestMatchers("/admin").hasRole(ADMIN)

                        .requestMatchers(HttpMethod.GET, "/auditoria", "/api/auditoria/**").hasAnyRole(ADMIN, GERENTE)
                        .requestMatchers("/auditoria/**", "/api/auditoria/**").denyAll()

                        .requestMatchers(HttpMethod.POST, "/api/pedidos/*/cancelar", "/api/pedidos/*/desconto").hasRole(GERENTE)
                        .requestMatchers(HttpMethod.POST, "/api/pedidos").hasAnyRole(ATENDENTE, GERENTE)
                        .requestMatchers(HttpMethod.PUT, "/api/pedidos/*/status", "/api/pedidos/*/itens").hasAnyRole(ATENDENTE, GERENTE)
                        .requestMatchers(HttpMethod.GET, "/pedidos", "/api/pedidos", "/api/pedidos/*").hasAnyRole(ADMIN, GERENTE, ATENDENTE)
                        .requestMatchers("/pedidos/**", "/api/pedidos/**").denyAll()

                        .requestMatchers(HttpMethod.GET, "/clientes", "/api/clientes", "/api/clientes/*").hasAnyRole(ATENDENTE, GERENTE)
                        .requestMatchers(HttpMethod.DELETE, "/api/clientes/*").hasRole(GERENTE)
                        .requestMatchers("/clientes/**", "/api/clientes/**").hasRole(ATENDENTE)

                        .requestMatchers(HttpMethod.GET, "/produtos", "/api/produtos", "/api/produtos/*").authenticated()
                        .requestMatchers("/produtos/**", "/api/produtos/**").hasAnyRole(ADMIN, GERENTE)

                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .permitAll()
                        .successHandler(authenticationSuccessHandler())
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .permitAll()
                )
                .exceptionHandling(ex -> ex

                        .accessDeniedHandler(acessoNegadoHandler)
                        .authenticationEntryPoint(entryPoint)
                );

        return http.build();
    }

    @Bean
    public AuthenticationSuccessHandler authenticationSuccessHandler() {
        return (request, response, authentication) -> {
            response.sendRedirect(request.getContextPath() + "/");
        };
    }
}
