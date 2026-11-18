package com.example.workflow.workprobe
import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import javax.inject.Named
import java.util.UUID

@Module @InstallIn(SingletonComponent::class)
object ProbeModule {
 @Provides @Named("prefix") fun prefix(): String = "hilt-injected"
}
@Singleton
class ProbeRepository @Inject constructor(@Named("prefix") val prefix: String) {
 val token=UUID.randomUUID().toString()
 private val state=OwnedWorkState()
 fun own(id:UUID) = state.own(id)
 fun entered(id:UUID) = state.entered(id)
 fun count(id:UUID) = state.count(id)
 fun markStopped(id:UUID) = state.markStopped(id)
 fun isStopped(id:UUID) = state.isStopped(id)
 fun forget(ids:Collection<UUID>) = state.forget(ids)
}
@HiltAndroidApp
class ProbeApplication: Application(), Configuration.Provider {
 @Inject lateinit var factory: HiltWorkerFactory
 @Inject lateinit var repository: ProbeRepository
 override val workManagerConfiguration: Configuration
  get()=Configuration.Builder().setWorkerFactory(factory).build()
}
