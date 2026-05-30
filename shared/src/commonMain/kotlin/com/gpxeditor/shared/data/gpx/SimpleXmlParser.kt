package com.gpxeditor.shared.data.gpx

internal data class XmlElement(
    val name: String,
    val attributes: Map<String, String>,
    val children: List<XmlElement>,
    val text: String,
) {
    val localName: String
        get() = name.substringAfter(':')

    fun child(localName: String): XmlElement? {
        return children.firstOrNull { it.localName == localName }
    }

    fun childText(localName: String): String? {
        return child(localName)
            ?.text
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }
}

internal class SimpleXmlParser(
    private val source: String,
) {
    private var index = 0
    private var line = 1
    private var column = 1

    fun parse(): XmlElement {
        skipWhitespace()
        skipXmlDeclaration()
        skipWhitespace()
        val root = parseElement()
        skipWhitespace()

        if (!isAtEnd()) {
            fail("Unexpected content after root element")
        }

        return root
    }

    private fun parseElement(): XmlElement {
        expect('<')

        if (peek() == '/') {
            fail("Unexpected closing tag")
        }

        val name = readName()
        val attributes = readAttributes()

        if (consumeIf("/>")) {
            return XmlElement(name, attributes, emptyList(), text = "")
        }

        expect('>')

        val children = mutableListOf<XmlElement>()
        val text = StringBuilder()

        while (!isAtEnd()) {
            when {
                startsWith("</") -> {
                    advance(2)
                    val closeName = readName()
                    skipWhitespace()
                    expect('>')

                    if (closeName != name) {
                        fail("Expected closing tag </$name>, got </$closeName>")
                    }

                    return XmlElement(
                        name = name,
                        attributes = attributes,
                        children = children,
                        text = decodeXmlText(text.toString()),
                    )
                }

                startsWith("<!--") -> skipComment()
                startsWith("<![CDATA[") -> text.append(readCData())
                startsWith("<?") -> skipProcessingInstruction()
                peek() == '<' -> children += parseElement()
                else -> text.append(readTextUntilTag())
            }
        }

        fail("Unclosed tag <$name>")
    }

    private fun readAttributes(): Map<String, String> {
        val attributes = mutableMapOf<String, String>()

        while (!isAtEnd()) {
            skipWhitespace()
            if (startsWith("/>") || peek() == '>') break

            val name = readName()
            skipWhitespace()
            expect('=')
            skipWhitespace()

            val quote = peek()
            if (quote != '"' && quote != '\'') {
                fail("Expected quoted attribute value")
            }
            advance()

            val value = StringBuilder()
            while (!isAtEnd() && peek() != quote) {
                value.append(peek())
                advance()
            }
            expect(quote)

            attributes[name.substringAfter(':')] = decodeXmlText(value.toString())
        }

        return attributes
    }

    private fun readName(): String {
        if (isAtEnd() || !isNameStart(peek())) {
            fail("Expected XML name")
        }

        val start = index
        while (!isAtEnd() && isNameChar(peek())) {
            advance()
        }

        return source.substring(start, index)
    }

    private fun readTextUntilTag(): String {
        val start = index
        while (!isAtEnd() && peek() != '<') {
            advance()
        }
        return source.substring(start, index)
    }

    private fun readCData(): String {
        advance("<![CDATA[".length)
        val start = index
        while (!isAtEnd() && !startsWith("]]>")) {
            advance()
        }

        if (isAtEnd()) {
            fail("Unclosed CDATA section")
        }

        val value = source.substring(start, index)
        advance("]]>".length)
        return value
    }

    private fun skipXmlDeclaration() {
        if (startsWith("<?xml")) {
            skipProcessingInstruction()
        }
    }

    private fun skipProcessingInstruction() {
        advance(2)
        while (!isAtEnd() && !startsWith("?>")) {
            advance()
        }

        if (isAtEnd()) {
            fail("Unclosed processing instruction")
        }

        advance(2)
    }

    private fun skipComment() {
        advance("<!--".length)
        while (!isAtEnd() && !startsWith("-->")) {
            advance()
        }

        if (isAtEnd()) {
            fail("Unclosed XML comment")
        }

        advance("-->".length)
    }

    private fun skipWhitespace() {
        while (!isAtEnd() && peek().isWhitespace()) {
            advance()
        }
    }

    private fun expect(char: Char) {
        if (isAtEnd() || peek() != char) {
            fail("Expected '$char'")
        }
        advance()
    }

    private fun consumeIf(value: String): Boolean {
        if (!startsWith(value)) return false

        advance(value.length)
        return true
    }

    private fun startsWith(value: String): Boolean {
        return source.startsWith(value, index)
    }

    private fun peek(): Char = source[index]

    private fun advance(count: Int = 1) {
        repeat(count) {
            if (isAtEnd()) return

            val char = source[index]
            index++
            if (char == '\n') {
                line++
                column = 1
            } else {
                column++
            }
        }
    }

    private fun isAtEnd(): Boolean = index >= source.length

    private fun fail(message: String): Nothing {
        throw GpxParseException(GpxParseError(message, line, column))
    }

    private fun decodeXmlText(value: String): String {
        return buildString(value.length) {
            var cursor = 0
            while (cursor < value.length) {
                val char = value[cursor]
                if (char != '&') {
                    append(char)
                    cursor++
                    continue
                }

                val end = value.indexOf(';', startIndex = cursor + 1)
                if (end == -1) {
                    append(char)
                    cursor++
                    continue
                }

                val entity = value.substring(cursor + 1, end)
                val decoded = when {
                    entity == "amp" -> '&'
                    entity == "lt" -> '<'
                    entity == "gt" -> '>'
                    entity == "quot" -> '"'
                    entity == "apos" -> '\''
                    entity.startsWith("#x") -> entity.drop(2).toIntOrNull(radix = 16)?.toChar()
                    entity.startsWith("#") -> entity.drop(1).toIntOrNull()?.toChar()
                    else -> null
                }

                if (decoded == null) {
                    append('&')
                    append(entity)
                    append(';')
                } else {
                    append(decoded)
                }

                cursor = end + 1
            }
        }
    }

    private fun isNameStart(char: Char): Boolean {
        return char == '_' || char == ':' || char.isLetter()
    }

    private fun isNameChar(char: Char): Boolean {
        return isNameStart(char) || char == '-' || char == '.' || char.isDigit()
    }
}
