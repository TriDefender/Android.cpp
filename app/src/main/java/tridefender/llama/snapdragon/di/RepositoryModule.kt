package tridefender.llama.snapdragon.di

import tridefender.llama.snapdragon.repository.ConfigRepository
import tridefender.llama.snapdragon.repository.PresetRepository
import tridefender.llama.snapdragon.repository.impl.ConfigRepositoryImpl
import tridefender.llama.snapdragon.repository.impl.PresetRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    
    @Binds
    @Singleton
    abstract fun bindConfigRepository(impl: ConfigRepositoryImpl): ConfigRepository
    
    @Binds
    @Singleton
    abstract fun bindPresetRepository(impl: PresetRepositoryImpl): PresetRepository
}