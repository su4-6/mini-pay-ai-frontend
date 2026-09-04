package com.minipay.mobile.voice

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class VoiceModule {
    @Binds @Singleton abstract fun bindVoiceInput(implementation: AndroidVoiceInputController): VoiceInputController
    @Binds @Singleton abstract fun bindSpeechOutput(implementation: AndroidSpeechOutput): SpeechOutput
    @Binds @Singleton abstract fun bindVoiceSettings(implementation: AndroidVoiceSettings): VoiceSettings
    @Binds @Singleton abstract fun bindTextToSpeechEngineFactory(
        implementation: AndroidTextToSpeechEngineFactory
    ): TextToSpeechEngineFactory
}
