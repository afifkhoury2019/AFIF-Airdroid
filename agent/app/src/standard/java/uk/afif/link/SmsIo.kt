package uk.afif.link

import android.content.Context
import org.json.JSONArray

/**
 * `standard` flavour: built with no telephony code and no SMS permissions.
 * Every SMS command reports that the capability is absent from this build.
 */
object SmsIo {

    const val AVAILABLE = false

    fun list(c: Context, limit: Int): JSONArray = JSONArray()

    fun send(c: Context, to: String, text: String): Boolean = false
}
