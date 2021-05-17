package org.andstatus.app.util

import org.andstatus.app.util.MagnetUri.Companion.tryMargetUri
import org.hamcrest.MatcherAssert
import org.hamcrest.collection.IsIterableContainingInOrder.contains
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class MagnetUriTest {

    @Test
    fun testUris() {
        val xtVal1 = "urn:erisx2:AAADNWEJALNQLKXYIKCOA6X2HEXZOLT7YF7S5KYKXJIIQGPSWJKCWZTMQFEBEMHYRJXOTESONTM7NNJV4XO7WWBKVQRZ7SQRU7KDGLIZWA"
        val xsVal = "http://074661a15e2e.ngrok.io/uri-res/N2R?$xtVal1"
        "magnet:?xs=$xsVal&xt=$xtVal1"
            .tryMargetUri()
            .onFailure { fail(it.message) }
            .get().apply {
                assertEquals(xsVal, xs.toString())
                MatcherAssert.assertThat(xt.map { it.toString() }, contains(xtVal1))
            }

        val xtVal2 = "urn:erisx2:SOMETHINGELSE"
        "magnet:?xs=$xsVal&xt.1=$xtVal1&xt.2=$xtVal2"
            .tryMargetUri()
            .onFailure { fail(it.message) }
            .get().apply {
                assertEquals(xsVal, xs.toString())
                MatcherAssert.assertThat(xt.map { it.toString() }, contains(xtVal1, xtVal2))
            }

        "magnet:?xt=$xtVal1"
            .tryMargetUri()
            .onSuccess { fail("Should fail, but was: $it") }

    }
}