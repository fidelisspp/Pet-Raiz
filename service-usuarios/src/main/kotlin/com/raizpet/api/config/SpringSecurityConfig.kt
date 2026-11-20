package com.raizpet.api.config

import com.raizpet.api.jwt.JwtAuthenticationEntryPoint
import com.raizpet.api.jwt.JwtAuthorizationFilter
import com.raizpet.api.jwt.JwtUserDetailsService
import com.raizpet.api.jwt.JwtUtils
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
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
        detailsService: JwtUserDetailsService,
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
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/usuarios/clientes", "/api/v1/usuarios/produtores").permitAll()
                    // Vitrine pública dos produtores (só aprovados; quem filtra é o ProducerService).
                    // ATENÇÃO: "/produtores/*" também casa com "/produtores/me". Quando essa rota (PRODUCER)
                    // for criada, a regra autenticada dela precisa vir ANTES desta linha.
                    .requestMatchers(HttpMethod.GET, "/api/v1/produtores", "/api/v1/produtores/*").permitAll()
                    // Só outros serviços, com X-Internal-Key (InternoKeyFilter dá o ROLE_SERVICE)
                    .requestMatchers("/api/v1/interno/**").hasRole(InternoKeyFilter.ROLE)
                    .requestMatchers(*DOCUMENTATION_OPENAPI).permitAll()
                    .requestMatchers("/error").permitAll()
                    .anyRequest().authenticated()
            }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            // Filtros criados aqui (e não como @Bean) para o Spring Boot não registrá-los uma segunda vez como filtro de servlet
            .addFilterBefore(JwtAuthorizationFilter(detailsService, jwtUtils), UsernamePasswordAuthenticationFilter::class.java)
            .addFilterAfter(InternoKeyFilter(chaveInterna, jsonMapper), JwtAuthorizationFilter::class.java)
            .exceptionHandling { it.authenticationEntryPoint(JwtAuthenticationEntryPoint(jsonMapper)) }
            .build()

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun authenticationManager(authenticationConfiguration: AuthenticationConfiguration): AuthenticationManager =
        authenticationConfiguration.authenticationManager
}
