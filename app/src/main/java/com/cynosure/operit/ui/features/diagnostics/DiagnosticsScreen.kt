package com.cynosure.operit.ui.features.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cynosure.operit.R
import com.cynosure.operit.core.diagnostics.DiagnosticsRegistry
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

@Composable
fun DiagnosticsScreen() {
    val context = LocalContext.current
    val snapshot by DiagnosticsRegistry.snapshot.collectAsState()

    LaunchedEffect(Unit) {
        DiagnosticsRegistry.initialize(context.applicationContext)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(stringResource(R.string.diagnostics_active_tasks), snapshot.activeExecutions.values.sum().toString(), Modifier.weight(1f))
                MetricCard(stringResource(R.string.diagnostics_skills), snapshot.skillNames.size.toString(), Modifier.weight(1f))
                MetricCard(stringResource(R.string.diagnostics_tool_calls), snapshot.toolStats.sumOf { it.callCount }.toString(), Modifier.weight(1f))
            }
        }

        item { SectionTitle(stringResource(R.string.diagnostics_running_tools)) }
        if (snapshot.activeExecutions.isEmpty()) {
            item { EmptyCard(stringResource(R.string.diagnostics_no_active_tasks)) }
        } else {
            items(snapshot.activeExecutions.toList(), key = { it.first }) { (name, count) ->
                DetailCard(name, stringResource(R.string.diagnostics_running_count, count))
            }
        }

        item { SectionTitle(stringResource(R.string.diagnostics_tool_failures)) }
        if (snapshot.toolStats.isEmpty()) {
            item { EmptyCard(stringResource(R.string.diagnostics_no_tool_history)) }
        } else {
            items(snapshot.toolStats.take(10), key = { it.toolName }) { stats ->
                DetailCard(
                    stats.toolName,
                    stringResource(
                        R.string.diagnostics_tool_stats,
                        stats.callCount,
                        (stats.failureRate * 100).roundToInt(),
                        stats.averageDurationMs
                    )
                )
            }
        }

        item { SectionTitle(stringResource(R.string.diagnostics_builtin_skills)) }
        if (snapshot.skillNames.isEmpty()) {
            item { EmptyCard(stringResource(R.string.diagnostics_no_skills)) }
        } else {
            items(snapshot.skillNames, key = { it }) { name -> DetailCard(name, stringResource(R.string.diagnostics_skill_ready)) }
        }

        item { SectionTitle(stringResource(R.string.diagnostics_validation_history)) }
        if (snapshot.validationHistory.isEmpty()) {
            item { EmptyCard(stringResource(R.string.diagnostics_no_validation_history)) }
        } else {
            itemsIndexed(snapshot.validationHistory, key = { index, validation -> "${validation.timestamp}:${validation.subject}:$index" }) { _, validation ->
                DetailCard(validation.subject, validation.summary)
            }
        }

        item { SectionTitle(stringResource(R.string.diagnostics_recent_events)) }
        if (snapshot.recentEvents.isEmpty()) {
            item { EmptyCard(stringResource(R.string.diagnostics_no_recent_events)) }
        } else {
            itemsIndexed(snapshot.recentEvents.take(30), key = { index, event -> "${event.timestamp}:${event.toolName}:$index" }) { _, event ->
                DetailCard(
                    event.toolName,
                    "${DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(event.timestamp))}  ${event.message}"
                )
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun DetailCard(title: String, detail: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyCard(message: String) {
    DetailCard(message, stringResource(R.string.diagnostics_waiting_for_data))
}
