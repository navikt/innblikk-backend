package no.nav.reops.query

import java.time.LocalDateTime

data class QueryDto(
    val id: Long,
    val graphId: Long,
    val name: String,
    val sqlText: String,
    val updatedAt: LocalDateTime,
)

data class CreateQueryRequest(
    val name: String,
    val sqlText: String,
)

data class UpdateQueryRequest(
    val name: String,
    val sqlText: String,
)
