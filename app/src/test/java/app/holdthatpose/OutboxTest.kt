package app.holdthatpose

import app.holdthatpose.net.PhotoHeader
import app.holdthatpose.session.PhotoOutbox
import app.holdthatpose.session.ShotRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OutboxTest {

    private fun header(id: String) = PhotoHeader(id, 10, 10, 0)

    private fun PhotoOutbox.dueIds(owner: String) = due(owner).map { it.header.id }

    @Test
    fun dueReturnsOnlyTheOwnersEntries() {
        val box = PhotoOutbox()
        box.add("alice", header("a1"), ByteArray(1))
        box.add("bob", header("b1"), ByteArray(1))
        box.add("alice", header("a2"), ByteArray(1))
        assertEquals(listOf("a1", "a2"), box.dueIds("alice"))
        assertEquals(listOf("b1"), box.dueIds("bob"))
        assertTrue(box.dueIds("mallory").isEmpty())
    }

    @Test
    fun sentEntriesWaitForTheAck() {
        val box = PhotoOutbox()
        box.add("alice", header("a1"), ByteArray(1))
        box.markSent("a1")
        assertTrue(box.dueIds("alice").isEmpty())
        assertEquals(1, box.size)
        box.ack("a1")
        assertEquals(0, box.size)
    }

    @Test
    fun unackedEntryIsDueAgainOnTheNextLink() {
        val box = PhotoOutbox()
        box.add("alice", header("a1"), ByteArray(1))
        box.markSent("a1")
        box.newLink()
        assertEquals(listOf("a1"), box.dueIds("alice"))
        box.ack("a1")
        box.newLink()
        assertTrue(box.dueIds("alice").isEmpty())
    }

    @Test
    fun capDropsTheOldest() {
        val box = PhotoOutbox(capacity = 3)
        (1..5).forEach { box.add("alice", header("p$it"), ByteArray(1)) }
        assertEquals(3, box.size)
        assertEquals(listOf("p3", "p4", "p5"), box.dueIds("alice"))
        assertEquals(20, PhotoOutbox.MAX_OUTBOX)
    }

    @Test
    fun clearForgetsEverything() {
        val box = PhotoOutbox()
        box.add("alice", header("a1"), ByteArray(1))
        box.clear()
        assertEquals(0, box.size)
        assertTrue(box.dueIds("alice").isEmpty())
    }

    @Test
    fun registryRefusesAnotherOwner() {
        val reg = ShotRegistry<String>()
        reg.put("a1", "content://a1", "alice")
        assertNull(reg.takeIfOwned("a1", "bob"))
        assertNull(reg.takeIfOwned("a1", null))
        assertNull(reg.takeIfOwned("nope", "alice"))
        val taken = reg.takeIfOwned("a1", "alice")
        assertNotNull(taken)
        assertEquals("content://a1", taken!!.uri)
        // Only once.
        assertNull(reg.takeIfOwned("a1", "alice"))
    }

    @Test
    fun registryClearForgetsOwnership() {
        val reg = ShotRegistry<String>()
        reg.put("a1", null, "alice")
        reg.clear()
        assertNull(reg.takeIfOwned("a1", "alice"))
    }
}
