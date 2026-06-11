package no.nav.reops.cohort

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
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class CohortEntryServiceTest {

    @Mock
    private lateinit var cohortRepository: CohortRepository

    @Mock
    private lateinit var cohortEntryRepository: CohortEntryRepository

    @Mock
    private lateinit var changeService: ChangeService

    private lateinit var cohortEntryService: CohortEntryService

    private val updatedAt = LocalDateTime.of(2026, 4, 13, 10, 0, 0)
    private val websiteId = UUID.fromString("98c1936a-38a4-11f1-8197-5a715c0b0a36")

    @BeforeEach
    fun setUp() {
        cohortEntryService = CohortEntryService(cohortRepository, cohortEntryRepository, changeService)
    }

    @Test
    fun `createEntry saves entry with conditions and returns dto`() {
        val cohort = CohortEntity(id = 1L, websiteId = websiteId, name = "Test", updatedAt = updatedAt)
        whenever(cohortRepository.findById(1L)).thenReturn(Optional.of(cohort))
        whenever(cohortEntryRepository.findMaxOrderingByCohortId(1L)).thenReturn(-1)

        val savedEntry = CohortEntryEntity(
            id = 10L, cohort = cohort, negated = true, inCohort = false,
            ordering = 0, updatedAt = updatedAt,
        )
        savedEntry.conditions.add(
            CohortEntryConditionEntity(
                id = 100L, cohortEntry = savedEntry, ordering = 1,
                conditionType = ComparisonOperator.CONTAINS, field = "platform", value = "https://example.no"
            )
        )
        whenever(cohortEntryRepository.save(any<CohortEntryEntity>())).thenReturn(savedEntry)

        val result = cohortEntryService.createEntry(1L, CreateCohortEntryRequest(
            negated = true,
            conditions = listOf(
                CreateCohortEntryConditionRequest(
                    conditionType = ComparisonOperator.CONTAINS, field = "platform", value = "https://example.no"
                )
            ),
        ))

        val captor = argumentCaptor<CohortEntryEntity>()
        verify(cohortEntryRepository).save(captor.capture())
        assertThat(captor.firstValue.negated).isTrue()
        assertThat(captor.firstValue.inCohort).isFalse()
        assertThat(captor.firstValue.ordering).isEqualTo(0)
        assertThat(captor.firstValue.conditions).hasSize(1)
        assertThat(captor.firstValue.conditions[0].conditionType).isEqualTo(ComparisonOperator.CONTAINS)

        verify(changeService).createChange(10L, EntityType.COHORT_ENTRY, ChangeType.CREATED)

        assertThat(result.id).isEqualTo(10L)
        assertThat(result.negated).isTrue()
        assertThat(result.conditions).hasSize(1)
        assertThat(result.conditions[0].field).isEqualTo("platform")
    }

    @Test
    fun `createEntry throws when cohort not found`() {
        whenever(cohortRepository.findById(99L)).thenReturn(Optional.empty())

        assertThatThrownBy {
            cohortEntryService.createEntry(99L, CreateCohortEntryRequest())
        }.isInstanceOf(IllegalArgumentException::class.java).hasMessageContaining("99")
    }

    @Test
    fun `createEntry auto-increments ordering`() {
        val cohort = CohortEntity(id = 1L, websiteId = websiteId, name = "Test", updatedAt = updatedAt)
        whenever(cohortRepository.findById(1L)).thenReturn(Optional.of(cohort))
        whenever(cohortEntryRepository.findMaxOrderingByCohortId(1L)).thenReturn(2)

        val savedEntry = CohortEntryEntity(
            id = 10L, cohort = cohort, negated = false, inCohort = false,
            operator = LogicalOperator.AND, ordering = 3, updatedAt = updatedAt,
        )
        whenever(cohortEntryRepository.save(any<CohortEntryEntity>())).thenReturn(savedEntry)

        cohortEntryService.createEntry(1L, CreateCohortEntryRequest(operator = LogicalOperator.AND))

        val captor = argumentCaptor<CohortEntryEntity>()
        verify(cohortEntryRepository).save(captor.capture())
        assertThat(captor.firstValue.ordering).isEqualTo(3)
    }

    @Test
    fun `createEntry with inCohort sets referenced cohort`() {
        val cohort = CohortEntity(id = 1L, websiteId = websiteId, name = "Test", updatedAt = updatedAt)
        whenever(cohortRepository.findById(1L)).thenReturn(Optional.of(cohort))
        whenever(cohortEntryRepository.findMaxOrderingByCohortId(1L)).thenReturn(-1)

        val savedEntry = CohortEntryEntity(
            id = 10L, cohort = cohort, negated = false, inCohort = true,
            referencedCohortId = 5L, ordering = 0, updatedAt = updatedAt,
        )
        whenever(cohortEntryRepository.save(any<CohortEntryEntity>())).thenReturn(savedEntry)

        cohortEntryService.createEntry(1L, CreateCohortEntryRequest(inCohort = true, referencedCohortId = 5L))

        val captor = argumentCaptor<CohortEntryEntity>()
        verify(cohortEntryRepository).save(captor.capture())
        assertThat(captor.firstValue.inCohort).isTrue()
        assertThat(captor.firstValue.referencedCohortId).isEqualTo(5L)
    }

    @Test
    fun `updateEntry replaces conditions and returns dto`() {
        val cohort = CohortEntity(id = 1L, websiteId = websiteId, name = "Test", updatedAt = updatedAt)
        val entry = CohortEntryEntity(
            id = 10L, cohort = cohort, negated = true, inCohort = false, ordering = 0, updatedAt = updatedAt,
        )
        entry.conditions.add(
            CohortEntryConditionEntity(
                id = 100L, cohortEntry = entry, ordering = 1,
                conditionType = ComparisonOperator.CONTAINS, field = "old", value = "old",
            )
        )

        whenever(cohortEntryRepository.findByIdAndCohortId(10L, 1L)).thenReturn(entry)
        whenever(cohortEntryRepository.save(any<CohortEntryEntity>())).thenAnswer { invocation ->
            val saved = invocation.getArgument<CohortEntryEntity>(0)
            saved.conditions.forEachIndexed { index, cond ->
                if (cond.id == null) cond.id = 200L + index
            }
            saved
        }

        val result = cohortEntryService.updateEntry(1L, 10L, CreateCohortEntryRequest(
            negated = false,
            operator = LogicalOperator.AND,
            conditions = listOf(
                CreateCohortEntryConditionRequest(conditionType = ComparisonOperator.EQUALS, field = "count", value = "5")
            ),
        ))

        val captor = argumentCaptor<CohortEntryEntity>()
        verify(cohortEntryRepository).save(captor.capture())
        assertThat(captor.firstValue.negated).isFalse()
        assertThat(captor.firstValue.operator).isEqualTo(LogicalOperator.AND)
        assertThat(captor.firstValue.conditions).hasSize(1)
        assertThat(captor.firstValue.conditions[0].conditionType).isEqualTo(ComparisonOperator.EQUALS)

        verify(changeService).createChange(10L, EntityType.COHORT_ENTRY, ChangeType.UPDATED)

        assertThat(result).isNotNull
    }

    @Test
    fun `updateEntry returns null when not found`() {
        whenever(cohortEntryRepository.findByIdAndCohortId(10L, 1L)).thenReturn(null)

        assertThat(
            cohortEntryService.updateEntry(1L, 10L, CreateCohortEntryRequest(negated = true))
        ).isNull()
        verifyNoInteractions(changeService)
    }

    @Test
    fun `updateOrdering reorders entries`() {
        val cohort = CohortEntity(id = 1L, websiteId = websiteId, name = "Test", updatedAt = updatedAt)
        val entry1 = CohortEntryEntity(
            id = 10L, cohort = cohort, negated = true, inCohort = false, ordering = 0, updatedAt = updatedAt,
        )
        val entry2 = CohortEntryEntity(
            id = 11L, cohort = cohort, negated = false, inCohort = false,
            operator = LogicalOperator.AND, ordering = 1, updatedAt = updatedAt,
        )

        whenever(cohortRepository.existsById(1L)).thenReturn(true)
        whenever(cohortEntryRepository.findAllByCohortIdAndIdIn(1L, listOf(11L, 10L)))
            .thenReturn(listOf(entry1, entry2))
        whenever(cohortEntryRepository.saveAll(any<Collection<CohortEntryEntity>>()))
            .thenReturn(listOf(entry2, entry1))

        cohortEntryService.updateOrdering(1L, listOf(
            CohortEntryOrderingEntry(id = 11L, ordering = 0),
            CohortEntryOrderingEntry(id = 10L, ordering = 1),
        ))

        assertThat(entry2.ordering).isEqualTo(0)
        assertThat(entry1.ordering).isEqualTo(1)
    }

    @Test
    fun `updateOrdering throws when cohort not found`() {
        whenever(cohortRepository.existsById(99L)).thenReturn(false)

        assertThatThrownBy {
            cohortEntryService.updateOrdering(99L, listOf(CohortEntryOrderingEntry(id = 1L, ordering = 0)))
        }.isInstanceOf(IllegalArgumentException::class.java).hasMessageContaining("99")
    }

    @Test
    fun `updateOrdering throws when entries not found in cohort`() {
        whenever(cohortRepository.existsById(1L)).thenReturn(true)
        whenever(cohortEntryRepository.findAllByCohortIdAndIdIn(1L, listOf(999L))).thenReturn(emptyList())

        assertThatThrownBy {
            cohortEntryService.updateOrdering(1L, listOf(CohortEntryOrderingEntry(id = 999L, ordering = 0)))
        }.isInstanceOf(IllegalArgumentException::class.java).hasMessageContaining("999")
    }

    @Test
    fun `deleteEntry deletes and records change`() {
        val cohort = CohortEntity(id = 1L, websiteId = websiteId, name = "Test", updatedAt = updatedAt)
        val entry = CohortEntryEntity(
            id = 10L, cohort = cohort, negated = true, inCohort = false, ordering = 0, updatedAt = updatedAt,
        )
        whenever(cohortEntryRepository.findByIdAndCohortId(10L, 1L)).thenReturn(entry)

        assertThat(cohortEntryService.deleteEntry(1L, 10L)).isTrue()
        verify(cohortEntryRepository).delete(entry)
        verify(changeService).createChange(10L, EntityType.COHORT_ENTRY, ChangeType.DELETED)
    }

    @Test
    fun `deleteEntry returns false when not found`() {
        whenever(cohortEntryRepository.findByIdAndCohortId(10L, 1L)).thenReturn(null)

        assertThat(cohortEntryService.deleteEntry(1L, 10L)).isFalse()
        verifyNoInteractions(changeService)
    }
}
