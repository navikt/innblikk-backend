package no.nav.reops.sidegroup

import java.time.LocalDateTime

data class SidegroupDto(
    val id: Long,
    val name: String,
    val include: List<String>,
    val exclude: List<String>,
    val exact: List<String>,
    val startWith: List<String>,
    val endWith: List<String>,
    val updatedAt: LocalDateTime,
    val changedByEmail: String?,
    val changedByName: String?,
    val changedByNavIdent: String?
)

data class CreateSidegroupRequest(
    val name: String,
    val include: List<String>? = null,
    val exclude: List<String>? = null,
    val exact: List<String>? = null,
    val startWith: List<String>? = null,
    val endWith: List<String>? = null,
)

data class UpdateSidegroupRequest(
    val name: String,
    val include: List<String>? = null,
    val exclude: List<String>? = null,
    val exact: List<String>? = null,
    val startWith: List<String>? = null,
    val endWith: List<String>? = null,
)
