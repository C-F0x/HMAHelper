package org.cf0x.hma.helper

import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel

// ─────────────────────────────────────────────────
//  Extra App List Screen — thin wrapper around AppManagerScreen
// ─────────────────────────────────────────────────
@Composable
fun ExtraAppListScreen(
    configPackageName: String,
    initialSelection: List<String> = emptyList(),
    onBackClick: () -> Unit,
    onConfirm: (List<String>) -> Unit,
    viewModel: AppManagerViewModel = viewModel()
) {
    // Pre-select initial packages when the screen opens
    LaunchedEffect(initialSelection) {
        viewModel.clearSelection()
        viewModel.setSelection(initialSelection)
    }

    AppManagerScreen(
        onBackClick = {
            viewModel.clearSelection()
            onBackClick()
        },
        onExtraConfirm = { selected ->
            onConfirm(selected)
        },
        viewModel = viewModel
    )
}
