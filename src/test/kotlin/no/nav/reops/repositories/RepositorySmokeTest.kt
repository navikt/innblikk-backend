package no.nav.reops.repositories

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import jakarta.persistence.EntityManager
import no.nav.reops.dashboard.DashboardEntity
import no.nav.reops.dashboard.DashboardRepository
import no.nav.reops.graph.GraphEntity
import no.nav.reops.graph.GraphRepository
import no.nav.reops.project.ProjectEntity
import no.nav.reops.project.ProjectRepository
import no.nav.reops.query.QueryEntity
import no.nav.reops.query.QueryRepository
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.test.annotation.FlywayTest
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestConstructor
import org.springframework.transaction.annotation.Transactional

@Transactional
@SpringBootTest
@FlywayTest
@AutoConfigureEmbeddedDatabase(provider = AutoConfigureEmbeddedDatabase.DatabaseProvider.ZONKY)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class RepositorySmokeTest(
    private val projectRepository: ProjectRepository,
    private val dashboardRepository: DashboardRepository,
    private val graphRepository: GraphRepository,
    private val queryRepository: QueryRepository,
    private val entityManager: EntityManager,
) {

    private fun createProject(name: String = "project"): ProjectEntity =
        projectRepository.save(ProjectEntity(name = name)).also {
            entityManager.flush()
            entityManager.clear()
        }

    private fun createDashboard(project: ProjectEntity, name: String = "dashboard"): DashboardEntity =
        dashboardRepository.save(DashboardEntity(project = project, name = name)).also {
            entityManager.flush()
            entityManager.clear()
        }

    private fun createGraph(dashboard: DashboardEntity, name: String = "graph"): GraphEntity =
        graphRepository.save(GraphEntity(dashboard = dashboard, name = name)).also {
            entityManager.flush()
            entityManager.clear()
        }

    @Test
    fun `project repository can save and read`() {
        val saved = projectRepository.save(ProjectEntity(name = "project"))
        assertThat(saved.id).isNotNull()

        val loaded = projectRepository.findById(saved.id!!)
        assertThat(loaded).isPresent
        assertThat(loaded.get().name).isEqualTo("project")
    }

    @Test
    fun `dashboard repository can save and read`() {
        val project = createProject("project")

        val saved = dashboardRepository.save(
            DashboardEntity(
                project = project,
                name = "dashboard",
            )
        )
        assertThat(saved.id).isNotNull()

        val loaded = dashboardRepository.findById(saved.id!!)
        assertThat(loaded).isPresent
        assertThat(loaded.get().name).isEqualTo("dashboard")
    }

    @Test
    fun `graph repository can save and read`() {
        val project = createProject("project")
        val dashboard = createDashboard(project, "dashboard")

        val saved = graphRepository.save(
            GraphEntity(
                dashboard = dashboard,
                name = "graph",
            )
        )
        assertThat(saved.id).isNotNull()

        val loaded = graphRepository.findById(saved.id!!)
        assertThat(loaded).isPresent
        assertThat(loaded.get().name).isEqualTo("graph")
    }

    @Test
    fun `query repository can save and read`() {
        val project = createProject("project")
        val dashboard = createDashboard(project, "dashboard")
        val graph = createGraph(dashboard, "graph")

        val saved = queryRepository.save(
            QueryEntity(
                graph = graph,
                name = "query",
                sqlText = "select 1",
            )
        )
        assertThat(saved.id).isNotNull()

        val loaded = queryRepository.findById(saved.id!!)
        assertThat(loaded).isPresent
        assertThat(loaded.get().name).isEqualTo("query")
        assertThat(loaded.get().sqlText).isEqualTo("select 1")
    }
}