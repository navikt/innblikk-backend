package no.nav.reops.usersettings

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface UserSettingsRepository : JpaRepository<UserSettingsEntity, String> {

    @Modifying
    @Query(
        value = """
            INSERT INTO user_settings (nav_ident, created_at, updated_at)
            VALUES (:navIdent, NOW(), NOW())
            ON CONFLICT (nav_ident) DO UPDATE SET updated_at = NOW()
        """,
        nativeQuery = true,
    )
    fun upsertUser(navIdent: String)
}
