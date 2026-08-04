package no.nav.reops.cohort

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CohortNodeTreeBuilderTest {

    private var nextId = 1L
    private fun id() = nextId++

    private fun groupEntity(
        cohortId: Long = 1L,
        parentId: Long? = null,
        combinator: LogicalOperator = LogicalOperator.AND,
        negated: Boolean = false,
        ordering: Int = 0,
    ) = CohortNodeEntity(
        id = id(), cohortId = cohortId, parentId = parentId,
        nodeType = CohortNodeType.GROUP, combinator = combinator, negated = negated, ordering = ordering,
    )

    private fun conditionEntity(parentId: Long?, field: String, value: String, ordering: Int = 0) = CohortNodeEntity(
        id = id(), cohortId = 1L, parentId = parentId,
        nodeType = CohortNodeType.CONDITION, field = field, conditionType = ComparisonOperator.EQUALS, value = value,
        ordering = ordering,
    )

    private fun cohortRefEntity(parentId: Long?, referencedCohortId: Long, negated: Boolean = false, ordering: Int = 0) =
        CohortNodeEntity(
            id = id(), cohortId = 1L, parentId = parentId,
            nodeType = CohortNodeType.COHORT_REF, referencedCohortId = referencedCohortId, negated = negated,
            ordering = ordering,
        )

    @Test
    fun `builds a flat AND group with one condition`() {
        val root = groupEntity()
        val condition = conditionEntity(parentId = root.id, field = "event_name", value = "besok")

        val tree = CohortNodeTreeBuilder.build(listOf(root, condition))

        assertThat(tree).isInstanceOf(CohortNodeDto.GroupNodeDto::class.java)
        val group = tree as CohortNodeDto.GroupNodeDto
        assertThat(group.combinator).isEqualTo(LogicalOperator.AND)
        assertThat(group.children).hasSize(1)
        val child = group.children.single() as CohortNodeDto.ConditionNodeDto
        assertThat(child.field).isEqualTo("event_name")
        assertThat(child.value).isEqualTo("besok")
    }

    @Test
    fun `preserves ordering of siblings`() {
        val root = groupEntity()
        val c2 = conditionEntity(parentId = root.id, field = "b", value = "2", ordering = 2)
        val c1 = conditionEntity(parentId = root.id, field = "a", value = "1", ordering = 1)

        // intentionally inserted out of order — builder must sort by `ordering`
        val tree = CohortNodeTreeBuilder.build(listOf(root, c2, c1)) as CohortNodeDto.GroupNodeDto

        assertThat(tree.children.map { (it as CohortNodeDto.ConditionNodeDto).field }).containsExactly("a", "b")
    }

    @Test
    fun `builds nested OR-of-groups`() {
        val root = groupEntity(combinator = LogicalOperator.OR)
        val subGroup1 = groupEntity(parentId = root.id, ordering = 0)
        val subGroup2 = groupEntity(parentId = root.id, ordering = 1)
        val c1 = conditionEntity(parentId = subGroup1.id, field = "text", value = "A")
        val c2 = conditionEntity(parentId = subGroup2.id, field = "text", value = "B")

        val tree = CohortNodeTreeBuilder.build(listOf(root, subGroup1, subGroup2, c1, c2)) as CohortNodeDto.GroupNodeDto

        assertThat(tree.children).hasSize(2)
        val group1 = tree.children[0] as CohortNodeDto.GroupNodeDto
        val group2 = tree.children[1] as CohortNodeDto.GroupNodeDto
        assertThat((group1.children.single() as CohortNodeDto.ConditionNodeDto).value).isEqualTo("A")
        assertThat((group2.children.single() as CohortNodeDto.ConditionNodeDto).value).isEqualTo("B")
    }

    @Test
    fun `builds a negated cohort reference`() {
        val root = groupEntity()
        val ref = cohortRefEntity(parentId = root.id, referencedCohortId = 99L, negated = true)

        val tree = CohortNodeTreeBuilder.build(listOf(root, ref)) as CohortNodeDto.GroupNodeDto

        val child = tree.children.single() as CohortNodeDto.CohortRefNodeDto
        assertThat(child.referencedCohortId).isEqualTo(99L)
        assertThat(child.negated).isTrue()
    }

    @Test
    fun `builds a sequence node from its tagged anchor and target children`() {
        val root = groupEntity()
        val sequence = CohortNodeEntity(
            id = id(), cohortId = 1L, parentId = root.id, nodeType = CohortNodeType.SEQUENCE,
            sequenceRelation = SequenceRelation.NOT_FOLLOWED_BY, sequenceWindowValue = 1,
            sequenceWindowUnit = SequenceTimeUnit.DAY,
        )
        val anchor = CohortNodeEntity(
            id = id(), cohortId = 1L, parentId = sequence.id, nodeType = CohortNodeType.GROUP,
            combinator = LogicalOperator.AND, sequenceRole = SequenceRole.ANCHOR,
        )
        val target = CohortNodeEntity(
            id = id(), cohortId = 1L, parentId = sequence.id, nodeType = CohortNodeType.GROUP,
            combinator = LogicalOperator.AND, sequenceRole = SequenceRole.TARGET,
        )
        val anchorCondition = conditionEntity(parentId = anchor.id, field = "tekst", value = "Vedtak alderspensjon")
        val targetCondition = conditionEntity(parentId = target.id, field = "tekst", value = "Fremtidig vedtak")

        val tree = CohortNodeTreeBuilder.build(
            listOf(root, sequence, anchor, target, anchorCondition, targetCondition),
        ) as CohortNodeDto.GroupNodeDto

        val sequenceDto = tree.children.single() as CohortNodeDto.SequenceNodeDto
        assertThat(sequenceDto.relation).isEqualTo(SequenceRelation.NOT_FOLLOWED_BY)
        assertThat(sequenceDto.windowValue).isEqualTo(1)
        assertThat(sequenceDto.windowUnit).isEqualTo(SequenceTimeUnit.DAY)
        assertThat((sequenceDto.anchor.children.single() as CohortNodeDto.ConditionNodeDto).value)
            .isEqualTo("Vedtak alderspensjon")
        assertThat((sequenceDto.target.children.single() as CohortNodeDto.ConditionNodeDto).value)
            .isEqualTo("Fremtidig vedtak")
    }

    @Test
    fun `builds an empty root group`() {
        val root = groupEntity()

        val tree = CohortNodeTreeBuilder.build(listOf(root)) as CohortNodeDto.GroupNodeDto

        assertThat(tree.children).isEmpty()
    }
}
