package no.nav.reops.change

import no.nav.reops.security.AuthenticatedUser
import no.nav.reops.security.AuthenticatedUserProvider
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

    @Mock
    private lateinit var authenticatedUserProvider: AuthenticatedUserProvider

    private lateinit var changeService: ChangeService

    @BeforeEach
    fun setUp() {
        changeService = ChangeService(changeRepository, authenticatedUserProvider)
    }

    @Test
    fun `createChange saves entity and returns dto`() {
        val updatedAt = LocalDateTime.of(2025, 1, 2, 3, 4, 5)
        val user = AuthenticatedUser(
            email = "test@nav.no",
            name = "Test User",
            navIdent = "T123456",
        )
        whenever(authenticatedUserProvider.getCurrentUser()).thenReturn(user)

        val savedEntity = ChangeEntity(
            id = 1L,
            entityType = EntityType.GRAPH,
            entityId = 10L,
            changeType = ChangeType.CREATED,
            updatedAt = updatedAt,
            changedByEmail = "test@nav.no",
            changedByName = "Test User",
            changedByNavIdent = "T123456",
        )

        whenever(changeRepository.save(any<ChangeEntity>())).thenReturn(savedEntity)

        val result = changeService.createChange(10L, EntityType.GRAPH, ChangeType.CREATED)

        val captor = argumentCaptor<ChangeEntity>()
        verify(changeRepository).save(captor.capture())
        assertThat(captor.firstValue.entityId).isEqualTo(10L)
        assertThat(captor.firstValue.entityType).isEqualTo(EntityType.GRAPH)
        assertThat(captor.firstValue.changeType).isEqualTo(ChangeType.CREATED)
        assertThat(captor.firstValue.changedByEmail).isEqualTo("test@nav.no")
        assertThat(captor.firstValue.changedByName).isEqualTo("Test User")
        assertThat(captor.firstValue.changedByNavIdent).isEqualTo("T123456")

        assertThat(result.entityId).isEqualTo(10L)
        assertThat(result.entityType).isEqualTo(EntityType.GRAPH)
        assertThat(result.changeType).isEqualTo(ChangeType.CREATED)
        assertThat(result.updatedAt).isEqualTo(updatedAt)
        assertThat(result.changedByEmail).isEqualTo("test@nav.no")
        assertThat(result.changedByName).isEqualTo("Test User")
        assertThat(result.changedByNavIdent).isEqualTo("T123456")
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
    fun `getChangeByEntityIdAndEntityType returns null when not found`() {
        whenever(
            changeRepository.findFirstByEntityTypeAndEntityIdOrderByUpdatedAtDesc(
                EntityType.QUERY,
                999L,
            )
        ).thenReturn(null)

        val result = changeService.getChangeByEntityIdAndEntityType(999L, EntityType.QUERY)

        assertThat(result).isNull()
    }

    @Test
    fun `createChange with explicit user uses provided user instead of SecurityContext`() {
        val updatedAt = LocalDateTime.of(2025, 3, 4, 5, 6, 7)
        val explicitUser = AuthenticatedUser(
            email = "ws-user@nav.no",
            name = "WS User",
            navIdent = "W654321",
        )

        val savedEntity = ChangeEntity(
            id = 3L,
            entityType = EntityType.QUERY,
            entityId = 30L,
            changeType = ChangeType.UPDATED,
            updatedAt = updatedAt,
            changedByEmail = "ws-user@nav.no",
            changedByName = "WS User",
            changedByNavIdent = "W654321",
        )

        whenever(changeRepository.save(any<ChangeEntity>())).thenReturn(savedEntity)

        val result = changeService.createChange(30L, EntityType.QUERY, ChangeType.UPDATED, explicitUser)

        val captor = argumentCaptor<ChangeEntity>()
        verify(changeRepository).save(captor.capture())
        assertThat(captor.firstValue.changedByEmail).isEqualTo("ws-user@nav.no")
        assertThat(captor.firstValue.changedByName).isEqualTo("WS User")
        assertThat(captor.firstValue.changedByNavIdent).isEqualTo("W654321")

        assertThat(result.changedByEmail).isEqualTo("ws-user@nav.no")
        assertThat(result.changedByName).isEqualTo("WS User")
        assertThat(result.changedByNavIdent).isEqualTo("W654321")
    }

    @Test
    fun `createChange with null explicit user records empty fields`() {
        val updatedAt = LocalDateTime.of(2025, 4, 5, 6, 7, 8)
        val savedEntity = ChangeEntity(
            id = 4L,
            entityType = EntityType.GRAPH,
            entityId = 40L,
            changeType = ChangeType.CREATED,
            updatedAt = updatedAt,
        )

        whenever(changeRepository.save(any<ChangeEntity>())).thenReturn(savedEntity)

        changeService.createChange(40L, EntityType.GRAPH, ChangeType.CREATED, null)

        val captor = argumentCaptor<ChangeEntity>()
        verify(changeRepository).save(captor.capture())
        assertThat(captor.firstValue.changedByEmail).isEmpty()
        assertThat(captor.firstValue.changedByName).isEmpty()
        assertThat(captor.firstValue.changedByNavIdent).isEmpty()
    }
}
