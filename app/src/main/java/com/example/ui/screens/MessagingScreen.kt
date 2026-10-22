package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ContactModel
import com.example.ui.theme.JarvisBackground
import com.example.ui.theme.JarvisBorder
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisDeepNavy
import com.example.ui.theme.JarvisElectricBlue
import com.example.ui.theme.JarvisSurface
import com.example.ui.theme.JarvisTextMuted
import com.example.ui.theme.JarvisTextPrimary
import com.example.ui.theme.JarvisTextSecondary
import com.example.viewmodel.JarvisViewModel

@Composable
fun MessagingScreen(
    viewModel: JarvisViewModel,
    onRequestContactsPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var recipientNumber by remember { mutableStateOf("") }
    var selectedContactName by remember { mutableStateOf("") }
    var messageBody by remember { mutableStateOf("") }
    val contactResults = remember { mutableStateListOf<ContactModel>() }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(JarvisBackground)
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = "SECURE MESSAGING & CONTACTS HUB",
            color = JarvisCyan,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.2.sp
        )
        Text(
            text = "SMS Dispatch & WhatsApp Direct Intents",
            color = JarvisTextMuted,
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 14.dp)
        )

        // 1. Contact Search & Number Entry
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(JarvisDeepNavy, RoundedCornerShape(12.dp))
                .border(1.dp, JarvisBorder, RoundedCornerShape(12.dp))
                .padding(14.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ContactPhone,
                        contentDescription = "Contacts",
                        tint = JarvisCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "RECIPIENT SELECTION",
                        color = JarvisTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { query ->
                            searchQuery = query
                            if (query.length >= 2) {
                                val results = viewModel.deviceController.searchContacts(query)
                                contactResults.clear()
                                contactResults.addAll(results)
                            } else {
                                contactResults.clear()
                            }
                        },
                        placeholder = {
                            Text("Search contact name (e.g. Dad, Rahul)...", color = JarvisTextMuted, fontSize = 12.sp)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = JarvisCyan,
                            unfocusedBorderColor = JarvisBorder,
                            focusedTextColor = JarvisTextPrimary,
                            unfocusedTextColor = JarvisTextPrimary,
                            focusedContainerColor = JarvisSurface,
                            unfocusedContainerColor = JarvisSurface
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(
                        onClick = {
                            onRequestContactsPermission()
                            if (searchQuery.isNotBlank()) {
                                val results = viewModel.deviceController.searchContacts(searchQuery)
                                contactResults.clear()
                                contactResults.addAll(results)
                            }
                        },
                        modifier = Modifier
                            .size(46.dp)
                            .background(JarvisSurface, RoundedCornerShape(8.dp))
                            .border(1.dp, JarvisBorder, RoundedCornerShape(8.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search Contacts",
                            tint = JarvisCyan
                        )
                    }
                }

                // Contact query results dropdown
                if (contactResults.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(JarvisSurface, RoundedCornerShape(8.dp))
                            .border(1.dp, JarvisBorder, RoundedCornerShape(8.dp))
                            .padding(6.dp)
                    ) {
                        contactResults.take(4).forEach { contact ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedContactName = contact.name
                                        recipientNumber = contact.phoneNumber
                                        contactResults.clear()
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = contact.name,
                                        color = JarvisCyan,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = contact.phoneNumber,
                                        color = JarvisTextSecondary,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Select",
                                    tint = JarvisCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Direct number input
                OutlinedTextField(
                    value = recipientNumber,
                    onValueChange = { recipientNumber = it },
                    placeholder = {
                        Text("Or enter direct phone number (+91...)", color = JarvisTextMuted, fontSize = 12.sp)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = JarvisCyan,
                        unfocusedBorderColor = JarvisBorder,
                        focusedTextColor = JarvisTextPrimary,
                        unfocusedTextColor = JarvisTextPrimary,
                        focusedContainerColor = JarvisSurface,
                        unfocusedContainerColor = JarvisSurface
                    ),
                    singleLine = true
                )

                if (selectedContactName.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "SELECTED: $selectedContactName",
                        color = JarvisCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Message Composition Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(JarvisDeepNavy, RoundedCornerShape(12.dp))
                .border(1.dp, JarvisBorder, RoundedCornerShape(12.dp))
                .padding(14.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Message,
                        contentDescription = "Message",
                        tint = JarvisCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "MESSAGE COMPOSER",
                        color = JarvisTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = messageBody,
                    onValueChange = { messageBody = it },
                    placeholder = {
                        Text(
                            "Type your message in English or Hindi / Hinglish...",
                            color = JarvisTextMuted,
                            fontSize = 13.sp
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .testTag("message_body_input"),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = JarvisCyan,
                        unfocusedBorderColor = JarvisBorder,
                        focusedTextColor = JarvisTextPrimary,
                        unfocusedTextColor = JarvisTextPrimary,
                        focusedContainerColor = JarvisSurface,
                        unfocusedContainerColor = JarvisSurface
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Quick Templates
                Text(
                    text = "QUICK TEMPLATES:",
                    color = JarvisTextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "On my way!" to "I will be home soon, on my way!",
                        "Call you later" to "Busy right now, will call you back later.",
                        "Mujhe phone karo" to "Free hokar mujhe call karo please."
                    ).forEach { (chip, text) ->
                        Box(
                            modifier = Modifier
                                .background(JarvisSurface, RoundedCornerShape(6.dp))
                                .border(1.dp, JarvisBorder, RoundedCornerShape(6.dp))
                                .clickable { messageBody = text }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = chip,
                                color = JarvisTextSecondary,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 3. Action Dispatch Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // WhatsApp Button
            Button(
                onClick = {
                    val target = recipientNumber.ifBlank { selectedContactName }
                    if (target.isNotBlank() && messageBody.isNotBlank()) {
                        viewModel.processUserCommand(
                            "send whatsapp to $target saying $messageBody",
                            wasVoice = false
                        )
                    } else if (target.isNotBlank()) {
                        viewModel.processUserCommand("Open WhatsApp chat with $target", wasVoice = false)
                    } else {
                        viewModel.deviceController.launchApp("whatsapp")
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("whatsapp_dispatch_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF25D366),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    "WHATSAPP",
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            }

            // SMS Button
            Button(
                onClick = {
                    val target = recipientNumber.ifBlank { selectedContactName }
                    if (target.isNotBlank() && messageBody.isNotBlank()) {
                        viewModel.processUserCommand(
                            "send sms to $target saying $messageBody",
                            wasVoice = false
                        )
                    } else {
                        viewModel.deviceController.prepareSms(target, messageBody)
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("sms_dispatch_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = JarvisCyan,
                    contentColor = Color(0xFF001E2E)
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    "SEND SMS",
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Dialer quick call button
        if (recipientNumber.isNotBlank()) {
            OutlinedButton(
                onClick = {
                    viewModel.deviceController.dialNumber(recipientNumber)
                },
                modifier = Modifier.fillMaxWidth(),
                border = androidx.compose.foundation.BorderStroke(1.dp, JarvisElectricBlue),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = "Call",
                    tint = JarvisElectricBlue,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "DIAL RECIPIENT ($recipientNumber)",
                    color = JarvisElectricBlue,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
