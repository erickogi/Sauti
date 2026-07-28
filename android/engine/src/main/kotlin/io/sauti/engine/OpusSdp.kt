package io.sauti.engine

data class OpusConfig(val fec: Boolean = false, val dtx: Boolean = false)

private data class SdpLine(var content: String, var eol: String)

private val opusRtpmap = Regex("a=rtpmap:(\\d+) opus/48000(?:/\\d+)?")

fun transformOpusSdp(sdp: String, dtx: Boolean, fec: Boolean): String {
    if (!dtx && !fec) return sdp
    val lines = splitLines(sdp)
    val tokens = requestedTokens(dtx, fec)
    val fallbackEol = defaultEol(lines)
    val out = mutableListOf<SdpLine>()
    var i = 0
    while (i < lines.size) {
        if (lines[i].content.startsWith("m=audio")) {
            var end = i + 1
            while (end < lines.size && !lines[end].content.startsWith("m=")) end += 1
            out.addAll(transformSection(lines.subList(i, end), tokens, fallbackEol))
            i = end
        } else {
            out.add(lines[i])
            i += 1
        }
    }
    return out.joinToString("") { it.content + it.eol }
}

private fun splitLines(sdp: String): List<SdpLine> {
    val result = mutableListOf<SdpLine>()
    var start = 0
    var i = 0
    while (i < sdp.length) {
        if (sdp[i] == '\n') {
            result.add(toLine(sdp.substring(start, i + 1)))
            start = i + 1
        }
        i += 1
    }
    result.add(toLine(sdp.substring(start)))
    return result
}

private fun toLine(part: String): SdpLine = when {
    part.endsWith("\r\n") -> SdpLine(part.dropLast(2), "\r\n")
    part.endsWith("\n") -> SdpLine(part.dropLast(1), "\n")
    else -> SdpLine(part, "")
}

private fun defaultEol(lines: List<SdpLine>): String {
    for (line in lines) {
        if (line.eol != "") return line.eol
    }
    return "\n"
}

private fun requestedTokens(dtx: Boolean, fec: Boolean): List<Pair<String, String>> {
    val tokens = mutableListOf<Pair<String, String>>()
    if (fec) tokens.add("useinbandfec" to "1")
    if (dtx) tokens.add("usedtx" to "1")
    return tokens
}

private fun mergeParams(params: String, tokens: List<Pair<String, String>>): String {
    val parts = if (params.isNotEmpty()) params.split(";") else emptyList()
    val wanted = tokens.toMap()
    val handled = mutableSetOf<String>()
    val merged = parts.map { part ->
        val eq = part.indexOf('=')
        val key = if (eq == -1) part else part.substring(0, eq)
        val value = wanted[key]
        if (value != null) {
            handled.add(key)
            "$key=$value"
        } else {
            part
        }
    }.toMutableList()
    for ((key, value) in tokens) {
        if (!handled.contains(key)) merged.add("$key=$value")
    }
    return merged.joinToString(";")
}

private fun transformSection(
    section: List<SdpLine>,
    tokens: List<Pair<String, String>>,
    fallbackEol: String
): List<SdpLine> {
    val lines = section.map { SdpLine(it.content, it.eol) }.toMutableList()
    var pt = ""
    var rtpmapIndex = -1
    for (i in lines.indices) {
        val match = opusRtpmap.matchEntire(lines[i].content)
        if (match != null) {
            pt = match.groupValues[1]
            rtpmapIndex = i
            break
        }
    }
    if (rtpmapIndex == -1) return lines

    val fmtp = Regex("a=fmtp:$pt(?: (.*))?")
    for (i in lines.indices) {
        val match = fmtp.matchEntire(lines[i].content)
        if (match != null) {
            val params = match.groupValues[1]
            lines[i] = SdpLine("a=fmtp:$pt ${mergeParams(params, tokens)}", lines[i].eol)
            return lines
        }
    }

    val body = tokens.joinToString(";") { "${it.first}=${it.second}" }
    val rtpmap = lines[rtpmapIndex]
    val inserted = SdpLine("a=fmtp:$pt $body", rtpmap.eol)
    if (rtpmap.eol == "") {
        rtpmap.eol = fallbackEol
        inserted.eol = ""
    }
    lines.add(rtpmapIndex + 1, inserted)
    return lines
}
