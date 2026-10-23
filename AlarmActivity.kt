package com.lockin.app

import android.app.NotificationManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.random.Random

private data class MathQuestion(val prompt: String, val answer: Int)

class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        setContent { AlarmChallenge(onDismissAlarm = { finishAlarm() }) }
    }

    private fun finishAlarm() {
        stopService(Intent(this, AlarmSoundService::class.java).setAction(AlarmSoundService.ACTION_STOP))
        getSystemService(NotificationManager::class.java).cancel(4201)
        finish()
    }
}

@Composable
private fun AlarmChallenge(onDismissAlarm: () -> Unit) {
    val questions = remember { makeQuestions() }
    var questionIndex by remember { mutableIntStateOf(0) }
    var answerText by remember { mutableStateOf("") }
    var feedback by remember { mutableStateOf("") }
    var emergencyCount by remember { mutableIntStateOf(-1) }

    LaunchedEffect(emergencyCount) {
        if (emergencyCount > 0) {
            delay(1_000)
            emergencyCount--
        }
    }

    Box(Modifier.fillMaxSize().background(Navy).padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Icon(Icons.Default.Alarm, null, tint = Amber, modifier = Modifier.height(42.dp))
            Text("Good morning.", color = Amber, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
            Text("Wake your mind up.", color = Paper, fontSize = 28.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark)
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("QUESTION ${questionIndex + 1} OF 3", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    Text(questions[questionIndex].prompt, color = Paper, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
                    OutlinedTextField(
                        value = answerText,
                        onValueChange = { answerText = it.filter(Char::isDigit).take(4); feedback = "" },
                        label = { Text("Your answer") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (feedback.isNotBlank()) Text(feedback, color = if (feedback == "Correct. Keep going.") Amber else Color(0xFFFCA5A5), textAlign = TextAlign.Center)
                    Button(
                        onClick = {
                            val supplied = answerText.toIntOrNull()
                            if (supplied == questions[questionIndex].answer) {
                                if (questionIndex == 2) onDismissAlarm()
                                else {
                                    questionIndex++
                                    answerText = ""
                                    feedback = "Correct. Keep going."
                                }
                            } else feedback = "Not quite. Try again."
                        },
                        enabled = answerText.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Navy),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text(if (questionIndex == 2) "Finish and dismiss" else "Check answer", fontWeight = FontWeight.Bold) }
                }
            }
            if (emergencyCount < 0) {
                androidx.compose.material3.TextButton(onClick = { emergencyCount = 10 }) {
                    Text("Emergency stop · 10-second safety confirmation", color = Muted, fontSize = 11.sp, textAlign = TextAlign.Center)
                }
            } else {
                Text(
                    if (emergencyCount > 0) "Emergency stop unlocks in $emergencyCount seconds" else "Emergency stop is available",
                    color = Muted, fontSize = 11.sp, textAlign = TextAlign.Center
                )
                if (emergencyCount == 0) {
                    Button(
                        onClick = onDismissAlarm,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7F1D1D), contentColor = Paper),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Stop alarm (emergency)") }
                }
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}

private fun makeQuestions(): List<MathQuestion> = List(3) { index ->
    when (index % 3) {
        0 -> {
            val a = Random.nextInt(12, 48)
            val b = Random.nextInt(7, 36)
            MathQuestion("$a + $b = ?", a + b)
        }
        1 -> {
            val a = Random.nextInt(30, 90)
            val b = Random.nextInt(5, a)
            MathQuestion("$a − $b = ?", a - b)
        }
        else -> {
            val a = Random.nextInt(3, 13)
            val b = Random.nextInt(3, 10)
            MathQuestion("$a × $b = ?", a * b)
        }
    }
}
