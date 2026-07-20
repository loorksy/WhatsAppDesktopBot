package com.whatsappdesktopbot.local.domain.logic

import com.whatsappdesktopbot.local.domain.engine.WhatsAppMessageActions
import com.whatsappdesktopbot.local.domain.model.BotClient
import com.whatsappdesktopbot.local.domain.model.BotSettings
import com.whatsappdesktopbot.local.domain.model.IncomingMessage
import com.whatsappdesktopbot.local.domain.store.InMemoryBotProcessingStore
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BotOrchestratorTest {
    private lateinit var store: InMemoryBotProcessingStore
    private lateinit var orchestrator: BotOrchestrator
    private val reactions = mutableListOf<String>()

    @BeforeEach
    fun setup() = runBlocking {
        store = InMemoryBotProcessingStore()
        store.saveSettings(BotSettings(replyMode = false, rpm = 20))
        store.replaceClients(listOf(BotClient(name = "محمد", emoji = "👍")))
        store.setSelectedGroupIds(listOf("120363001@g.us"))
        reactions.clear()
        orchestrator = BotOrchestrator(
            store = store,
            actions = object : WhatsAppMessageActions {
                override suspend fun react(message: IncomingMessage, emoji: String) {
                    reactions.add(emoji)
                }

                override suspend fun reply(message: IncomingMessage, text: String) {
                    reactions.add(text)
                }

                override suspend fun forwardMessage(messageId: String, targetChatId: String): Boolean = true
            },
        )
        orchestrator.startBot()
    }

    @Test
    fun processesMatchingGroupMessage() = runBlocking {
        val result = orchestrator.processMessage(
            IncomingMessage(
                id = "m1",
                chatId = "120363001@g.us",
                groupName = "test",
                fromMe = false,
                body = "مرحبا محمد",
            ),
        )
        assertTrue(result.processed)
        assertEquals(listOf("👍"), reactions)
    }

    @Test
    fun skipsWhenNoMatch() = runBlocking {
        orchestrator.processMessage(
            IncomingMessage(
                id = "m2",
                chatId = "120363001@g.us",
                groupName = "test",
                fromMe = false,
                body = "hello world",
            ),
        )
        assertEquals(0, reactions.size)
        assertTrue(store.isProcessed("m2"))
    }

    @Test
    fun queueProcessesSequentially() = runBlocking {
        orchestrator.handleIncoming(
            IncomingMessage("q1", "120363001@g.us", "g", false, body = "محمد 1"),
        )
        orchestrator.handleIncoming(
            IncomingMessage("q2", "120363001@g.us", "g", false, body = "محمد 2"),
        )
        assertEquals(2, reactions.size)
    }
}

class ForwardQueueProcessorTest {
    @Test
    fun flushesWhenBatchSizeReached() = runBlocking {
        val store = InMemoryBotProcessingStore()
        store.saveSettings(
            BotSettings(
                forwardEnabled = true,
                forwardTargetChatId = "target@g.us",
                forwardBatchSize = 2,
            ),
        )
        val processor = ForwardQueueProcessor(store)
        val message = IncomingMessage("m1", "120363001@g.us", "g", false, body = "x")
        processor.enqueue(message)
        processor.enqueue(message.copy(id = "m2"))
        val forwarded = processor.flushBatch(force = false, store.getSettings()) { true }
        assertEquals(2, forwarded)
        assertEquals(0, store.getForwardQueue().size)
    }
}

class MessageFilterTest {
    @Test
    fun rejectsNonSelectedGroup() {
        val result = MessageFilter.shouldProcess(
            IncomingMessage("1", "999@g.us", "g", false, body = "x"),
            botRunning = true,
            selectedGroupIds = listOf("120363001@g.us"),
        )
        assertEquals(false, result.eligible)
        assertEquals("group not selected", result.reason)
    }
}
