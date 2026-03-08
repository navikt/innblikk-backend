package no.nav.reops.config

import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.RuntimeHintsRegistrar

class NativeRuntimeHints : RuntimeHintsRegistrar {
    override fun registerHints(hints: RuntimeHints, classLoader: ClassLoader?) {
        // Register Flyway migration SQL files so they are discoverable in the native image
        hints.resources().registerPattern("db/migration/.*")

        // Flyway uses reflection to call isFlywaySpecificVersionOf on its exception classes
        registerFlywayExceptionClasses(hints, classLoader)
    }

    private fun registerFlywayExceptionClasses(hints: RuntimeHints, classLoader: ClassLoader?) {
        val flywayExceptionClasses = listOf(
            "org.flywaydb.core.internal.exception.sqlExceptions.FlywaySqlNoIntegratedAuthException",
            "org.flywaydb.core.internal.exception.sqlExceptions.FlywaySqlServerUntrustedCertificateSqlException",
            "org.flywaydb.core.internal.exception.sqlExceptions.FlywaySqlServerEncryptionException",
            "org.flywaydb.core.internal.exception.sqlExceptions.FlywayPgUpgradeRequiredException",
            "org.flywaydb.core.internal.exception.sqlExceptions.FlywaySqlServerWindowsAuthException",
            "org.flywaydb.core.internal.exception.FlywaySqlException",
        )

        for (className in flywayExceptionClasses) {
            try {
                val clazz = Class.forName(className, false, classLoader ?: javaClass.classLoader)
                hints.reflection().registerType(clazz, MemberCategory.INVOKE_DECLARED_METHODS)
            } catch (_: ClassNotFoundException) {
                // Class may not be present in all Flyway editions; skip safely
            }
        }
    }
}

