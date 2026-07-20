package com.whatsappdesktopbot.local.domain.logic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import com.whatsappdesktopbot.local.domain.model.BotClient

class ArabicNormalizerTest {
    @Test
    fun removesDiacriticsAndUnifiesLetters() {
        assertEquals("احمد", ArabicNormalizer.normalize("أَحْمَد"))
        assertEquals("علي", ArabicNormalizer.normalize("على"))
    }

    @Test
    fun convertsArabicIndicDigits() {
        assertEquals("123", ArabicNormalizer.normalize("١٢٣"))
    }
}

class ClientMatcherTest {
    private val matcher = ClientMatcher(normalizeArabicEnabled = { true }, defaultEmoji = { "✅" })

    @Test
    fun findsClientNameInText() {
        val clients = listOf(BotClient(name = "محمد", emoji = "👍"))
        val result = matcher.match("رسالة من محمد اليوم", clients)
        assertEquals("محمد", result?.match)
        assertEquals("👍", result?.emoji)
    }

    @Test
    fun returnsNullWhenNoMatch() {
        val clients = listOf(BotClient(name = "Ali", emoji = "✅"))
        assertNull(matcher.match("nothing here", clients))
    }
}

class ProcessedMessageTrackerTest {
    @Test
    fun preventsDuplicates() {
        val tracker = ProcessedMessageTracker(maxSize = 10)
        tracker.markProcessed("a")
        assertEquals(true, tracker.isProcessed("a"))
        tracker.markProcessed("a")
        assertEquals(1, tracker.size())
    }
}

class RateLimiterTest {
    @Test
    fun blocksWhenLimitReached() {
        val limiter = RateLimiter()
        val now = 1_000L
        limiter.record(now, rpmLimit = 2)
        limiter.record(now + 1, rpmLimit = 2)
        assertEquals(false, limiter.canProceed(now + 2, rpmLimit = 2))
        assertEquals(true, limiter.waitMillis(now + 2, rpmLimit = 2) > 0)
    }
}

class ReconnectBackoffTest {
    @Test
    fun increasesDelayUntilCap() {
        val backoff = ReconnectBackoff()
        assertEquals(5, backoff.nextDelaySeconds())
        assertEquals(10, backoff.nextDelaySeconds())
        assertEquals(20, backoff.nextDelaySeconds())
        assertEquals(40, backoff.nextDelaySeconds())
        assertEquals(60, backoff.nextDelaySeconds())
        assertEquals(60, backoff.nextDelaySeconds())
    }

    @Test
    fun resetRestartsSequence() {
        val backoff = ReconnectBackoff()
        backoff.nextDelaySeconds()
        backoff.reset()
        assertEquals(5, backoff.nextDelaySeconds())
    }
}

class BulkJobRunnerTest {
    @Test
    fun tracksRunningState() {
        val runner = BulkJobRunner()
        runner.start("group1", messageCount = 3)
        var snap = runner.snapshot()
        assertEquals(BulkJobRunner.State.RUNNING, snap.state)
        assertEquals(3, snap.total)
        runner.markSent()
        runner.markSent()
        runner.markSent()
        snap = runner.snapshot()
        assertEquals(BulkJobRunner.State.IDLE, snap.state)
    }

    @Test
    fun pauseAndResume() {
        val runner = BulkJobRunner()
        runner.start("g", 5)
        runner.pause()
        assertEquals(BulkJobRunner.State.PAUSED, runner.snapshot().state)
        runner.resume()
        assertEquals(BulkJobRunner.State.RUNNING, runner.snapshot().state)
    }
}
