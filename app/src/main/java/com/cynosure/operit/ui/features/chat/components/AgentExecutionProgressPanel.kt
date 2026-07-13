package com.cynosure.operit.ui.features.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.cynosure.operit.R
import com.cynosure.operit.core.agent.AgentExecutionSnapshot
import com.cynosure.operit.core.agent.AgentExecutionSource
import com.cynosure.operit.core.agent.AgentExecutionState

@Composable
fun AgentExecutionProgressPanel(
    snapshot: AgentExecutionSnapshot,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember(snapshot.taskId) { mutableStateOf(false) }
    val canPause = snapshot.source == AgentExecutionSource.GROUP &&
        snapshot.state in pausableStates
    val canResume = snapshot.source == AgentExecutionSource.GROUP &&
        snapshot.state == AgentExecutionState.PAUSED
    val canCancel = snapshot.state !in terminalStates

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (snapshot.state !in terminalStates && snapshot.state != AgentExecutionState.PAUSED) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(17.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(9.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = snapshot.title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = buildStatusText(snapshot),
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor(snapshot.state),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (canPause) {
                    IconButton(onClick = onPause, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Filled.Pause, contentDescription = stringResource(R.string.agent_task_pause))
                    }
                }
                if (canResume) {
                    IconButton(onClick = onResume, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.agent_task_resume))
                    }
                }
                if (canCancel) {
                    IconButton(onClick = onCancel, modifier = Modifier.size(34.dp)) {
                        Icon(
                            Icons.Filled.Cancel,
                            contentDescription = stringResource(R.string.agent_task_cancel),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = stringResource(R.string.agent_task_details)
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    snapshot.detail?.takeIf { it.isNotBlank() }?.let { detail ->
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    snapshot.steps.takeLast(6).forEach { step ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stateMarker(step.state),
                                color = statusColor(step.state),
                                style = MaterialTheme.typography.labelMedium
                            )
                            Spacer(Modifier.width(7.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = step.title,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                step.owner?.let { owner ->
                                    Text(
                                        text = owner,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(1.dp))
                }
            }
        }
    }
}

@Composable
private fun buildStatusText(snapshot: AgentExecutionSnapshot): String {
    val state = when (snapshot.state) {
        AgentExecutionState.PLANNING -> stringResource(R.string.agent_task_planning)
        AgentExecutionState.RUNNING -> stringResource(R.string.agent_task_running)
        AgentExecutionState.WAITING_USER -> stringResource(R.string.agent_task_waiting_user)
        AgentExecutionState.VERIFYING -> stringResource(R.string.agent_task_verifying)
        AgentExecutionState.CORRECTING -> stringResource(R.string.agent_task_correcting)
        AgentExecutionState.RETRYING -> stringResource(R.string.agent_task_retrying)
        AgentExecutionState.PAUSED -> stringResource(R.string.agent_task_paused)
        AgentExecutionState.COMPLETED -> stringResource(R.string.agent_task_completed)
        AgentExecutionState.FAILED -> stringResource(R.string.agent_task_failed)
        AgentExecutionState.CANCELLED -> stringResource(R.string.agent_task_cancelled)
    }
    return snapshot.owner?.let { stringResource(R.string.agent_task_state_owner, state, it) } ?: state
}

@Composable
private fun statusColor(state: AgentExecutionState) = when (state) {
    AgentExecutionState.COMPLETED -> MaterialTheme.colorScheme.primary
    AgentExecutionState.FAILED, AgentExecutionState.CANCELLED -> MaterialTheme.colorScheme.error
    AgentExecutionState.PAUSED, AgentExecutionState.WAITING_USER -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.secondary
}

private fun stateMarker(state: AgentExecutionState): String = when (state) {
    AgentExecutionState.COMPLETED -> "OK"
    AgentExecutionState.FAILED, AgentExecutionState.CANCELLED -> "X"
    AgentExecutionState.PAUSED -> "II"
    else -> "-"
}

private val terminalStates = setOf(
    AgentExecutionState.COMPLETED,
    AgentExecutionState.FAILED,
    AgentExecutionState.CANCELLED
)

private val pausableStates = setOf(
    AgentExecutionState.PLANNING,
    AgentExecutionState.RUNNING,
    AgentExecutionState.WAITING_USER,
    AgentExecutionState.VERIFYING,
    AgentExecutionState.CORRECTING,
    AgentExecutionState.RETRYING
)
