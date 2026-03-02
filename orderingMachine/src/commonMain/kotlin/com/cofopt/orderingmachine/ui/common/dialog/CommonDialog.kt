package com.cofopt.orderingmachine.ui.common.dialog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun CommonAlertDialog(
    onDismissRequest: () -> Unit,
    onConfirm: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    title: String,
    text: String,
    confirmText: String = "Yes",
    dismissText: String = "Cancel",
    dismissOnBackPress: Boolean = true,
    dismissOnClickOutside: Boolean = true
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = dismissOnBackPress,
            dismissOnClickOutside = dismissOnClickOutside
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.98f)
                .padding(20.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Title
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )
                
                // Text
                Text(
                    text = text,
                    style = MaterialTheme.typography.headlineMedium
                )
                
                // Buttons - Horizontal layout
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Dismiss button
                    if (onDismiss != null) {
                        Button(
                            onClick = {
                                onDismiss()
                                onDismissRequest()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(80.dp)
                        ) {
                            Text(
                                text = dismissText,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    
                    // Confirm button
                    Button(
                        onClick = {
                            onConfirm?.invoke()
                            onDismissRequest()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(80.dp)
                    ) {
                        Text(
                            text = confirmText,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CommonConfirmDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    title: String,
    text: String,
    confirmText: String = "Yes",
    dismissText: String = "Cancel"
) {
    CommonAlertDialog(
        onDismissRequest = onDismissRequest,
        onConfirm = onConfirm,
        onDismiss = null,
        title = title,
        text = text,
        confirmText = confirmText,
        dismissText = dismissText
    )
}

@Composable
fun CommonChoiceDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    title: String,
    text: String,
    confirmText: String = "Yes",
    dismissText: String = "Cancel"
) {
    CommonAlertDialog(
        onDismissRequest = onDismissRequest,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        title = title,
        text = text,
        confirmText = confirmText,
        dismissText = dismissText
    )
}
