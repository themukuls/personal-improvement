package com.chiefofstaff.llm

import com.chiefofstaff.data.model.Domain

/**
 * SYS-19 / §6.8 — per-domain egress control. A domain can be marked local-only: its data is then
 * handled by deterministic rules alone and never assembled into a request that leaves the device.
 * Default is empty (nothing restricted); the spec's example is marking `money` local-only. Context
 * providers consult this before emitting domain-scoped data.
 */
object EgressPolicy {
    val localOnly: MutableSet<Domain> = mutableSetOf()

    fun isLocalOnly(domain: Domain): Boolean = domain in localOnly
}
