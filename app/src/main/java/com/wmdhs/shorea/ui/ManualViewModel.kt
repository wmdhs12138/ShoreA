package com.wmdhs.shorea.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wmdhs.shorea.data.ManualRepository
import com.wmdhs.shorea.data.model.HardnessManual
import com.wmdhs.shorea.data.model.HardnessSet
import com.wmdhs.shorea.data.model.InspectionEntry
import com.wmdhs.shorea.data.model.ManualPreferences
import com.wmdhs.shorea.data.model.ManualSortOrder
import com.wmdhs.shorea.data.model.ManualViewMode
import com.wmdhs.shorea.data.model.RubberCompound
import com.wmdhs.shorea.data.model.hasDuplicateCompoundCode
import com.wmdhs.shorea.data.model.nextEntityId
import com.wmdhs.shorea.data.model.normalizePartNumbers
import com.wmdhs.shorea.data.transfer.ManualBackup
import com.wmdhs.shorea.data.transfer.SpreadsheetImport
import com.wmdhs.shorea.data.transfer.decodeManualBackup
import com.wmdhs.shorea.data.transfer.decodeManualSpreadsheet
import com.wmdhs.shorea.data.transfer.encodeManualBackup
import com.wmdhs.shorea.data.transfer.encodeManualSpreadsheet
import com.wmdhs.shorea.data.transfer.mergeManualBackup
import com.wmdhs.shorea.ui.home.ManualHomeItem
import com.wmdhs.shorea.ui.home.emptyCompoundKey
import com.wmdhs.shorea.ui.home.inspectionKey
import com.wmdhs.shorea.ui.home.visibleHomeItems
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class FileOperation(val label: String) {
    IMPORT_JSON("正在解析 JSON 备份"),
    EXPORT_JSON("正在写入 JSON 备份"),
    IMPORT_EXCEL("正在解析 Excel"),
    EXPORT_EXCEL("正在写入 Excel"),
}

/** 已解析、等待用户选择"合并 / 覆盖"的导入内容。 */
@Immutable
internal sealed interface PendingImport {
    val manual: HardnessManual

    data class Backup(val backup: ManualBackup) : PendingImport {
        override val manual: HardnessManual get() = backup.manual
    }

    data class Spreadsheet(val spreadsheet: SpreadsheetImport) : PendingImport {
        override val manual: HardnessManual get() = spreadsheet.manual
    }
}

@Immutable
internal data class ManualUiState(
    val isLoading: Boolean = true,
    val manual: HardnessManual = HardnessManual(),
    val loadError: String? = null,
    val preferences: ManualPreferences = ManualPreferences(),
    val transferSheetVisible: Boolean = false,
    val fileOperation: FileOperation? = null,
    val pendingImport: PendingImport? = null,
) {
    /** 读取失败时禁止编辑，避免覆盖无法解析的原始资料。 */
    val canEdit: Boolean get() = !isLoading && loadError == null
}

/** 一次性提示；带操作时显示为可撤回的 Snackbar。 */
internal class UiMessage(
    val text: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
)

internal data class CompoundForm(
    val compoundCode: String,
    val baseRubber: String,
    val testPieceCureTemperatureC: String,
    val testPieceCureTimeMinutes: String,
    val customBlockCureTimeMinutes: String,
    val notes: String,
)

internal data class InspectionForm(
    val standardNumber: String,
    val partNumbers: List<String>,
    val hardness: HardnessSet,
    val productCategory: String,
    val color: String,
    val tensileStrength: String,
    val elongation: String,
    val notes: String,
)

internal class ManualViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ManualRepository(application)
    private val contentResolver = application.contentResolver

    private val _uiState = MutableStateFlow(ManualUiState())
    val uiState: StateFlow<ManualUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** 首页可见条目；null 表示尚未完成首次计算。 */
    val homeItems: StateFlow<List<ManualHomeItem>?> = combine(
        _uiState.map { it.manual }.distinctUntilChanged(),
        _searchQuery,
        _uiState.map { it.preferences.sortOrder }.distinctUntilChanged(),
    ) { manual, query, order -> manual.visibleHomeItems(query, order) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** 撤回删除后首页需要滚动并高亮的条目。 */
    private val _homeFocusKey = MutableStateFlow<String?>(null)
    val homeFocusKey: StateFlow<String?> = _homeFocusKey.asStateFlow()

    /** 保存检测标准后详情页需要滚动并高亮的条目。 */
    private val _detailFocusEntryId = MutableStateFlow<Long?>(null)
    val detailFocusEntryId: StateFlow<Long?> = _detailFocusEntryId.asStateFlow()

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    private var persistRevision = 0L
    private var pendingSaves = 0

    init {
        viewModelScope.launch {
            repository.manual.collect { result ->
                // 保存进行中时，界面已是最新的乐观状态；跳过中间回传，避免列表闪回旧数据。
                if (pendingSaves > 0 && result.loadError == null) return@collect
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        manual = result.manual,
                        loadError = result.loadError,
                    )
                }
            }
        }
        viewModelScope.launch {
            repository.preferences.collect { preferences ->
                _uiState.update { it.copy(preferences = preferences) }
            }
        }
    }

    // region 首页显示

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortOrder(order: ManualSortOrder) {
        _uiState.update { it.copy(preferences = it.preferences.copy(sortOrder = order)) }
        viewModelScope.launch { runCatching { repository.setSortOrder(order) } }
    }

    fun setViewMode(mode: ManualViewMode) {
        _uiState.update { it.copy(preferences = it.preferences.copy(viewMode = mode)) }
        viewModelScope.launch { runCatching { repository.setViewMode(mode) } }
    }

    fun setDynamicColor(enabled: Boolean) {
        _uiState.update { it.copy(preferences = it.preferences.copy(dynamicColor = enabled)) }
        viewModelScope.launch { runCatching { repository.setDynamicColor(enabled) } }
    }

    fun consumeHomeFocus(key: String) {
        _homeFocusKey.compareAndSet(key, null)
    }

    fun consumeDetailFocus(entryId: Long) {
        _detailFocusEntryId.compareAndSet(entryId, null)
    }

    // endregion

    // region 编辑

    /** 保存胶料，返回其 ID；胶料号重复或不可编辑时返回 null。 */
    fun saveCompound(existingId: Long?, form: CompoundForm): Long? {
        val state = _uiState.value
        if (!state.canEdit) return null
        val manual = state.manual
        val code = form.compoundCode.trim()
        if (code.isEmpty() || hasDuplicateCompoundCode(manual.compounds, code, existingId)) return null
        val existing = existingId?.let(manual::findCompound)
        if (existingId != null && existing == null) return null

        val compound = (existing ?: RubberCompound(
            id = nextEntityId(manual.compounds.map(RubberCompound::id)),
            compoundCode = code,
        )).copy(
            compoundCode = code,
            baseRubber = form.baseRubber.trim(),
            testPieceCureTemperatureC = form.testPieceCureTemperatureC.trim(),
            testPieceCureTimeMinutes = form.testPieceCureTimeMinutes.trim(),
            customBlockCureTimeMinutes = form.customBlockCureTimeMinutes.trim(),
            notes = form.notes.trim(),
        )
        persist(manual.upsertCompound(compound))
        return compound.id
    }

    /** 保存检测标准，返回其 ID；所属胶料不存在或不可编辑时返回 null。 */
    fun saveInspection(compoundId: Long, existingEntryId: Long?, form: InspectionForm): Long? {
        val state = _uiState.value
        if (!state.canEdit) return null
        val manual = state.manual
        if (manual.findCompound(compoundId) == null) return null
        val existing = existingEntryId?.let(manual::findEntry)
        if (existingEntryId != null && (existing == null || existing.compoundId != compoundId)) return null
        val partNumbers = normalizePartNumbers(form.partNumbers)
        if (partNumbers.isEmpty()) return null

        val entry = (existing ?: InspectionEntry(
            id = nextEntityId(manual.inspectionEntries.map(InspectionEntry::id)),
            compoundId = compoundId,
            partNumbers = partNumbers,
        )).copy(
            standardNumber = form.standardNumber.trim(),
            partNumbers = partNumbers,
            hardness = form.hardness.normalized(),
            productCategory = form.productCategory.trim(),
            color = form.color.trim(),
            tensileStrength = form.tensileStrength.trim(),
            elongation = form.elongation.trim(),
            notes = form.notes.trim(),
        )
        persist(manual.upsertEntry(entry))
        _detailFocusEntryId.value = entry.id
        return entry.id
    }

    fun deleteInspection(entryId: Long) {
        val manual = _uiState.value.manual
        val index = manual.inspectionEntries.indexOfFirst { it.id == entryId }
        if (index < 0 || !_uiState.value.canEdit) return
        val entry = manual.inspectionEntries[index]
        persist(manual.deleteEntry(entryId))
        send(
            UiMessage(
                text = "已删除标准“${entry.displayTitle}”",
                actionLabel = "撤回",
                onAction = { restoreInspection(entry, index) },
            ),
        )
    }

    private fun restoreInspection(entry: InspectionEntry, index: Int) {
        val current = _uiState.value.manual
        when {
            current.findCompound(entry.compoundId) == null ->
                send(UiMessage("所属胶料已删除，无法恢复该检测标准"))

            current.findEntry(entry.id) == null -> {
                val restored = current.inspectionEntries.toMutableList()
                restored.add(index.coerceIn(0, restored.size), entry)
                persist(current.copy(inspectionEntries = restored))
                _homeFocusKey.value = inspectionKey(entry.id)
                send(UiMessage("已撤回删除"))
            }
        }
    }

    fun deleteCompound(compoundId: Long) {
        val manual = _uiState.value.manual
        val compoundIndex = manual.compounds.indexOfFirst { it.id == compoundId }
        if (compoundIndex < 0 || !_uiState.value.canEdit) return
        val compound = manual.compounds[compoundIndex]
        val deletedEntries = manual.inspectionEntries.mapIndexedNotNull { index, entry ->
            if (entry.compoundId == compoundId) index to entry else null
        }
        persist(manual.deleteCompound(compoundId))
        send(
            UiMessage(
                text = "已删除胶料“${compound.compoundCode}”",
                actionLabel = "撤回",
                onAction = { restoreCompound(compound, compoundIndex, deletedEntries) },
            ),
        )
    }

    private fun restoreCompound(
        compound: RubberCompound,
        compoundIndex: Int,
        deletedEntries: List<Pair<Int, InspectionEntry>>,
    ) {
        val current = _uiState.value.manual
        if (current.findCompound(compound.id) != null) return
        val compounds = current.compounds.toMutableList()
        compounds.add(compoundIndex.coerceIn(0, compounds.size), compound)
        val entries = current.inspectionEntries.toMutableList()
        deletedEntries.forEach { (index, entry) ->
            if (entries.none { it.id == entry.id }) {
                entries.add(index.coerceIn(0, entries.size), entry)
            }
        }
        persist(current.copy(compounds = compounds, inspectionEntries = entries))
        _homeFocusKey.value = deletedEntries.firstOrNull()?.second?.id
            ?.let(::inspectionKey)
            ?: emptyCompoundKey(compound.id)
        send(UiMessage("已撤回删除"))
    }

    /** 乐观更新界面，写入失败时回滚到写入前状态。 */
    private fun persist(updated: HardnessManual) {
        val previous = _uiState.value.manual
        val revision = ++persistRevision
        _uiState.update { it.copy(manual = updated) }
        pendingSaves++
        viewModelScope.launch {
            try {
                repository.saveManual(updated)
                // 成功写入合法资料后，之前的读取错误（如从备份恢复）随之解除。
                _uiState.update { if (it.loadError != null) it.copy(loadError = null) else it }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                if (persistRevision == revision) {
                    _uiState.update { it.copy(manual = previous) }
                }
                send(UiMessage("保存失败，已恢复保存前状态：${error.message ?: "无法写入本地资料"}"))
            } finally {
                pendingSaves--
            }
        }
    }

    // endregion

    // region 导入与导出

    fun showTransferSheet() {
        if (!_uiState.value.isLoading) _uiState.update { it.copy(transferSheetVisible = true) }
    }

    fun hideTransferSheet() {
        if (_uiState.value.fileOperation == null) {
            _uiState.update { it.copy(transferSheetVisible = false) }
        }
    }

    fun exportBackup(uri: Uri) {
        val manual = _uiState.value.manual
        runFileOperation(FileOperation.EXPORT_JSON, failurePrefix = "导出失败") {
            withContext(Dispatchers.IO) {
                val json = encodeManualBackup(manual)
                contentResolver.openOutputStream(uri, "wt")
                    ?.bufferedWriter()
                    ?.use { it.write(json) }
                    ?: error("无法写入所选文件")
            }
            _uiState.update { it.copy(transferSheetVisible = false) }
            send(UiMessage("备份已导出"))
        }
    }

    fun exportSpreadsheet(uri: Uri) {
        val manual = _uiState.value.manual
        runFileOperation(FileOperation.EXPORT_EXCEL, failurePrefix = "导出失败") {
            withContext(Dispatchers.IO) {
                contentResolver.openOutputStream(uri, "wt")
                    ?.use { output -> encodeManualSpreadsheet(manual, output) }
                    ?: error("无法写入所选文件")
            }
            _uiState.update { it.copy(transferSheetVisible = false) }
            send(UiMessage("Excel 已导出"))
        }
    }

    fun importBackup(uri: Uri) {
        runFileOperation(FileOperation.IMPORT_JSON, failurePrefix = "导入失败") {
            val backup = withContext(Dispatchers.IO) {
                val raw = contentResolver.openInputStream(uri)
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    ?: error("无法读取所选文件")
                decodeManualBackup(raw).getOrThrow()
            }
            _uiState.update {
                it.copy(transferSheetVisible = false, pendingImport = PendingImport.Backup(backup))
            }
        }
    }

    fun importSpreadsheet(uri: Uri) {
        runFileOperation(FileOperation.IMPORT_EXCEL, failurePrefix = "Excel 导入失败") {
            val spreadsheet = withContext(Dispatchers.IO) {
                contentResolver.openInputStream(uri)
                    ?.use { input -> decodeManualSpreadsheet(input).getOrThrow() }
                    ?: error("无法读取所选文件")
            }
            _uiState.update {
                it.copy(
                    transferSheetVisible = false,
                    pendingImport = PendingImport.Spreadsheet(spreadsheet),
                )
            }
        }
    }

    fun mergePendingImport() {
        val pending = _uiState.value.pendingImport ?: return
        val source = if (pending is PendingImport.Backup) "备份" else "Excel 资料"
        runCatching { mergeManualBackup(_uiState.value.manual, pending.manual) }
            .onSuccess { merged ->
                persist(merged)
                _uiState.update { it.copy(pendingImport = null) }
                send(UiMessage("${source}已合并"))
            }
            .onFailure { error ->
                send(UiMessage("${source}合并失败：${error.message ?: "资料存在冲突"}"))
            }
    }

    fun replaceWithPendingImport() {
        val pending = _uiState.value.pendingImport ?: return
        persist(pending.manual)
        _uiState.update { it.copy(pendingImport = null) }
        send(
            UiMessage(
                if (pending is PendingImport.Backup) "当前手册已由备份覆盖" else "当前手册已由 Excel 覆盖",
            ),
        )
    }

    fun dismissPendingImport() {
        _uiState.update { it.copy(pendingImport = null) }
    }

    private fun runFileOperation(
        operation: FileOperation,
        failurePrefix: String,
        block: suspend () -> Unit,
    ) {
        if (_uiState.value.fileOperation != null) return
        _uiState.update { it.copy(fileOperation = operation) }
        viewModelScope.launch {
            try {
                block()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                send(UiMessage("$failurePrefix：${error.message ?: "文件格式错误"}"))
            } finally {
                _uiState.update { it.copy(fileOperation = null) }
            }
        }
    }

    // endregion

    private fun send(message: UiMessage) {
        _messages.trySend(message)
    }
}
