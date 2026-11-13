package uk.afif.link

import org.json.JSONObject

/** Tiny in-memory outbox drained by LinkService on each publish cycle. */
object EventOutbox {
    private val items = ArrayDeque<JSONObject>()

    @Synchronized
    fun push(o: JSONObject) {
        items.addFirst(o)
        while (items.size > 50) items.removeLast()
    }

    @Synchronized
    fun drain(): List<JSONObject> {
        val out = items.toList()
        items.clear()
        return out
    }
}
