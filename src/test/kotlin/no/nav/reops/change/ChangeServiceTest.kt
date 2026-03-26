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
}
