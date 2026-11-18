package com.example.workflow.notes
import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import javax.inject.Inject
import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
@HiltAndroidApp class NotesApplication:Application(),Configuration.Provider {
 @Inject lateinit var workerFactory:HiltWorkerFactory
 override val workManagerConfiguration:Configuration get()=Configuration.Builder().setWorkerFactory(workerFactory).build()
}
@Module @InstallIn(SingletonComponent::class)
object NotesModule {
 @Provides @Singleton fun database(@ApplicationContext context:Context):NotesDatabase = Room.databaseBuilder(context,NotesDatabase::class.java,"notes.db").addMigrations(NOTES_MIGRATION_1_2,NOTES_MIGRATION_2_3).build()
}
