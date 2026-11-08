package com.example.whatcanicook

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.whatcanicook.lidl.LidlLoginActivity
import com.example.whatcanicook.scan.ScannerActivity
import com.example.whatcanicook.ui.AddItemScreen
import com.example.whatcanicook.ui.KitchenScreen
import com.example.whatcanicook.ui.KitchenViewModel
import com.example.whatcanicook.ui.ReceiptPickerScreen
import com.example.whatcanicook.ui.theme.WhatCanICookTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WhatCanICookTheme {
                val vm: KitchenViewModel = viewModel()
                val context = LocalContext.current

                val loginLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult(),
                ) { result ->
                    vm.onLoginResult(result.resultCode == Activity.RESULT_OK)
                }

                // The scanner runs a whole session of its own and writes items
                // as it goes, so all that comes back is how many it added.
                val scanLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult(),
                ) { result ->
                    val added = result.data
                        ?.getIntExtra(ScannerActivity.EXTRA_ADDED_COUNT, 0) ?: 0
                    vm.onScanSessionFinished(added)
                }

                val pending = vm.pending
                when {
                    pending != null -> {
                        BackHandler { vm.cancelPending() }
                        AddItemScreen(
                            pending = pending,
                            onNameChange = vm::updatePendingName,
                            onSave = vm::savePending,
                            onCancel = vm::cancelPending,
                        )
                    }

                    vm.pickerVisible -> {
                        BackHandler { vm.closePicker() }
                        ReceiptPickerScreen(
                            options = vm.receiptOptions,
                            isBusy = vm.isBusy,
                            onToggle = vm::toggleReceipt,
                            onSelectAll = vm::selectAll,
                            onImport = vm::importSelected,
                            onCancel = vm::closePicker,
                        )
                    }

                    else -> KitchenScreen(
                        snapshot = vm.snapshot,
                        status = vm.status,
                        isSyncing = vm.isBusy || vm.isLookingUp,
                        isConnectedToLidl = vm.isConnectedToLidl,
                        onConnectLidl = {
                            loginLauncher.launch(LidlLoginActivity.intent(context))
                        },
                        onChooseShops = { vm.openReceiptPicker() },
                        onScan = { scanLauncher.launch(ScannerActivity.intent(context)) },
                        onAddManually = vm::startManualAdd,
                        onAskAi = { target -> vm.askAssistant(context, target) },
                        onImportSample = vm::importSampleShop,
                        onClear = vm::clearKitchen,
                        onEaten = vm::markEaten,
                        onBinned = vm::throwAway,
                    )
                }
            }
        }
    }
}
