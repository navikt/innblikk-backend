package no.nav.reops.cohort

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test

class CohortTreeValidatorTest {

    private val validator = CohortTreeValidator(maxDepth = 15, maxNodes = 100)

    private fun condition(field: String = "event_name", value: String = "besok") =
        CohortNodeRequest.ConditionNode(field = field, conditionType = ComparisonOperator.EQUALS, value = value)

    private fun group(
        combinator: LogicalOperator = LogicalOperator.AND,
        negated: Boolean = false,
        children: List<CohortNodeRequest> = listOf(condition()),
    ) = CohortNodeRequest.GroupNode(combinator = combinator, negated = negated, children = children)

    private fun cohortRef(id: Long = 1L, negated: Boolean = false) =
        CohortNodeRequest.CohortRefNode(referencedCohortId = id, negated = negated)

    /** Runs validate(root) and returns the collected violation messages, or emptyList() if valid. */
    private fun violationsOf(root: CohortNodeRequest): List<String> = try {
        validator.validate(root)
        emptyList()
    } catch (ex: CohortTreeValidationException) {
        ex.violations
    }

    // ─── Valid shapes ──────────────────────────────────────────────────────────

    @Test
    fun `simple flat AND group with one condition is valid`() {
        val root = group(children = listOf(condition()))

        assertThatCode { validator.validate(root) }.doesNotThrowAnyException()
    }

    @Test
    fun `nested OR-of-groups is valid`() {
        // (text = A AND data = Ja) OR (text = B AND data = Ja)
        val root = group(
            combinator = LogicalOperator.OR,
            children = listOf(
                group(children = listOf(condition("text", "A"), condition("data", "Ja"))),
                group(children = listOf(condition("text", "B"), condition("data", "Ja"))),
            ),
        )

        assertThatCode { validator.validate(root) }.doesNotThrowAnyException()
    }

    @Test
    fun `OR group with multiple cohort references is valid`() {
        val root = group(
            combinator = LogicalOperator.OR,
            children = listOf(cohortRef(1L), cohortRef(2L)),
        )

        assertThatCode { validator.validate(root) }.doesNotThrowAnyException()
    }

    @Test
    fun `negated cohort reference is valid`() {
        val root = group(children = listOf(condition(), cohortRef(1L, negated = true)))

        assertThatCode { validator.validate(root) }.doesNotThrowAnyException()
    }

    @Test
    fun `empty root group is valid (cohort with no criteria yet)`() {
        val root = group(children = emptyList())

        assertThatCode { validator.validate(root) }.doesNotThrowAnyException()
    }

    @Test
    fun `valid sequence node is valid`() {
        val root = group(
            children = listOf(
                CohortNodeRequest.SequenceNode(
                    anchor = group(children = listOf(condition("tekst", "Vedtak alderspensjon"))),
                    target = group(children = listOf(condition("tekst", "Fremtidig vedtak"))),
                    relation = SequenceRelation.NOT_FOLLOWED_BY,
                    windowValue = 1,
                    windowUnit = SequenceTimeUnit.DAY,
                ),
            ),
        )

        assertThatCode { validator.validate(root) }.doesNotThrowAnyException()
    }

    @Test
    fun `tree at exactly max depth is valid`() {
        val root = deepChain(depth = 15)

        assertThatCode { validator.validate(root) }.doesNotThrowAnyException()
    }

    @Test
    fun `tree at exactly max node count is valid`() {
        // root group + 99 condition children = 100 nodes total
        val root = group(children = (1..99).map { condition(value = "v$it") })

        assertThatCode { validator.validate(root) }.doesNotThrowAnyException()
    }

    // ─── Root shape ──────────────────────────────────────────────────────────

    @Test
    fun `root must be a group node - condition at root is rejected`() {
        val violations = violationsOf(condition())

        assertThat(violations).anyMatch { it.contains("root", ignoreCase = true) }
    }

    @Test
    fun `root must be a group node - cohort reference at root is rejected`() {
        val violations = violationsOf(cohortRef())

        assertThat(violations).anyMatch { it.contains("root", ignoreCase = true) }
    }

    // ─── Empty non-root groups ───────────────────────────────────────────────

    @Test
    fun `non-root empty group is rejected`() {
        val root = group(children = listOf(group(children = emptyList())))

        val violations = violationsOf(root)

        assertThat(violations).anyMatch { it.contains("empty", ignoreCase = true) }
    }

    // ─── Depth limit ─────────────────────────────────────────────────────────

    @Test
    fun `tree exceeding max depth is rejected`() {
        val root = deepChain(depth = 16)

        val violations = violationsOf(root)

        assertThat(violations).anyMatch { it.contains("depth", ignoreCase = true) }
    }

    // ─── Node count limit ────────────────────────────────────────────────────

    @Test
    fun `tree exceeding max node count is rejected`() {
        // root group + 100 condition children = 101 nodes total, one over the limit
        val root = group(children = (1..100).map { condition(value = "v$it") })

        val violations = violationsOf(root)

        assertThat(violations).anyMatch { it.contains("node", ignoreCase = true) }
    }

    // ─── Sequence structural constraints ────────────────────────────────────

    @Test
    fun `sequence anchor containing a cohort reference is rejected`() {
        val root = group(
            children = listOf(
                CohortNodeRequest.SequenceNode(
                    anchor = group(children = listOf(cohortRef(1L))),
                    target = group(children = listOf(condition())),
                    relation = SequenceRelation.FOLLOWED_BY,
                    windowValue = 1,
                    windowUnit = SequenceTimeUnit.DAY,
                ),
            ),
        )

        val violations = violationsOf(root)

        assertThat(violations).anyMatch { it.contains("cohort reference", ignoreCase = true) }
    }

    @Test
    fun `sequence target containing a nested sequence is rejected`() {
        val nestedSequence = CohortNodeRequest.SequenceNode(
            anchor = group(children = listOf(condition())),
            target = group(children = listOf(condition())),
            relation = SequenceRelation.FOLLOWED_BY,
            windowValue = 1,
            windowUnit = SequenceTimeUnit.DAY,
        )
        val root = group(
            children = listOf(
                CohortNodeRequest.SequenceNode(
                    anchor = group(children = listOf(condition())),
                    target = group(children = listOf(nestedSequence)),
                    relation = SequenceRelation.FOLLOWED_BY,
                    windowValue = 1,
                    windowUnit = SequenceTimeUnit.DAY,
                ),
            ),
        )

        val violations = violationsOf(root)

        assertThat(violations).anyMatch { it.contains("sequence", ignoreCase = true) }
    }

    @Test
    fun `sequence with non-positive window value is rejected`() {
        val root = group(
            children = listOf(
                CohortNodeRequest.SequenceNode(
                    anchor = group(children = listOf(condition())),
                    target = group(children = listOf(condition())),
                    relation = SequenceRelation.FOLLOWED_BY,
                    windowValue = 0,
                    windowUnit = SequenceTimeUnit.DAY,
                ),
            ),
        )

        val violations = violationsOf(root)

        assertThat(violations).anyMatch { it.contains("window", ignoreCase = true) }
    }

    // ─── Multiple violations collected together ─────────────────────────────

    @Test
    fun `multiple violations are all collected, not just the first`() {
        val root = group(
            children = listOf(
                group(children = emptyList()), // empty non-root group
                CohortNodeRequest.SequenceNode(
                    anchor = group(children = listOf(cohortRef(1L))), // invalid anchor
                    target = group(children = listOf(condition())),
                    relation = SequenceRelation.FOLLOWED_BY,
                    windowValue = -1, // invalid window
                    windowUnit = SequenceTimeUnit.DAY,
                ),
            ),
        )

        val violations = violationsOf(root)

        assertThat(violations.size).isGreaterThanOrEqualTo(3)
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /** Builds a chain of `depth` nested AND-groups, each wrapping the next, ending in a condition leaf. */
    private fun deepChain(depth: Int): CohortNodeRequest.GroupNode {
        var node: CohortNodeRequest = condition()
        repeat(depth - 1) {
            node = group(children = listOf(node))
        }
        return node as CohortNodeRequest.GroupNode
    }
}
