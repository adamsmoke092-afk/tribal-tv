package com.example.iptvapp.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the bounded application-level recovery budget and the
 * buffering-warning guard. These encode the two rules that matter:
 *  - recovery is bounded and only fires for the still-selected channel;
 *  - prolonged buffering never marks a channel dead, and a stale buffering
 *    timer (from a switched-away channel) is cancelled.
 */
class RecoveryPolicyTest {

    @Test
    fun `first recovery is allowed for the loaded channel`() {
        val budget = RecoveryBudget(maxAttempts = 1)
        assertTrue(budget.tryConsume("http://a", "http://a"))
        assertEquals(1, budget.attempts)
    }

    @Test
    fun `a second attempt in the same episode is refused`() {
        val budget = RecoveryBudget(maxAttempts = 1)
        assertTrue(budget.tryConsume("http://a", "http://a"))
        assertFalse(budget.tryConsume("http://a", "http://a"))
        assertEquals(1, budget.attempts)
    }

    @Test
    fun `refuses when the player moved to a different channel`() {
        val budget = RecoveryBudget(maxAttempts = 1)
        assertFalse(budget.tryConsume("http://b", "http://a"))
        assertEquals(0, budget.attempts)
    }

    @Test
    fun `refuses when nothing is loaded`() {
        val budget = RecoveryBudget(maxAttempts = 1)
        assertFalse(budget.tryConsume(null, "http://a"))
        assertEquals(0, budget.attempts)
    }

    @Test
    fun `reset after READY starts a new recovery episode`() {
        val budget = RecoveryBudget(maxAttempts = 1)
        assertTrue(budget.tryConsume("http://a", "http://a"))
        assertFalse(budget.tryConsume("http://a", "http://a"))
        budget.reset()
        assertTrue(budget.tryConsume("http://a", "http://a"))
    }

    @Test
    fun `the default budget is bounded to one attempt`() {
        val budget = RecoveryBudget()
        assertTrue(budget.tryConsume("u", "u"))
        assertFalse(budget.tryConsume("u", "u"))
    }

    // ---- buffering warning guard (prolonged buffering must not mark dead) ----

    @Test
    fun `warning is due while still buffering in the same attempt`() {
        assertTrue(bufferingWarningDue(stillBuffering = true, timerAttemptId = 7, currentAttemptId = 7))
    }

    @Test
    fun `warning is cancelled once buffering ends`() {
        assertFalse(bufferingWarningDue(stillBuffering = false, timerAttemptId = 7, currentAttemptId = 7))
    }

    @Test
    fun `warning is cancelled when the attempt changed (channel switch)`() {
        assertFalse(bufferingWarningDue(stillBuffering = true, timerAttemptId = 7, currentAttemptId = 8))
    }
}
