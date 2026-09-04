package com.minipay.mobile.finance

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

fun interface PaymentStatusReader {
    suspend fun read(reference: PaymentResultReference): PaymentResultSnapshot
}

sealed interface PaymentMonitorEvent {
    data class Updated(val snapshot: PaymentResultSnapshot) : PaymentMonitorEvent
    data object RefreshFailed : PaymentMonitorEvent
    data object TimedOut : PaymentMonitorEvent
}

@Singleton
class PaymentStatusMonitor @Inject constructor(
    private val reader: PaymentStatusReader
) {
    fun observe(
        initial: PaymentResultSnapshot,
        presentation: PaymentResultPresentation = PaymentResultPresentation()
    ): Flow<PaymentMonitorEvent> = flow {
        var snapshot = initial.withPresentation(presentation)
        emit(PaymentMonitorEvent.Updated(snapshot))
        if (snapshot.status.terminal) return@flow

        var elapsedMillis = 0L
        var nextDelayMillis = 1_000L
        while (!snapshot.status.terminal && elapsedMillis < TIMEOUT_MILLIS) {
            val waitMillis = min(nextDelayMillis, TIMEOUT_MILLIS - elapsedMillis)
            delay(waitMillis)
            elapsedMillis += waitMillis
            try {
                snapshot = reader.read(snapshot.reference).withPresentation(presentation)
                emit(PaymentMonitorEvent.Updated(snapshot))
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                emit(PaymentMonitorEvent.RefreshFailed)
            }
            nextDelayMillis = when (nextDelayMillis) {
                1_000L -> 2_000L
                2_000L -> 3_000L
                else -> 5_000L
            }
        }
        if (!snapshot.status.terminal) emit(PaymentMonitorEvent.TimedOut)
    }

    suspend fun refresh(
        reference: PaymentResultReference,
        presentation: PaymentResultPresentation = PaymentResultPresentation()
    ): PaymentResultSnapshot = reader.read(reference).withPresentation(presentation)

    companion object {
        const val TIMEOUT_MILLIS = 30_000L
    }
}

@Module
@InstallIn(SingletonComponent::class)
object PaymentStatusModule {
    @Provides
    @Singleton
    fun providePaymentStatusReader(repository: FinanceRepository): PaymentStatusReader =
        PaymentStatusReader { reference ->
            when (reference.operation) {
                PaymentOperation.TRANSFER -> repository.transferOrder(reference.orderId).toPaymentResultSnapshot()
                PaymentOperation.RECHARGE -> repository.rechargeOrder(reference.orderId).toPaymentResultSnapshot()
                PaymentOperation.WITHDRAWAL -> repository.withdrawalOrder(reference.orderId).toPaymentResultSnapshot()
                PaymentOperation.PAYMENT -> repository.paymentOrder(reference.orderId).toPaymentResultSnapshot()
            }
        }
}
