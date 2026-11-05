package com.jussicodes.music.lyric.parser

/**
 * 极简 XML 读取器，只服务于 AMLL TTML。
 * 移植自 Melodia 开源实现，无外部 DOM/SAX 依赖，避免 XXE 并具备极高性能。
 */

/** XML 节点：元素或文本。 */
internal sealed class XmlNode

/** 文本节点（含 CDATA 展开后的内容），实体引用已在读取时解码。 */
internal class XmlText(val value: String) : XmlNode()

/** 元素节点。[namespaceUri] 为 null 表示该元素不在任何命名空间中（含 `xmlns=""` 的重置）。 */
internal class XmlElement(
    val localName: String,
    val namespaceUri: String?,
    val attributes: List<XmlAttribute>,
    val children: List<XmlNode>
) : XmlNode()

/** 属性名只保留局部名（`m:role` → `role`），与 DOM 的 `getAttribute` 按前缀无关的取法一致。 */
internal class XmlAttribute(val localName: String, val value: String)

// ======================= 树遍历辅助 =======================

internal fun XmlElement.childElements(): List<XmlElement> = children.filterIsInstance<XmlElement>()

/** 递归拼接全部后代文本，对应 DOM 的 `textContent`。 */
internal val XmlElement.textContent: String
    get() = buildString { appendTextInto(this, this@textContent) }

private fun appendTextInto(out: StringBuilder, node: XmlNode) {
    when (node) {
        is XmlText -> out.append(node.value)
        is XmlElement -> node.children.forEach { appendTextInto(out, it) }
    }
}

/** 取属性值；空白值视为未设置。 */
internal fun XmlElement.attribute(localName: String): String? =
    attributes.firstOrNull { it.localName == localName }?.value?.takeIf { it.isNotBlank() }

/** 是否显式声明了该属性。 */
internal fun XmlElement.hasAttribute(localName: String): Boolean =
    attributes.any { it.localName == localName }

/** 文档序深度优先搜索，对应 DOM 的 `getElementsByTagNameNS("*", localName)`。 */
internal fun XmlElement.findDescendants(localName: String): List<XmlElement> = buildList {
    collectDescendants(this@findDescendants, localName, this)
}

private fun collectDescendants(element: XmlElement, localName: String, out: MutableList<XmlElement>) {
    if (element.localName == localName) out += element
    element.children.forEach { child ->
        if (child is XmlElement) collectDescendants(child, localName, out)
    }
}

// ======================= 读取器 =======================

internal object XmlReader {

    private const val XML_NAMESPACE = "http://www.w3.org/XML/1998/namespace"

    /** 解析失败一律返回 null，不抛异常。 */
    fun parse(source: String): XmlElement? {
        val open = ArrayList<OpenElement>()
        var root: XmlElement? = null
        var index = 0

        while (true) {
            val mark = source.indexOf('<', index)
            if (mark < 0) break

            // 标签之间的文本
            if (mark > index) {
                val raw = source.substring(index, mark)
                if (open.isNotEmpty()) open.last().children += XmlText(decode(raw))
                else if (raw.isNotBlank()) return null // 根元素之外的游离文本
            }

            when {
                source.startsWith("<!--", mark) -> {
                    index = endOf(source, mark + 4, "-->") ?: return null
                }

                source.startsWith("<![CDATA[", mark) -> {
                    val close = source.indexOf("]]>", mark + 9)
                    if (close < 0) return null
                    if (open.isNotEmpty()) open.last().children += XmlText(source.substring(mark + 9, close))
                    index = close + 3
                }

                source.startsWith("<?", mark) -> {
                    index = endOf(source, mark + 2, "?>") ?: return null
                }

                // DTD、实体声明、注释以外的 <! 一律拒绝：TTML 不需要，且能挡掉 XXE
                source.startsWith("<!", mark) -> return null

                source.startsWith("</", mark) -> {
                    val close = source.indexOf('>', mark + 2)
                    if (close < 0) return null
                    val name = source.substring(mark + 2, close).trim()
                    val element = open.removeLastOrNull() ?: return null
                    if (element.localName != localPart(name)) return null
                    val built = element.build()
                    if (open.isEmpty()) {
                        if (root != null) return null // 多个根元素
                        root = built
                    } else {
                        open.last().children += built
                    }
                    index = close + 1
                }

                else -> {
                    val close = tagEnd(source, mark) ?: return null
                    val body = source.substring(mark + 1, close)
                    val selfClosing = body.endsWith("/")
                    val inner = if (selfClosing) body.dropLast(1) else body
                    val parsed = parseStartTag(inner) ?: return null

                    val parentScope = open.lastOrNull()?.scope ?: mapOf("xml" to XML_NAMESPACE)
                    val scope = parsed.namespaceDeclarations.entries.fold(parentScope.toMutableMap()) { acc, e ->
                        acc[e.key] = e.value
                        acc
                    }
                    val element = OpenElement(
                        localName = localPart(parsed.rawName),
                        namespaceUri = resolveNamespace(parsed.rawName, scope),
                        attributes = parsed.attributes.map { XmlAttribute(localPart(it.first), it.second) },
                        scope = scope
                    )

                    if (selfClosing) {
                        val built = element.build()
                        if (open.isEmpty()) {
                            if (root != null) return null
                            root = built
                        } else {
                            open.last().children += built
                        }
                    } else {
                        open += element
                    }
                    index = close + 1
                }
            }

            if (index >= source.length) break
        }

        if (open.isNotEmpty()) return null // 标签未闭合
        return root
    }

    private class OpenElement(
        val localName: String,
        val namespaceUri: String?,
        val attributes: List<XmlAttribute>,
        val scope: Map<String, String>,
        val children: MutableList<XmlNode> = mutableListOf()
    ) {
        fun build() = XmlElement(localName, namespaceUri, attributes, children.toList())
    }

    private class StartTag(
        val rawName: String,
        val attributes: List<Pair<String, String>>,
        val namespaceDeclarations: Map<String, String>
    )

    /** 找到当前标签的 `>`，跳过引号内的 `>`。返回下标；未闭合返回 null。 */
    private fun tagEnd(source: String, from: Int): Int? {
        var i = from + 1
        var quote: Char? = null
        while (i < source.length) {
            val c = source[i]
            when {
                quote != null -> if (c == quote) quote = null
                c == '"' || c == '\'' -> quote = c
                c == '>' -> return i
            }
            i++
        }
        return null
    }

    private fun endOf(source: String, from: Int, literal: String): Int? {
        val at = source.indexOf(literal, from)
        return if (at < 0) null else at + literal.length
    }

    private fun parseStartTag(body: String): StartTag? {
        val n = body.length
        var i = 0
        fun skipSpace() { while (i < n && body[i].isWhitespace()) i++ }

        skipSpace()
        val nameStart = i
        while (i < n && !body[i].isWhitespace()) i++
        if (i == nameStart) return null
        val rawName = body.substring(nameStart, i)

        val attributes = ArrayList<Pair<String, String>>()
        val declarations = HashMap<String, String>()
        while (true) {
            skipSpace()
            if (i >= n) break

            val attrStart = i
            while (i < n && !body[i].isWhitespace() && body[i] != '=') i++
            if (i == attrStart) return null
            val rawAttrName = body.substring(attrStart, i)

            skipSpace()
            if (i >= n || body[i] != '=') return null
            i++
            skipSpace()
            if (i >= n || (body[i] != '"' && body[i] != '\'')) return null
            val quote = body[i]
            i++
            val valueStart = i
            while (i < n && body[i] != quote) i++
            if (i >= n) return null
            val decoded = decode(body.substring(valueStart, i))
            i++

            when {
                rawAttrName == "xmlns" -> declarations[""] = decoded
                rawAttrName.startsWith("xmlns:") -> declarations[rawAttrName.removePrefix("xmlns:")] = decoded
                else -> attributes += rawAttrName to decoded
            }
        }
        return StartTag(rawName, attributes, declarations)
    }

    private fun resolveNamespace(rawName: String, scope: Map<String, String>): String? {
        val prefix = rawName.substringBeforeLast(':', "")
        val uri = if (prefix.isEmpty()) scope[""] else scope[prefix]
        return uri?.takeIf { it.isNotEmpty() }
    }

    private fun localPart(rawName: String): String = rawName.substringAfterLast(':')

    /**
     * 解码内置实体与数字字符引用。未定义的实体直接抛错——由 [parse] 的调用方转成失败，
     * 这样残缺 XML 不会静默产出半截歌词。
     */
    private fun decode(raw: String): String {
        if ('&' !in raw) return raw
        val out = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c != '&') {
                out.append(c)
                i++
                continue
            }
            val semi = raw.indexOf(';', i + 1)
            require(semi > i) { "Unterminated entity reference" }
            val name = raw.substring(i + 1, semi)
            out.append(
                when {
                    name == "amp" -> '&'
                    name == "lt" -> '<'
                    name == "gt" -> '>'
                    name == "quot" -> '"'
                    name == "apos" -> '\''
                    name.startsWith("#x") || name.startsWith("#X") ->
                        codePoint(name.substring(2).toInt(16))
                    name.startsWith("#") -> codePoint(name.substring(1).toInt(10))
                    else -> throw IllegalArgumentException("Undefined entity: &$name;")
                }
            )
            i = semi + 1
        }
        return out.toString()
    }

    private fun codePoint(value: Int): String {
        require(value in 0..0x10FFFF) { "Code point out of range: $value" }
        return StringBuilder().appendCodePointCompat(value).toString()
    }
}

private fun StringBuilder.appendCodePointCompat(value: Int): StringBuilder = when {
    value < 0x10000 -> append(value.toChar())
    else -> {
        val v = value - 0x10000
        append((0xD800 + (v shr 10)).toChar())
        append((0xDC00 + (v and 0x3FF)).toChar())
    }
}
