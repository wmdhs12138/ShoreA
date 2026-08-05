package com.wmdhs.shorea

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { ShoreA() }
    }
}

@Composable
private fun ShoreA() {
    ShoreATheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            HardnessManualHome()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HardnessManualHome() {
    val appContext = LocalContext.current.applicationContext
    val store = remember(appContext) { HardnessManualStore(appContext) }
    val manualState = remember { mutableStateOf(HardnessManual()) }
    var manual by manualState
    var hasLoaded by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchActive by rememberSaveable { mutableStateOf(false) }
    var sortOrderName by rememberSaveable { mutableStateOf(ManualSortOrder.COMPOUND_CODE.name) }
    var viewModeName by rememberSaveable { mutableStateOf(ManualViewMode.LIST.name) }
    val sortOrder = ManualSortOrder.entries.firstOrNull { it.name == sortOrderName }
        ?: ManualSortOrder.COMPOUND_CODE
    val viewMode = ManualViewMode.entries.firstOrNull { it.name == viewModeName }
        ?: ManualViewMode.LIST
    var selectedCompoundId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedEntryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var compoundEditorVisible by rememberSaveable { mutableStateOf(false) }
    var compoundEditorId by rememberSaveable { mutableStateOf<Long?>(null) }
    var inspectionEditorVisible by rememberSaveable { mutableStateOf(false) }
    var inspectionEditorCompoundId by rememberSaveable { mutableStateOf<Long?>(null) }
    var inspectionEditorEntryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deleteInspectionVisible by rememberSaveable { mutableStateOf(false) }
    var deleteInspectionEntryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deleteCompoundVisible by rememberSaveable { mutableStateOf(false) }
    var deleteCompoundId by rememberSaveable { mutableStateOf<Long?>(null) }
    var backupActionsVisible by rememberSaveable { mutableStateOf(false) }
    var fileOperation by remember { mutableStateOf<ManualFileOperation?>(null) }
    var filePickerActive by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<ManualBackup?>(null) }
    var pendingSpreadsheetImport by remember {
        mutableStateOf<SpreadsheetImport?>(null)
    }
    var persistRevision by remember { mutableLongStateOf(0L) }
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var restoreFocusKey by remember { mutableStateOf<String?>(null) }
    var revealedItemKey by remember { mutableStateOf<String?>(null) }

    fun showMessage(message: String) {
        coroutineScope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    fun persist(updatedManual: HardnessManual) {
        val previousManual = manual
        val revision = persistRevision + 1L
        persistRevision = revision
        manual = updatedManual

        coroutineScope.launch {
            runCatching { store.saveManual(updatedManual) }
                .onFailure { error ->
                    if (persistRevision == revision) {
                        manual = previousManual
                    }
                    showMessage(
                        "保存失败，已恢复保存前状态：${error.message ?: "无法写入本地资料"}",
                    )
                }
        }
    }

    fun updateCompound(updatedCompound: RubberCompound) {
        if (manual.findCompound(updatedCompound.id) == null) return
        persist(
            manual.copy(
                compounds = manual.compounds.map { compound ->
                    if (compound.id == updatedCompound.id) updatedCompound else compound
                },
            ),
        )
    }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        filePickerActive = false
        if (uri != null) {
            fileOperation = ManualFileOperation.EXPORT_JSON
            coroutineScope.launch {
                try {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        appContext.contentResolver.openOutputStream(uri, "wt")
                            ?.bufferedWriter()
                            ?.use { it.write(encodeManualBackup(manual)) }
                            ?: error("无法写入所选文件")
                    }
                }
                showMessage(
                    if (result.isSuccess) {
                        "备份已导出"
                    } else {
                        "导出失败：${result.exceptionOrNull()?.message ?: "未知错误"}"
                    },
                )
                } finally {
                    fileOperation = null
                    backupActionsVisible = false
                }
            }
        }
    }

    val exportSpreadsheetLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        ),
    ) { uri ->
        filePickerActive = false
        if (uri != null) {
            fileOperation = ManualFileOperation.EXPORT_EXCEL
            coroutineScope.launch {
                try {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        appContext.contentResolver.openOutputStream(uri)?.use { output ->
                            encodeManualSpreadsheet(manual, output)
                        } ?: error("无法写入所选文件")
                    }
                }
                showMessage(
                    if (result.isSuccess) "Excel 已导出" else {
                        "导出失败：${result.exceptionOrNull()?.message ?: "未知错误"}"
                    },
                )
                } finally {
                    fileOperation = null
                    backupActionsVisible = false
                }
            }
        }
    }

    val importSpreadsheetLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        filePickerActive = false
        if (uri != null) {
            fileOperation = ManualFileOperation.IMPORT_EXCEL
            coroutineScope.launch {
                try {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        appContext.contentResolver.openInputStream(uri)?.use { input ->
                            decodeManualSpreadsheet(input).getOrThrow()
                        } ?: error("无法读取所选文件")
                    }
                }
                result.onSuccess { spreadsheet ->
                    backupActionsVisible = false
                    pendingSpreadsheetImport = spreadsheet
                }.onFailure { error ->
                    showMessage("Excel 导入失败：${error.message ?: "文件格式错误"}")
                }
                } finally {
                    fileOperation = null

                }
            }
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        filePickerActive = false
        if (uri != null) {
            fileOperation = ManualFileOperation.IMPORT_JSON
            coroutineScope.launch {
                try {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val rawValue = appContext.contentResolver
                            .openInputStream(uri)
                            ?.bufferedReader()
                            ?.use { it.readText() }
                            ?: error("无法读取所选文件")
                        decodeManualBackup(rawValue).getOrThrow()
                    }
                }
                result.onSuccess { backup ->
                    backupActionsVisible = false
                    pendingImport = backup
                }.onFailure { error ->
                    showMessage("导入失败：${error.message ?: "文件格式错误"}")
                }
                } finally {
                    fileOperation = null

                }
            }
        }
    }

    LaunchedEffect(store) {
        store.state.collectLatest { state ->
            manual = state.manual
            loadError = state.loadError
            if (state.loadError != null) {
                compoundEditorVisible = false
                inspectionEditorVisible = false
                deleteInspectionVisible = false
                deleteCompoundVisible = false
            }
            selectedCompoundId = selectedCompoundId?.takeIf { selectedId ->
                state.manual.findCompound(selectedId) != null
            }
            selectedEntryId = selectedEntryId?.takeIf { selectedId ->
                val entry = state.manual.findEntry(selectedId)
                entry != null && entry.compoundId == selectedCompoundId
            }
            hasLoaded = true
        }
    }

    BackHandler(searchActive) {
        searchActive = false
        revealedItemKey = null
    }

    val normalizedQuery = searchQuery.trim()
    val allHomeItems = remember(manual) { manual.toHomeItems() }
    val visibleHomeItems = remember(allHomeItems, normalizedQuery, sortOrder) {
        sortHomeItems(
            items = allHomeItems.filter { item ->
                normalizedQuery.isEmpty() || item.matches(normalizedQuery)
            },
            order = sortOrder,
        )
    }

    LaunchedEffect(restoreFocusKey, visibleHomeItems) {
        val focusKey = restoreFocusKey ?: return@LaunchedEffect
        val restoredIndex = visibleHomeItems.indexOfFirst { it.stableKey == focusKey }
        if (restoredIndex >= 0) {
            listState.animateScrollToItem(restoredIndex)
            delay(1_200L)
            if (restoreFocusKey == focusKey) restoreFocusKey = null
        }
    }

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) revealedItemKey = null
    }
    val selectedCompound = selectedCompoundId?.let { id -> manual.findCompound(id) }
    val selectedEntries = selectedCompound?.let { compound ->
        manual.entriesForCompound(compound.id)
    }.orEmpty()

    Scaffold(
        topBar = {
            ManualTopBar(
                hasLoaded = hasLoaded,
                compoundCount = manual.compounds.size,
                entryCount = manual.inspectionEntries.size,
                actionsEnabled = hasLoaded && loadError == null,
                searchActive = searchActive,
                query = searchQuery,
                sortOrder = sortOrder,
                viewMode = viewMode,
                onSearchActiveChange = { revealedItemKey = null; searchActive = it },
                onQueryChange = { revealedItemKey = null; searchQuery = it },
                onClearQuery = { revealedItemKey = null; searchQuery = "" },
                onSortOrderChange = { revealedItemKey = null; sortOrderName = it.name },
                onViewModeChange = { revealedItemKey = null; viewModeName = it.name },
                onImportExport = { revealedItemKey = null; backupActionsVisible = true },
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            if (hasLoaded && loadError == null && manual.compounds.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = {
                        revealedItemKey = null
                        compoundEditorId = null; compoundEditorVisible = true
                    },
                ) {
                    Text("＋ 添加胶料")
                }
            }
        },
    ) { innerPadding ->
        when {
            !hasLoaded -> ManualLoadingState(innerPadding)
            loadError != null -> ManualDataErrorState(
                innerPadding = innerPadding,
                detail = loadError.orEmpty(),
                onRestoreBackup = { backupActionsVisible = true },
            )
            manual.compounds.isEmpty() -> EmptyManualState(
                innerPadding = innerPadding,
                onAddCompound = { compoundEditorId = null; compoundEditorVisible = true },
            )
            visibleHomeItems.isEmpty() -> EmptyManualSearchState(
                innerPadding = innerPadding,
                query = normalizedQuery,
                onClearSearch = { searchQuery = "" },
            )
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
                    .padding(innerPadding)
                    .pointerInput(revealedItemKey) {
                        if (revealedItemKey != null) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                do {
                                    val event = awaitPointerEvent(PointerEventPass.Final)
                                } while (event.changes.any { it.pressed })
                                revealedItemKey = null
                            }
                        }
                    },
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = 12.dp,
                    end = 16.dp,
                    bottom = 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(
                    items = visibleHomeItems,
                    key = { it.stableKey },
                ) { item ->
                    SwipeManualHomeItem(
                        item = item,
                        highlighted = restoreFocusKey == item.stableKey,
                        revealed = revealedItemKey == item.stableKey,
                        modifier = Modifier.animateItem(
                            fadeInSpec = spring(),
                            placementSpec = spring(),
                            fadeOutSpec = spring(),
                        ),
                        searchQuery = normalizedQuery,
                        viewMode = viewMode,
                        onOpen = {
                            when (item) {
                                is ManualHomeItem.Inspection -> {
                                    selectedCompoundId = item.compound.id
                                    selectedEntryId = item.entry.id
                                }
                                is ManualHomeItem.EmptyCompound -> {
                                    selectedCompoundId = item.compound.id
                                    selectedEntryId = null
                                }
                            }
                        },
                        onReveal = { revealedItemKey = item.stableKey },
                        onInteractionOutsideDelete = { revealedItemKey = null },
                        onDeleteInspection = { entry ->
                            revealedItemKey = null
                            deleteInspectionEntryId = entry.id; deleteInspectionVisible = true
                        },
                        onDeleteEmptyCompound = { compound ->
                            revealedItemKey = null
                            deleteCompoundId = compound.id; deleteCompoundVisible = true
                        },
                    )
                }
            }
        }
    }

    if (
        selectedCompound != null &&
        !compoundEditorVisible &&
        !inspectionEditorVisible &&
        !deleteInspectionVisible &&
        !deleteCompoundVisible
    ) {
        CompoundDetailSheet(
            compound = selectedCompound,
            entries = selectedEntries,
            highlightedEntryId = selectedEntryId,
            onEditCompound = {
                compoundEditorId = selectedCompound.id; compoundEditorVisible = true
            },
            onAddInspection = {
                inspectionEditorCompoundId = selectedCompound.id; inspectionEditorEntryId = null; inspectionEditorVisible = true
            },
            onEditInspection = { entry ->
                inspectionEditorCompoundId = selectedCompound.id; inspectionEditorEntryId = entry.id; inspectionEditorVisible = true
            },
            onDeleteInspection = { entry ->
                deleteInspectionEntryId = entry.id; deleteInspectionVisible = true
            },
            onDeleteCompound = {
                deleteCompoundId = selectedCompound.id; deleteCompoundVisible = true
            },
            onDismiss = {
                selectedCompoundId = null
                selectedEntryId = null
            },
        )
    }

    if (compoundEditorVisible && hasLoaded && loadError == null) {
        val existing = compoundEditorId?.let(manual::findCompound)
        if (compoundEditorId != null && existing == null) {
            compoundEditorVisible = false
        } else {
            CompoundEditorDialog(
                initial = existing,
                compounds = manual.compounds,
                onDismiss = { compoundEditorVisible = false },
                onSave = { form ->
                    val updatedCompound = if (existing == null) RubberCompound(
                        id = nextEntityId(manual.compounds.map(RubberCompound::id)), compoundCode = form.compoundCode.trim(),
                        testPieceCureTemperatureC = form.testPieceCureTemperatureC, testPieceCureTimeMinutes = form.testPieceCureTimeMinutes,
                        customBlockCureTimeMinutes = form.customBlockCureTimeMinutes, notes = form.notes,
                    ) else existing.copy(compoundCode = form.compoundCode.trim(), testPieceCureTemperatureC = form.testPieceCureTemperatureC,
                        testPieceCureTimeMinutes = form.testPieceCureTimeMinutes, customBlockCureTimeMinutes = form.customBlockCureTimeMinutes, notes = form.notes)
                    if (existing == null) { persist(manual.copy(compounds = manual.compounds + updatedCompound)); selectedCompoundId = updatedCompound.id }
                    else updateCompound(updatedCompound)
                    compoundEditorVisible = false
                },
            )
        }
    }

    if (inspectionEditorVisible && hasLoaded && loadError == null) {
        val compound = inspectionEditorCompoundId?.let(manual::findCompound)
        val existing = inspectionEditorEntryId?.let(manual::findEntry)
        if (compound == null || (inspectionEditorEntryId != null && (existing == null || existing.compoundId != compound.id))) {
            inspectionEditorVisible = false
        } else {
            InspectionEditorDialog(
                compound = compound, entries = manual.entriesForCompound(compound.id), initial = existing,
                onDismiss = { inspectionEditorVisible = false },
                onSave = { form ->
                    val normalizedParts = normalizePartNumbers(form.partNumbers)
                    val entry = if (existing == null) InspectionEntry(
                        id = nextEntityId(manual.inspectionEntries.map(InspectionEntry::id)), compoundId = compound.id,
                        standardNumber = form.standardNumber.trim(), partNumbers = normalizedParts, hardness = form.hardness.normalized(),
                        productCategory = form.productCategory.trim(), color = form.color.trim(), tensileStrength = form.tensileStrength.trim(),
                        elongation = form.elongation.trim(), notes = form.notes.trim(),
                    ) else existing.copy(standardNumber = form.standardNumber.trim(), partNumbers = normalizedParts, hardness = form.hardness.normalized(),
                        productCategory = form.productCategory.trim(), color = form.color.trim(), tensileStrength = form.tensileStrength.trim(),
                        elongation = form.elongation.trim(), notes = form.notes.trim())
                    val entries = if (existing == null) manual.inspectionEntries + entry else manual.inspectionEntries.map { if (it.id == entry.id) entry else it }
                    persist(manual.copy(inspectionEntries = entries)); selectedCompoundId = compound.id; selectedEntryId = entry.id; inspectionEditorVisible = false
                },
            )
        }
    }

    if (deleteInspectionVisible && hasLoaded && loadError == null) {
        val entry = deleteInspectionEntryId?.let(manual::findEntry)
        if (entry == null) deleteInspectionVisible = false else DeleteInspectionDialog(
            entry = entry, onDismiss = { deleteInspectionVisible = false }, onConfirm = {
                deleteInspectionVisible = false
                if (manualState.value.findEntry(entry.id) != null) {
                    if (selectedEntryId == entry.id) selectedEntryId = null
                    deleteInspectionWithUndo(entry, { manualState.value }, ::persist, ::showMessage, snackbarHostState, coroutineScope) { restoreFocusKey = it }
                }
            },
        )
    }

    if (deleteCompoundVisible && hasLoaded && loadError == null) {
        val compound = deleteCompoundId?.let(manual::findCompound)
        if (compound == null) deleteCompoundVisible = false else DeleteCompoundDialog(
            compound = compound, entryCount = manual.entryCountForCompound(compound.id), partCount = manual.partCountForCompound(compound.id),
            onDismiss = { deleteCompoundVisible = false }, onConfirm = {
                selectedCompoundId = null; selectedEntryId = null; compoundEditorVisible = false; inspectionEditorVisible = false
                deleteInspectionVisible = false; deleteCompoundVisible = false
                deleteCompoundWithUndo(compound, { manualState.value }, ::persist, ::showMessage, snackbarHostState, coroutineScope) { restoreFocusKey = it }
            },
        )
    }

    if (backupActionsVisible) {
        ManualBackupActionsDialog(
            manual = manual,
            operation = fileOperation,
            pickerActive = filePickerActive,
            onExportBackup = {
                if (fileOperation == null && !filePickerActive) { filePickerActive = true; exportBackupLauncher.launch(manualBackupFileName()) }
            },
            onImportBackup = {
                if (fileOperation == null && !filePickerActive) {
                    filePickerActive = true
                    importBackupLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                }
            },
            onExportSpreadsheet = {
                if (fileOperation == null && !filePickerActive) { filePickerActive = true; exportSpreadsheetLauncher.launch(manualSpreadsheetFileName()) }
            },
            onImportSpreadsheet = {
                if (fileOperation == null && !filePickerActive) {
                    filePickerActive = true
                    importSpreadsheetLauncher.launch(arrayOf(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "application/octet-stream",
                    ))
                }
            },
            onDismiss = { backupActionsVisible = false },
        )
    }

    pendingImport?.let { backup ->
        ManualImportPreviewDialog(
            backup = backup,
            onMerge = {
                runCatching { mergeManualBackup(manual, backup.manual) }
                    .onSuccess { mergedManual ->
                        persist(mergedManual)
                        pendingImport = null
                        showMessage("备份已合并")
                    }
                    .onFailure { error ->
                        showMessage("备份合并失败：${error.message ?: "资料存在冲突"}")
                    }
            },
            onReplace = {
                persist(backup.manual)
                selectedCompoundId = null
                selectedEntryId = null
                pendingImport = null
                showMessage("当前手册已由备份覆盖")
            },
            onDismiss = { pendingImport = null },
        )
    }

    pendingSpreadsheetImport?.let { spreadsheet ->
        ManualSpreadsheetImportPreviewDialog(
            spreadsheet = spreadsheet,
            onMerge = {
                runCatching { mergeManualBackup(manual, spreadsheet.manual) }
                    .onSuccess { mergedManual ->
                        persist(mergedManual)
                        pendingSpreadsheetImport = null
                        showMessage("Excel 资料已合并")
                    }
                    .onFailure { error ->
                        showMessage("Excel 合并失败：${error.message ?: "资料存在冲突"}")
                    }
            },
            onReplace = {
                persist(spreadsheet.manual)
                selectedCompoundId = null
                selectedEntryId = null
                pendingSpreadsheetImport = null
                showMessage("当前手册已由 Excel 覆盖")
            },
            onDismiss = { pendingSpreadsheetImport = null },
        )
    }
}

private fun deleteInspectionWithUndo(
    entry: InspectionEntry,
    currentManual: () -> HardnessManual,
    persist: (HardnessManual) -> Unit,
    showMessage: (String) -> Unit,
    snackbarHostState: SnackbarHostState,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    onRestored: (String) -> Unit,
) {
    val manual = currentManual()
    val index = manual.inspectionEntries.indexOfFirst { it.id == entry.id }
    if (index < 0) return
    persist(manual.deleteEntry(entry.id))
    val title = entry.standardNumber.ifBlank {
        entry.partNumbers.joinToString("、")
    }
    coroutineScope.launch {
        snackbarHostState.currentSnackbarData?.dismiss()
        val result = snackbarHostState.showSnackbar(
            message = "已删除标准“$title”",
            actionLabel = "撤回",
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) {
            val current = currentManual()
            if (
                current.findEntry(entry.id) == null &&
                current.findCompound(entry.compoundId) != null
            ) {
                val restored = current.inspectionEntries.toMutableList()
                restored.add(index.coerceIn(0, restored.size), entry)
                persist(current.copy(inspectionEntries = restored))
                onRestored("inspection:${entry.id}")
                showMessage("已撤回删除")
            } else if (current.findCompound(entry.compoundId) == null) {
                showMessage("所属胶料已删除，无法恢复该检测标准")
            }
        }
    }
}

private fun deleteCompoundWithUndo(
    compound: RubberCompound,
    currentManual: () -> HardnessManual,
    persist: (HardnessManual) -> Unit,
    showMessage: (String) -> Unit,
    snackbarHostState: SnackbarHostState,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    onRestored: (String) -> Unit,
) {
    val manual = currentManual()
    val compoundIndex = manual.compounds.indexOfFirst { it.id == compound.id }
    if (compoundIndex < 0) return
    val deletedEntries = manual.inspectionEntries.mapIndexedNotNull { index, entry ->
        if (entry.compoundId == compound.id) index to entry else null
    }
    persist(manual.deleteCompound(compound.id))
    coroutineScope.launch {
        snackbarHostState.currentSnackbarData?.dismiss()
        val result = snackbarHostState.showSnackbar(
            message = "已删除胶料“${compound.compoundCode}”",
            actionLabel = "撤回",
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) {
            val current = currentManual()
            if (current.findCompound(compound.id) == null) {
                val compounds = current.compounds.toMutableList()
                compounds.add(
                    compoundIndex.coerceIn(0, compounds.size),
                    compound,
                )
                val entries = current.inspectionEntries.toMutableList()
                deletedEntries.forEach { (index, entry) ->
                    if (entries.none { it.id == entry.id }) {
                        entries.add(index.coerceIn(0, entries.size), entry)
                    }
                }
                persist(
                    current.copy(
                        compounds = compounds,
                        inspectionEntries = entries,
                    ),
                )
                val restoredKey = deletedEntries.firstOrNull()?.second?.id
                    ?.let { "inspection:$it" }
                    ?: "empty-compound:${compound.id}"
                onRestored(restoredKey)
                showMessage("已撤回删除")
            }
        }
    }
}
