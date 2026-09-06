package com.tipil.app.data.remote

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

/** One `<subfield code="a">value</subfield>` within a MARC datafield. */
data class MarcSubfield(val code: String, val value: String)

/** One `<datafield tag="245" ind1="1" ind2="0">` and its subfields. */
data class MarcField(
    val tag: String,
    val ind1: String,
    val ind2: String,
    val subfields: List<MarcSubfield>
) {
    /** First value for [code], or null when the subfield is absent. */
    fun sub(code: String): String? =
        subfields.firstOrNull { it.code == code }?.value?.takeIf { it.isNotBlank() }
}

/**
 * A parsed MARC21 bibliographic record.
 *
 * Only the handful of fields the app displays are read back out; the rest of
 * the record is kept as-is so the accessors stay simple.
 */
data class MarcRecord(val fields: List<MarcField>) {

    fun fields(tag: String): List<MarcField> = fields.filter { it.tag == tag }

    /** First value of [code] in the first occurrence of [tag] that has it. */
    fun first(tag: String, code: String): String? =
        fields(tag).firstNotNullOfOrNull { it.sub(code) }

    /** Every value of [code] across all occurrences of [tag]. */
    fun all(tag: String, code: String): List<String> =
        fields(tag).mapNotNull { it.sub(code) }

    /**
     * Like [first], but prefers a field whose second indicator is [ind2].
     *
     * MARC 264 repeats with ind2="1" for publication and ind2="4" for
     * copyright; the publication statement is the one worth showing.
     */
    fun firstPreferringInd2(tag: String, code: String, ind2: String): String? =
        fields(tag).firstOrNull { it.ind2 == ind2 }?.sub(code)
            ?: first(tag, code)
}

/**
 * Minimal MARCXML reader for SRU responses.
 *
 * Namespace handling is deliberately off: the SRU envelope uses a `zs:` prefix
 * while the MARC record inside sits in a default namespace, so tags are
 * matched on local name only.
 */
object MarcXmlParser {

    /**
     * Returns the first complete MARC record in [xml], or null when the
     * response carries no records (an SRU hit count of zero) or cannot be read.
     */
    fun parseFirstRecord(xml: String): MarcRecord? {
        return try {
            val parser = XmlPullParserFactory.newInstance().apply {
                isNamespaceAware = false
            }.newPullParser()
            parser.setInput(StringReader(xml))

            val fields = mutableListOf<MarcField>()

            var tag: String? = null
            var ind1 = ""
            var ind2 = ""
            var subfields = mutableListOf<MarcSubfield>()
            var code: String? = null
            var text = StringBuilder()

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (localName(parser.name)) {
                        "datafield" -> {
                            tag = parser.getAttributeValue(null, "tag")
                            ind1 = parser.getAttributeValue(null, "ind1").orEmpty()
                            ind2 = parser.getAttributeValue(null, "ind2").orEmpty()
                            subfields = mutableListOf()
                        }
                        "subfield" -> {
                            code = parser.getAttributeValue(null, "code")
                            text = StringBuilder()
                        }
                    }

                    // Text can arrive in several chunks for one element.
                    XmlPullParser.TEXT -> text.append(parser.text)

                    XmlPullParser.END_TAG -> when (localName(parser.name)) {
                        "subfield" -> {
                            val c = code
                            if (c != null && tag != null) {
                                subfields.add(MarcSubfield(c, text.toString().trim()))
                            }
                            code = null
                        }
                        "datafield" -> {
                            tag?.let { fields.add(MarcField(it, ind1, ind2, subfields.toList())) }
                            tag = null
                        }
                        // The inner MARC </record> closes before the SRU
                        // envelope's own </zs:record>, so the first one to
                        // fire with fields collected is the record we want.
                        "record" -> if (fields.isNotEmpty()) return MarcRecord(fields)
                    }
                }
                event = parser.next()
            }

            if (fields.isNotEmpty()) MarcRecord(fields) else null
        } catch (_: Exception) {
            null
        }
    }

    private fun localName(name: String?): String = name?.substringAfterLast(':').orEmpty()
}
