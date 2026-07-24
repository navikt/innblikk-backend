package no.nav.reops.cohort

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CohortNodeTreePersisterTest {

    private val saved = mutableListOf<CohortNodeEntity>()
    private var nextId = 1L

    private val persister = CohortNodeTreePersister { entity ->
        val withId = entity.also { it.id = nextId++ }
        saved.add(withId)
        withId
    }

    private fun condition(field: String, value: String) =
        CohortNodeRequest.ConditionNode(field = field, conditionType = ComparisonOperator.EQUALS, value = value)

    @Test
    fun `persists a flat AND group with one condition, root has no parent`() {
        val root = CohortNodeRequest.GroupNode(
            combinator = LogicalOperator.AND,
            children = listOf(condition("event_name", "besok")),
        )

        persister.persist(root, cohortId = 42L)

        assertThat(saved).hasSize(2)
        val rootEntity = saved.first { it.nodeType == CohortNodeType.GROUP }
        val conditionEntity = saved.first { it.nodeType == CohortNodeType.CONDITION }
        assertThat(rootEntity.parentId).isNull()
        assertThat(rootEntity.cohortId).isEqualTo(42L)
        assertThat(conditionEntity.parentId).isEqualTo(rootEntity.id)
        assertThat(conditionEntity.field).isEqualTo("event_name")
        assertThat(conditionEntity.value).isEqualTo("besok")
    }

    @Test
    fun `assigns sibling ordering based on child position`() {
        val root = CohortNodeRequest.GroupNode(
            children = listOf(condition("a", "1"), condition("b", "2"), condition("c", "3")),
        )

        persister.persist(root, cohortId = 1L)

        val conditions = saved.filter { it.nodeType == CohortNodeType.CONDITION }.sortedBy { it.ordering }
        assertThat(conditions.map { it.field }).containsExactly("a", "b", "c")
        assertThat(conditions.map { it.ordering }).containsExactly(0, 1, 2)
    }

    @Test
    fun `persists nested groups with correct parent chain`() {
        val root = CohortNodeRequest.GroupNode(
            combinator = LogicalOperator.OR,
            children = listOf(
                CohortNodeRequest.GroupNode(children = listOf(condition("text", "A"))),
                CohortNodeRequest.GroupNode(children = listOf(condition("text", "B"))),
            ),
        )

        persister.persist(root, cohortId = 1L)

        val groups = saved.filter { it.nodeType == CohortNodeType.GROUP }
        assertThat(groups).hasSize(3) // root + 2 sub-groups
        val rootEntity = groups.first { it.parentId == null }
        val subGroups = groups.filter { it.parentId == rootEntity.id }
        assertThat(subGroups).hasSize(2)
        val conditions = saved.filter { it.nodeType == CohortNodeType.CONDITION }
        assertThat(conditions).allMatch { c -> subGroups.any { it.id == c.parentId } }
    }

    @Test
    fun `persists a negated cohort reference`() {
        val root = CohortNodeRequest.GroupNode(
            children = listOf(CohortNodeRequest.CohortRefNode(referencedCohortId = 99L, negated = true)),
        )

        persister.persist(root, cohortId = 1L)

        val ref = saved.first { it.nodeType == CohortNodeType.COHORT_REF }
        assertThat(ref.referencedCohortId).isEqualTo(99L)
        assertThat(ref.negated).isTrue()
    }

    @Test
    fun `persists a sequence node with anchor and target tagged by sequence role`() {
        val root = CohortNodeRequest.GroupNode(
            children = listOf(
                CohortNodeRequest.SequenceNode(
                    anchor = CohortNodeRequest.GroupNode(children = listOf(condition("tekst", "Vedtak alderspensjon"))),
                    target = CohortNodeRequest.GroupNode(children = listOf(condition("tekst", "Fremtidig vedtak"))),
                    relation = SequenceRelation.NOT_FOLLOWED_BY,
                    windowValue = 1,
                    windowUnit = SequenceTimeUnit.DAY,
                ),
            ),
        )

        persister.persist(root, cohortId = 1L)

        val sequenceEntity = saved.first { it.nodeType == CohortNodeType.SEQUENCE }
        assertThat(sequenceEntity.sequenceRelation).isEqualTo(SequenceRelation.NOT_FOLLOWED_BY)
        assertThat(sequenceEntity.sequenceWindowValue).isEqualTo(1)
        assertThat(sequenceEntity.sequenceWindowUnit).isEqualTo(SequenceTimeUnit.DAY)

        val anchorGroup = saved.first { it.parentId == sequenceEntity.id && it.sequenceRole == SequenceRole.ANCHOR }
        val targetGroup = saved.first { it.parentId == sequenceEntity.id && it.sequenceRole == SequenceRole.TARGET }
        val anchorCondition = saved.first { it.parentId == anchorGroup.id }
        val targetCondition = saved.first { it.parentId == targetGroup.id }
        assertThat(anchorCondition.value).isEqualTo("Vedtak alderspensjon")
        assertThat(targetCondition.value).isEqualTo("Fremtidig vedtak")
    }

    @Test
    fun `persists an empty root group with no children`() {
        val root = CohortNodeRequest.GroupNode(children = emptyList())

        persister.persist(root, cohortId = 1L)

        assertThat(saved).hasSize(1)
        assertThat(saved.single().parentId).isNull()
    }
}
