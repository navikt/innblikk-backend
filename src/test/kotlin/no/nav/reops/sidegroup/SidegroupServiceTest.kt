package no.nav.reops.sidegroup

import no.nav.reops.change.ChangeDto
import no.nav.reops.change.ChangeService
import no.nav.reops.change.ChangeType
import no.nav.reops.change.EntityType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class SidegroupServiceTest {

    @Mock
    private lateinit var sidegroupRepository: SidegroupRepository

    @Mock
    private lateinit var changeService: ChangeService

    private lateinit var sidegroupService: SidegroupService

    private val updatedAt = LocalDateTime.of(2025, 1, 2, 3, 4, 5)

    @BeforeEach
    fun setUp() {
        sidegroupService = SidegroupService(sidegroupRepository, changeService)
    }

    @Test
    fun `createSidegroup saves entity with all URL path filters and returns dto`() {
        val saved = sidegroup(
            id = 1L,
            name = "Product pages",
            include = listOf("/products/", "/shop/"),
            exclude = listOf("/products/archived/"),
            exact = listOf("/products/featured"),
            startWith = listOf("/products/category/"),
            endWith = listOf("/details"),
        )
        whenever(sidegroupRepository.existsByName("Product pages")).thenReturn(false)
        whenever(sidegroupRepository.save(any<SidegroupEntity>())).thenReturn(saved)
        whenever(changeService.getChangeByEntityIdAndEntityType(1L, EntityType.SIDEGROUP)).thenReturn(
            changeDto(1L, ChangeType.CREATED)
        )

        val result = sidegroupService.createSidegroup(
            CreateSidegroupRequest(
                name = "Product pages",
                include = listOf("/products/", "/shop/"),
                exclude = listOf("/products/archived/"),
                exact = listOf("/products/featured"),
                startWith = listOf("/products/category/"),
                endWith = listOf("/details"),
            )
        )

        val captor = argumentCaptor<SidegroupEntity>()
        verify(sidegroupRepository).save(captor.capture())
        assertThat(captor.firstValue.name).isEqualTo("Product pages")
        assertThat(captor.firstValue.include).containsExactly("/products/", "/shop/")
        assertThat(captor.firstValue.exclude).containsExactly("/products/archived/")
        assertThat(captor.firstValue.exact).containsExactly("/products/featured")
        assertThat(captor.firstValue.startWith).containsExactly("/products/category/")
        assertThat(captor.firstValue.endWith).containsExactly("/details")

        verify(changeService).createChange(1L, EntityType.SIDEGROUP, ChangeType.CREATED)

        assertThat(result.id).isEqualTo(1L)
        assertThat(result.name).isEqualTo("Product pages")
        assertThat(result.include).containsExactly("/products/", "/shop/")
        assertThat(result.exclude).containsExactly("/products/archived/")
        assertThat(result.exact).containsExactly("/products/featured")
        assertThat(result.startWith).containsExactly("/products/category/")
        assertThat(result.endWith).containsExactly("/details")
        assertThat(result.changedByEmail).isEqualTo("test@nav.no")
        assertThat(result.changedByName).isEqualTo("Test User")
        assertThat(result.changedByNavIdent).isEqualTo("T123456")
    }

    @Test
    fun `createSidegroup with no URL path filters`() {
        val saved = sidegroup(id = 1L, name = "All pages")
        whenever(sidegroupRepository.existsByName("All pages")).thenReturn(false)
        whenever(sidegroupRepository.save(any<SidegroupEntity>())).thenReturn(saved)
        whenever(changeService.getChangeByEntityIdAndEntityType(1L, EntityType.SIDEGROUP)).thenReturn(null)

        val result = sidegroupService.createSidegroup(CreateSidegroupRequest(name = "All pages"))

        val captor = argumentCaptor<SidegroupEntity>()
        verify(sidegroupRepository).save(captor.capture())
        assertThat(captor.firstValue.include).isEmpty()
        assertThat(captor.firstValue.exclude).isEmpty()
        assertThat(captor.firstValue.exact).isEmpty()
        assertThat(captor.firstValue.startWith).isEmpty()
        assertThat(captor.firstValue.endWith).isEmpty()

        assertThat(result.include).isEmpty()
        assertThat(result.changedByEmail).isNull()
    }

    @Test
    fun `createSidegroup with explicit empty lists`() {
        val saved = sidegroup(id = 1L, name = "All pages")
        whenever(sidegroupRepository.existsByName("All pages")).thenReturn(false)
        whenever(sidegroupRepository.save(any<SidegroupEntity>())).thenReturn(saved)
        whenever(changeService.getChangeByEntityIdAndEntityType(1L, EntityType.SIDEGROUP)).thenReturn(null)

        val result = sidegroupService.createSidegroup(
            CreateSidegroupRequest(name = "All pages", include = emptyList(), exclude = emptyList())
        )

        val captor = argumentCaptor<SidegroupEntity>()
        verify(sidegroupRepository).save(captor.capture())
        assertThat(captor.firstValue.include).isEmpty()
        assertThat(captor.firstValue.exclude).isEmpty()
        assertThat(result.include).isEmpty()
    }

    @Test
    fun `createSidegroup throws when name already exists and does not save`() {
        whenever(sidegroupRepository.existsByName("Product pages")).thenReturn(true)

        assertThatThrownBy {
            sidegroupService.createSidegroup(CreateSidegroupRequest(name = "Product pages"))
        }.isInstanceOf(IllegalArgumentException::class.java).hasMessageContaining("Product pages")

        verify(sidegroupRepository, never()).save(any<SidegroupEntity>())
        verifyNoInteractions(changeService)
    }

    @Test
    fun `getAllSidegroup maps entities to dtos`() {
        val blogPages = sidegroup(id = 1L, name = "Blog pages", include = listOf("/blog/", "/articles/"))
        val adminPages = sidegroup(id = 2L, name = "Admin pages", startWith = listOf("/admin/"))
        whenever(sidegroupRepository.findAll()).thenReturn(listOf(blogPages, adminPages))
        whenever(changeService.getChangeByEntityIdAndEntityType(1L, EntityType.SIDEGROUP)).thenReturn(null)
        whenever(changeService.getChangeByEntityIdAndEntityType(2L, EntityType.SIDEGROUP)).thenReturn(null)

        val result = sidegroupService.getAllSidegroup()

        assertThat(result).hasSize(2)
        assertThat(result[0].id).isEqualTo(1L)
        assertThat(result[0].name).isEqualTo("Blog pages")
        assertThat(result[0].include).containsExactly("/blog/", "/articles/")
        assertThat(result[1].id).isEqualTo(2L)
        assertThat(result[1].name).isEqualTo("Admin pages")
        assertThat(result[1].startWith).containsExactly("/admin/")
    }

    @Test
    fun `getAllSidegroup returns empty list when none exist`() {
        whenever(sidegroupRepository.findAll()).thenReturn(emptyList())

        val result = sidegroupService.getAllSidegroup()

        assertThat(result).isEmpty()
    }

    @Test
    fun `getSidegroupById returns dto when found`() {
        val entity = sidegroup(id = 1L, name = "Blog pages", include = listOf("/blog/", "/articles/"))
        whenever(sidegroupRepository.findById(1L)).thenReturn(Optional.of(entity))
        whenever(changeService.getChangeByEntityIdAndEntityType(1L, EntityType.SIDEGROUP)).thenReturn(null)

        val result = sidegroupService.getSidegroupById(1L)

        assertThat(result).isNotNull
        assertThat(result!!.id).isEqualTo(1L)
        assertThat(result.name).isEqualTo("Blog pages")
        assertThat(result.include).containsExactly("/blog/", "/articles/")
    }

    @Test
    fun `getSidegroupById returns null when not found`() {
        whenever(sidegroupRepository.findById(99L)).thenReturn(Optional.empty())

        val result = sidegroupService.getSidegroupById(99L)

        assertThat(result).isNull()
    }

    @Test
    fun `updateSidegroup saves changes and returns dto`() {
        val entity = sidegroup(id = 10L, name = "Old landing pages", include = listOf("/landing/old/"))
        whenever(sidegroupRepository.findById(10L)).thenReturn(Optional.of(entity))
        whenever(sidegroupRepository.save(any<SidegroupEntity>())).thenReturn(entity)
        whenever(changeService.getChangeByEntityIdAndEntityType(10L, EntityType.SIDEGROUP)).thenReturn(null)

        val result = sidegroupService.updateSidegroup(
            10L,
            UpdateSidegroupRequest(
                name = "Landing pages",
                include = listOf("/landing/", "/promo/"),
                exclude = listOf("/landing/archived/"),
                exact = listOf("/landing/home"),
                startWith = listOf("/landing/campaign/"),
                endWith = listOf("/signup"),
            )
        )

        val captor = argumentCaptor<SidegroupEntity>()
        verify(sidegroupRepository).save(captor.capture())
        assertThat(captor.firstValue.name).isEqualTo("Landing pages")
        assertThat(captor.firstValue.include).containsExactly("/landing/", "/promo/")
        assertThat(captor.firstValue.exclude).containsExactly("/landing/archived/")
        assertThat(captor.firstValue.exact).containsExactly("/landing/home")
        assertThat(captor.firstValue.startWith).containsExactly("/landing/campaign/")
        assertThat(captor.firstValue.endWith).containsExactly("/signup")

        verify(changeService).createChange(10L, EntityType.SIDEGROUP, ChangeType.UPDATED)

        assertThat(result).isNotNull
        assertThat(result!!.name).isEqualTo("Landing pages")
        assertThat(result.include).containsExactly("/landing/", "/promo/")
    }

    @Test
    fun `updateSidegroup preserves current filters when fields are omitted`() {
        val entity = sidegroup(
            id = 10L,
            name = "Blog pages",
            include = listOf("/blog/", "/articles/"),
            exclude = listOf("/blog/drafts/"),
            exact = listOf("/blog/featured"),
            startWith = listOf("/blog/2025/"),
            endWith = listOf("/comments"),
        )
        whenever(sidegroupRepository.findById(10L)).thenReturn(Optional.of(entity))
        whenever(sidegroupRepository.save(any<SidegroupEntity>())).thenReturn(entity)
        whenever(changeService.getChangeByEntityIdAndEntityType(10L, EntityType.SIDEGROUP)).thenReturn(null)

        val result = sidegroupService.updateSidegroup(
            10L,
            UpdateSidegroupRequest(name = "Blog & articles")
        )

        val captor = argumentCaptor<SidegroupEntity>()
        verify(sidegroupRepository).save(captor.capture())
        assertThat(captor.firstValue.name).isEqualTo("Blog & articles")
        assertThat(captor.firstValue.include).containsExactly("/blog/", "/articles/")
        assertThat(captor.firstValue.exclude).containsExactly("/blog/drafts/")
        assertThat(captor.firstValue.exact).containsExactly("/blog/featured")
        assertThat(captor.firstValue.startWith).containsExactly("/blog/2025/")
        assertThat(captor.firstValue.endWith).containsExactly("/comments")

        assertThat(result).isNotNull
    }

    @Test
    fun `updateSidegroup clears filters when explicitly set to empty list`() {
        val entity = sidegroup(
            id = 10L,
            name = "Blog pages",
            include = listOf("/blog/"),
            exclude = listOf("/blog/drafts/"),
        )
        whenever(sidegroupRepository.findById(10L)).thenReturn(Optional.of(entity))
        whenever(sidegroupRepository.save(any<SidegroupEntity>())).thenReturn(entity)
        whenever(changeService.getChangeByEntityIdAndEntityType(10L, EntityType.SIDEGROUP)).thenReturn(null)

        val result = sidegroupService.updateSidegroup(
            10L,
            UpdateSidegroupRequest(name = "Blog pages", include = emptyList(), exclude = emptyList())
        )

        val captor = argumentCaptor<SidegroupEntity>()
        verify(sidegroupRepository).save(captor.capture())
        assertThat(captor.firstValue.include).isEmpty()
        assertThat(captor.firstValue.exclude).isEmpty()

        assertThat(result).isNotNull
    }

    @Test
    fun `updateSidegroup updates provided filters and preserves omitted ones`() {
        val entity = sidegroup(
            id = 10L,
            name = "Blog pages",
            include = listOf("/blog/"),
            exclude = listOf("/blog/drafts/"),
            startWith = listOf("/blog/2025/"),
        )
        whenever(sidegroupRepository.findById(10L)).thenReturn(Optional.of(entity))
        whenever(sidegroupRepository.save(any<SidegroupEntity>())).thenReturn(entity)
        whenever(changeService.getChangeByEntityIdAndEntityType(10L, EntityType.SIDEGROUP)).thenReturn(null)

        sidegroupService.updateSidegroup(
            10L,
            UpdateSidegroupRequest(
                name = "Blog pages",
                include = listOf("/blog/", "/articles/"),
                // exclude, exact, startWith, endWith omitted → kept as-is
            )
        )

        val captor = argumentCaptor<SidegroupEntity>()
        verify(sidegroupRepository).save(captor.capture())
        assertThat(captor.firstValue.include).containsExactly("/blog/", "/articles/")
        assertThat(captor.firstValue.exclude).containsExactly("/blog/drafts/")
        assertThat(captor.firstValue.startWith).containsExactly("/blog/2025/")
        assertThat(captor.firstValue.exact).isEmpty()
        assertThat(captor.firstValue.endWith).isEmpty()
    }

    @Test
    fun `updateSidegroup returns null when not found`() {
        whenever(sidegroupRepository.findById(10L)).thenReturn(Optional.empty())

        val result = sidegroupService.updateSidegroup(
            10L,
            UpdateSidegroupRequest(name = "Landing pages", include = listOf("/landing/"))
        )

        assertThat(result).isNull()
        verifyNoInteractions(changeService)
    }

    @Test
    fun `deleteSidegroup deletes entity and records change`() {
        val entity = sidegroup(id = 10L, name = "Deprecated pages")
        whenever(sidegroupRepository.findById(10L)).thenReturn(Optional.of(entity))

        val result = sidegroupService.deleteSidegroup(10L)

        verify(sidegroupRepository).delete(entity)
        verify(changeService).createChange(10L, EntityType.SIDEGROUP, ChangeType.DELETED)
        assertThat(result).isTrue()
    }

    @Test
    fun `deleteSidegroup returns false when not found`() {
        whenever(sidegroupRepository.findById(10L)).thenReturn(Optional.empty())

        val result = sidegroupService.deleteSidegroup(10L)

        assertThat(result).isFalse()
        verifyNoInteractions(changeService)
    }

    private fun sidegroup(
        id: Long,
        name: String = "sidegroup",
        include: List<String> = emptyList(),
        exclude: List<String> = emptyList(),
        exact: List<String> = emptyList(),
        startWith: List<String> = emptyList(),
        endWith: List<String> = emptyList(),
    ) = SidegroupEntity(
        id = id,
        name = name,
        include = include,
        exclude = exclude,
        exact = exact,
        startWith = startWith,
        endWith = endWith,
        updatedAt = updatedAt,
    )

    private fun changeDto(entityId: Long, changeType: ChangeType) = ChangeDto(
        entityId = entityId,
        entityType = EntityType.SIDEGROUP,
        changeType = changeType,
        updatedAt = updatedAt,
        changedByEmail = "test@nav.no",
        changedByName = "Test User",
        changedByNavIdent = "T123456",
    )
}
