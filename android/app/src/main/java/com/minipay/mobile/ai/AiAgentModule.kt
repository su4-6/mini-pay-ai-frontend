package com.minipay.mobile.ai

import com.minipay.mobile.auth.AuthRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object AiAgentModule {
    @Provides
    @Singleton
    fun provideAiAgentService(
        client: OkHttpClient,
        json: Json,
        auth: AuthRepository
    ): AiAgentService = AiAgentApi(client, json, auth)
}
