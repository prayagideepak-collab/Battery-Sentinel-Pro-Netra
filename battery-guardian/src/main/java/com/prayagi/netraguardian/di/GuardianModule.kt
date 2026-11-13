package com.prayagi.netraguardian.di

import android.content.Context
import androidx.room.Room
import com.prayagi.netraguardian.data.BatteryDao
import com.prayagi.netraguardian.data.GuardianDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object GuardianModule {
    @Provides @Singleton
    fun provideDatabase(@ApplicationContext context: Context): GuardianDatabase =
        Room.databaseBuilder(context, GuardianDatabase::class.java, "netra_guardian.db")
            .fallbackToDestructiveMigration().build()
    @Provides fun provideBatteryDao(database: GuardianDatabase): BatteryDao = database.batteryDao()
}
