package com.mtos.phoenix.ide.hybrid.network

import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

// Moshi data mappings for Remote Compiler Engine
data class CompileFile(
    @Json(name = "path") val path: String,
    @Json(name = "content") val content: String
)

data class CompileRequest(
    @Json(name = "projectName") val projectName: String,
    @Json(name = "templateType") val templateType: String,
    @Json(name = "files") val files: List<CompileFile>,
    @Json(name = "targetPlatform") val targetPlatform: String
)

data class CompileResponse(
    @Json(name = "success") val success: Boolean,
    @Json(name = "logs") val logs: String,
    @Json(name = "error") val error: String? = null,
    @Json(name = "downloadUrl") val downloadUrl: String? = null
)

interface CompilerApiService {
    @POST
    suspend fun compileProject(
        @retrofit2.http.Url url: String,
        @Body request: CompileRequest
    ): CompileResponse
}

object CompilerApiClient {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(90, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://localhost/") // Dynamic baseUrl overridden at request-time via @Url
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val service: CompilerApiService = retrofit.create(CompilerApiService::class.java)
}
