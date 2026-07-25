package com.chiefofstaff.llm

/**
 * §6.8 / SYS-16 — the privacy boundary. Only assembled context ever leaves the device, and it
 * passes through this redaction stage first. Account/card numbers and anything tagged
 * never_send are masked deterministically before egress. Per-domain egress control lives one
 * level up (a domain marked local-only is handled by deterministic rules and never assembled
 * into a request at all).
 */
object Redaction {
    private val cardLike = Regex("\\b(?:\\d[ -]?){13,19}\\b")
    private val longDigits = Regex("\\b\\d{9,}\\b")          // account / policy / passport numbers
    private val neverSendTag = Regex("(?i)\\bnever_send:\\S+")

    fun redact(text: String): String = text
        .replace(cardLike) { m -> maskDigits(m.value) }
        .replace(longDigits) { m -> maskDigits(m.value) }
        .replace(neverSendTag, "«redacted»")

    private fun maskDigits(s: String): String {
        val digits = s.filter(Char::isDigit)
        if (digits.length < 4) return "«redacted»"
        return "••••" + digits.takeLast(4)
    }
}
