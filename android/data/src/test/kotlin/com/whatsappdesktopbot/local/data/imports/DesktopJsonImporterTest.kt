package com.whatsappdesktopbot.local.data.imports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopJsonImporterTest {
    @Test
    fun parsesSettingsAndGroups() {
        val bundle = DesktopJsonImporter.parseBundle(
            settingsJson = """{"rpm":15,"defaultEmoji":"✅","bulkMessagesPerMinute":30}""",
            groupsJson = """["120363001@g.us","120363002@g.us"]""",
            clientsJson = """[{"name":"Ali","emoji":"👍"}]""",
        )
        assertEquals(15, bundle.settings?.rpm)
        assertEquals(30, bundle.settings?.bulkMessagesPerMinute)
        assertEquals(2, bundle.selectedGroupIds.size)
        assertEquals(1, bundle.clients.size)
        assertTrue(bundle.clients.first().name == "Ali")
    }
}
