package no.nav.reops.repositories

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import jakarta.persistence.EntityManager
import no.nav.reops.dashboard.DashboardEntity
import no.nav.reops.dashboard.DashboardRepository
import no.nav.reops.graph.GraphEntity
import no.nav.reops.graph.GraphRepository
import no.nav.reops.graphcategory.GraphCategoryEntity
import no.nav.reops.graphcategory.GraphCategoryRepository
import no.nav.reops.project.ProjectEntity
import no.nav.reops.project.ProjectRepository
import no.nav.reops.query.QueryEntity
import no.nav.reops.query.QueryRepository
import no.nav.reops.cohort.CohortEntity
import no.nav.reops.cohort.CohortEntryConditionEntity
import no.nav.reops.cohort.CohortEntryEntity
import no.nav.reops.cohort.CohortEntryRepository
import no.nav.reops.cohort.CohortRepository
import no.nav.reops.cohort.ConditionType
import no.nav.reops.cohort.EntryCondition
import no.nav.reops.cohort.EntryOperator
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.test.annotation.FlywayTest
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestConstructor
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Transactional
@SpringBootTest
@FlywayTest
@AutoConfigureEmbeddedDatabase(provider = AutoConfigureEmbeddedDatabase.DatabaseProvider.ZONKY)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class RepositorySmokeTest(
    private val projectRepository: ProjectRepository,
    private val dashboardRepository: DashboardRepository,
    private val graphCategoryRepository: GraphCategoryRepository,
    private val graphRepository: GraphRepository,
    private val queryRepository: QueryRepository,
    private val cohortRepository: CohortRepository,
    private val cohortEntryRepository: CohortEntryRepository,
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

    private fun createCategory(dashboard: DashboardEntity, name: String = "category"): GraphCategoryEntity =
        graphCategoryRepository.save(GraphCategoryEntity(dashboard = dashboard, name = name)).also {
            entityManager.flush()
            entityManager.clear()
        }

    private fun createGraph(category: GraphCategoryEntity, name: String = "graph"): GraphEntity =
        graphRepository.save(GraphEntity(category = category, name = name)).also {
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
    fun `graph category repository can save and read`() {
        val project = createProject("project")
        val dashboard = createDashboard(project, "dashboard")

        val saved = graphCategoryRepository.save(
            GraphCategoryEntity(
                dashboard = dashboard,
                name = "category",
            )
        )
        assertThat(saved.id).isNotNull()

        val loaded = graphCategoryRepository.findById(saved.id!!)
        assertThat(loaded).isPresent
        assertThat(loaded.get().name).isEqualTo("category")
    }

    @Test
    fun `graph repository can save and read`() {
        val project = createProject("project")
        val dashboard = createDashboard(project, "dashboard")
        val category = createCategory(dashboard, "category")

        val saved = graphRepository.save(
            GraphEntity(
                category = category,
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
        val category = createCategory(dashboard, "category")
        val graph = createGraph(category, "graph")

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

    @Test
    fun `cohort repository can save and read`() {
        val saved = cohortRepository.save(
            CohortEntity(
                websiteId = UUID.fromString("98c1936a-38a4-11f1-8197-5a715c0b0a36"),
                name = "test cohort",
            )
        )
        assertThat(saved.id).isNotNull()

        entityManager.flush()
        entityManager.clear()

        val loaded = cohortRepository.findById(saved.id!!)
        assertThat(loaded).isPresent
        assertThat(loaded.get().name).isEqualTo("test cohort")
    }

    @Test
    fun `cohort entry repository can save and read with conditions`() {
        val cohort = cohortRepository.save(
            CohortEntity(
                websiteId = UUID.fromString("98c1936a-38a4-11f1-8197-5a715c0b0a36"),
                name = "cohort for entries",
            )
        )
        entityManager.flush()
        entityManager.clear()

        val entry = CohortEntryEntity(
            cohort = cohort,
            condition = EntryCondition.NOT,
            operator = null,
        )
        entry.conditions.add(
            CohortEntryConditionEntity(
                cohortEntry = entry,
                ordering = 1,
                conditionType = ConditionType.NOT_LIKE,
                field = "platform",
                value = "https://example.no",
            )
        )

        val saved = cohortEntryRepository.save(entry)
        assertThat(saved.id).isNotNull()

        entityManager.flush()
        entityManager.clear()

        val loaded = cohortEntryRepository.findById(saved.id!!)
        assertThat(loaded).isPresent
        assertThat(loaded.get().condition).isEqualTo(EntryCondition.NOT)
        assertThat(loaded.get().conditions).hasSize(1)
        assertThat(loaded.get().conditions[0].conditionType).isEqualTo(ConditionType.NOT_LIKE)
        assertThat(loaded.get().conditions[0].field).isEqualTo("platform")
    }
}