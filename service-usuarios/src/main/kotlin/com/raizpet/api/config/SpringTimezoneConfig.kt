package com.raizpet.api.config

import jakarta.annotation.PostConstruct
import org.springframework.context.annotation.Configuration
import java.util.TimeZone

// Marca esta classe como fonte de beans de configuração, gerenciada pelo Spring
@Configuration
class SpringTimezoneConfig {

    // Executado pelo Spring logo após a injeção de dependências do bean,
    // antes de ele ser disponibilizado para uso — ponto ideal para ajustes de ambiente
    @PostConstruct
    fun timezoneConfig() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"))
    }
}
