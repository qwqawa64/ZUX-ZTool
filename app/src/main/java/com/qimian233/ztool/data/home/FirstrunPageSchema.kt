package com.qimian233.ztool.data.home

/**
 * Registry of every page participating in the first-run (OOBE) flow, in flow
 * order. Each page owns a schema version: bump it whenever the page's content
 * or semantics change in a way the user must see again — the next launch then
 * replays the flow and re-records acceptance.
 *
 * AGREEMENT.schemaVersion tracks the packaged agreement markdown version
 * (res/raw/agreement.md, "协议版本：x.y"); keep the two in sync when bumping.
 *
 * New pages must be inserted here with their position in [order]; the flow
 * decision in MainActivity and the page wiring in FirstrunAgreementRoute both
 * derive from this registry.
 */
enum class FirstrunPageSchema(val order: Int, val schemaVersion: Int) {
    AGREEMENT(order = 0, schemaVersion = 1),
    SOURCE_VERIFY(order = 1, schemaVersion = 1),
    PERMISSIONS(order = 2, schemaVersion = 2);
}
