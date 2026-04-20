package no.nav.reops.usersettings

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface UserSettingValueRepository : JpaRepository<UserSettingValueEntity, Long> {

    @Modifying
    @Query(
        value = """
            INSERT INTO user_setting_values (nav_ident, setting_key, value, analytics_id, created_at, updated_at)
            VALUES (:navIdent, :key, :value, :analyticsId, NOW(), NOW())
            ON CONFLICT (nav_ident, analytics_id, setting_key) DO UPDATE
                SET value      = EXCLUDED.value,
                    updated_at = NOW()
        """,
        nativeQuery = true,
    )
    fun upsertValue(
        navIdent: String,
        key: String,
        value: String,
        analyticsId: String?,
    )
}
