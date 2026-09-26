package app.holdthatpose.session

import app.holdthatpose.net.PhotoHeader

/**
 * Review copies waiting for their Remote. An entry belongs to the Remote that was in session
 * when the photo was taken ([Entry.owner]) and is only ever sent to that Remote. It stays until
 * the Remote acknowledges it, so a link drop mid-transfer resends it on the next link.
 *
 * Pure Kotlin (no Android types) so it can be unit-tested on the JVM. Not thread-safe: the
 * Camera session uses it from the main thread only.
 */
class PhotoOutbox(private val capacity: Int = MAX_OUTBOX) {

    class Entry(val owner: String, val header: PhotoHeader, val bytes: ByteArray)

    private val entries = ArrayDeque<Entry>()
    private val sent = mutableSetOf<String>()

    val size: Int get() = entries.size

    fun add(owner: String, header: PhotoHeader, bytes: ByteArray) {
        entries.removeAll { it.header.id == header.id }
        entries.addLast(Entry(owner, header, bytes))
        while (entries.size > capacity) {
            sent.remove(entries.removeFirst().header.id)
        }
    }

    /** Entries for [owner] not yet sent on the current link, oldest first. */
    fun due(owner: String): List<Entry> = entries.filter { it.owner == owner && it.header.id !in sent }

    fun markSent(id: String) {
        sent += id
    }

    /** The Remote confirmed [id]; forget it. */
    fun ack(id: String) {
        entries.removeAll { it.header.id == id }
        sent.remove(id)
    }

    /** A new link started: anything unacknowledged is due again. */
    fun newLink() {
        sent.clear()
    }

    fun clear() {
        entries.clear()
        sent.clear()
    }

    companion object {
        const val MAX_OUTBOX = 20
    }
}

/**
 * Which photo id belongs to which Remote, and where the Camera saved it. A Remote may only
 * delete photos it owns. [U] is the gallery Uri on Android; generic so the JVM tests need no
 * Android types.
 */
class ShotRegistry<U> {
    private data class Item<U>(val uri: U?, val owner: String)

    private val items = mutableMapOf<String, Item<U>>()

    fun put(id: String, uri: U?, owner: String) {
        items[id] = Item(uri, owner)
    }

    /**
     * Removes and returns [id] only if [owner] owns it. Returns null for unknown ids and for
     * another Remote's photos (which stay registered).
     */
    fun takeIfOwned(id: String, owner: String?): Taken<U>? {
        val item = items[id] ?: return null
        if (owner == null || item.owner != owner) return null
        items.remove(id)
        return Taken(item.uri)
    }

    fun clear() {
        items.clear()
    }

    class Taken<U>(val uri: U?)
}
