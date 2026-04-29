package no.nav.reops.canvas.ws

import no.nav.reops.dashboard.DashboardRepository
import no.nav.reops.graph.GraphEntity
import no.nav.reops.graph.GraphRepository
import no.nav.reops.graph.GraphType
import no.nav.reops.graphcategory.GraphCategoryRepository
import no.nav.reops.query.QueryRepository
import no.nav.reops.query.QueryEntity
import no.nav.reops.change.ChangeService
import no.nav.reops.change.ChangeType
import no.nav.reops.change.EntityType
import no.nav.reops.security.AuthenticatedUser
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import javax.sql.DataSource

private const val TIMER_TOKEN = "[canvas-timer]"
private const val TIMER_GRAPH_NAME = "canvas:timer"
private const val TIMER_QUERY_NAME = "canvas-timer"
private const val DOT_VOTING_TOKEN = "[canvas-dot-voting]"
private const val DOT_VOTING_SESSION_GRAPH_NAME = "canvas:dot-voting:session"
private const val DOT_VOTING_SESSION_QUERY_NAME = "canvas-dot-voting-session"
private const val DOT_VOTING_BALLOT_QUERY_NAME = "canvas-dot-voting-ballot"
private const val DOT_VOTING_BALLOT_GRAPH_PREFIX = "canvas:dot-voting:ballot:"

class CanvasVersionConflictException(
    message: String,
    val currentState: Map<String, Any?>,
) : RuntimeException(message)

@Service
class CanvasDataService(
    private val graphCategoryRepository: GraphCategoryRepository,
    private val graphRepository: GraphRepository,
    private val queryRepository: QueryRepository,
    private val dashboardRepository: DashboardRepository,
    private val changeService: ChangeService,
    private val dataSource: DataSource,
) {

    @Transactional(readOnly = true)
    fun getCanvasState(projectId: Long, dashboardId: Long): Map<String, Any?> {
        val allCategories = graphCategoryRepository.findAllByDashboardIdOrderByOrderingAsc(dashboardId)
        val categories = allCategories.map { it.toCategoryMap() }

        val frames = mutableListOf<Map<String, Any?>>()
        val connections = mutableListOf<Map<String, Any?>>()

        for (cat in allCategories) {
            val catId = requireNotNull(cat.id)
            val graphs = graphRepository.findAllByCategoryIdOrderByOrderingAsc(catId)

            for (graph in graphs) {
                if (graph.graphType != GraphType.TEXT) continue
                if (!isCanvasStorageGraph(graph.description)) continue

                val queries = queryRepository.findAllByGraphIdOrderByOrderingAsc(requireNotNull(graph.id))
                val configQuery = queries.find { it.name == "canvas-config" } ?: queries.firstOrNull() ?: continue

                val entry = mapOf(
                    "categoryId" to catId,
                    "graph" to graph.toGraphMap(),
                    "query" to configQuery.toQueryMap(),
                )

                frames.add(entry)
            }
        }

        return mapOf(
            "categories" to categories,
            "entries" to frames,
        )
    }

    @Transactional
    fun saveFrame(msg: Map<String, Any?>, changedBy: AuthenticatedUser? = null): Map<String, Any?> {
        val graphId = (msg["graphId"] as? Number)?.toLong()
        val queryId = (msg["queryId"] as? Number)?.toLong()
        val categoryId = (msg["categoryId"] as? Number)?.toLong()
        val sqlText = msg["sqlText"] as? String
            ?: throw IllegalArgumentException("save requires sqlText")
        val name = msg["name"] as? String ?: "canvas-config"
        val graphName = msg["graphName"] as? String
        val version = (msg["version"] as? Number)?.toLong()

        // Update existing query
        if (graphId != null && queryId != null) {
            val query = queryRepository.findByIdAndGraphId(queryId, graphId)
                ?: throw IllegalArgumentException("Query $queryId not found in graph $graphId")

            if (version != null && query.version != version) {
                throw CanvasVersionConflictException(
                    "Version conflict for query $queryId: expected $version, actual ${query.version}",
                    currentState = query.toQueryMap(),
                )
            }

            query.sqlText = sqlText
            query.name = name
            query.version += 1
            val saved = queryRepository.save(query)
            changeService.createChange(requireNotNull(saved.id), EntityType.QUERY, ChangeType.UPDATED, changedBy)
            return saved.toQueryMap()
        }

        // Create new graph + query
        if (categoryId != null && graphName != null) {
            val category = graphCategoryRepository.findById(categoryId).orElseThrow {
                IllegalArgumentException("Category $categoryId not found")
            }

            val maxOrdering = graphRepository.findMaxOrderingByCategoryId(categoryId)
            val graph = GraphEntity(
                category = category,
                name = graphName,
                description = "[canvas]",
                graphType = GraphType.TEXT,
                width = 100,
                ordering = maxOrdering + 1,
            )
            val savedGraph = graphRepository.save(graph)
            changeService.createChange(requireNotNull(savedGraph.id), EntityType.GRAPH, ChangeType.CREATED, changedBy)

            val query = QueryEntity(
                graph = savedGraph,
                name = name,
                sqlText = sqlText,
                ordering = 0,
            )
            val savedQuery = queryRepository.save(query)
            changeService.createChange(requireNotNull(savedQuery.id), EntityType.QUERY, ChangeType.CREATED, changedBy)

            return mapOf(
                "graphId" to savedGraph.id,
                "queryId" to savedQuery.id,
                "categoryId" to categoryId,
                "version" to savedQuery.version,
            )
        }

        // Create query for existing graph
        if (graphId != null) {
            val graph = graphRepository.findById(graphId).orElseThrow {
                IllegalArgumentException("Graph $graphId not found")
            }
            val maxOrdering = queryRepository.findMaxOrderingByGraphId(graphId)
            val query = QueryEntity(
                graph = graph,
                name = name,
                sqlText = sqlText,
                ordering = maxOrdering + 1,
            )
            val savedQuery = queryRepository.save(query)
            changeService.createChange(requireNotNull(savedQuery.id), EntityType.QUERY, ChangeType.CREATED, changedBy)

            return mapOf(
                "graphId" to graphId,
                "queryId" to savedQuery.id,
                "version" to savedQuery.version,
            )
        }

        throw IllegalArgumentException("save requires either (graphId + queryId), (categoryId + graphName), or graphId")
    }

    @Transactional
    fun deleteFrame(categoryId: Long, graphId: Long, changedBy: AuthenticatedUser? = null) {
        val graph = graphRepository.findByIdAndCategoryId(graphId, categoryId)
            ?: throw IllegalArgumentException("Graph $graphId not found in category $categoryId")
        graphRepository.delete(graph)
        changeService.createChange(graphId, EntityType.GRAPH, ChangeType.DELETED, changedBy)
    }

    // ── Timer ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    fun getTimerState(dashboardId: Long): Map<String, Any?>? {
        val record = findNewestRecordByToken(dashboardId, TIMER_TOKEN, TIMER_GRAPH_NAME, TIMER_QUERY_NAME)
            ?: return null
        return record.queryMap
    }

    @Transactional
    fun saveTimerState(dashboardId: Long, payload: Map<String, Any?>, changedBy: AuthenticatedUser? = null): Map<String, Any?> {
        val sqlText = payload["sqlText"] as? String
            ?: throw IllegalArgumentException("timer:save requires sqlText")

        val record = findNewestRecordByToken(dashboardId, TIMER_TOKEN, TIMER_GRAPH_NAME, TIMER_QUERY_NAME)
        if (record != null) {
            record.query.sqlText = sqlText
            record.query.version = record.query.version + 1
            val saved = queryRepository.save(record.query)
            changeService.createChange(requireNotNull(saved.id), EntityType.QUERY, ChangeType.UPDATED, changedBy)
            return saved.toQueryMap()
        }

        // Create new timer graph + query
        val categoryId = getOrCreatePrimaryCategory(dashboardId, changedBy)
        val category = graphCategoryRepository.findById(categoryId).orElseThrow()
        val maxOrdering = graphRepository.findMaxOrderingByCategoryId(categoryId)
        val graph = graphRepository.save(GraphEntity(
            category = category,
            name = TIMER_GRAPH_NAME,
            description = TIMER_TOKEN,
            graphType = GraphType.TEXT,
            width = 100,
            ordering = maxOrdering + 1,
        ))
        changeService.createChange(requireNotNull(graph.id), EntityType.GRAPH, ChangeType.CREATED, changedBy)

        val query = queryRepository.save(QueryEntity(
            graph = graph,
            name = TIMER_QUERY_NAME,
            sqlText = sqlText,
            ordering = 0,
        ))
        changeService.createChange(requireNotNull(query.id), EntityType.QUERY, ChangeType.CREATED, changedBy)
        return query.toQueryMap()
    }

    @Transactional
    fun clearTimer(dashboardId: Long, changedBy: AuthenticatedUser? = null) {
        deleteGraphsByToken(dashboardId, TIMER_TOKEN, changedBy)
    }

    // ── Dot Voting ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    fun getDotVotingState(dashboardId: Long): Map<String, Any?> {
        val categories = graphCategoryRepository.findAllByDashboardIdOrderByOrderingAsc(dashboardId)
        var sessionRecord: GraphQueryRecord? = null
        val ballotRecords = mutableListOf<Map<String, Any?>>()

        for (cat in categories) {
            val catId = requireNotNull(cat.id)
            val graphs = graphRepository.findAllByCategoryIdOrderByOrderingAsc(catId)
            for (graph in graphs) {
                if (graph.graphType != GraphType.TEXT) continue
                if (!hasDotVotingToken(graph.description)) continue

                val queries = queryRepository.findAllByGraphIdOrderByOrderingAsc(requireNotNull(graph.id))
                if (queries.isEmpty()) continue

                if (graph.name == DOT_VOTING_SESSION_GRAPH_NAME) {
                    val q = queries.find { it.name == DOT_VOTING_SESSION_QUERY_NAME } ?: queries.first()
                    val candidate = GraphQueryRecord(catId, graph, q, q.toQueryMap())
                    if (sessionRecord == null || q.updatedAt.isAfter(sessionRecord.query.updatedAt)) {
                        sessionRecord = candidate
                    }
                } else if (graph.name.startsWith(DOT_VOTING_BALLOT_GRAPH_PREFIX)) {
                    val q = queries.find { it.name == DOT_VOTING_BALLOT_QUERY_NAME } ?: queries.first()
                    ballotRecords.add(q.toQueryMap())
                }
            }
        }

        return mapOf(
            "session" to sessionRecord?.queryMap,
            "ballots" to ballotRecords,
        )
    }

    @Transactional
    fun saveDotVotingSession(dashboardId: Long, payload: Map<String, Any?>, changedBy: AuthenticatedUser? = null): Map<String, Any?> {
        val sqlText = payload["sqlText"] as? String
            ?: throw IllegalArgumentException("dotvoting:save:session requires sqlText")

        val record = findNewestRecordByToken(dashboardId, DOT_VOTING_TOKEN, DOT_VOTING_SESSION_GRAPH_NAME, DOT_VOTING_SESSION_QUERY_NAME)
        if (record != null) {
            record.query.sqlText = sqlText
            record.query.version = record.query.version + 1
            val saved = queryRepository.save(record.query)
            changeService.createChange(requireNotNull(saved.id), EntityType.QUERY, ChangeType.UPDATED, changedBy)
            return saved.toQueryMap()
        }

        val categoryId = getOrCreatePrimaryCategory(dashboardId, changedBy)
        val category = graphCategoryRepository.findById(categoryId).orElseThrow()
        val maxOrdering = graphRepository.findMaxOrderingByCategoryId(categoryId)
        val graph = graphRepository.save(GraphEntity(
            category = category,
            name = DOT_VOTING_SESSION_GRAPH_NAME,
            description = DOT_VOTING_TOKEN,
            graphType = GraphType.TEXT,
            width = 100,
            ordering = maxOrdering + 1,
        ))
        changeService.createChange(requireNotNull(graph.id), EntityType.GRAPH, ChangeType.CREATED, changedBy)

        val query = queryRepository.save(QueryEntity(
            graph = graph,
            name = DOT_VOTING_SESSION_QUERY_NAME,
            sqlText = sqlText,
            ordering = 0,
        ))
        changeService.createChange(requireNotNull(query.id), EntityType.QUERY, ChangeType.CREATED, changedBy)
        return query.toQueryMap()
    }

    @Transactional
    fun saveDotVotingBallot(dashboardId: Long, ownerId: String, payload: Map<String, Any?>, changedBy: AuthenticatedUser? = null): Map<String, Any?> {
        val sqlText = payload["sqlText"] as? String
            ?: throw IllegalArgumentException("dotvoting:save:ballot requires sqlText")
        val graphName = "${DOT_VOTING_BALLOT_GRAPH_PREFIX}${ownerId}".take(200)

        // Find existing ballot by owner
        val categories = graphCategoryRepository.findAllByDashboardIdOrderByOrderingAsc(dashboardId)
        for (cat in categories) {
            val catId = requireNotNull(cat.id)
            val graphs = graphRepository.findAllByCategoryIdOrderByOrderingAsc(catId)
            for (graph in graphs) {
                if (graph.graphType != GraphType.TEXT) continue
                if (!hasDotVotingToken(graph.description)) continue
                if (graph.name != graphName) continue

                val queries = queryRepository.findAllByGraphIdOrderByOrderingAsc(requireNotNull(graph.id))
                val q = queries.find { it.name == DOT_VOTING_BALLOT_QUERY_NAME } ?: queries.firstOrNull()
                if (q != null) {
                    q.sqlText = sqlText
                    q.version = q.version + 1
                    val saved = queryRepository.save(q)
                    changeService.createChange(requireNotNull(saved.id), EntityType.QUERY, ChangeType.UPDATED, changedBy)
                    return saved.toQueryMap()
                }
                // Graph exists but no query — create one
                val newQuery = queryRepository.save(QueryEntity(
                    graph = graph,
                    name = DOT_VOTING_BALLOT_QUERY_NAME,
                    sqlText = sqlText,
                    ordering = 0,
                ))
                changeService.createChange(requireNotNull(newQuery.id), EntityType.QUERY, ChangeType.CREATED, changedBy)
                return newQuery.toQueryMap()
            }
        }

        // No existing ballot graph — create new
        val categoryId = getOrCreatePrimaryCategory(dashboardId, changedBy)
        val category = graphCategoryRepository.findById(categoryId).orElseThrow()
        val maxOrdering = graphRepository.findMaxOrderingByCategoryId(categoryId)
        val graph = graphRepository.save(GraphEntity(
            category = category,
            name = graphName,
            description = DOT_VOTING_TOKEN,
            graphType = GraphType.TEXT,
            width = 100,
            ordering = maxOrdering + 1,
        ))
        changeService.createChange(requireNotNull(graph.id), EntityType.GRAPH, ChangeType.CREATED, changedBy)

        val query = queryRepository.save(QueryEntity(
            graph = graph,
            name = DOT_VOTING_BALLOT_QUERY_NAME,
            sqlText = sqlText,
            ordering = 0,
        ))
        changeService.createChange(requireNotNull(query.id), EntityType.QUERY, ChangeType.CREATED, changedBy)
        return query.toQueryMap()
    }

    @Transactional
    fun clearDotVoting(dashboardId: Long, changedBy: AuthenticatedUser? = null) {
        deleteGraphsByToken(dashboardId, DOT_VOTING_TOKEN, changedBy)
    }

    // ── Shared helpers ──────────────────────────────────────────────────────

    private data class GraphQueryRecord(
        val categoryId: Long,
        val graph: GraphEntity,
        val query: QueryEntity,
        val queryMap: Map<String, Any?>,
    )

    private fun findNewestRecordByToken(
        dashboardId: Long,
        token: String,
        graphName: String,
        queryName: String,
    ): GraphQueryRecord? {
        val categories = graphCategoryRepository.findAllByDashboardIdOrderByOrderingAsc(dashboardId)
        var newest: GraphQueryRecord? = null
        for (cat in categories) {
            val catId = requireNotNull(cat.id)
            val graphs = graphRepository.findAllByCategoryIdOrderByOrderingAsc(catId)
            for (graph in graphs) {
                if (graph.graphType != GraphType.TEXT) continue
                if (!graph.description.orEmpty().lowercase().contains(token)) continue
                if (graph.name != graphName) continue

                val queries = queryRepository.findAllByGraphIdOrderByOrderingAsc(requireNotNull(graph.id))
                val q = queries.find { it.name == queryName } ?: queries.firstOrNull() ?: continue
                if (newest == null || q.updatedAt.isAfter(newest.query.updatedAt)) {
                    newest = GraphQueryRecord(catId, graph, q, q.toQueryMap())
                }
            }
        }
        return newest
    }

    private fun getOrCreatePrimaryCategory(dashboardId: Long, changedBy: AuthenticatedUser? = null): Long {
        val categories = graphCategoryRepository.findAllByDashboardIdOrderByOrderingAsc(dashboardId)
        if (categories.isNotEmpty()) return requireNotNull(categories.first().id)

        val dashboard = dashboardRepository.findById(dashboardId).orElseThrow {
            IllegalArgumentException("Dashboard $dashboardId not found")
        }
        val maxOrdering = graphCategoryRepository.findMaxOrderingByDashboardId(dashboardId)
        val created = graphCategoryRepository.save(
            no.nav.reops.graphcategory.GraphCategoryEntity(
                dashboard = dashboard,
                name = "Fane 1",
                ordering = maxOrdering + 1,
            )
        )
        changeService.createChange(requireNotNull(created.id), EntityType.GRAPH_CATEGORY, ChangeType.CREATED, changedBy)
        return requireNotNull(created.id)
    }

    private fun deleteGraphsByToken(dashboardId: Long, token: String, changedBy: AuthenticatedUser? = null) {
        val categories = graphCategoryRepository.findAllByDashboardIdOrderByOrderingAsc(dashboardId)
        for (cat in categories) {
            val catId = requireNotNull(cat.id)
            val graphs = graphRepository.findAllByCategoryIdOrderByOrderingAsc(catId)
            for (graph in graphs) {
                if (graph.graphType != GraphType.TEXT) continue
                if (!graph.description.orEmpty().lowercase().contains(token)) continue
                graphRepository.delete(graph)
                changeService.createChange(requireNotNull(graph.id), EntityType.GRAPH, ChangeType.DELETED, changedBy)
            }
        }
    }

    private fun hasDotVotingToken(description: String?): Boolean =
        description.orEmpty().lowercase().contains(DOT_VOTING_TOKEN)

    // ── Locks ─────────────────────────────────────────────────────────────

    data class LockResult(val acquired: Boolean, val lock: LockEntry?)
    data class LockEntry(val ownerId: String, val ownerLabel: String, val expiresAt: Instant)

    fun acquireLock(dashboardId: Long, frameGraphId: Int, ownerId: String, ownerLabel: String, ttlMs: Long): LockResult {
        dataSource.connection.use { conn ->
            // Try to insert; on conflict check if same owner or expired
            val sql = """
                INSERT INTO canvas_locks (frame_graph_id, dashboard_id, owner_id, owner_label, expires_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (frame_graph_id, dashboard_id) DO UPDATE
                SET owner_id = EXCLUDED.owner_id,
                    owner_label = EXCLUDED.owner_label,
                    expires_at = EXCLUDED.expires_at
                WHERE canvas_locks.owner_id = EXCLUDED.owner_id
                   OR canvas_locks.expires_at < NOW()
            """.trimIndent()
            val expiresAt = Instant.now().plusMillis(ttlMs)
            conn.prepareStatement(sql).use { stmt ->
                stmt.setInt(1, frameGraphId)
                stmt.setLong(2, dashboardId)
                stmt.setString(3, ownerId)
                stmt.setString(4, ownerLabel)
                stmt.setObject(5, java.time.OffsetDateTime.ofInstant(expiresAt, java.time.ZoneOffset.UTC))
                val rows = stmt.executeUpdate()
                if (rows > 0) {
                    return LockResult(true, LockEntry(ownerId, ownerLabel, expiresAt))
                }
            }

            // Lock held by another user — return their lock info
            val selectSql = "SELECT owner_id, owner_label, expires_at FROM canvas_locks WHERE frame_graph_id = ? AND dashboard_id = ?"
            conn.prepareStatement(selectSql).use { stmt ->
                stmt.setInt(1, frameGraphId)
                stmt.setLong(2, dashboardId)
                val rs = stmt.executeQuery()
                if (rs.next()) {
                    return LockResult(false, LockEntry(
                        ownerId = rs.getString("owner_id"),
                        ownerLabel = rs.getString("owner_label"),
                        expiresAt = rs.getTimestamp("expires_at").toInstant(),
                    ))
                }
            }
            return LockResult(false, null)
        }
    }

    fun releaseLock(dashboardId: Long, frameGraphId: Int, ownerId: String): Boolean {
        dataSource.connection.use { conn ->
            val sql = "DELETE FROM canvas_locks WHERE frame_graph_id = ? AND dashboard_id = ? AND owner_id = ?"
            conn.prepareStatement(sql).use { stmt ->
                stmt.setInt(1, frameGraphId)
                stmt.setLong(2, dashboardId)
                stmt.setString(3, ownerId)
                return stmt.executeUpdate() > 0
            }
        }
    }

    fun renewLock(dashboardId: Long, frameGraphId: Int, ownerId: String, ttlMs: Long): Boolean {
        dataSource.connection.use { conn ->
            val expiresAt = Instant.now().plusMillis(ttlMs)
            val sql = "UPDATE canvas_locks SET expires_at = ? WHERE frame_graph_id = ? AND dashboard_id = ? AND owner_id = ?"
            conn.prepareStatement(sql).use { stmt ->
                stmt.setObject(1, java.time.OffsetDateTime.ofInstant(expiresAt, java.time.ZoneOffset.UTC))
                stmt.setInt(2, frameGraphId)
                stmt.setLong(3, dashboardId)
                stmt.setString(4, ownerId)
                return stmt.executeUpdate() > 0
            }
        }
    }

    fun getLocksForDashboard(dashboardId: Long): Map<Int, LockEntry> {
        dataSource.connection.use { conn ->
            val sql = "SELECT frame_graph_id, owner_id, owner_label, expires_at FROM canvas_locks WHERE dashboard_id = ? AND expires_at > NOW()"
            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, dashboardId)
                val rs = stmt.executeQuery()
                val result = mutableMapOf<Int, LockEntry>()
                while (rs.next()) {
                    result[rs.getInt("frame_graph_id")] = LockEntry(
                        ownerId = rs.getString("owner_id"),
                        ownerLabel = rs.getString("owner_label"),
                        expiresAt = rs.getTimestamp("expires_at").toInstant(),
                    )
                }
                return result
            }
        }
    }

    private fun isCanvasStorageGraph(description: String?): Boolean {
        if (description == null) return false
        val lower = description.lowercase()
        return lower.contains("[canvas]") && !lower.contains("[canvas-lock]") && !lower.contains("[canvas-presence]")
    }

    private fun GraphEntity.toGraphMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "name" to name,
        "description" to description,
        "graphType" to graphType?.name,
        "width" to width,
        "ordering" to ordering,
    )

    private fun QueryEntity.toQueryMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "graphId" to graph.id,
        "name" to name,
        "sqlText" to sqlText,
        "ordering" to ordering,
        "version" to version,
    )

    private fun no.nav.reops.graphcategory.GraphCategoryEntity.toCategoryMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "name" to name,
        "ordering" to ordering,
    )
}
