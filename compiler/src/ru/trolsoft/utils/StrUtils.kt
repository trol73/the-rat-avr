package ru.trolsoft.utils

private val HEX_CHAR_ARRAY = "0123456789ABCDEF".toCharArray()
private val HEX_BYTE_STRINGS = arrayOfNulls<String>(256)
private val STRING_OF_ZERO = arrayOf("", "0", "00", "000", "0000", "00000", "000000", "0000000", "00000000", "000000000", "0000000000")


fun hexByte(b: Int): String {
    val v = b and 0xFF
    var result = HEX_BYTE_STRINGS[v]
    if (result == null) {
        result = Character.toString(HEX_CHAR_ARRAY[v.ushr(4)]) + HEX_CHAR_ARRAY[v and 0x0f]
        HEX_BYTE_STRINGS[v] = result
    }
    return result
}

fun hexWord(w: Int): String = "${hexByte(w.ushr(8))}${hexByte(w and 0xff)}"

