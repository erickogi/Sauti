package io.sauti.rx2

import io.reactivex.Completable
import io.reactivex.Observable
import io.sauti.engine.CallEvent
import io.sauti.engine.CallSession
import io.sauti.engine.CallState
import io.sauti.engine.JoinConfig
import kotlinx.coroutines.rx2.asObservable
import kotlinx.coroutines.rx2.rxCompletable
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

class RxCallSession(
    private val session: CallSession,
    private val context: CoroutineContext = EmptyCoroutineContext
) {
    fun state(): Observable<CallState> = session.state.asObservable(context)

    fun events(): Observable<CallEvent> = session.eventFlow.asObservable(context)

    fun join(config: JoinConfig): Completable = rxCompletable(context) {
        session.join(config)
    }

    fun setMuted(muted: Boolean): Completable = Completable.fromAction {
        session.setMuted(muted)
    }

    fun setHold(onHold: Boolean): Completable = Completable.fromAction {
        session.setHold(onHold)
    }

    fun leave(): Completable = Completable.fromAction {
        session.leave()
    }
}
