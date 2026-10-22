package com.example

import com.example.ai.CommandRouter
import com.example.ai.ParsedAction
import org.junit.Assert.assertEquals
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
    fun testMessagingAndCalls() {
        val call = CommandRouter.parse("call Rahul")
        assertTrue(call is ParsedAction.CallContact)
        assertEquals("Rahul", (call as ParsedAction.CallContact).query)

        val wa = CommandRouter.parse("send whatsapp to Dad saying I am coming home")
        assertTrue(wa is ParsedAction.DraftWhatsApp)
        val waAction = wa as ParsedAction.DraftWhatsApp
        assertEquals("Dad", waAction.recipient)
        assertEquals("I am coming home", waAction.message)
    }
}
