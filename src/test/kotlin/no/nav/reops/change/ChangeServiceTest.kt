package no.nav.reops.change

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class ChangeServiceTest {

    @Mock
    private lateinit var changeRepository: ChangeRepository

    private lateinit var changeService: ChangeService

    @BeforeEach
    fun setUp() {
        changeService = ChangeService(changeRepository)
    }

    @Test
    fun `createChange saves entity and returns dto`() {
        val updatedAt = LocalDateTime.of(2025, 1, 2, 3, 4, 5)
        val savedEntity = ChangeEntity(
            id = 1L,
            entityType = EntityType.GRAPH,
            entityId = 10L,
            changeType = ChangeType.CREATED,
            updatedAt = updatedAt,
        )

        whenever(changeRepository.save(any<ChangeEntity>())).thenReturn(savedEntity)

        val result = changeService.createChange(10L, EntityType.GRAPH, ChangeType.CREATED)

        val captor = argumentCaptor<ChangeEntity>()
        verify(changeRepository).save(captor.capture())
        assertThat(captor.firstValue.entityId).isEqualTo(10L)
        assertThat(captor.firstValue.entityType).isEqualTo(EntityType.GRAPH)
        assertThat(captor.firstValue.changeType).isEqualTo(ChangeType.CREATED)

        assertThat(result.entityId).isEqualTo(10L)
        assertThat(result.entityType).isEqualTo(EntityType.GRAPH)
        assertThat(result.changeType).isEqualTo(ChangeType.CREATED)
        assertThat(result.updatedAt).isEqualTo(updatedAt)
    }

    @Test
    fun `getChangeByEntityIdAndEntityType returns latest change`() {
        val updatedAt = LocalDateTime.of(2025, 2, 3, 4, 5, 6)
        val entity = ChangeEntity(
            id = 2L,
            entityType = EntityType.PROJECT,
            entityId = 20L,
            changeType = ChangeType.UPDATED,
            updatedAt = updatedAt,
        )
        whenever(
            changeRepository.findFirstByEntityTypeAndEntityIdOrderByUpdatedAtDesc(
                EntityType.PROJECT,
                20L,
            )
        ).thenReturn(entity)

        val result = changeService.getChangeByEntityIdAndEntityType(20L, EntityType.PROJECT)

        assertThat(result).isNotNull
        assertThat(result!!.entityId).isEqualTo(20L)
        assertThat(result.entityType).isEqualTo(EntityType.PROJECT)
        assertThat(result.changeType).isEqualTo(ChangeType.UPDATED)
        assertThat(result.updatedAt).isEqualTo(updatedAt)
    }

    @Test
    fun `getAllChangeByEntityIdAndEntityType maps list to dto`() {
        val first = ChangeEntity(
            id = 3L,
            entityType = EntityType.DASHBOARD,
            entityId = 30L,
            changeType = ChangeType.CREATED,
            updatedAt = LocalDateTime.of(2025, 3, 4, 5, 6, 7),
        )
        val second = ChangeEntity(
            id = 4L,
            entityType = EntityType.DASHBOARD,
            entityId = 30L,
            changeType = ChangeType.DELETED,
            updatedAt = LocalDateTime.of(2025, 3, 5, 6, 7, 8),
        )
        whenever(
            changeRepository.findByEntityTypeAndEntityIdOrderByUpdatedAtDesc(
                EntityType.DASHBOARD,
                30L,
            )
        ).thenReturn(listOf(second, first))

        val result = changeService.getAllChangeByEntityIdAndEntityType(30L, EntityType.DASHBOARD)

        assertThat(result).hasSize(2)
        assertThat(result[0].changeType).isEqualTo(ChangeType.DELETED)
        assertThat(result[1].changeType).isEqualTo(ChangeType.CREATED)
    }
}
