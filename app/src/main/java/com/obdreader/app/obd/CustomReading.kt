package com.obdreader.app.obd

import java.util.Locale

/**
 * A reading the user adds themselves: a name, the request (service 01, 21 or 22 only, so it
 * can only read), an optional module address, a formula over the answer's bytes and a unit.
 * For example Ford transmission temperature: request 221E1C, address 7E0, formula
 * "(A*256+B)/16", unit "°C". Kept on the phone only.
 */
data class CustomReading(val name: String, val request: String, val header: String?, val formula: String, val unit: String) {

    /** The answer's header: the request's service plus 0x40, then the rest of the request ("221E1C" → "621E1C"). */
    val replyHeader: String get() = String.format(Locale.US, "%02X", request.substring(0, 2).toInt(16) + 0x40) + request.substring(2)

    fun encode(): String = listOf(name, request, header.orEmpty(), formula, unit).joinToString("\u001F")

    /** The value from the answer's data bytes, or null when the formula can't use them. */
    fun value(bytes: IntArray): Double? = Formula.evaluate(formula, bytes)

    companion object {
        /** Services a custom reading may use: read current data only. */
        private val SERVICES = setOf("01", "21", "22")

        fun decode(line: String): CustomReading? {
            val p = line.split("\u001F")
            if (p.size != 5) return null
            return validate(p[0], p[1], p[2], p[3], p[4])
        }

        fun decodeAll(text: String?): List<CustomReading> = text.orEmpty().lines().mapNotNull { decode(it) }
        fun encodeAll(list: List<CustomReading>): String = list.joinToString("\n") { it.encode() }

        /** A reading from what the user typed, or null with the reason in [problem]. */
        fun validate(name: String, request: String, header: String, formula: String, unit: String): CustomReading? =
            if (problem(name, request, header, formula) != null) null
            else CustomReading(name.trim(), clean(request), clean(header).ifEmpty { null }, formula.trim(), unit.trim())

        private fun clean(s: String) = s.uppercase(Locale.US).filter { !it.isWhitespace() }

        fun problem(name: String, request: String, header: String, formula: String): String? {
            val r = clean(request); val h = clean(header)
            return when {
                name.isBlank() -> "Give it a name."
                r.length < 4 || r.length % 2 != 0 || !r.all { it in '0'..'9' || it in 'A'..'F' } -> "The request is hex, like 010C or 221E1C."
                r.substring(0, 2) !in SERVICES -> "Only reading requests are allowed: they start with 01, 21 or 22."
                h.isNotEmpty() && (h.length !in setOf(3, 6, 8) || !h.all { it in '0'..'9' || it in 'A'..'F' }) -> "The address is hex, like 7E0 (or leave it empty)."
                Formula.evaluate(formula, IntArray(8) { 1 }) == null -> "The formula can use A, B, C, D (the answer's bytes), numbers, + - * / and brackets."
                else -> null
            }
        }
    }
}

/** A small arithmetic formula over the answer's bytes: A, B, C… numbers, + - * / and brackets. */
object Formula {
    fun evaluate(text: String, bytes: IntArray): Double? = try {
        Parser(text.uppercase(Locale.US).filter { !it.isWhitespace() }, bytes).run()
    } catch (e: Exception) { null }

    private class Parser(val s: String, val bytes: IntArray) {
        var i = 0
        fun run(): Double? { if (s.isEmpty()) return null; val v = expr(); return if (i == s.length && v.isFinite()) v else null }
        fun expr(): Double { var v = term(); while (i < s.length && s[i] in "+-") { val op = s[i++]; val t = term(); v = if (op == '+') v + t else v - t }; return v }
        fun term(): Double { var v = factor(); while (i < s.length && s[i] in "*/") { val op = s[i++]; val f = factor(); v = if (op == '*') v * f else v / f }; return v }
        fun factor(): Double {
            if (i >= s.length) throw IllegalArgumentException()
            val c = s[i]
            return when {
                c == '-' -> { i++; -factor() }
                c == '(' -> { i++; val v = expr(); if (i >= s.length || s[i] != ')') throw IllegalArgumentException(); i++; v }
                c in 'A'..'H' -> { i++; bytes.getOrNull(c - 'A')?.toDouble() ?: throw IllegalArgumentException() }
                c.isDigit() || c == '.' -> { val start = i; while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++; s.substring(start, i).toDouble() }
                else -> throw IllegalArgumentException()
            }
        }
    }
}
