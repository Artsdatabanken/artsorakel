package no.artsdatabanken.artsorakel.core

import no.artsdatabanken.artsorakel.R
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpeciesGroupTest {

    @Test
    fun `fromName - maps valid group names correctly`() {
        // Test some key species groups
        assertEquals(SpeciesGroup.Fugler, SpeciesGroup.fromName("fugler"))
        assertEquals(SpeciesGroup.Pattedyr, SpeciesGroup.fromName("pattedyr"))
        assertEquals(SpeciesGroup.Karplanter, SpeciesGroup.fromName("karplanter"))
        assertEquals(SpeciesGroup.Sopper, SpeciesGroup.fromName("sopper"))
    }

    @Test
    fun `fromName - handles case variations`() {
        assertEquals(SpeciesGroup.Fugler, SpeciesGroup.fromName("FUGLER"))
        assertEquals(SpeciesGroup.Fugler, SpeciesGroup.fromName("Fugler"))
        assertEquals(SpeciesGroup.Fugler, SpeciesGroup.fromName("  fugler  "))
    }

    @Test
    fun `fromName - returns Unknown for invalid names`() {
        assertEquals(SpeciesGroup.Unknown, SpeciesGroup.fromName("invalid_group"))
        assertEquals(SpeciesGroup.Unknown, SpeciesGroup.fromName(""))
        assertEquals(SpeciesGroup.Unknown, SpeciesGroup.fromName(null))
    }

    @Test
    fun `fromName - handles complex group names`() {
        assertEquals(
            SpeciesGroup.NettvingereOsv,
            SpeciesGroup.fromName("nettvinger, kakerlakker, saksedyr")
        )
        assertEquals(
            SpeciesGroup.AmfibierReptiler,
            SpeciesGroup.fromName("amfibier, reptiler")
        )
        assertEquals(
            SpeciesGroup.DøgnfluerOsv,
            SpeciesGroup.fromName("døgnfluer, øyenstikkere, steinfluer, vårfluer")
        )
    }

    @Test
    fun `getPlaceholderRes - returns correct placeholder resources`() {
        assertEquals(R.drawable.placeholder_fugler, SpeciesGroup.getPlaceholderRes(mapOf("en" to "Birds", "nb" to "Fugler")))
        assertEquals(R.drawable.placeholder_pattedyr, SpeciesGroup.getPlaceholderRes(mapOf("en" to "Mammals", "nb" to "Pattedyr")))
        assertEquals(R.drawable.placeholder_generic, SpeciesGroup.getPlaceholderRes(mapOf("en" to "Invalid", "nb" to "Invalid")))
        assertEquals(R.drawable.placeholder_generic, SpeciesGroup.getPlaceholderRes(null))
    }

    @Test
    fun `all species groups have valid display names and placeholders`() {
        val allGroups = listOf(
            SpeciesGroup.Karplanter,
            SpeciesGroup.Fugler,
            SpeciesGroup.Pattedyr,
            SpeciesGroup.Lav,
            SpeciesGroup.Sommerfugler,
            SpeciesGroup.Sopper,
            SpeciesGroup.Nebbmunner,
            SpeciesGroup.Moser,
            SpeciesGroup.Bløtdyr,
            SpeciesGroup.Edderkoppdyr,
            SpeciesGroup.NettvingereOsv,
            SpeciesGroup.Veps,
            SpeciesGroup.Biller,
            SpeciesGroup.Tovinger,
            SpeciesGroup.Fisker,
            SpeciesGroup.AmfibierReptiler,
            SpeciesGroup.DøgnfluerOsv,
            SpeciesGroup.ArmfotingerOsv,
            SpeciesGroup.Unknown
        )

        allGroups.forEach { group ->
            assertTrue(group.displayName.isNotBlank(), "Display name should not be blank for $group")
            assertTrue(group.placeholderRes > 0, "Placeholder resource should be valid for $group")
        }
    }

    @Test
    fun `fromName roundtrip test`() {
        val testGroups = listOf(
            SpeciesGroup.Karplanter,
            SpeciesGroup.Fugler,
            SpeciesGroup.Pattedyr,
            SpeciesGroup.NettvingereOsv,
            SpeciesGroup.AmfibierReptiler
        )

        testGroups.forEach { group ->
            assertEquals(group, SpeciesGroup.fromName(group.displayName))
        }
    }
} 