package com.tasktracker.pro.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContactActionsTest {

    @Test
    fun tenDigitIndianNumberGetsCountryCode() {
        assertEquals("919876543210", ContactActions.whatsAppDigits("98765 43210"))
        assertEquals("https://wa.me/919876543210", ContactActions.whatsAppUrl("9876543210"))
    }

    @Test
    fun leadingZeroIsDroppedAndCountryCodeAdded() {
        assertEquals("919876543210", ContactActions.whatsAppDigits("09876543210"))
    }

    @Test
    fun numberAlreadyWithCountryCodeIsKept() {
        assertEquals("919876543210", ContactActions.whatsAppDigits("+91 98765 43210"))
        assertEquals("14155552671", ContactActions.whatsAppDigits("+1 415 555 2671"))
    }

    @Test
    fun internationalPrefix00IsNormalized() {
        assertEquals("919876543210", ContactActions.whatsAppDigits("0091 98765 43210"))
    }

    @Test
    fun tooShortNumbersAreRejectedForWhatsApp() {
        assertNull(ContactActions.whatsAppUrl("12345"))
        assertNull(ContactActions.whatsAppUrl(""))
        assertNull(ContactActions.whatsAppUrl("no digits"))
    }

    @Test
    fun dialUriKeepsPlusAndDigitsOnly() {
        assertEquals("tel:+919876543210", ContactActions.dialUri("+91 98765-43210"))
        assertEquals("tel:9876543210", ContactActions.dialUri("98765 43210"))
    }

    @Test
    fun dialUriRejectsGarbage() {
        assertNull(ContactActions.dialUri("abc"))
        assertNull(ContactActions.dialUri(""))
    }
}
