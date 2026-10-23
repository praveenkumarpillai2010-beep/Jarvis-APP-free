package com.example

import com.example.ai.CommandRouter
import com.example.ai.ParsedAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testFlashlightCommands_EnglishAndHindi() {
        val on1 = CommandRouter.parse("turn on flashlight")
        assertTrue(on1 is ParsedAction.FlashlightOn)

        val on2 = CommandRouter.parse("torch jalao")
        assertTrue(on2 is ParsedAction.FlashlightOn)

        val off1 = CommandRouter.parse("turn off torch")
        assertTrue(off1 is ParsedAction.FlashlightOff)

        val off2 = CommandRouter.parse("torch band karo")
        assertTrue(off2 is ParsedAction.FlashlightOff)
    }

    @Test
    fun testBatteryStatus_Commands() {
        val bat1 = CommandRouter.parse("what is my battery percentage")
        assertTrue(bat1 is ParsedAction.BatteryStatus)

        val bat2 = CommandRouter.parse("battery kitni hai")
        assertTrue(bat2 is ParsedAction.BatteryStatus)
    }

    @Test
    fun testAppOpening_EnglishAndHindi() {
        val app1 = CommandRouter.parse("open youtube")
        assertTrue(app1 is ParsedAction.OpenApp)
        assertEquals("youtube", (app1 as ParsedAction.OpenApp).appName)

        val app2 = CommandRouter.parse("whatsapp kholo")
        assertTrue(app2 is ParsedAction.OpenApp)
        assertEquals("whatsapp", (app2 as ParsedAction.OpenApp).appName)
    }

    @Test
    fun testYouTubeSearchAndPlay() {
        val yt1 = CommandRouter.parse("search YouTube for the latest AI news")
        assertTrue(yt1 is ParsedAction.SearchYouTube)
        val s1 = yt1 as ParsedAction.SearchYouTube
        assertEquals("the latest AI news", s1.query)
        assertFalse(s1.autoPlay)

        val yt2 = CommandRouter.parse("play relaxing music on YouTube")
        assertTrue(yt2 is ParsedAction.SearchYouTube)
        val s2 = yt2 as ParsedAction.SearchYouTube
        assertEquals("relaxing music", s2.query)
        assertTrue(s2.autoPlay)

        val yt3 = CommandRouter.parse("search for Doraemon movies on YouTube")
        assertTrue(yt3 is ParsedAction.SearchYouTube)
        val s3 = yt3 as ParsedAction.SearchYouTube
        assertEquals("Doraemon movies", s3.query)
    }

    @Test
    fun testFindContactAndCalls() {
        val find = CommandRouter.parse("find Pravin in my contacts")
        assertTrue(find is ParsedAction.FindContact)
        assertEquals("Pravin", (find as ParsedAction.FindContact).query)

        val call = CommandRouter.parse("call Dad")
        assertTrue(call is ParsedAction.CallContact)
        assertEquals("Dad", (call as ParsedAction.CallContact).query)
    }

    @Test
    fun testMessagingAndCancellation() {
        val wa = CommandRouter.parse("send whatsapp to Dad saying I am coming home")
        assertTrue(wa is ParsedAction.DraftWhatsApp)
        val waAction = wa as ParsedAction.DraftWhatsApp
        assertEquals("Dad", waAction.recipient)
        assertEquals("I am coming home", waAction.message)

        val cancel = CommandRouter.parse("cancel the message")
        assertTrue(cancel is ParsedAction.CancelAction)
    }

    @Test
    fun testTimerAndAlarm_Commands() {
        val timer = CommandRouter.parse("set a timer for 10 minutes")
        assertTrue(timer is ParsedAction.CreateTimer)
        assertEquals(600, (timer as ParsedAction.CreateTimer).seconds)

        val alarm = CommandRouter.parse("set an alarm for 6:30 am")
        assertTrue(alarm is ParsedAction.CreateAlarm)
        assertEquals(6, (alarm as ParsedAction.CreateAlarm).hour)
        assertEquals(30, (alarm as ParsedAction.CreateAlarm).minute)
    }

    @Test
    fun testScreenshotCommands() {
        val shot1 = CommandRouter.parse("take a screenshot")
        assertTrue(shot1 is ParsedAction.TakeScreenshot)

        val shot2 = CommandRouter.parse("screenshot lo")
        assertTrue(shot2 is ParsedAction.TakeScreenshot)

        val shot3 = CommandRouter.parse("capture screen")
        assertTrue(shot3 is ParsedAction.TakeScreenshot)

        val shot4 = CommandRouter.parse("hey jarvis, take screenshot")
        assertTrue(shot4 is ParsedAction.TakeScreenshot)
    }
}

