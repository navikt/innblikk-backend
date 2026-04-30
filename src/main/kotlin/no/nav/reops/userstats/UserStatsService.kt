package no.nav.reops.userstats

import org.springframework.stereotype.Service

@Service
class UserStatsService(private val userStatsRepository: UserStatsRepository) {

    companion object {
        private const val ACTIVE_USER_WINDOW_DAYS = 30
    }

    fun getStats(): UserStatsResponse {
        val totalUsers = userStatsRepository.countTotalUsers()
        val activeUsers = userStatsRepository.countActiveUsers(ACTIVE_USER_WINDOW_DAYS)
        val counts = userStatsRepository.countBySettingKeyAndValue()

        val settings = counts
            .groupBy { it.settingKey }
            .mapValues { (_, values) -> values.associate { it.value to it.count } }

        return UserStatsResponse(
            totalUsers = totalUsers,
            activeUsers = activeUsers,
            activeUserWindowDays = ACTIVE_USER_WINDOW_DAYS,
            settings = settings,
        )
    }
}
