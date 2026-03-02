package com.cofopt.orderingmachine.ui.DebugScreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class DebugTabPage(
    val title: String,
    val content: @Composable () -> Unit
)

@Composable
fun DebugScreenScaffold(
    onBack: () -> Unit,
    tabs: List<DebugTabPage>,
    onExitApp: (() -> Unit)? = null,
    title: String = "Debug"
) {
    var selectedTabIndex by remember(tabs.size) { mutableIntStateOf(0) }
    if (selectedTabIndex > tabs.lastIndex) {
        selectedTabIndex = 0
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )
            Row {
                if (onExitApp != null) {
                    Button(onClick = onExitApp) {
                        Text("Exit App")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Button(onClick = onBack) {
                    Text("Return")
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        if (tabs.isNotEmpty()) {
            TabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(tab.title) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            tabs[selectedTabIndex].content()
        }
    }
}
