package com.ourindia.app.di

import com.ourindia.app.BuildConfig
import com.ourindia.app.data.civic.CivicApi
import com.ourindia.app.data.civic.CivicSession
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CivicModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        encodeDefaults = true
    }

    @Provides
    @Singleton
    fun provideCivicApi(json: Json, session: CivicSession): CivicApi {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val token = session.token
                val req = chain.request().newBuilder()
                if (token != null) req.header("Authorization", "Bearer $token")
                chain.proceed(req.build())
            }
            // BASIC, not BODY: bodies carry passwords, tokens and base64 photos.
            .apply { if (BuildConfig.DEBUG) addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC)) }
            // The free-tier host sleeps; the first request after idle takes ~20–50 s.
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.CIVIC_API_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(CivicApi::class.java)
    }
}
