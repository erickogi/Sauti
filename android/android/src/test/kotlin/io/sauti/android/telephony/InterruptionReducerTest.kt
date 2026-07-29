package io.sauti.android.telephony

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InterruptionReducerTest {

    private val mutePolicy = InterruptionPolicy(InterruptionMode.Mute)
    private val holdPolicy = InterruptionPolicy(InterruptionMode.Hold)

    @Test
    fun defaultPolicyIsMute() {
        assertEquals(InterruptionMode.Mute, InterruptionPolicy().mode)
    }

    @Test
    fun muteModeInterruptMutesAndLeavesHoldUntouched() {
        val effect = InterruptionReducer.reduce(true, UserAudioIntent(false, false), mutePolicy)
        assertEquals(true, effect.muted)
        assertNull(effect.onHold)
    }

    @Test
    fun muteModeRestoreReturnsUserMuteAndLeavesHoldUntouched() {
        val effect = InterruptionReducer.reduce(false, UserAudioIntent(true, false), mutePolicy)
        assertEquals(true, effect.muted)
        assertNull(effect.onHold)
    }

    @Test
    fun muteModeRestoreUnmutedWhenUserNotMuted() {
        val effect = InterruptionReducer.reduce(false, UserAudioIntent(false, false), mutePolicy)
        assertEquals(false, effect.muted)
        assertNull(effect.onHold)
    }

    @Test
    fun muteModeNeverTouchesHoldEvenWhenIntentHeld() {
        assertNull(InterruptionReducer.reduce(true, UserAudioIntent(false, true), mutePolicy).onHold)
        assertNull(InterruptionReducer.reduce(false, UserAudioIntent(false, true), mutePolicy).onHold)
    }

    @Test
    fun holdModeInterruptMutesAndHolds() {
        val effect = InterruptionReducer.reduce(true, UserAudioIntent(false, false), holdPolicy)
        assertEquals(true, effect.muted)
        assertEquals(true, effect.onHold)
    }

    @Test
    fun holdModeRestoreReturnsUserMuteAndUserHold() {
        val effect = InterruptionReducer.reduce(false, UserAudioIntent(true, true), holdPolicy)
        assertEquals(true, effect.muted)
        assertEquals(true, effect.onHold)
    }

    @Test
    fun holdModeRestoreReleasesHoldWhenUserNotHeld() {
        val effect = InterruptionReducer.reduce(false, UserAudioIntent(false, false), holdPolicy)
        assertEquals(false, effect.muted)
        assertEquals(false, effect.onHold)
    }

    @Test
    fun overlappingSourcesCollapseToSingleTrueThenFalseAndRestoreIntent() {
        val saved = UserAudioIntent(true, false)
        val raised = InterruptionReducer.reduce(true, saved, mutePolicy)
        assertEquals(true, raised.muted)
        assertNull(raised.onHold)
        val cleared = InterruptionReducer.reduce(false, saved, mutePolicy)
        assertEquals(true, cleared.muted)
        assertNull(cleared.onHold)
    }

    @Test
    fun overlappingSourcesUnderHoldModeRestoreBothDimensions() {
        val saved = UserAudioIntent(false, true)
        val raised = InterruptionReducer.reduce(true, saved, holdPolicy)
        assertEquals(true, raised.muted)
        assertEquals(true, raised.onHold)
        val cleared = InterruptionReducer.reduce(false, saved, holdPolicy)
        assertEquals(false, cleared.muted)
        assertEquals(true, cleared.onHold)
    }
}
