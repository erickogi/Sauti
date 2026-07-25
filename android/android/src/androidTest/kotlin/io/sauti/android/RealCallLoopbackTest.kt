package io.sauti.android

import android.Manifest
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import io.sauti.android.rtc.WebRtcFactory
import io.sauti.engine.CallPhase
import io.sauti.engine.CallSession
import io.sauti.engine.ConnectionState
import io.sauti.engine.EngineConfig
import io.sauti.engine.JoinConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.Executors

@RunWith(AndroidJUnit4::class)
class RealCallLoopbackTest {

    @get:Rule
    val audioPermission: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)

    @Test
    fun twoRealPeersNegotiateAudio() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val executor = Executors.newSingleThreadExecutor()
        val dispatcher = executor.asCoroutineDispatcher()
        val scope = CoroutineScope(dispatcher + SupervisorJob())

        val bridge = LoopbackSignaling("room-loopback")
        val webRtcA = WebRtcFactory(context)
        val webRtcB = WebRtcFactory(context)
        val factoryA = RecordingRtcFactory(webRtcA)
        val factoryB = RecordingRtcFactory(webRtcB)

        val politeId = "peer-a"
        val impoliteId = "peer-z"

        val sessionA = CallSession(factoryA, bridge, webRtcA, EngineConfig(statsIntervalMs = 500), scope)
        val sessionB = CallSession(factoryB, bridge, webRtcB, EngineConfig(statsIntervalMs = 500), scope)

        try {
            runBlocking {
                withTimeout(90_000) {
                    scope.launch { sessionA.join(JoinConfig("loopback://a", politeId)) }
                    sessionA.state.first { it.phase == CallPhase.CONNECTED }

                    scope.launch { sessionB.join(JoinConfig("loopback://b", impoliteId)) }
                    sessionB.state.first { it.phase == CallPhase.CONNECTED }

                    sessionA.state.first { state ->
                        state.participants.any {
                            it.participantId == impoliteId && it.connectionState == ConnectionState.CONNECTED
                        }
                    }
                    sessionB.state.first { state ->
                        state.participants.any {
                            it.participantId == politeId && it.connectionState == ConnectionState.CONNECTED
                        }
                    }

                    factoryA.remoteAudio.await()
                    factoryB.remoteAudio.await()
                }
            }

            val aParticipants = sessionA.state.value.participants.map { it.participantId }.toSet()
            val bParticipants = sessionB.state.value.participants.map { it.participantId }.toSet()
            assertEquals(setOf(politeId, impoliteId), aParticipants)
            assertEquals(setOf(politeId, impoliteId), bParticipants)

            val politeType = factoryA.connections.first().lastLocalDescriptionType
            val impoliteType = factoryB.connections.first().lastLocalDescriptionType
            assertEquals(listOf("answer", "offer"), listOf(politeType, impoliteType).sortedBy { it ?: "" })
            assertEquals("offer", impoliteType)
            assertEquals("answer", politeType)

            runBlocking {
                withTimeout(30_000) {
                    withContext(dispatcher) { sessionA.setMuted(true) }
                    sessionB.state.first { state ->
                        state.participants.any { it.participantId == politeId && it.muted }
                    }
                }
            }
            assertTrue(
                sessionB.state.value.participants.first { it.participantId == politeId }.muted
            )
        } finally {
            sessionA.leave()
            sessionB.leave()
            webRtcA.dispose()
            webRtcB.dispose()
            scope.cancel()
            executor.shutdownNow()
        }
    }
}
