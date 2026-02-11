package no.nav.reops.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.tags.Tag
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {

    @Bean
    fun tagOrderCustomiser(): OpenApiCustomizer = OpenApiCustomizer { openApi: OpenAPI ->
        val desiredOrder = listOf("Project", "Dashboard", "Graph", "Query", "Change")
        val existingByName = (openApi.tags ?: emptyList()).associateBy { it.name }
        val ordered = desiredOrder.map { name -> existingByName[name] ?: Tag().name(name) }
        openApi.tags = ordered
    }
}