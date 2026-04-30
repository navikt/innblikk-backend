package no.nav.reops.canvas.ws

import org.postgresql.PGConnection
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties
import org.springframework.stereotype.Component
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import java.sql.Connection
import java.sql.DriverManager
import java.util.concurrent.atomic.AtomicBoolean
import javax.sql.DataSource

private const val CHANNEL = "canvas_ws"
private const val POLL_TIMEOUT_MS = 500

@Component
@EnableConfigurationProperties(DataSourceProperties::class)
class CanvasPgNotifyBridge(
    private val dataSourceProperties: DataSourceProperties,
    private val dataSource: DataSource,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val running = AtomicBoolean(false)

    @Volatile
    private var listener: NotificationListener? = null

    @Volatile
    private var listenConnection: Connection? = null

    @Volatile
    private var listenThread: Thread? = null

    fun interface NotificationListener {
        fun onNotification(payload: String)
    }

    fun setListener(listener: NotificationListener) {
        this.listener = listener
    }

    fun notify(payload: String) {
        try {
            dataSource.connection.use { c ->
                c.createStatement().use { stmt ->
                    stmt.execute("NOTIFY $CHANNEL, '${payload.replace("'", "''")}'")
                }
            }
        } catch (e: Exception) {
            log.warn("Failed to send PG NOTIFY: {}", e.message)
        }
    }

    @PostConstruct
    fun start() {
        running.set(true)
        listenThread = Thread.ofVirtual().name("pg-listen-canvas").start {
            while (running.get()) {
                try {
                    listenLoop()
                } catch (e: Exception) {
                    if (running.get()) {
                        log.warn("PG LISTEN connection lost, reconnecting in 2s: {}", e.message)
                        try {
                            Thread.sleep(2000)
                        } catch (_: InterruptedException) {
                            break
                        }
                    }
                }
            }
        }
    }

    private fun listenLoop() {
        val conn = DriverManager.getConnection(
            dataSourceProperties.url,
            dataSourceProperties.username,
            dataSourceProperties.password,
        )
        listenConnection = conn
        conn.use { c ->
            c.createStatement().use { stmt ->
                stmt.execute("LISTEN $CHANNEL")
            }
            log.info("PG LISTEN started on channel '{}'", CHANNEL)

            val pgConn = c.unwrap(PGConnection::class.java)
            while (running.get()) {
                val notifications = pgConn.getNotifications(POLL_TIMEOUT_MS)
                if (notifications != null) {
                    for (n in notifications) {
                        try {
                            listener?.onNotification(n.parameter)
                        } catch (e: Exception) {
                            log.warn("Error handling PG notification: {}", e.message)
                        }
                    }
                }
            }
        }
    }

    @PreDestroy
    fun stop() {
        running.set(false)
        try {
            listenConnection?.close()
        } catch (_: Exception) {
        }
        listenThread?.interrupt()
        log.info("PG LISTEN stopped")
    }
}
