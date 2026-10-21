package com.ashis.notesphare.recording

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingStateMachineTest {

    @Test
    fun supportsRecordPauseResumeStopAndSaveLifecycle() {
        val machine = RecordingStateMachine()

        assertEquals(RecorderPhase.PREPARING, machine.transition(RecorderEvent.PREPARE))
        assertEquals(RecorderPhase.RECORDING, machine.transition(RecorderEvent.START))
        assertEquals(RecorderPhase.PAUSED, machine.transition(RecorderEvent.PAUSE))
        assertEquals(RecorderPhase.RECORDING, machine.transition(RecorderEvent.RESUME))
        assertEquals(RecorderPhase.STOPPED, machine.transition(RecorderEvent.STOP))
        assertEquals(RecorderPhase.SAVING, machine.transition(RecorderEvent.SAVE))
        machine.markSaved()
        assertEquals(RecorderPhase.SAVED, machine.phase)
    }

    @Test
    fun cancelDiscardsActiveRecordingAndReturnsToIdle() {
        val machine = RecordingStateMachine(RecorderPhase.PAUSED)

        assertEquals(RecorderPhase.IDLE, machine.transition(RecorderEvent.CANCEL))
        assertTrue(machine.canHandle(RecorderEvent.PREPARE))
    }

    @Test
    fun rejectsInvalidTransitions() {
        val machine = RecordingStateMachine()

        assertFalse(machine.canHandle(RecorderEvent.PAUSE))
        assertFalse(machine.canHandle(RecorderEvent.SAVE))
        try {
            machine.transition(RecorderEvent.STOP)
            throw AssertionError("STOP should be rejected while idle")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message!!.contains("STOP"))
        }
    }

    @Test
    fun failureCanBeRetriedFromErrorState() {
        val machine = RecordingStateMachine(RecorderPhase.RECORDING)

        assertEquals(RecorderPhase.ERROR, machine.transition(RecorderEvent.FAIL))
        assertEquals(RecorderPhase.PREPARING, machine.transition(RecorderEvent.PREPARE))
    }

    @Test
    fun cannotCancelWhileSavingOrAfterSave() {
        val saving = RecordingStateMachine(RecorderPhase.SAVING)
        assertFalse(saving.canHandle(RecorderEvent.CANCEL))

        val saved = RecordingStateMachine(RecorderPhase.SAVED)
        assertFalse(saved.canHandle(RecorderEvent.CANCEL))
        assertFalse(saved.canHandle(RecorderEvent.FAIL))
    }
}
