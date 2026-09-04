package com.minipay.mobile.voice

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidVoiceInputControllerTest {
    @Test
    fun realRecognizerStaysActiveUntilExplicitStop() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val speechOutput = RecordingSpeechOutput()
        val controller = AndroidVoiceInputController(instrumentation.targetContext, speechOutput)

        instrumentation.runOnMainSync(controller::start)
        assertTrue(controller.state.value is VoiceInputState.Listening)

        Thread.sleep(1_500)
        assertTrue(
            "系统识别器在松手前提前退出：${controller.state.value}",
            controller.state.value is VoiceInputState.Listening
        )

        instrumentation.runOnMainSync(controller::stop)
        assertTrue(controller.state.value is VoiceInputState.Processing)
        instrumentation.runOnMainSync(controller::cancel)
        assertEquals(VoiceInputState.Idle, controller.state.value)
        assertEquals(listOf(true, false), speechOutput.voiceInputBlocks)
    }

    private class RecordingSpeechOutput : SpeechOutput {
        private val mutableState = MutableStateFlow<SpeechOutputState>(SpeechOutputState.Ready)
        private val mutableErrors = MutableSharedFlow<String>()
        val voiceInputBlocks = mutableListOf<Boolean>()

        override val state = mutableState.asStateFlow()
        override val errors = mutableErrors.asSharedFlow()
        override fun prepare() = Unit
        override fun speakAi(messageId: String, text: String) = Unit
        override fun speakReceipt(eventId: String, text: String) = Unit
        override fun stop(channel: SpeechChannel) = Unit
        override fun stopAll() = Unit

        override fun setBlocked(blocker: SpeechBlocker, blocked: Boolean) {
            if (blocker == SpeechBlocker.VOICE_INPUT) voiceInputBlocks += blocked
        }
    }
}
