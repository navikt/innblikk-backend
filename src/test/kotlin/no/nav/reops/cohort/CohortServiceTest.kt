package no.nav.reops.cohort

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
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class CohortServiceTest {

    @Mock
    private lateinit var cohortRepository: CohortRepository

    @Mock
    private lateinit var changeService: ChangeService

    private lateinit var cohortService: CohortService

    private val updatedAt = LocalDateTime.of(2026, 4, 13, 10, 0, 0)
    private val websiteId = UUID.fromString("98c1936a-38a4-11f1-8197-5a715c0b0a36")

    @BeforeEach
    fun setUp() {
        cohortService = CohortService(cohortRepository, changeService)
    }

    @Test
    fun `createCohort saves entity and returns dto`() {
        val saved = cohort(id = 1L, name = "Test cohort", description = "desc")
        whenever(cohortRepository.existsByNameAndWebsiteId("Test cohort", websiteId)).thenReturn(false)
        whenever(cohortRepository.save(any<CohortEntity>())).thenReturn(saved)
        whenever(changeService.getChangeByEntityIdAndEntityType(1L, EntityType.COHORT)).thenReturn(
            changeDto(1L, ChangeType.CREATED)
        )

        val result = cohortService.createCohort(
            CreateCohortRequest(websiteId = websiteId, name = "Test cohort", description = "desc")
        )

        val captor = argumentCaptor<CohortEntity>()
        verify(cohortRepository).save(captor.capture())
        assertThat(captor.firstValue.name).isEqualTo("Test cohort")
        assertThat(captor.firstValue.websiteId).isEqualTo(websiteId)
        assertThat(captor.firstValue.description).isEqualTo("desc")

        verify(changeService).createChange(1L, EntityType.COHORT, ChangeType.CREATED)

        assertThat(result.id).isEqualTo(1L)
        assertThat(result.name).isEqualTo("Test cohort")
        assertThat(result.changedByEmail).isEqualTo("test@nav.no")
    }

    @Test
    fun `createCohort throws when name already exists`() {
        whenever(cohortRepository.existsByNameAndWebsiteId("Existing", websiteId)).thenReturn(true)

        assertThatThrownBy {
            cohortService.createCohort(CreateCohortRequest(websiteId = websiteId, name = "Existing"))
        }.isInstanceOf(IllegalArgumentException::class.java).hasMessageContaining("Existing")

        verify(cohortRepository, never()).save(any<CohortEntity>())
        verifyNoInteractions(changeService)
    }

    @Test
    fun `getAllCohorts returns all cohorts`() {
        whenever(cohortRepository.findAll()).thenReturn(listOf(
            cohort(id = 1L, name = "A"), cohort(id = 2L, name = "B")
        ))
        whenever(changeService.getChangeByEntityIdAndEntityType(any(), any())).thenReturn(null)

        val result = cohortService.getAllCohorts()

        assertThat(result).hasSize(2)
        assertThat(result[0].name).isEqualTo("A")
        assertThat(result[1].name).isEqualTo("B")
    }

    @Test
    fun `getAllCohorts returns empty list when none exist`() {
        whenever(cohortRepository.findAll()).thenReturn(emptyList())

        val result = cohortService.getAllCohorts()

        assertThat(result).isEmpty()
    }

    @Test
    fun `getCohortsByWebsiteId returns filtered cohorts`() {
        whenever(cohortRepository.findAllByWebsiteId(websiteId)).thenReturn(listOf(cohort(id = 1L, name = "A")))
        whenever(changeService.getChangeByEntityIdAndEntityType(1L, EntityType.COHORT)).thenReturn(null)

        val result = cohortService.getCohortsByWebsiteId(websiteId)

        assertThat(result).hasSize(1)
        assertThat(result[0].websiteId).isEqualTo(websiteId)
    }

    @Test
    fun `getCohortsByWebsiteId returns empty list when none for website`() {
        val otherWebsiteId = UUID.fromString("22222222-2222-2222-2222-222222222222")
        whenever(cohortRepository.findAllByWebsiteId(otherWebsiteId)).thenReturn(emptyList())

        val result = cohortService.getCohortsByWebsiteId(otherWebsiteId)

        assertThat(result).isEmpty()
    }

    @Test
    fun `getCohortById returns detail dto with entries and conditions`() {
        val entity = cohort(id = 1L, name = "Test")
        val entry = cohortEntry(id = 10L, cohort = entity, negated = true, ordering = 0)
        entry.conditions.add(
            CohortEntryConditionEntity(
                id = 100L, cohortEntry = entry, ordering = 1,
                conditionType = ComparisonOperator.CONTAINS, field = "platform", value = "https://example.no"
            )
        )
        entity.entries.add(entry)
        whenever(cohortRepository.findById(1L)).thenReturn(Optional.of(entity))
        whenever(changeService.getChangeByEntityIdAndEntityType(1L, EntityType.COHORT)).thenReturn(null)

        val result = cohortService.getCohortById(1L)

        assertThat(result).isNotNull
        assertThat(result!!.id).isEqualTo(1L)
        assertThat(result.entries).hasSize(1)
        assertThat(result.entries[0].negated).isTrue()
        assertThat(result.entries[0].conditions).hasSize(1)
        assertThat(result.entries[0].conditions[0].conditionType).isEqualTo(ComparisonOperator.CONTAINS)
        assertThat(result.entries[0].conditions[0].field).isEqualTo("platform")
    }

    @Test
    fun `getCohortById returns entries sorted by ordering`() {
        val entity = cohort(id = 1L, name = "Test")
        entity.entries.add(cohortEntry(id = 11L, cohort = entity, negated = false, ordering = 1))
        entity.entries.add(cohortEntry(id = 10L, cohort = entity, negated = true, ordering = 0))
        whenever(cohortRepository.findById(1L)).thenReturn(Optional.of(entity))
        whenever(changeService.getChangeByEntityIdAndEntityType(1L, EntityType.COHORT)).thenReturn(null)

        val result = cohortService.getCohortById(1L)

        assertThat(result!!.entries[0].ordering).isEqualTo(0)
        assertThat(result.entries[1].ordering).isEqualTo(1)
    }

    @Test
    fun `getCohortById returns null when not found`() {
        whenever(cohortRepository.findById(99L)).thenReturn(Optional.empty())

        assertThat(cohortService.getCohortById(99L)).isNull()
    }

    @Test
    fun `updateCohort saves changes and returns dto`() {
        val entity = cohort(id = 1L, name = "Old name")
        whenever(cohortRepository.findById(1L)).thenReturn(Optional.of(entity))
        whenever(cohortRepository.existsByNameAndWebsiteId("New name", websiteId)).thenReturn(false)
        whenever(cohortRepository.save(any<CohortEntity>())).thenReturn(entity)
        whenever(changeService.getChangeByEntityIdAndEntityType(1L, EntityType.COHORT)).thenReturn(null)

        val result = cohortService.updateCohort(
            1L, UpdateCohortRequest(name = "New name", description = "updated", websiteId = websiteId)
        )

        val captor = argumentCaptor<CohortEntity>()
        verify(cohortRepository).save(captor.capture())
        assertThat(captor.firstValue.name).isEqualTo("New name")
        assertThat(captor.firstValue.description).isEqualTo("updated")

        verify(changeService).createChange(1L, EntityType.COHORT, ChangeType.UPDATED)

        assertThat(result).isNotNull
        assertThat(result!!.name).isEqualTo("New name")
    }

    @Test
    fun `updateCohort allows same name for same entity`() {
        val entity = cohort(id = 1L, name = "Same name")
        whenever(cohortRepository.findById(1L)).thenReturn(Optional.of(entity))
        whenever(cohortRepository.save(any<CohortEntity>())).thenReturn(entity)
        whenever(changeService.getChangeByEntityIdAndEntityType(1L, EntityType.COHORT)).thenReturn(null)

        val result = cohortService.updateCohort(
            1L, UpdateCohortRequest(name = "Same name", websiteId = websiteId)
        )

        assertThat(result).isNotNull
    }

    @Test
    fun `updateCohort throws when new name already exists`() {
        val entity = cohort(id = 1L, name = "Old name")
        whenever(cohortRepository.findById(1L)).thenReturn(Optional.of(entity))
        whenever(cohortRepository.existsByNameAndWebsiteId("Taken", websiteId)).thenReturn(true)

        assertThatThrownBy {
            cohortService.updateCohort(1L, UpdateCohortRequest(name = "Taken", websiteId = websiteId))
        }.isInstanceOf(IllegalArgumentException::class.java).hasMessageContaining("Taken")
    }

    @Test
    fun `updateCohort returns null when not found`() {
        whenever(cohortRepository.findById(1L)).thenReturn(Optional.empty())

        assertThat(cohortService.updateCohort(1L, UpdateCohortRequest(name = "x", websiteId = websiteId))).isNull()
        verifyNoInteractions(changeService)
    }

    @Test
    fun `deleteCohort deletes and records change`() {
        val entity = cohort(id = 1L, name = "To delete")
        whenever(cohortRepository.findById(1L)).thenReturn(Optional.of(entity))

        assertThat(cohortService.deleteCohort(1L)).isTrue()
        verify(cohortRepository).delete(entity)
        verify(changeService).createChange(1L, EntityType.COHORT, ChangeType.DELETED)
    }

    @Test
    fun `deleteCohort returns false when not found`() {
        whenever(cohortRepository.findById(1L)).thenReturn(Optional.empty())

        assertThat(cohortService.deleteCohort(1L)).isFalse()
        verifyNoInteractions(changeService)
    }

    private fun cohort(
        id: Long,
        name: String = "cohort",
        description: String? = null,
        websiteId: UUID = this.websiteId,
    ) = CohortEntity(
        id = id, websiteId = websiteId, name = name, description = description, updatedAt = updatedAt,
    )

    private fun cohortEntry(
        id: Long,
        cohort: CohortEntity,
        negated: Boolean = false,
        inCohort: Boolean = false,
        operator: LogicalOperator? = null,
        ordering: Int = 0,
    ) = CohortEntryEntity(
        id = id, cohort = cohort, negated = negated, inCohort = inCohort,
        operator = operator, ordering = ordering, updatedAt = updatedAt,
    )

    private fun changeDto(entityId: Long, changeType: ChangeType) = ChangeDto(
        entityId = entityId, entityType = EntityType.COHORT, changeType = changeType,
        updatedAt = updatedAt, changedByEmail = "test@nav.no", changedByName = "Test User", changedByNavIdent = "T123456",
    )
}
