package com.wmdhs.shorea

import android.util.Xml
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.xmlpull.v1.XmlPullParser

internal data class SpreadsheetImport(
    val compounds: List<RubberCompound>,
    val sourceRowCount: Int,
    val skippedRowCount: Int,
) {
    val groupCount: Int get() = compounds.sumOf { it.groups.size }
    val partCount: Int get() = compounds.sumOf { it.totalPartCount }
}

internal fun decodeManualSpreadsheet(input: InputStream): Result<SpreadsheetImport> = runCatching {
    val entries = mutableMapOf<String, ByteArray>()
    ZipInputStream(input.buffered()).use { zip ->
        var entry = zip.nextEntry
        while (entry != null) {
            if (!entry.isDirectory && (entry.name == "xl/sharedStrings.xml" || entry.name.startsWith("xl/worksheets/sheet"))) {
                entries[entry.name] = zip.readBytes()
            }
            zip.closeEntry()
            entry = zip.nextEntry
        }
    }
    val sheet = entries.entries.firstOrNull { it.key.startsWith("xl/worksheets/sheet") }?.value
        ?: error("Excel 文件中没有工作表")
    val sharedStrings = entries["xl/sharedStrings.xml"]?.let(::readSharedStrings).orEmpty()
    val rows = readSheetRows(sheet, sharedStrings)
    require(rows.size >= 2 && cell(rows[0], 0).contains("胶料号")) { "表格格式不正确：第一列应为胶料号" }

    data class GroupBuilder(
        val standardNumber: String,
        val parts: LinkedHashSet<String> = linkedSetOf(),
        var testPiece: String = "",
        var block: String = "",
        var product: String = "",
        var category: String = "",
        var color: String = "",
        var tensile: String = "",
        var elongation: String = "",
    )
    data class CompoundBuilder(
        val code: String,
        var temperature: String = "",
        var time: String = "",
        val groups: LinkedHashMap<String, GroupBuilder> = linkedMapOf(),
    )

    val compounds = linkedMapOf<String, CompoundBuilder>()
    var sourceRows = 0
    var skippedRows = 0
    rows.drop(2).forEach { row ->
        if (row.values.all(String::isBlank)) return@forEach
        sourceRows++
        val code = cleanRequired(cell(row, 0))
        val standard = cleanRequired(cell(row, 3))
        val part = cleanRequired(cell(row, 4))
        if (code.isBlank() || standard.isBlank() || part.isBlank()) {
            skippedRows++
            return@forEach
        }
        val compound = compounds.getOrPut(code.uppercase(Locale.ROOT)) { CompoundBuilder(code) }
        compound.temperature = compound.temperature.ifBlank { cleanOptional(cell(row, 1)) }
        compound.time = compound.time.ifBlank { cleanOptional(cell(row, 2)) }
        val group = compound.groups.getOrPut(standard.uppercase(Locale.ROOT)) { GroupBuilder(standard) }
        group.parts += part
        group.category = group.category.ifBlank { cleanOptional(cell(row, 5)) }
        group.color = group.color.ifBlank { cleanOptional(cell(row, 6)) }
        group.testPiece = group.testPiece.ifBlank { cleanOptional(cell(row, 7)) }
        group.block = group.block.ifBlank { cleanOptional(cell(row, 8)) }
        group.product = group.product.ifBlank { cleanOptional(cell(row, 9)) }
        group.tensile = group.tensile.ifBlank { cleanOptional(cell(row, 10)) }
        group.elongation = group.elongation.ifBlank { cleanOptional(cell(row, 11)) }
    }
    require(compounds.isNotEmpty()) { "表格中没有可导入的资料" }

    var compoundId = 1L
    val result = compounds.values.map { source ->
        var groupId = 1L
        RubberCompound(
            id = compoundId++,
            compoundCode = source.code,
            testPieceCureTemperatureC = source.temperature,
            testPieceCureTimeMinutes = source.time,
            groups = source.groups.values.map { group ->
                PartSpecificationGroup(
                    id = groupId++,
                    standardNumber = group.standardNumber,
                    partNumbers = group.parts.toList(),
                    hardness = HardnessSet(group.testPiece, group.block, group.product),
                    productCategory = group.category,
                    color = group.color,
                    tensileStrength = group.tensile,
                    elongation = group.elongation,
                )
            },
        )
    }
    SpreadsheetImport(result, sourceRows, skippedRows)
}

internal fun encodeManualSpreadsheet(compounds: List<RubberCompound>, output: OutputStream) {
    ZipOutputStream(output.buffered()).use { zip ->
        zip.textEntry("[Content_Types].xml", contentTypesXml)
        zip.textEntry("_rels/.rels", rootRelsXml)
        zip.textEntry("xl/workbook.xml", workbookXml)
        zip.textEntry("xl/_rels/workbook.xml.rels", workbookRelsXml)
        zip.textEntry("xl/styles.xml", stylesXml)
        zip.textEntry("xl/worksheets/sheet1.xml", worksheetXml(compounds))
    }
}

internal fun manualSpreadsheetFileName(now: Instant = Instant.now()): String {
    val formatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT)
        .withZone(ZoneId.systemDefault())
    return "ShoreA-送样参考-${formatter.format(now)}.xlsx"
}

private fun readSharedStrings(bytes: ByteArray): List<String> {
    val parser = Xml.newPullParser().apply { setInput(ByteArrayInputStream(bytes), "UTF-8") }
    val result = mutableListOf<String>()
    var inItem = false
    var value = StringBuilder()
    while (parser.eventType != XmlPullParser.END_DOCUMENT) {
        when (parser.eventType) {
            XmlPullParser.START_TAG -> when (parser.name) {
                "si" -> { inItem = true; value = StringBuilder() }
                "t" -> if (inItem) value.append(parser.nextText())
            }
            XmlPullParser.END_TAG -> if (parser.name == "si") { result += value.toString(); inItem = false }
        }
        parser.next()
    }
    return result
}

private fun readSheetRows(bytes: ByteArray, sharedStrings: List<String>): List<Map<Int, String>> {
    val parser = Xml.newPullParser().apply { setInput(ByteArrayInputStream(bytes), "UTF-8") }
    val rows = mutableListOf<Map<Int, String>>()
    var row = linkedMapOf<Int, String>()
    var column = 0
    var type = ""
    var value = ""
    while (parser.eventType != XmlPullParser.END_DOCUMENT) {
        when (parser.eventType) {
            XmlPullParser.START_TAG -> when (parser.name) {
                "row" -> row = linkedMapOf()
                "c" -> {
                    column = columnIndex(parser.getAttributeValue(null, "r").orEmpty())
                    type = parser.getAttributeValue(null, "t").orEmpty()
                    value = ""
                }
                "v", "t" -> value += parser.nextText()
            }
            XmlPullParser.END_TAG -> when (parser.name) {
                "c" -> row[column] = if (type == "s") sharedStrings.getOrNull(value.toIntOrNull() ?: -1).orEmpty() else value
                "row" -> rows += row
            }
        }
        parser.next()
    }
    return rows
}

private fun columnIndex(reference: String): Int {
    var result = 0
    reference.takeWhile(Char::isLetter).forEach { result = result * 26 + (it.uppercaseChar() - 'A' + 1) }
    return (result - 1).coerceAtLeast(0)
}

private fun cell(row: Map<Int, String>, index: Int): String = row[index].orEmpty().trim()
private fun cleanRequired(value: String): String = value.trim()
private fun cleanOptional(value: String): String = value.trim().takeUnless { it in setOf("/", "-", "—") }.orEmpty()

private fun ZipOutputStream.textEntry(name: String, text: String) {
    putNextEntry(ZipEntry(name))
    write(text.toByteArray(Charsets.UTF_8))
    closeEntry()
}

private fun worksheetXml(compounds: List<RubberCompound>): String {
    val rows = mutableListOf<List<String>>()
    rows += listOf("胶料号", "硫化条件试片", "", "标准号", "部品号", "产品类别", "颜色", "性能", "", "", "", "")
    rows += listOf("", "温度/℃", "时间/min", "", "", "", "", "硬度片/A", "硬度块/A", "硬度产品/A", "拉伸强度/Mpa", "伸长率/%")
    compounds.forEach { compound ->
        compound.groups.forEach { group ->
            group.partNumbers.forEach { part ->
                rows += listOf(
                    compound.compoundCode, compound.testPieceCureTemperatureC, compound.testPieceCureTimeMinutes,
                    group.standardNumber, part, group.productCategory, group.color,
                    group.hardness.testPieceHardness, group.hardness.blockHardness, group.hardness.productHardness,
                    group.tensileStrength, group.elongation,
                )
            }
        }
    }
    val body = rows.mapIndexed { rowIndex, values ->
        val cells = values.mapIndexed { col, value ->
            val ref = columnName(col) + (rowIndex + 1)
            val style = if (rowIndex < 2) 1 else 0
            "<c r=\"$ref\" t=\"inlineStr\" s=\"$style\"><is><t xml:space=\"preserve\">${xmlEscape(value)}</t></is></c>"
        }.joinToString("")
        "<row r=\"${rowIndex + 1}\">$cells</row>"
    }.joinToString("")
    return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><dimension ref="A1:L${rows.size.coerceAtLeast(2)}"/><sheetViews><sheetView workbookViewId="0"/></sheetViews><cols><col min="1" max="12" width="18" customWidth="1"/></cols><sheetData>$body</sheetData></worksheet>"""
}

private fun columnName(index: Int): String {
    var number = index + 1
    val result = StringBuilder()
    while (number > 0) { number--; result.append(('A'.code + number % 26).toChar()); number /= 26 }
    return result.reverse().toString()
}

private fun xmlEscape(value: String): String = value
    .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

private val contentTypesXml = """<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>"""
private val rootRelsXml = """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""
private val workbookXml = """<?xml version="1.0" encoding="UTF-8"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="送样参考" sheetId="1" r:id="rId1"/></sheets></workbook>"""
private val workbookRelsXml = """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>"""
private val stylesXml = """<?xml version="1.0" encoding="UTF-8"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><fonts count="2"><font><sz val="11"/><name val="Arial"/></font><font><b/><sz val="11"/><name val="Arial"/></font></fonts><fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills><borders count="1"><border/></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="2"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0" applyAlignment="1"><alignment vertical="center" wrapText="1"/></xf><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1"/></xf></cellXfs></styleSheet>"""
