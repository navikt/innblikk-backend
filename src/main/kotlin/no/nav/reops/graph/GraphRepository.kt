package no.nav.reops.graph

import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository

interface GraphRepository : CrudRepository<GraphEntity, Long> {
    fun findAllByCategoryIdOrderByOrderingAsc(categoryId: Long): List<GraphEntity>
    fun findByIdAndCategoryId(id: Long, categoryId: Long): GraphEntity?
    fun existsByCategoryIdAndName(categoryId: Long, name: String): Boolean
    fun existsByIdAndName(id: Long, name: String): Boolean
    fun findAllByCategoryIdAndIdIn(categoryId: Long, ids: List<Long>): List<GraphEntity>

    @Query("SELECT COALESCE(MAX(g.ordering), -1) FROM GraphEntity g WHERE g.category.id = :categoryId")
    fun findMaxOrderingByCategoryId(categoryId: Long): Int
}