package no.nav.reops.config

import org.springframework.aot.hint.RuntimeHintsRegistrar
import org.springframework.aot.hint.RuntimeHints

class NativeRuntimeHints : RuntimeHintsRegistrar {
    override fun registerHints(hints: RuntimeHints, classLoader: ClassLoader?) {
        // Register Flyway migration files so they are included in the native image
        hints.resources().registerPattern("db/migration/*")
    }
}

