package com.mindease

import android.app.Application
import com.mindease.data.Repository
import com.mindease.data.SleepDetectionService
import com.mindease.data.SleepPatternRepository
import com.mindease.data.UserPreferences

class MindEaseApp : Application() {

    lateinit var repository: Repository
    lateinit var userPreferences: UserPreferences
    lateinit var sleepPatternRepository: SleepPatternRepository

    override fun onCreate() {
        super.onCreate()
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Kolkata"))
        userPreferences = UserPreferences(this)
        repository = Repository(userPreferences)
        sleepPatternRepository = SleepPatternRepository(this)

    }
}
