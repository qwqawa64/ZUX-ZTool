package com.qimian233.ztool

import com.qimian233.ztool.data.features.FeatureAvailability
import com.qimian233.ztool.data.features.FeatureCatalog
import com.qimian233.ztool.data.features.FeatureDestination
import com.qimian233.ztool.data.features.FeatureIconSource
import com.qimian233.ztool.data.features.FeatureScopeSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Catalog coverage and package-backing invariants; no Android APIs involved. */
class FeatureCatalogTest {

    @Test
    fun `every destination has exactly one catalog card`() {
        val destinations = FeatureCatalog.cards.map { it.destination }
        assertEquals(
            "Catalog destinations must match FeatureDestination.entries",
            FeatureDestination.entries.toSet(),
            destinations.toSet()
        )
        val duplicates = destinations
            .groupingBy { it }
            .eachCount()
            .filterValues { it > 1 }
            .keys
        assertEquals(
            "Catalog has duplicate destinations: $duplicates",
            destinations.size,
            destinations.toSet().size
        )
        FeatureDestination.entries.forEach { destination ->
            assertEquals(destination, FeatureCatalog.card(destination).destination)
        }
    }

    @Test
    fun `package-backed entries use non-blank package names`() {
        FeatureCatalog.cards.forEach { card ->
            val icon = card.icon
            if (icon is FeatureIconSource.Package) {
                assertTrue(
                    "Blank icon package on ${card.destination}",
                    icon.packageName.isNotBlank()
                )
            }
            val availability = card.availability
            if (availability is FeatureAvailability.RequiresInstalled) {
                assertTrue(
                    "Blank required package on ${card.destination}",
                    availability.packageName.isNotBlank()
                )
            }
        }
    }

    @Test
    fun `app-backed cards use the matching package icon`() {
        FeatureCatalog.cards.forEach { card ->
            val requiredPackage = card.requiredPackage() ?: return@forEach
            val iconPackage = (card.icon as? FeatureIconSource.Package)?.packageName
            assertEquals(
                "Card ${card.destination} must use the icon of $requiredPackage",
                requiredPackage,
                iconPackage
            )
        }
    }

    @Test
    fun `user-managed cards expose no scopes`() {
        FeatureCatalog.cards.forEach { card ->
            if (card.scope == FeatureScopeSource.UserManaged) {
                assertTrue(
                    "UserManaged card ${card.destination} must expose no scopes",
                    card.scopes().isEmpty()
                )
            }
        }
    }
}
