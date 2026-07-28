package com.wmdhs.shorea

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CenterAlignedTopAppBar
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

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
    var searchQuery by remember { mutableStateOf("") }
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
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    fun persist(updatedCompounds: List<RubberCompound>) {
        compounds = updatedCompounds

        coroutineScope.launch {
            store.saveCompounds(updatedCompounds)
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

    LaunchedEffect(store) {
        store.compounds.collectLatest { storedCompounds ->
            compounds = storedCompounds
            selectedCompoundId = selectedCompoundId?.takeIf { selectedId ->
                storedCompounds.any { it.id == selectedId }
            }
            hasLoaded = true
        }
    }

    val normalizedQuery = searchQuery.trim()
    val visibleCompounds = if (normalizedQuery.isEmpty()) {
        compounds
    } else {
        compounds.filter { it.matches(normalizedQuery) }
    }
    val selectedCompound = compounds.firstOrNull {
        it.id == selectedCompoundId
    }

    Scaffold(
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = "硬度块手册",
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                )

                if (hasLoaded) {
                    ManualSearchField(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        onClear = { searchQuery = "" },
                    )
                }
            }
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
        floatingActionButton = {
            if (hasLoaded) {
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
            onDismiss = {
                compoundEditorRequest = null
            },
            onSave = { form ->
                val existing = request.existing
                val updatedCompound = if (existing == null) {
                    RubberCompound(
                        id = (
                            compounds.maxOfOrNull {
                                it.id
                            } ?: 0L
                        ) + 1L,
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
                compoundCode = compound.compoundCode,
                initial = request.existing,
                onDismiss = {
                    groupEditorRequest = null
                },
                onSave = { form ->
                    val existing = request.existing
                    val updatedGroup = if (existing == null) {
                        PartSpecificationGroup(
                            id = (
                                compound.groups.maxOfOrNull {
                                    it.id
                                } ?: 0L
                            ) + 1L,
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
}
