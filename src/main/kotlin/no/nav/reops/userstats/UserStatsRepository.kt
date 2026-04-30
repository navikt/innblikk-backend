package no.nav.reops.userstats

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

@Repository
class UserStatsRepository(private val jdbcClient: JdbcClient) {

    fun countTotalUsers(): Long =
        jdbcClient
            .sql("SELECT COUNT(*) FROM user_settings")
            .query(Long::class.java)
            .single()

    fun countActiveUsers(withinDays: Int): Long =
        jdbcClient
            .sql("SELECT COUNT(*) FROM user_settings WHERE updated_at >= NOW() - CAST(:days || ' days' AS INTERVAL)")
            .param("days", withinDays)
            .query(Long::class.java)
            .single()

    fun countBySettingKeyAndValue(): List<SettingValueCount> =
        jdbcClient
            .sql(
                """
                SELECT setting_key, value, COUNT(*) AS count
                FROM user_setting_values
                GROUP BY setting_key, value
                ORDER BY setting_key, value
                """,
            )
            .query { rs, _ ->
                SettingValueCount(
                    settingKey = rs.getString("setting_key"),
                    value = rs.getString("value"),
                    count = rs.getLong("count"),
                )
            }
            .list()
}

data class SettingValueCount(
    val settingKey: String,
    val value: String,
    val count: Long,
)
