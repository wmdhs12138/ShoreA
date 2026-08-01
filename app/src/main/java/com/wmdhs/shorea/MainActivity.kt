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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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

        setContent {
            ShoreA()
        }
    }
}

@Composable
private fun ShoreA() {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) {
            darkColorScheme()
        } else {
            lightColorScheme()
        },
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            HardnessManualHome()
        }
    }
}

private data class CompoundEditorRequest(
    val existing: RubberCompound?,
)

private data class GroupEditorRequest(
    val compoundId: Long,
    val existing: PartSpecificationGroup?,
)

private data class DeleteGroupRequest(
    val compoundId: Long,
    val group: PartSpecificationGroup,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HardnessManualHome() {
    val appContext = LocalContext.current.applicationContext
    val store = remember(appContext) {
        HardnessManualStore(appContext)
    }
    var compounds by remember {
        mutableStateOf<List<RubberCompound>>(emptyList())
    }
    var hasLoaded by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var searchActive by remember { mutableStateOf(false) }
    var sortOrder by remember {
        mutableStateOf(ManualSortOrder.COMPOUND_CODE)
    }
    var viewMode by remember {
        mutableStateOf(ManualViewMode.LIST)
    }
    var selectedCompoundId by remember {
        mutableStateOf<Long?>(null)
    }
    var compoundEditorRequest by remember {
        mutableStateOf<CompoundEditorRequest?>(null)
    }
    var groupEditorRequest by remember {
        mutableStateOf<GroupEditorRequest?>(null)
    }
    var deleteGroupRequest by remember {
        mutableStateOf<DeleteGroupRequest?>(null)
    }
    var backupActionsVisible by remember { mutableStateOf(false) }
    var pendingImport by remember {
        mutableStateOf<ManualBackup?>(null)
    }
    var pendingSpreadsheetImport by remember {
        mutableStateOf<SpreadsheetImport?>(null)
    }
    var persistRevision by remember { mutableStateOf(0L) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    fun showMessage(message: String) {
        coroutineScope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    fun persist(updatedCompounds: List<RubberCompound>) {
        val previousCompounds = compounds
        val revision = persistRevision + 1L
        persistRevision = revision
        compounds = updatedCompounds

        coroutineScope.launch {
            runCatching {
                store.saveCompounds(updatedCompounds)
            }.onFailure { error ->
                if (persistRevision == revision) {
                    compounds = previousCompounds
                }
                showMessage(
                    "保存失败，已恢复保存前状态：${error.message ?: "无法写入本地资料"}",
                )
            }
        }
    }

    fun updateCompound(updatedCompound: RubberCompound) {
        val index = compounds.indexOfFirst {
            it.id == updatedCompound.id
        }

        if (index < 0) {
            return
        }

        val updatedCompounds = compounds.toMutableList().apply {
            this[index] = updatedCompound
        }
        persist(updatedCompounds)
    }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(
            "application/json",
        ),
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        appContext.contentResolver
                            .openOutputStream(uri, "wt")
                            ?.bufferedWriter()
                            ?.use { writer ->
                                writer.write(
                                    encodeManualBackup(compounds),
                                )
                            }
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
                            encodeManualSpreadsheet(compounds, output)
                        } ?: error("无法写入所选文件")
                    }
                }
                showMessage(if (result.isSuccess) "Excel 已导出" else "导出失败：${result.exceptionOrNull()?.message ?: "未知错误"}")
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
                    showMessage(
                        "导入失败：${error.message ?: "文件格式错误"}",
                    )
                }
            }
        }
    }

    LaunchedEffect(store) {
        store.state.collectLatest { state ->
            compounds = state.compounds
            loadError = state.loadError
            selectedCompoundId = selectedCompoundId?.takeIf { selectedId ->
                state.compounds.any { it.id == selectedId }
            }
            hasLoaded = true
        }
    }

    val normalizedQuery = searchQuery.trim()
    val filteredCompounds = if (normalizedQuery.isEmpty()) {
        compounds
    } else {
        compounds.filter { it.matches(normalizedQuery) }
    }
    val visibleCompounds = sortCompounds(
        compounds = filteredCompounds,
        order = sortOrder,
    )
    val selectedCompound = compounds.firstOrNull {
        it.id == selectedCompoundId
    }

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
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
        floatingActionButton = {
            if (hasLoaded && loadError == null) {
                ExtendedFloatingActionButton(
                    onClick = {
                        compoundEditorRequest =
                            CompoundEditorRequest(existing = null)
                    },
                ) {
                    Text("＋ 添加胶料")
                }
            }
        },
    ) { innerPadding ->
        when {
            !hasLoaded -> {
                ManualLoadingState(innerPadding)
            }

            loadError != null -> {
                ManualDataErrorState(
                    innerPadding = innerPadding,
                    detail = loadError.orEmpty(),
                    onRestoreBackup = {
                        backupActionsVisible = true
                    },
                )
            }

            compounds.isEmpty() -> {
                EmptyManualState(innerPadding)
            }

            visibleCompounds.isEmpty() -> {
                EmptyManualSearchState(
                    innerPadding = innerPadding,
                    query = normalizedQuery,
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        top = 12.dp,
                        end = 16.dp,
                        bottom = 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(
                        items = visibleCompounds,
                        key = RubberCompound::id,
                    ) { compound ->
                        SwipeCompoundCard(
                            compound = compound,
                            searchQuery = normalizedQuery,
                            viewMode = viewMode,
                            onOpen = {
                                selectedCompoundId = compound.id
                            },
                            onDelete = {
                                val deletedIndex = compounds
                                    .indexOfFirst { it.id == compound.id }

                                if (deletedIndex >= 0) {
                                    val deletedCompound =
                                        compounds[deletedIndex]
                                    val updatedCompounds =
                                        compounds.toMutableList().apply {
                                            removeAt(deletedIndex)
                                        }
                                    persist(updatedCompounds)

                                    if (
                                        selectedCompoundId ==
                                        deletedCompound.id
                                    ) {
                                        selectedCompoundId = null
                                        compoundEditorRequest = null
                                        groupEditorRequest = null
                                        deleteGroupRequest = null
                                    }

                                    coroutineScope.launch {
                                        snackbarHostState
                                            .currentSnackbarData
                                            ?.dismiss()

                                        val result =
                                            snackbarHostState.showSnackbar(
                                                message =
                                                    "已删除“${deletedCompound.compoundCode}”",
                                                actionLabel = "撤回",
                                                duration =
                                                    SnackbarDuration.Long,
                                            )

                                        if (
                                            result ==
                                            SnackbarResult.ActionPerformed &&
                                            compounds.none {
                                                it.id == deletedCompound.id
                                            }
                                        ) {
                                            val restored =
                                                compounds.toMutableList()
                                                    .apply {
                                                        add(
                                                            index =
                                                                deletedIndex
                                                                    .coerceIn(
                                                                        0,
                                                                        size,
                                                                    ),
                                                            element =
                                                                deletedCompound,
                                                        )
                                                    }
                                            persist(restored)
                                        }
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (
        selectedCompound != null &&
        compoundEditorRequest == null &&
        groupEditorRequest == null &&
        deleteGroupRequest == null
    ) {
        CompoundDetailSheet(
            compound = selectedCompound,
            onEditCompound = {
                compoundEditorRequest =
                    CompoundEditorRequest(selectedCompound)
            },
            onAddGroup = {
                groupEditorRequest = GroupEditorRequest(
                    compoundId = selectedCompound.id,
                    existing = null,
                )
            },
            onEditGroup = { group ->
                groupEditorRequest = GroupEditorRequest(
                    compoundId = selectedCompound.id,
                    existing = group,
                )
            },
            onDeleteGroup = { group ->
                deleteGroupRequest = DeleteGroupRequest(
                    compoundId = selectedCompound.id,
                    group = group,
                )
            },
            onDismiss = {
                selectedCompoundId = null
            },
        )
    }

    compoundEditorRequest?.let { request ->
        CompoundEditorDialog(
            initial = request.existing,
            compounds = compounds,
            onDismiss = {
                compoundEditorRequest = null
            },
            onSave = { form ->
                val existing = request.existing
                val updatedCompound = if (existing == null) {
                    RubberCompound(
                        id = nextEntityId(
                            compounds.map(RubberCompound::id),
                        ),
                        compoundCode = form.compoundCode,
                        testPieceCureTemperatureC =
                            form.testPieceCureTemperatureC,
                        testPieceCureTimeMinutes =
                            form.testPieceCureTimeMinutes,
                        customBlockCureTimeMinutes =
                            form.customBlockCureTimeMinutes,
                        notes = form.notes,
                    )
                } else {
                    existing.copy(
                        compoundCode = form.compoundCode,
                        testPieceCureTemperatureC =
                            form.testPieceCureTemperatureC,
                        testPieceCureTimeMinutes =
                            form.testPieceCureTimeMinutes,
                        customBlockCureTimeMinutes =
                            form.customBlockCureTimeMinutes,
                        notes = form.notes,
                    )
                }

                if (existing == null) {
                    persist(compounds + updatedCompound)
                    selectedCompoundId = updatedCompound.id
                } else {
                    updateCompound(updatedCompound)
                }

                compoundEditorRequest = null
            },
        )
    }

    groupEditorRequest?.let { request ->
        val compound = compounds.firstOrNull {
            it.id == request.compoundId
        }

        if (compound != null) {
            GroupEditorDialog(
                compound = compound,
                initial = request.existing,
                onDismiss = {
                    groupEditorRequest = null
                },
                onSave = { form ->
                    val existing = request.existing
                    val updatedGroup = if (existing == null) {
                        PartSpecificationGroup(
                            id = nextEntityId(
                                compound.groups.map(
                                    PartSpecificationGroup::id,
                                ),
                            ),
                            standardNumber = form.standardNumber,
                            partNumbers = form.partNumbers,
                            hardness = form.hardness,
                            productCategory = form.productCategory,
                            color = form.color,
                            tensileStrength = form.tensileStrength,
                            elongation = form.elongation,
                            notes = form.notes,
                        )
                    } else {
                        existing.copy(
                            standardNumber = form.standardNumber,
                            partNumbers = form.partNumbers,
                            hardness = form.hardness,
                            productCategory = form.productCategory,
                            color = form.color,
                            tensileStrength = form.tensileStrength,
                            elongation = form.elongation,
                            notes = form.notes,
                        )
                    }

                    val updatedGroups = if (existing == null) {
                        compound.groups + updatedGroup
                    } else {
                        compound.groups.map { group ->
                            if (group.id == updatedGroup.id) {
                                updatedGroup
                            } else {
                                group
                            }
                        }
                    }

                    updateCompound(
                        compound.copy(groups = updatedGroups),
                    )
                    groupEditorRequest = null
                },
            )
        } else {
            groupEditorRequest = null
        }
    }

    deleteGroupRequest?.let { request ->
        DeleteStandardGroupDialog(
            group = request.group,
            onDismiss = {
                deleteGroupRequest = null
            },
            onConfirm = {
                val compound = compounds.firstOrNull {
                    it.id == request.compoundId
                }

                if (compound != null) {
                    updateCompound(
                        compound.copy(
                            groups = compound.groups.filterNot {
                                it.id == request.group.id
                            },
                        ),
                    )
                }

                deleteGroupRequest = null
            },
        )
    }

    if (backupActionsVisible) {
        ManualBackupActionsDialog(
            compounds = compounds,
            onExportBackup = {
                backupActionsVisible = false
                exportBackupLauncher.launch(
                    manualBackupFileName(),
                )
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
            onDismiss = {
                backupActionsVisible = false
            },
        )
    }

    pendingImport?.let { backup ->
        ManualImportPreviewDialog(
            backup = backup,
            onMerge = {
                persist(
                    mergeManualBackup(
                        current = compounds,
                        imported = backup.compounds,
                    ),
                )
                pendingImport = null
                showMessage("备份已合并")
            },
            onReplace = {
                persist(backup.compounds)
                selectedCompoundId = null
                pendingImport = null
                showMessage("当前手册已由备份覆盖")
            },
            onDismiss = {
                pendingImport = null
            },
        )
    }

    pendingSpreadsheetImport?.let { spreadsheet ->
        ManualSpreadsheetImportPreviewDialog(
            spreadsheet = spreadsheet,
            onMerge = {
                persist(mergeManualBackup(compounds, spreadsheet.compounds))
                pendingSpreadsheetImport = null
                showMessage("Excel 资料已合并")
            },
            onReplace = {
                persist(spreadsheet.compounds)
                selectedCompoundId = null
                pendingSpreadsheetImport = null
                showMessage("当前手册已由 Excel 覆盖")
            },
            onDismiss = { pendingSpreadsheetImport = null },
        )
    }

}
