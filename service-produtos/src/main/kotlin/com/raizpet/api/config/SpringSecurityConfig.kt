package com.raizpet.api.config

import com.raizpet.api.jwt.JwtAuthenticationEntryPoint
import com.raizpet.api.jwt.JwtAuthorizationFilter
import com.raizpet.api.jwt.JwtUtils
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import tools.jackson.databind.json.JsonMapper

@EnableMethodSecurity
@Configuration
class SpringSecurityConfig {

    companion object {
        private val DOCUMENTATION_OPENAPI = arrayOf(
            "/docs-petraiz.html", "/docs-petraiz/**",
            "/v3/api-docs/**",
            "/swagger-ui.html", "/swagger-ui/**",
            "/webjars/**"
        )
    }

    @Bean
    fun filterChain(
        http: HttpSecurity,
        jwtUtils: JwtUtils,
        jsonMapper: JsonMapper,
        @Value("\${petraiz.interno.chave}") chaveInterna: String
    ): SecurityFilterChain =
        http
            .csrf { it.disable() }
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .authorizeHttpRequests { auth ->
                auth
                    // Área do produtor. PRECISA vir antes do permitAll do catálogo: "/api/v1/produtos/*" também
                    // casa com "/api/v1/produtos/meus" e, como vale a primeira regra que casar, a lista ficaria pública.
                    // Aqui só exige token; o perfil PRODUCER é conferido pelo @PreAuthorize (403 em ErrorMessage).
                    .requestMatchers("/api/v1/produtos/meus", "/api/v1/produtos/meus/**").authenticated()
                    // Catálogo e categorias: públicos (quem filtra os produtores aprovados é o ProductService)
                    .requestMatchers(HttpMethod.GET, "/api/v1/produtos", "/api/v1/produtos/*", "/api/v1/categorias").permitAll()
                    // Imagens de produtos e produtores (src/main/resources/static/images)
                    .requestMatchers(HttpMethod.GET, "/images/**").permitAll()
                    // Só outros serviços, com X-Internal-Key (InternoKeyFilter dá o ROLE_SERVICE)
                    .requestMatchers("/api/v1/interno/**").hasRole(InternoKeyFilter.ROLE)
                    .requestMatchers(*DOCUMENTATION_OPENAPI).permitAll()
                    .requestMatchers("/error").permitAll()
                    // POST /api/v1/produtos cai aqui (autenticado) + @PreAuthorize PRODUCER no controller
                    .anyRequest().authenticated()
            }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            // Filtros criados aqui (e não como @Bean) para o Spring Boot não registrá-los uma segunda vez como filtro de servlet
            .addFilterBefore(JwtAuthorizationFilter(jwtUtils), UsernamePasswordAuthenticationFilter::class.java)
            .addFilterAfter(InternoKeyFilter(chaveInterna, jsonMapper), JwtAuthorizationFilter::class.java)
            .exceptionHandling { it.authenticationEntryPoint(JwtAuthenticationEntryPoint(jsonMapper)) }
            .build()

    /**
     * Não existe usuário/senha neste serviço (ninguém faz login aqui). Este bean só existe para o
     * Spring Boot não criar o usuário padrão "user" com senha gerada no log.
     */
    @Bean
    fun userDetailsService(): UserDetailsService =
        UserDetailsService { throw UsernameNotFoundException("service-produtos não autentica por usuário e senha") }
}
