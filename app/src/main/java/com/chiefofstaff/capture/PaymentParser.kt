package com.chiefofstaff.capture

/**
 * DOM-12 — spending capture from payment notifications, deterministically (P11, no bank API).
 * Payment and UPI apps post a structured "debited/spent/paid ₹NNN at MERCHANT" notification for
 * every transaction; this parses that text on-device into an amount, a merchant, and a light
 * category. It never touches a network and never guesses when the shape is unfamiliar — an
 * unparseable notification is simply skipped, so the worst case is a missed transaction, never a
 * wrong one. Automatic statement reconciliation still needs a real bank/UPI feed; this is the
 * passive half that works with nothing but the notification shade.
 */
object PaymentParser {
    data class Spend(val amount: Double, val merchant: String?, val category: String)

    // Only outflows. Credits/refunds/received are ignored so "spend" stays spend.
    private val debitWords = listOf("debited", "spent", "paid", "purchase", "debit", "sent")
    private val creditWords = listOf("credited", "received", "refund", "cashback", "added")

    // ₹1,234.50 · Rs. 1234 · INR 1,234.50
    private val amountRe = Regex(
        """(?:₹|rs\.?|inr)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE,
    )
    // "at STARBUCKS", "to swiggy", "@ Amazon"
    private val merchantRe = Regex(
        """(?:\bat\b|\bto\b|@)\s+([A-Za-z0-9][A-Za-z0-9 &._-]{1,40}?)(?:\s+(?:on|via|using|ref|upi|txn)\b|[.,;]|$)""",
        RegexOption.IGNORE_CASE,
    )

    private val categories: List<Pair<String, List<String>>> = listOf(
        "food" to listOf("swiggy", "zomato", "restaurant", "cafe", "coffee", "starbucks", "dominos", "eatery", "dining"),
        "transport" to listOf("uber", "ola", "rapido", "metro", "irctc", "fuel", "petrol", "hpcl", "indianoil", "bpcl"),
        "groceries" to listOf("bigbasket", "blinkit", "zepto", "dmart", "grofers", "instamart", "supermarket"),
        "shopping" to listOf("amazon", "flipkart", "myntra", "ajio", "nykaa", "store", "mall"),
        "bills" to listOf("electricity", "recharge", "postpaid", "broadband", "gas", "water bill", "dth"),
        "entertainment" to listOf("netflix", "spotify", "hotstar", "prime", "bookmyshow", "pvr"),
        "health" to listOf("pharmacy", "apollo", "pharmeasy", "1mg", "hospital", "clinic", "medplus"),
    )

    fun parse(raw: String): Spend? {
        val s = raw.lowercase()
        if (creditWords.any { s.contains(it) } && debitWords.none { s.contains(it) }) return null
        if (debitWords.none { s.contains(it) }) return null

        val amount = amountRe.find(raw)?.groupValues?.get(1)
            ?.replace(",", "")?.toDoubleOrNull()?.takeIf { it > 0 } ?: return null

        val merchant = merchantRe.find(raw)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
        val hay = (merchant.orEmpty() + " " + raw).lowercase()
        val category = categories.firstOrNull { (_, kws) -> kws.any { hay.contains(it) } }?.first ?: "other"
        return Spend(amount, merchant, category)
    }
}
