package no.nav.reops.cohort

class CohortTreeValidationException(val violations: List<String>) :
    RuntimeException(violations.joinToString("; "))

/**
 * Validates a [CohortNodeRequest] tree before it is persisted:
 * - root must be a GroupNode
 * - non-root groups must not be empty
 * - depth must not exceed [maxDepth]
 * - total node count must not exceed [maxNodes]
 * - every ConditionNode must set exactly one of field/paramKey
 * - SequenceNode anchor/target subtrees must not contain CohortRefNode or nested SequenceNode
 * - SequenceNode windowValue must be positive
 * - conditionType BETWEEN is only valid for the created_at field
 * - conditionType EXISTS is only valid on a paramKey condition (never a field condition)
 *
 * All violations are collected and reported together (not fail-fast).
 */
class CohortTreeValidator(
    private val maxDepth: Int = 15,
    private val maxNodes: Int = 100,
) {
    fun validate(root: CohortNodeRequest) {
        val violations = mutableListOf<String>()

        if (root !is CohortNodeRequest.GroupNode) {
            violations.add("root node must be a group node")
        }

        val totalNodes = countNodes(root)
        if (totalNodes > maxNodes) {
            violations.add("tree has $totalNodes nodes, exceeding the max node count of $maxNodes")
        }

        walk(root, depth = 1, isRoot = true, violations)

        if (violations.isNotEmpty()) {
            throw CohortTreeValidationException(violations)
        }
    }

    private fun countNodes(node: CohortNodeRequest): Int = when (node) {
        is CohortNodeRequest.GroupNode -> 1 + node.children.sumOf { countNodes(it) }
        is CohortNodeRequest.ConditionNode -> 1
        is CohortNodeRequest.CohortRefNode -> 1
        is CohortNodeRequest.SequenceNode -> 1 + countNodes(node.anchor) + countNodes(node.target)
    }

    private fun walk(node: CohortNodeRequest, depth: Int, isRoot: Boolean, violations: MutableList<String>) {
        if (depth > maxDepth) {
            violations.add("tree exceeds the max depth of $maxDepth (found a node at depth $depth)")
            return
        }

        when (node) {
            is CohortNodeRequest.GroupNode -> {
                if (!isRoot && node.children.isEmpty()) {
                    violations.add("a group at depth $depth is empty; groups must have at least one child unless they are the root")
                }
                node.children.forEach { walk(it, depth + 1, isRoot = false, violations) }
            }

            is CohortNodeRequest.ConditionNode -> {
                val hasField = !node.field.isNullOrBlank()
                val hasParamKey = !node.paramKey.isNullOrBlank()
                if (hasField == hasParamKey) {
                    violations.add(
                        "a condition at depth $depth must set exactly one of field/paramKey " +
                            "(had field=${node.field}, paramKey=${node.paramKey})",
                    )
                }
                if (node.conditionType == ComparisonOperator.BETWEEN && node.field != "created_at") {
                    violations.add(
                        "a condition at depth $depth uses BETWEEN, which is only valid for the created_at field " +
                            "(had field=${node.field})",
                    )
                }
                if (node.conditionType == ComparisonOperator.EXISTS && !hasParamKey) {
                    violations.add(
                        "a condition at depth $depth uses EXISTS, which is only valid on a paramKey condition " +
                            "(had field=${node.field}, paramKey=${node.paramKey})",
                    )
                }
            }

            is CohortNodeRequest.CohortRefNode -> Unit

            is CohortNodeRequest.SequenceNode -> {
                if (node.windowValue <= 0) {
                    violations.add("sequence window value must be positive, was ${node.windowValue}")
                }
                validateSequenceStep(node.anchor, "anchor", violations)
                validateSequenceStep(node.target, "target", violations)
                walk(node.anchor, depth + 1, isRoot = false, violations)
                walk(node.target, depth + 1, isRoot = false, violations)
            }
        }
    }

    /** Anchor/target must resolve to actual timestamped event rows — no cohort refs, no nested sequences. */
    private fun validateSequenceStep(step: CohortNodeRequest.GroupNode, role: String, violations: MutableList<String>) {
        if (containsForbiddenSequenceContent(step)) {
            violations.add(
                "sequence $role must not contain a cohort reference or a nested sequence " +
                    "(a step must resolve to plain event rows with a timestamp)",
            )
        }
    }

    private fun containsForbiddenSequenceContent(node: CohortNodeRequest): Boolean = when (node) {
        is CohortNodeRequest.CohortRefNode -> true
        is CohortNodeRequest.SequenceNode -> true
        is CohortNodeRequest.GroupNode -> node.children.any { containsForbiddenSequenceContent(it) }
        is CohortNodeRequest.ConditionNode -> false
    }
}
