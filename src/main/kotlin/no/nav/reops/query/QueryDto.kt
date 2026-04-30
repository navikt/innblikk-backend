package no.nav.reops.query

import java.time.LocalDateTime

data class QueryDto(
    val id: Long,
    val graphId: Long,
    val name: String,
    val sqlText: String,
    val ordering: Int,
    val version: Long,
    val updatedAt: LocalDateTime,
    val changedByEmail: String? = null,
    val changedByName: String? = null,
    val changedByNavIdent: String? = null,
)

data class CreateQueryRequest(
    val name: String,
    val sqlText: String,
)

data class UpdateQueryRequest(
    val name: String,
    val sqlText: String,
    val version: Long? = null,
)

data class QueryOrderingEntry(
    val id: Long,
    val ordering: Int,
)

