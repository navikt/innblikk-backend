package no.nav.reops

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class StartUmamiBackendApplication

fun main(args: Array<String>) {
	runApplication<StartUmamiBackendApplication>(*args)
}
