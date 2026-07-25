package com.chiefofstaff.capture

import com.chiefofstaff.data.model.Domain

/**
 * DOM-05 — a domain is "a capability, activated" the moment something in that domain is captured.
 * Activation is mechanical: once a fact carries the right [Domain] tag, the generic engines light
 * up for it automatically — expiry watch for HOME/ADMIN documents, the financial calendar for MONEY
 * obligations, horizon goals for LEARNING and PROJECTS, contact cadence for PEOPLE.
 *
 * This classifier is the deterministic backstop (P11): when the extractor doesn't name a domain —
 * offline, or a terse capture — a keyword pass infers one so nothing lands untagged in NONE. The
 * model's own tag always wins; this only fills the gap. Order matters: the first domain whose
 * vocabulary matches is chosen, so the more specific domains are checked before the broad ones.
 */
object DomainClassifier {
    // Ordered most-specific first. Whole-word matching, lowercased.
    private val vocab: List<Pair<Domain, List<String>>> = listOf(
        Domain.TRAVEL to listOf(
            "flight", "flights", "trip", "hotel", "booking", "boarding", "visa", "passport control",
            "itinerary", "packing", "check-in", "airport", "layover", "airbnb", "train ticket",
        ),
        Domain.MONEY to listOf(
            "emi", "sip", "insurance", "premium", "tax", "gst", "invoice", "bill", "rent",
            "renewal", "policy", "loan", "mutual fund", "credit card", "refund", "salary", "payment",
        ),
        Domain.LEARNING to listOf(
            "book", "read", "course", "chapter", "practice", "study", "tutorial", "certification",
            "lesson", "learn", "leetcode", "exercise set",
        ),
        Domain.ADMIN to listOf(
            "aadhaar", "pan card", "license", "licence", "registration", "rto", "municipal",
            "government", "passport", "notary", "affidavit", "form 16", "kyc",
        ),
        Domain.HEALTH to listOf(
            "gym", "workout", "run", "walk", "steps", "sleep", "doctor", "appointment", "blood",
            "medication", "medicine", "physio", "dentist", "meal", "water", "weight", "injury",
        ),
        Domain.HOME to listOf(
            "repair", "plumber", "electrician", "grocery", "groceries", "maintenance", "cleaning",
            "furniture", "appliance", "warranty", "landlord", "society",
        ),
        Domain.PEOPLE to listOf(
            "birthday", "anniversary", "call mom", "call dad", "catch up", "wish ", "gift",
            "meet ", "reconnect",
        ),
        Domain.WORK to listOf(
            "meeting", "standup", "deadline", "deliverable", "review", "ship", "deploy", "client",
            "report", "presentation", "sprint", "ticket", "pr ", "manager", "1:1", "one-on-one",
        ),
        Domain.PROJECTS to listOf(
            "side project", "prototype", "mvp", "launch", "build the", "hackathon", "portfolio",
        ),
    )

    /** Infer a domain from free text, or return [default] if nothing matches. */
    fun classify(text: String?, default: Domain = Domain.NONE): Domain {
        if (text.isNullOrBlank()) return default
        val s = " ${text.lowercase()} "
        for ((domain, words) in vocab) {
            if (words.any { s.contains(if (it.endsWith(" ")) it else " $it ") || s.contains(" $it") }) {
                return domain
            }
        }
        return default
    }
}
