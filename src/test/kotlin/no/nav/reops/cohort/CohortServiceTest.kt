package no.nav.reops.cohort

import no.nav.reops.change.ChangeDto
import no.nav.reops.change.ChangeService
import no.nav.reops.change.ChangeType
import no.nav.reops.change.EntityType
import org.assertj.core.api.Assertions.assertThat
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
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class CohortServiceTest {

    @Mock
    private lateinit var cohortRepository: CohortRepository

    @Mock
    private lateinit var cohortNodeRepository: CohortNodeRepository

    @Mock
    private lateinit var changeService: ChangeService

    private lateinit var cohortService: CohortService

    private val updatedAt = LocalDateTime.of(2026, 4, 13, 10, 0, 0)
    private val websiteId = UUID.fromString("98c1936a-38a4-11f1-8197-5a715c0b0a36")

    @BeforeEach
    fun setUp() {
        cohortService = CohortService(cohortRepository, cohortNodeRepository, changeService)
    }

    @Test
    fun `createCohort saves entity and returns dto`() {
        val saved = cohort(id = 1L, name = "Test cohort", description = "desc")
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
    fun `getAllCohorts returns all cohorts`() {
        whenever(cohortRepository.findAllByDeletedAtIsNull()).thenReturn(listOf(
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
        whenever(cohortRepository.findAllByDeletedAtIsNull()).thenReturn(emptyList())

        val result = cohortService.getAllCohorts()

        assertThat(result).isEmpty()
    }

    @Test
    fun `getCohortsByWebsiteId returns filtered cohorts`() {
        whenever(cohortRepository.findAllByWebsiteIdAndDeletedAtIsNull(websiteId)).thenReturn(listOf(cohort(id = 1L, name = "A")))
        whenever(changeService.getChangeByEntityIdAndEntityType(1L, EntityType.COHORT)).thenReturn(null)

        val result = cohortService.getCohortsByWebsiteId(websiteId)

        assertThat(result).hasSize(1)
        assertThat(result[0].websiteId).isEqualTo(websiteId)
    }

    @Test
    fun `getCohortsByWebsiteId returns empty list when none for website`() {
        val otherWebsiteId = UUID.fromString("22222222-2222-2222-2222-222222222222")
        whenever(cohortRepository.findAllByWebsiteIdAndDeletedAtIsNull(otherWebsiteId)).thenReturn(emptyList())

        val result = cohortService.getCohortsByWebsiteId(otherWebsiteId)

        assertThat(result).isEmpty()
    }

    @Test
    fun `getCohortById returns detail dto with the criteria tree`() {
        val entity = cohort(id = 1L, name = "Test")
        val root = node(id = 10L, cohortId = 1L, parentId = null, nodeType = CohortNodeType.GROUP)
        val condition = node(
            id = 100L, cohortId = 1L, parentId = 10L, nodeType = CohortNodeType.CONDITION, ordering = 0,
            field = "platform", conditionType = ComparisonOperator.CONTAINS, value = "https://example.no",
        )
        whenever(cohortRepository.findActiveById(1L)).thenReturn(entity)
        whenever(cohortNodeRepository.findAllByCohortIdOrderByOrderingAsc(1L)).thenReturn(listOf(root, condition))
        whenever(changeService.getChangeByEntityIdAndEntityType(1L, EntityType.COHORT)).thenReturn(null)

        val result = cohortService.getCohortById(1L)

        assertThat(result).isNotNull
        assertThat(result!!.id).isEqualTo(1L)
        val group = result.root as CohortNodeDto.GroupNodeDto
        assertThat(group.children).hasSize(1)
        val conditionDto = group.children.single() as CohortNodeDto.ConditionNodeDto
        assertThat(conditionDto.conditionType).isEqualTo(ComparisonOperator.CONTAINS)
        assertThat(conditionDto.field).isEqualTo("platform")
    }

    @Test
    fun `getCohortById returns null root when no criteria have been saved yet`() {
        val entity = cohort(id = 1L, name = "Test")
        whenever(cohortRepository.findActiveById(1L)).thenReturn(entity)
        whenever(cohortNodeRepository.findAllByCohortIdOrderByOrderingAsc(1L)).thenReturn(emptyList())
        whenever(changeService.getChangeByEntityIdAndEntityType(1L, EntityType.COHORT)).thenReturn(null)

        val result = cohortService.getCohortById(1L)

        assertThat(result!!.root).isNull()
    }

    @Test
    fun `getCohortById returns null when not found`() {
        whenever(cohortRepository.findActiveById(99L)).thenReturn(null)

        assertThat(cohortService.getCohortById(99L)).isNull()
    }

    @Test
    fun `updateCohort saves changes and returns dto`() {
        val entity = cohort(id = 1L, name = "Old name")
        whenever(cohortRepository.findActiveById(1L)).thenReturn(entity)
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
        whenever(cohortRepository.findActiveById(1L)).thenReturn(entity)
        whenever(cohortRepository.save(any<CohortEntity>())).thenReturn(entity)
        whenever(changeService.getChangeByEntityIdAndEntityType(1L, EntityType.COHORT)).thenReturn(null)

        val result = cohortService.updateCohort(
            1L, UpdateCohortRequest(name = "Same name", websiteId = websiteId)
        )

        assertThat(result).isNotNull
    }

    @Test
    fun `updateCohort returns null when not found`() {
        whenever(cohortRepository.findActiveById(1L)).thenReturn(null)

        assertThat(cohortService.updateCohort(1L, UpdateCohortRequest(name = "x", websiteId = websiteId))).isNull()
        verifyNoInteractions(changeService)
    }

    @Test
    fun `deleteCohort soft-deletes and records change`() {
        val entity = cohort(id = 1L, name = "To delete")
        whenever(cohortRepository.findActiveById(1L)).thenReturn(entity)
        whenever(cohortRepository.save(any<CohortEntity>())).thenReturn(entity)

        assertThat(cohortService.deleteCohort(1L)).isTrue()
        val captor = argumentCaptor<CohortEntity>()
        verify(cohortRepository).save(captor.capture())
        assertThat(captor.firstValue.deletedAt).isNotNull()
        verify(changeService).createChange(1L, EntityType.COHORT, ChangeType.DELETED)
    }

    @Test
    fun `deleteCohort returns false when not found`() {
        whenever(cohortRepository.findActiveById(1L)).thenReturn(null)

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

    private fun node(
        id: Long,
        cohortId: Long,
        parentId: Long?,
        nodeType: CohortNodeType,
        ordering: Int = 0,
        combinator: LogicalOperator? = if (nodeType == CohortNodeType.GROUP) LogicalOperator.AND else null,
        field: String? = null,
        conditionType: ComparisonOperator? = null,
        value: String? = null,
    ) = CohortNodeEntity(
        id = id, cohortId = cohortId, parentId = parentId, nodeType = nodeType, ordering = ordering,
        combinator = combinator, field = field, conditionType = conditionType, value = value, updatedAt = updatedAt,
    )

    private fun changeDto(entityId: Long, changeType: ChangeType) = ChangeDto(
        entityId = entityId, entityType = EntityType.COHORT, changeType = changeType,
        updatedAt = updatedAt, changedByEmail = "test@nav.no", changedByName = "Test User", changedByNavIdent = "T123456",
    )
}
