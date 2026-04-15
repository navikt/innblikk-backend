package no.nav.reops.sidegroup

import org.springframework.data.repository.CrudRepository
import java.util.UUID

interface SidegroupRepository : CrudRepository<SidegroupEntity, Long> {
    fun existsByName(name: String): Boolean
    fun findAllByWebsiteId(websiteId: UUID): List<SidegroupEntity>
}