package com.obdreader.app.obd

/**
 * One round of live readings. On CAN cars it asks for several readings in one request, which is
 * several times faster; if the car doesn't support that, it switches to one at a time for the rest
 * of the connection.
 */
class LiveReads(startMulti: Boolean) {
    /** Whether readings are asked for several at a time. */
    var multi: Boolean = startMulti
        private set

    /**
     * Reads [wanted] into [values], each one's value by hex (null for one the car didn't answer).
     * Throws [AdapterTimeoutException] if the adapter stops replying; [values] then keeps the
     * readings that came before.
     */
    suspend fun read(c: ElmClient, wanted: List<Pid>, values: MutableMap<String, Double?> = mutableMapOf()): MutableMap<String, Double?> {
        if (multi) {
            for (chunk in wanted.chunked(ElmClient.MAX_MULTI_PIDS)) {
                val got = c.readPids(chunk)
                if (got.isEmpty() && chunk.size > 1) {
                    // Either the car is quiet (ignition off) or it doesn't support multi-PID
                    // requests. If a single request works, it's the latter: switch to one at a time.
                    val single = c.readPid(chunk.first())
                    if (single != null) {
                        multi = false
                        values[chunk.first().hex] = single
                        break
                    }
                }
                for (p in chunk) values[p.hex] = got[p]
            }
        }
        if (!multi) {
            for (p in wanted) if (p.hex !in values || values[p.hex] == null) values[p.hex] = c.readPid(p)
        }
        return values
    }
}
