package no.nav.reops.config

import no.nav.reops.cohort.CohortNodeDto
import no.nav.reops.cohort.CohortNodeRequest
import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.RuntimeHintsRegistrar

class NativeRuntimeHints : RuntimeHintsRegistrar {
    override fun registerHints(hints: RuntimeHints, classLoader: ClassLoader?) {
        // Register Flyway migration SQL files so they are discoverable in the native image
        hints.resources().registerPattern("db/migration/.*")

        // Flyway uses reflection to call isFlywaySpecificVersionOf on its exception classes
        registerFlywayExceptionClasses(hints, classLoader)

        // CohortNodeRequest is a recursive/self-referential sealed interface deserialized
        // via @JsonTypeInfo/@JsonSubTypes. Spring's AOT binding-hint auto-detection doesn't
        // walk into the polymorphic subtypes for this kind of cyclic type graph, so Jackson's
        // Kotlin constructor introspection fails at runtime in the native image with:
        // "Cannot construct instance of ...GroupNode: cannot deserialize from Object value
        // (no delegate- or property-based Creator)". Register each subtype explicitly.
        registerCohortNodeRequestTypes(hints)

        // Same problem, mirror image: CohortNodeDto is the polymorphic response tree returned
        // by GET /api/cohort/{id} and PUT /api/cohort/{id}/criteria. Missing hints here don't
        // throw — Jackson silently serializes only the supertype's `id`/`nodeType` and drops
        // every subtype-specific field (combinator, children, field, conditionType, ...),
        // producing a truncated-looking JSON body instead of an error.
        registerCohortNodeDtoTypes(hints)
    }

    private fun registerCohortNodeDtoTypes(hints: RuntimeHints) {
        val memberCategories = arrayOf(
            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
            MemberCategory.DECLARED_FIELDS,
            MemberCategory.INVOKE_DECLARED_METHODS,
        )

        listOf(
            CohortNodeDto::class.java,
            CohortNodeDto.GroupNodeDto::class.java,
            CohortNodeDto.ConditionNodeDto::class.java,
            CohortNodeDto.CohortRefNodeDto::class.java,
            CohortNodeDto.SequenceNodeDto::class.java,
        ).forEach { clazz ->
            hints.reflection().registerType(clazz, *memberCategories)
        }
    }

    private fun registerCohortNodeRequestTypes(hints: RuntimeHints) {
        val memberCategories = arrayOf(
            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
            MemberCategory.DECLARED_FIELDS,
            MemberCategory.INVOKE_DECLARED_METHODS,
        )

        listOf(
            CohortNodeRequest::class.java,
            CohortNodeRequest.GroupNode::class.java,
            CohortNodeRequest.ConditionNode::class.java,
            CohortNodeRequest.CohortRefNode::class.java,
            CohortNodeRequest.SequenceNode::class.java,
        ).forEach { clazz ->
            hints.reflection().registerType(clazz, *memberCategories)
        }
    }

    private fun registerFlywayExceptionClasses(hints: RuntimeHints, classLoader: ClassLoader?) {
        // Flyway uses reflection (isFlywaySpecificVersionOf) to classify SQLExceptions into these
        // specific subtypes. If a class here is missing/renamed for the pinned flyway-core version,
        // a real DB connection failure gets masked by a NoSuchMethodException instead — verify this
        // list against the actual flyway-core jar's org.flywaydb.core.internal.exception(.sqlExceptions)
        // package when bumping the Flyway version.
        val flywayExceptionClasses = listOf(
            "org.flywaydb.core.internal.exception.sqlExceptions.FlywaySqlUnableToConnectToDbException",
            "org.flywaydb.core.internal.exception.sqlExceptions.FlywaySqlNoDriversForInteractiveAuthException",
            "org.flywaydb.core.internal.exception.sqlExceptions.FlywaySqlNoIntegratedAuthException",
            "org.flywaydb.core.internal.exception.sqlExceptions.FlywaySqlServerUntrustedCertificateSqlException",
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

