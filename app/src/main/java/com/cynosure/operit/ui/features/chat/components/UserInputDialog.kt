package com.cynosure.operit.ui.features.chat.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cynosure.operit.R
import com.cynosure.operit.data.model.QuestionType
import com.cynosure.operit.services.core.UserInputRequestRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserInputDialog(repository: UserInputRequestRepository = UserInputRequestRepository) {
    val request by repository.pendingRequestFlow.collectAsState()

    val currentRequest = request ?: return

    val answers = remember(currentRequest.id) {
        mutableStateMapOf<String, String>().apply {
            currentRequest.questions
                .filter { it.type == QuestionType.BOOLEAN }
                .forEach { put(it.key, "false") }
        }
    }
    val multiSelectValues = remember(currentRequest.id) {
        mutableStateMapOf<String, Set<String>>()
    }
    var expandedDropdown by remember(currentRequest.id) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = {
            repository.cancelRequest(currentRequest.id)
        },
        shape = RoundedCornerShape(16.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = currentRequest.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { repository.cancelRequest(currentRequest.id) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.close),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (currentRequest.description.isNotBlank()) {
                    Text(
                        text = currentRequest.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                currentRequest.questions.forEach { question ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = question.label + if (question.required) " *" else "",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        when (question.type) {
                            QuestionType.TEXT -> {
                                var textValue by remember(currentRequest.id, question.key) {
                                    mutableStateOf(answers[question.key] ?: "")
                                }
                                OutlinedTextField(
                                    value = textValue,
                                    onValueChange = {
                                        textValue = it
                                        answers[question.key] = it
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = question.placeholder.takeIf { it.isNotBlank() }?.let {
                                        { Text(it, fontSize = 12.sp) }
                                    },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyMedium,
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }

                            QuestionType.NUMBER -> {
                                var numValue by remember(currentRequest.id, question.key) {
                                    mutableStateOf(answers[question.key] ?: "")
                                }
                                OutlinedTextField(
                                    value = numValue,
                                    onValueChange = {
                                        if (it.isValidPartialNumber()) {
                                            numValue = it
                                            answers[question.key] = it
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = question.placeholder.takeIf { it.isNotBlank() }?.let {
                                        { Text(it, fontSize = 12.sp) }
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    textStyle = MaterialTheme.typography.bodyMedium,
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }

                            QuestionType.SELECT -> {
                                var selected by remember(currentRequest.id, question.key) {
                                    mutableStateOf(answers[question.key] ?: "")
                                }
                                val isExpanded = expandedDropdown == question.key

                                ExposedDropdownMenuBox(
                                    expanded = isExpanded,
                                    onExpandedChange = {
                                        expandedDropdown = if (it) question.key else null
                                    }
                                ) {
                                    OutlinedTextField(
                                        value = selected,
                                        onValueChange = {},
                                        readOnly = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .menuAnchor(),
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded) },
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodyMedium,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    ExposedDropdownMenu(
                                        expanded = isExpanded,
                                        onDismissRequest = { expandedDropdown = null }
                                    ) {
                                        question.options.forEach { option ->
                                            DropdownMenuItem(
                                                text = { Text(option, fontSize = 12.sp) },
                                                onClick = {
                                                    selected = option
                                                    answers[question.key] = option
                                                    expandedDropdown = null
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            QuestionType.MULTI_SELECT -> {
                                val selectedSet = multiSelectValues.getOrPut(question.key) { emptySet() }
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    question.options.forEach { option ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Checkbox(
                                                checked = option in selectedSet,
                                                onCheckedChange = { checked ->
                                                    val updated = if (checked) {
                                                        selectedSet + option
                                                    } else {
                                                        selectedSet - option
                                                    }
                                                    multiSelectValues[question.key] = updated
                                                    answers[question.key] = updated.joinToString(",")
                                                }
                                            )
                                            Text(
                                                text = option,
                                                fontSize = 12.sp,
                                                modifier = Modifier.padding(start = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            QuestionType.BOOLEAN -> {
                                var checked by remember(currentRequest.id, question.key) {
                                    mutableStateOf(answers[question.key]?.equals("true", ignoreCase = true) == true)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(if (checked) R.string.yes else R.string.no),
                                        fontSize = 12.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Switch(
                                        checked = checked,
                                        onCheckedChange = {
                                            checked = it
                                            answers[question.key] = it.toString()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val missingRequired = currentRequest.questions
                        .filter { it.required }
                        .any { question -> !question.hasValidAnswer(answers[question.key]) }
                    if (!missingRequired) {
                        repository.respond(currentRequest.id, answers.toMap())
                    }
                },
                shape = RoundedCornerShape(8.dp),
                enabled = currentRequest.questions
                    .filter { it.required }
                    .all { question -> question.hasValidAnswer(answers[question.key]) }
            ) {
                Text(stringResource(R.string.confirm), fontSize = 13.sp)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = { repository.cancelRequest(currentRequest.id) },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(stringResource(R.string.cancel), fontSize = 13.sp)
            }
        }
    )
}

private fun String.isValidPartialNumber(): Boolean =
    isEmpty() || matches(Regex("^-?\\d*(\\.\\d*)?$"))

private fun com.cynosure.operit.data.model.UserInputQuestion.hasValidAnswer(answer: String?): Boolean {
    if (answer.isNullOrBlank()) return type == QuestionType.BOOLEAN
    return type != QuestionType.NUMBER || answer.toDoubleOrNull() != null
}
