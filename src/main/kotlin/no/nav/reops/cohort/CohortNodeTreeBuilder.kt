package no.nav.reops.cohort

/**
 * Reconstructs a CohortNodeDto tree from the flat list of CohortNodeEntity rows
 * belonging to one cohort. Deliberately a pure, standalone function (no JPA
 * self-join) — see CohortNodeEntity's class doc for why.
 */
object CohortNodeTreeBuilder {

    fun build(nodes: List<CohortNodeEntity>): CohortNodeDto {
        val byParent = nodes.groupBy { it.parentId }
        val roots = nodes.filter { it.parentId == null }
        check(roots.size == 1) { "expected exactly one root node, found ${roots.size}" }
        return buildNode(roots.single(), byParent)
    }

    private fun buildNode(entity: CohortNodeEntity, byParent: Map<Long?, List<CohortNodeEntity>>): CohortNodeDto =
        when (entity.nodeType) {
            CohortNodeType.GROUP -> buildGroup(entity, byParent)
            CohortNodeType.CONDITION -> buildCondition(entity)
            CohortNodeType.COHORT_REF -> buildCohortRef(entity)
            CohortNodeType.SEQUENCE -> buildSequence(entity, byParent)
        }

    private fun buildGroup(entity: CohortNodeEntity, byParent: Map<Long?, List<CohortNodeEntity>>): CohortNodeDto.GroupNodeDto {
        val children = (byParent[entity.id] ?: emptyList())
            .sortedBy { it.ordering }
            .map { buildNode(it, byParent) }

        return CohortNodeDto.GroupNodeDto(
            id = requireNotNull(entity.id),
            combinator = entity.combinator ?: LogicalOperator.AND,
            negated = entity.negated,
            occurrenceOperator = entity.occurrenceOperator,
            occurrenceCount = entity.occurrenceCount,
            children = children,
        )
    }

    private fun buildCondition(entity: CohortNodeEntity): CohortNodeDto.ConditionNodeDto = CohortNodeDto.ConditionNodeDto(
        id = requireNotNull(entity.id),
        field = requireNotNull(entity.field),
        conditionType = requireNotNull(entity.conditionType),
        value = requireNotNull(entity.value),
    )

    private fun buildCohortRef(entity: CohortNodeEntity): CohortNodeDto.CohortRefNodeDto = CohortNodeDto.CohortRefNodeDto(
        id = requireNotNull(entity.id),
        referencedCohortId = requireNotNull(entity.referencedCohortId),
        negated = entity.negated,
    )

    private fun buildSequence(entity: CohortNodeEntity, byParent: Map<Long?, List<CohortNodeEntity>>): CohortNodeDto.SequenceNodeDto {
        val children = byParent[entity.id] ?: emptyList()
        val anchorEntity = children.firstOrNull { it.sequenceRole == SequenceRole.ANCHOR }
            ?: error("sequence node ${entity.id} is missing its anchor child")
        val targetEntity = children.firstOrNull { it.sequenceRole == SequenceRole.TARGET }
            ?: error("sequence node ${entity.id} is missing its target child")

        return CohortNodeDto.SequenceNodeDto(
            id = requireNotNull(entity.id),
            anchor = buildGroup(anchorEntity, byParent),
            target = buildGroup(targetEntity, byParent),
            relation = requireNotNull(entity.sequenceRelation),
            windowValue = requireNotNull(entity.sequenceWindowValue),
            windowUnit = requireNotNull(entity.sequenceWindowUnit),
        )
    }
}
