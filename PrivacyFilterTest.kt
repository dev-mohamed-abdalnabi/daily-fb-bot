package com.abdalnaby.dynamicisland.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrivacyFilterTest {
    private fun raw(visibility: NotificationVisibility = NotificationVisibility.PRIVATE, locked: Boolean = false) =
        RawNotification("WhatsApp", "Ahmed", "See you at 8", visibility, locked)

    @Test
    fun showAllUnlocked() {
        val d = PrivacyFilter.filter(raw(), NotificationPrivacy.SHOW_ALL, hideContentOnLockscreen = true)!!
        assertEquals("Ahmed", d.title)
        assertEquals("See you at 8", d.subtitle)
    }

    @Test
    fun hideContentKeepsSender() {
        val d = PrivacyFilter.filter(raw(), NotificationPrivacy.HIDE_CONTENT, false)!!
        assertEquals("Ahmed", d.title)
        assertEquals(PrivacyFilter.HIDDEN_CONTENT, d.subtitle)
    }

    @Test
    fun appNameOnly() {
        val d = PrivacyFilter.filter(raw(), NotificationPrivacy.APP_NAME_ONLY, false)!!
        assertEquals("WhatsApp", d.title)
        assertNull(d.subtitle)
    }

    @Test
    fun lockedWithHideOnLockscreenHidesContent() {
        val d = PrivacyFilter.filter(raw(NotificationVisibility.PUBLIC, locked = true), NotificationPrivacy.SHOW_ALL, true)!!
        assertEquals(PrivacyFilter.HIDDEN_CONTENT, d.subtitle)
    }

    @Test
    fun lockedPrivateNotificationHiddenEvenIfUserAllowsAll() {
        val d = PrivacyFilter.filter(raw(NotificationVisibility.PRIVATE, locked = true), NotificationPrivacy.SHOW_ALL, false)!!
        assertEquals(PrivacyFilter.HIDDEN_CONTENT, d.subtitle)
    }

    @Test
    fun lockedPublicNotificationShownWhenAllowed() {
        val d = PrivacyFilter.filter(raw(NotificationVisibility.PUBLIC, locked = true), NotificationPrivacy.SHOW_ALL, false)!!
        assertEquals("See you at 8", d.subtitle)
    }

    @Test
    fun secretNotificationDroppedWhenLocked() {
        assertNull(PrivacyFilter.filter(raw(NotificationVisibility.SECRET, locked = true), NotificationPrivacy.SHOW_ALL, false))
    }

    @Test
    fun lockNeverLoosensStricterSetting() {
        val d = PrivacyFilter.filter(raw(locked = true), NotificationPrivacy.APP_NAME_ONLY, true)!!
        assertEquals("WhatsApp", d.title)
        assertNull(d.subtitle)
    }

    @Test
    fun blankTitleFallsBackToAppName() {
        val d = PrivacyFilter.filter(RawNotification("Gmail", "  ", "\n hi \n there ", NotificationVisibility.PUBLIC, false), NotificationPrivacy.SHOW_ALL, false)!!
        assertEquals("Gmail", d.title)
        assertEquals("hi there", d.subtitle)
    }

    @Test
    fun longTextIsTruncated() {
        val long = "x".repeat(500)
        assertEquals(160, PrivacyFilter.clean(long)!!.length)
    }

    @Test
    fun clipboardPreview() {
        assertEquals("hello world", PrivacyFilter.clipPreview(" hello\nworld ", false, NotificationPrivacy.SHOW_ALL))
        assertEquals(PrivacyFilter.SENSITIVE_MASK, PrivacyFilter.clipPreview("123456", true, NotificationPrivacy.SHOW_ALL))
        assertEquals("6 characters copied", PrivacyFilter.clipPreview("abcdef", false, NotificationPrivacy.HIDE_CONTENT))
        assertEquals(PrivacyFilter.SENSITIVE_MASK, PrivacyFilter.clipPreview("abc", false, NotificationPrivacy.APP_NAME_ONLY))
        assertEquals("Clipboard is empty", PrivacyFilter.clipPreview(null, false, NotificationPrivacy.SHOW_ALL))
    }
}
