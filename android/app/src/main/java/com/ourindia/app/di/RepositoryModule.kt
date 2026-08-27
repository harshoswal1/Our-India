package com.ourindia.app.di

import com.ourindia.app.data.repository.PoliticalSyncRepository
import com.ourindia.app.data.repository.SupabaseSyncRepositoryImpl
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
    abstract fun bindPoliticalSyncRepository(
        impl: SupabaseSyncRepositoryImpl
    ): PoliticalSyncRepository
}
