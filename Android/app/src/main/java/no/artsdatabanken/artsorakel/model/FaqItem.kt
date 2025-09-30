package no.artsdatabanken.artsorakel.model

data class FaqItem(
    val question: String,
    val answer: String,
    var isExpanded: Boolean = false
)