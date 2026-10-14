package com.ourindia.app.data.civic

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface CivicApi {
    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): AuthResponse

    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): AuthResponse

    @GET("complaints")
    suspend fun feed(
        @Query("filter") filter: String? = null,
        @Query("category") category: String? = null,
        @Query("authority_id") authorityId: String? = null,
        @Query("latitude") latitude: Double? = null,
        @Query("longitude") longitude: Double? = null,
        @Query("limit") limit: Int = 30,
        @Query("offset") offset: Int = 0
    ): FeedResponse

    @GET("complaints/{id}")
    suspend fun detail(@Path("id") id: String): ComplaintDetailResponse

    @GET("complaints/check-duplicate")
    suspend fun checkDuplicate(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("category") category: String,
        @Query("subtype") subtype: String?,
        @Query("radius") radius: Double = 100.0
    ): CheckDuplicateResponse

    @POST("complaints")
    suspend fun create(@Body body: CreateComplaintRequest): CreateComplaintResponse

    // Body is required by the server's JSON parser even though the token identifies the user.
    @POST("complaints/{id}/support")
    suspend fun toggleSupport(@Path("id") id: String, @Body body: Map<String, String> = emptyMap()): SupportResponse

    @POST("complaints/{id}/claim-resolution")
    suspend fun claimResolution(@Path("id") id: String, @Body body: ClaimResolutionRequest)

    @POST("complaints/{id}/retry-email")
    suspend fun retryDelivery(@Path("id") id: String, @Body body: RetryRequest = RetryRequest())

    @GET("rankings")
    suspend fun rankings(): RankingsResponse

    @GET("authorities")
    suspend fun authorities(): AuthoritiesResponse

    @GET("users/{id}/profile")
    suspend fun profile(@Path("id") userId: String): ProfileResponse
}
