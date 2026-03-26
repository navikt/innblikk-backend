package no.nav.reops.sidegroup

import org.springframework.data.repository.CrudRepository

interface SidegroupRepository : CrudRepository<SidegroupEntity, Long> {
    fun existsByName(name: String): Boolean
}