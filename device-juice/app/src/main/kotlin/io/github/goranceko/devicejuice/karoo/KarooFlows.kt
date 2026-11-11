package io.github.goranceko.devicejuice.karoo

import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.KarooEvent
import io.hammerhead.karooext.models.OnStreamState
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Thrown into a flow when Karoo reports an error for a consumer (for example an unknown data type). */
class KarooConsumerError(message: String) : RuntimeException(message)

/** Stream a data type. Errors from Karoo fail the flow with [KarooConsumerError]. Same shape as the karoo-ext sample. */
fun KarooSystemService.streamDataFlow(dataTypeId: String): Flow<StreamState> = callbackFlow {
    val id = addConsumer(
        OnStreamState.StartStreaming(dataTypeId),
        onError = { close(KarooConsumerError(it)) },
        onComplete = { close() },
    ) { event: OnStreamState -> trySend(event.state) }
    awaitClose { removeConsumer(id) }
}

inline fun <reified T : KarooEvent> KarooSystemService.consumerFlow(): Flow<T> = callbackFlow {
    val id = addConsumer<T>(
        onError = { close(KarooConsumerError(it)) },
        onComplete = { close() },
    ) { trySend(it) }
    awaitClose { removeConsumer(id) }
}
