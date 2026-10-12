package com.bandcompanion.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.bandcompanion.app.ui.theme.ElectricTealRing
import com.bandcompanion.app.ui.theme.ObsidianCard
import com.bandcompanion.app.ui.theme.ObsidianHighlight
import com.bandcompanion.app.ui.theme.RelaxedGreen
import com.bandcompanion.app.ui.theme.TextMuted
import com.bandcompanion.app.ui.theme.TextPrimary
import com.bandcompanion.app.ui.theme.TextSecondary
import com.bandcompanion.core.model.WeightEntry

@Composable
fun LogWeightDialog(
    initialWeightKg: Float = 70.0f,
    initialHeightCm: Float = 175.0f,
    onDismiss: () -> Unit,
    onSave: (weightKg: Float, heightCm: Float, note: String?) -> Unit
) {
    var weightInput by remember { mutableStateOf("%.1f".format(initialWeightKg)) }
    var heightInput by remember { mutableStateOf("%.0f".format(initialHeightCm)) }
    var noteInput by remember { mutableStateOf("") }

    val parsedWeight = weightInput.toFloatOrNull() ?: 0f
    val parsedHeight = heightInput.toFloatOrNull() ?: 175f

    val calculatedBmi = if (parsedHeight > 0f) {
        val heightM = parsedHeight / 100f
        parsedWeight / (heightM * heightM)
    } else 0f

    val previewEntry = WeightEntry(
        timestamp = System.currentTimeMillis(),
        weightKg = parsedWeight,
        userHeightCm = parsedHeight
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = ObsidianCard),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = "BODY COMPOSITION",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Log Body Weight",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Weight Input
                OutlinedTextField(
                    value = weightInput,
                    onValueChange = { weightInput = it },
                    label = { Text("Weight (kg)", color = TextMuted, fontSize = 12.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = ElectricTealRing,
                        unfocusedBorderColor = ObsidianHighlight,
                        cursorColor = ElectricTealRing
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Height Input
                OutlinedTextField(
                    value = heightInput,
                    onValueChange = { heightInput = it },
                    label = { Text("Height (cm)", color = TextMuted, fontSize = 12.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = ElectricTealRing,
                        unfocusedBorderColor = ObsidianHighlight,
                        cursorColor = ElectricTealRing
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // BMI Live Preview Card
                if (calculatedBmi > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ObsidianHighlight, RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "CALCULATED BMI",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextMuted
                            )
                            Text(
                                text = "%.1f • %s".format(calculatedBmi, previewEntry.bmiCategory),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = RelaxedGreen
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Note Input
                OutlinedTextField(
                    value = noteInput,
                    onValueChange = { noteInput = it },
                    label = { Text("Note (optional)", color = TextMuted, fontSize = 12.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = ElectricTealRing,
                        unfocusedBorderColor = ObsidianHighlight,
                        cursorColor = ElectricTealRing
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = TextSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (parsedWeight > 0f) {
                                onSave(parsedWeight, parsedHeight, noteInput.ifBlank { null })
                                onDismiss()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricTealRing),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Save Weight", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
