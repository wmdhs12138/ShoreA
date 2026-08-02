package com.wmdhs.shorea

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
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
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            HardnessManualHome()
        }
    }
}

private data class CompoundEditorRequest(
    val existing: RubberCompound?,
)

private data class InspectionEditorRequest(
    val compoundId: Long,
    val existing: InspectionEntry?,
)

private data class DeleteInspectionRequest(
    val compoundId: Long,
    val entry: InspectionEntry,
)

private data class DeleteCompoundRequest(
    val compound: RubberCompound,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HardnessManualHome() {
    val appContext = LocalContext.current.applicationContext
    val store = remember(appContext) { HardnessManualStore(appContext) }
    var manual by remember { mutableStateOf(HardnessManual()) }
    var hasLoaded by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var searchActive by remember { mutableStateOf(false) }
    var sortOrder by remember { mutableStateOf(ManualSortOrder.COMPOUND_CODE) }
    var viewMode by remember { mutableStateOf(ManualViewMode.LIST) }
    var selectedCompoundId by remember { mutableStateOf<Long?>(null) }
    var selectedEntryId by remember { mutableStateOf<Long?>(null) }
    var compoundEditorRequest by remember {
        mutableStateOf<CompoundEditorRequest?>(null)
    }
    var inspectionEditorRequest by remember {
        mutableStateOf<InspectionEditorRequest?>(null)
    }
    var deleteInspectionRequest by remember {
        mutableStateOf<DeleteInspectionRequest?>(null)
    }
    var deleteCompoundRequest by remember {
        mutableStateOf<DeleteCompoundRequest?>(null)
    }
    var backupActionsVisible by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<ManualBackup?>(null) }
    var pendingSpreadsheetImport by remember {
        mutableStateOf<SpreadsheetImport?>(null)
    }
    var persistRevision by remember { mutableLongStateOf(0L) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val latestManual = rememberUpdatedState(manual)

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
        if (uri != null) {
            coroutineScope.launch {
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
            }
        }
    }

    val exportSpreadsheetLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        ),
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
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
            }
        }
    }

    val importSpreadsheetLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
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
            }
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
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
            }
        }
    }

    LaunchedEffect(store) {
        store.state.collectLatest { state ->
            manual = state.manual
            loadError = state.loadError
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
    val selectedCompound = selectedCompoundId?.let { id -> manual.findCompound(id) }
    val selectedEntries = selectedCompound?.let { compound ->
        manual.entriesForCompound(compound.id)
    }.orEmpty()

    Scaffold(
        topBar = {
            ManualTopBar(
                hasLoaded = hasLoaded,
                actionsEnabled = hasLoaded && loadError == null,
                searchActive = searchActive,
                query = searchQuery,
                sortOrder = sortOrder,
                viewMode = viewMode,
                onSearchActiveChange = { searchActive = it },
                onQueryChange = { searchQuery = it },
                onClearQuery = { searchQuery = "" },
                onSortOrderChange = { sortOrder = it },
                onViewModeChange = { viewMode = it },
                onImportExport = { backupActionsVisible = true },
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            if (hasLoaded && loadError == null) {
                ExtendedFloatingActionButton(
                    onClick = {
                        compoundEditorRequest = CompoundEditorRequest(existing = null)
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
            manual.compounds.isEmpty() -> EmptyManualState(innerPadding)
            visibleHomeItems.isEmpty() -> EmptyManualSearchState(
                innerPadding = innerPadding,
                query = normalizedQuery,
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
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
                        onDeleteInspection = { entry ->
                            deleteInspectionWithUndo(
                                entry = entry,
                                currentManual = { latestManual.value },
                                persist = ::persist,
                                showMessage = ::showMessage,
                                snackbarHostState = snackbarHostState,
                                coroutineScope = coroutineScope,
                            )
                        },
                        onDeleteEmptyCompound = { compound ->
                            deleteEmptyCompoundWithUndo(
                                compound = compound,
                                currentManual = { latestManual.value },
                                persist = ::persist,
                                showMessage = ::showMessage,
                                snackbarHostState = snackbarHostState,
                                coroutineScope = coroutineScope,
                            )
                        },
                    )
                }
            }
        }
    }

    if (
        selectedCompound != null &&
        compoundEditorRequest == null &&
        inspectionEditorRequest == null &&
        deleteInspectionRequest == null &&
        deleteCompoundRequest == null
    ) {
        CompoundDetailSheet(
            compound = selectedCompound,
            entries = selectedEntries,
            highlightedEntryId = selectedEntryId,
            onEditCompound = {
                compoundEditorRequest = CompoundEditorRequest(selectedCompound)
            },
            onAddInspection = {
                inspectionEditorRequest = InspectionEditorRequest(
                    compoundId = selectedCompound.id,
                    existing = null,
                )
            },
            onEditInspection = { entry ->
                inspectionEditorRequest = InspectionEditorRequest(
                    compoundId = selectedCompound.id,
                    existing = entry,
                )
            },
            onDeleteInspection = { entry ->
                deleteInspectionRequest = DeleteInspectionRequest(
                    compoundId = selectedCompound.id,
                    entry = entry,
                )
            },
            onDeleteCompound = {
                deleteCompoundRequest = DeleteCompoundRequest(selectedCompound)
            },
            onDismiss = {
                selectedCompoundId = null
                selectedEntryId = null
            },
        )
    }

    compoundEditorRequest?.let { request ->
        CompoundEditorDialog(
            initial = request.existing,
            compounds = manual.compounds,
            onDismiss = { compoundEditorRequest = null },
            onSave = { form ->
                val existing = request.existing
                val updatedCompound = if (existing == null) {
                    RubberCompound(
                        id = nextEntityId(manual.compounds.map(RubberCompound::id)),
                        compoundCode = form.compoundCode.trim(),
                        testPieceCureTemperatureC = form.testPieceCureTemperatureC,
                        testPieceCureTimeMinutes = form.testPieceCureTimeMinutes,
                        customBlockCureTimeMinutes = form.customBlockCureTimeMinutes,
                        notes = form.notes,
                    )
                } else {
                    existing.copy(
                        compoundCode = form.compoundCode.trim(),
                        testPieceCureTemperatureC = form.testPieceCureTemperatureC,
                        testPieceCureTimeMinutes = form.testPieceCureTimeMinutes,
                        customBlockCureTimeMinutes = form.customBlockCureTimeMinutes,
                        notes = form.notes,
                    )
                }
                if (existing == null) {
                    persist(manual.copy(compounds = manual.compounds + updatedCompound))
                    selectedCompoundId = updatedCompound.id
                } else {
                    updateCompound(updatedCompound)
                }
                compoundEditorRequest = null
            },
        )
    }

    inspectionEditorRequest?.let { request ->
        val compound = manual.findCompound(request.compoundId)
        if (compound == null) {
            inspectionEditorRequest = null
        } else {
            InspectionEditorDialog(
                compound = compound,
                entries = manual.entriesForCompound(compound.id),
                initial = request.existing,
                onDismiss = { inspectionEditorRequest = null },
                onSave = { form ->
                    val existing = request.existing
                    val normalizedParts = normalizePartNumbers(form.partNumbers)
                    val entry = if (existing == null) {
                        InspectionEntry(
                            id = nextEntityId(
                                manual.inspectionEntries.map(InspectionEntry::id),
                            ),
                            compoundId = compound.id,
                            standardNumber = form.standardNumber.trim(),
                            partNumbers = normalizedParts,
                            hardness = form.hardness.normalized(),
                            productCategory = form.productCategory.trim(),
                            color = form.color.trim(),
                            tensileStrength = form.tensileStrength.trim(),
                            elongation = form.elongation.trim(),
                            notes = form.notes.trim(),
                        )
                    } else {
                        existing.copy(
                            standardNumber = form.standardNumber.trim(),
                            partNumbers = normalizedParts,
                            hardness = form.hardness.normalized(),
                            productCategory = form.productCategory.trim(),
                            color = form.color.trim(),
                            tensileStrength = form.tensileStrength.trim(),
                            elongation = form.elongation.trim(),
                            notes = form.notes.trim(),
                        )
                    }
                    val entries = if (existing == null) {
                        manual.inspectionEntries + entry
                    } else {
                        manual.inspectionEntries.map { current ->
                            if (current.id == entry.id) entry else current
                        }
                    }
                    persist(manual.copy(inspectionEntries = entries))
                    selectedCompoundId = compound.id
                    selectedEntryId = entry.id
                    inspectionEditorRequest = null
                },
            )
        }
    }

    deleteInspectionRequest?.let { request ->
        DeleteInspectionDialog(
            entry = request.entry,
            onDismiss = { deleteInspectionRequest = null },
            onConfirm = {
                if (manual.findEntry(request.entry.id) != null) {
                    persist(manual.deleteEntry(request.entry.id))
                    if (selectedEntryId == request.entry.id) selectedEntryId = null
                    showMessage(
                        "已删除标准“${request.entry.standardNumber.ifBlank { request.entry.partNumbers.joinToString("、") }}”",
                    )
                }
                deleteInspectionRequest = null
            },
        )
    }

    deleteCompoundRequest?.let { request ->
        val compound = manual.findCompound(request.compound.id)
        if (compound == null) {
            deleteCompoundRequest = null
        } else {
            DeleteCompoundDialog(
                compound = compound,
                entryCount = manual.entryCountForCompound(compound.id),
                partCount = manual.partCountForCompound(compound.id),
                onDismiss = { deleteCompoundRequest = null },
                onConfirm = {
                    persist(manual.deleteCompound(compound.id))
                    selectedCompoundId = null
                    selectedEntryId = null
                    compoundEditorRequest = null
                    inspectionEditorRequest = null
                    deleteInspectionRequest = null
                    deleteCompoundRequest = null
                    showMessage("已删除胶料“${compound.compoundCode}”")
                },
            )
        }
    }

    if (backupActionsVisible) {
        ManualBackupActionsDialog(
            manual = manual,
            onExportBackup = {
                backupActionsVisible = false
                exportBackupLauncher.launch(manualBackupFileName())
            },
            onImportBackup = {
                importBackupLauncher.launch(
                    arrayOf("application/json", "text/plain", "application/octet-stream"),
                )
            },
            onExportSpreadsheet = {
                backupActionsVisible = false
                exportSpreadsheetLauncher.launch(manualSpreadsheetFileName())
            },
            onImportSpreadsheet = {
                importSpreadsheetLauncher.launch(
                    arrayOf(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "application/octet-stream",
                    ),
                )
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
                showMessage("已撤回删除")
            } else if (current.findCompound(entry.compoundId) == null) {
                showMessage("所属胶料已删除，无法恢复该检测标准")
            }
        }
    }
}

private fun deleteEmptyCompoundWithUndo(
    compound: RubberCompound,
    currentManual: () -> HardnessManual,
    persist: (HardnessManual) -> Unit,
    showMessage: (String) -> Unit,
    snackbarHostState: SnackbarHostState,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
) {
    val manual = currentManual()
    if (manual.entriesForCompound(compound.id).isNotEmpty()) return
    val index = manual.compounds.indexOfFirst { it.id == compound.id }
    if (index < 0) return
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
            if (
                current.findCompound(compound.id) == null &&
                current.entriesForCompound(compound.id).isEmpty()
            ) {
                val compounds = current.compounds.toMutableList()
                compounds.add(index.coerceIn(0, compounds.size), compound)
                persist(current.copy(compounds = compounds))
                showMessage("已撤回删除")
            }
        }
    }
}
