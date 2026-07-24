package no.nav.reops.cohort

/**
 * Persists a validated CohortNodeRequest tree as CohortNodeEntity rows.
 * IDs are DB-generated, so parent nodes must be saved before their children —
 * this walks the tree top-down, saving each node then recursing into children
 * with the freshly-assigned parent id.
 */
class CohortNodeTreePersister(private val save: (CohortNodeEntity) -> CohortNodeEntity) {

    fun persist(root: CohortNodeRequest, cohortId: Long) {
        insert(root, cohortId, parentId = null, ordering = 0, sequenceRole = null)
    }

    private fun insert(
        node: CohortNodeRequest,
        cohortId: Long,
        parentId: Long?,
        ordering: Int,
        sequenceRole: SequenceRole?,
    ) {
        when (node) {
            is CohortNodeRequest.GroupNode -> {
                val saved = save(
                    CohortNodeEntity(
                        cohortId = cohortId,
                        parentId = parentId,
                        nodeType = CohortNodeType.GROUP,
                        ordering = ordering,
                        combinator = node.combinator,
                        negated = node.negated,
                        occurrenceOperator = node.occurrenceOperator,
                        occurrenceCount = node.occurrenceCount,
                        sequenceRole = sequenceRole,
                    ),
                )
                node.children.forEachIndexed { index, child ->
                    insert(child, cohortId, parentId = saved.id, ordering = index, sequenceRole = null)
                }
            }

            is CohortNodeRequest.ConditionNode -> {
                save(
                    CohortNodeEntity(
                        cohortId = cohortId,
                        parentId = parentId,
                        nodeType = CohortNodeType.CONDITION,
                        ordering = ordering,
                        field = node.field,
                        conditionType = node.conditionType,
                        value = node.value,
                        sequenceRole = sequenceRole,
                    ),
                )
            }

            is CohortNodeRequest.CohortRefNode -> {
                save(
                    CohortNodeEntity(
                        cohortId = cohortId,
                        parentId = parentId,
                        nodeType = CohortNodeType.COHORT_REF,
                        ordering = ordering,
                        referencedCohortId = node.referencedCohortId,
                        negated = node.negated,
                        sequenceRole = sequenceRole,
                    ),
                )
            }

            is CohortNodeRequest.SequenceNode -> {
                val saved = save(
                    CohortNodeEntity(
                        cohortId = cohortId,
                        parentId = parentId,
                        nodeType = CohortNodeType.SEQUENCE,
                        ordering = ordering,
                        sequenceRelation = node.relation,
                        sequenceWindowValue = node.windowValue,
                        sequenceWindowUnit = node.windowUnit,
                        sequenceRole = sequenceRole,
                    ),
                )
                insert(node.anchor, cohortId, parentId = saved.id, ordering = 0, sequenceRole = SequenceRole.ANCHOR)
                insert(node.target, cohortId, parentId = saved.id, ordering = 1, sequenceRole = SequenceRole.TARGET)
            }
        }
    }
}
