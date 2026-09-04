package com.minipay.mobile.voice

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidTextToSpeechEngineTest {
    @Test
    fun chineseEngineInitializesAndCompletesUtterance() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val initialized = CountDownLatch(1)
        val completed = CountDownLatch(1)
        var failure: String? = null
        lateinit var engine: TextToSpeechEngine

        instrumentation.runOnMainSync {
            engine = AndroidTextToSpeechEngineFactory(instrumentation.targetContext).create(
                object : TextToSpeechEngine.Listener {
                    override fun onReady() = initialized.countDown()

                    override fun onUnavailable(message: String) {
                        failure = message
                        initialized.countDown()
                    }

                    override fun onStarted(utteranceId: String) = Unit

                    override fun onFinished(utteranceId: String) {
                        if (utteranceId == expectedUtteranceId) {
                            completed.countDown()
                        }
                    }

                    override fun onError(utteranceId: String) {
                        failure = "系统语音引擎播放失败"
                        completed.countDown()
                    }
                }
            )
        }

        assertTrue("系统语音引擎初始化超时", initialized.await(15, TimeUnit.SECONDS))
        assertNull(failure, failure)
        instrumentation.runOnMainSync {
            assertTrue(engine.speak(SpeechChannel.AI, "MiniPay 中文语音测试", expectedUtteranceId))
        }
        assertTrue("系统语音引擎播放超时", completed.await(20, TimeUnit.SECONDS))
        assertNull(failure, failure)
        instrumentation.runOnMainSync(engine::stop)
    }

    private val expectedUtteranceId = "minipay-device-tts-check"
}
