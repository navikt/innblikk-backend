package no.nav.reops.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.tags.Tag
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@ConfigurationProperties(prefix = "springdoc.swagger-ui")
data class SwaggerUiProps(
    val order: List<String> = emptyList()
)

@Configuration
@EnableConfigurationProperties(SwaggerUiProps::class)
class OpenApiConfig(
    private val swaggerUiProps: SwaggerUiProps
) {

    @Bean
    fun tagOrderCustomiser(): OpenApiCustomizer = OpenApiCustomizer { openApi: OpenAPI ->
        val desiredOrder = swaggerUiProps.order
        val existingByName = (openApi.tags ?: emptyList()).associateBy { it.name }
        val ordered = desiredOrder.map { name -> existingByName[name] ?: Tag().name(name) }
        openApi.tags = ordered
    }
}