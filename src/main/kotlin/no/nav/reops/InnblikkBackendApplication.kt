package no.nav.reops

import no.nav.reops.config.NativeRuntimeHints
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ImportRuntimeHints

@SpringBootApplication
@ImportRuntimeHints(NativeRuntimeHints::class)
class StartUmamiBackendApplication

fun main(args: Array<String>) {
	runApplication<StartUmamiBackendApplication>(*args)
}
