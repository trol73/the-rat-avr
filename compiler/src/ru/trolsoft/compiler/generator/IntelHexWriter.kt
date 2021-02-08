package ru.trolsoft.compiler.generator

import ru.trolsoft.utils.hexByte
import java.io.FileOutputStream
import java.io.OutputStream


class IntelHexWriter(
        val stream: OutputStream,
        private val bytesPerLine: Int = 16
) {

    var buf: ByteArray = ByteArray(bytesPerLine)
    var segmentAddress: Int = 0

    fun addData(startOffset: Int, data: ByteArray) {
        var offset = startOffset
        if (data.isEmpty()) {
            return
        }

        var pos = 0
        var bytesToAdd = data.size
        while (bytesToAdd > 0) {
            val partSize = when {
                bytesToAdd <= bytesPerLine -> bytesToAdd
                // can be true for first line if offset doesn't aligned
                offset % bytesPerLine != 0 -> bytesPerLine - offset % bytesPerLine
                else -> bytesPerLine
            }
            prepareBuf(partSize)
            System.arraycopy(data, pos, buf, 0, partSize)

            // Goto next segment if no more space available in current
            if (offset + partSize - 1 > segmentAddress + 0xffff) {
                val nextSegment = offset + bytesPerLine shr 4 shl 4
                addSegmentRecord(nextSegment)
                segmentAddress = nextSegment
            }
            addDataRecord(offset and 0xffff, buf)
            bytesToAdd -= partSize
            offset += partSize
            pos += partSize
        }
    }

    private fun prepareBuf(size: Int): ByteArray {
        if (buf.size == size) {
            return buf
        }
        buf = ByteArray(size)
        return buf
    }

    private fun addSegmentRecord(offset: Int) {
        val paragraph = offset shr 4
        val hi = paragraph shr 8 and 0xff
        val lo = paragraph and 0xff
        var crc = 2 + 2 + hi + lo
        crc = -crc and 0xff
        val rec = ":02000002" + hexByte(hi) + hexByte(lo) + hexByte(crc)
        writeStr(rec)
        // 02 0000 02 10 00 EC
        //:02 0000 04 00 01 F9
    }


    private fun addEofRecord() {
        writeStr(":00000001FF")
    }

    private fun writeStr(s: String) {
        stream.write(s.toByteArray())
        stream.write('\n'.toInt())
    }

    private fun addDataRecord(offset: Int, data: ByteArray) {
        val hi = offset shr 8 and 0xff
        val lo = offset and 0xff
        var crc = data.size + hi + lo
        var rec = ":" + hexByte(data.size) + hexByte(hi) + hexByte(lo) + "00"
        for (d in data) {
            rec += hexByte(d.toInt())
            crc += d.toInt()
        }
        crc = -crc and 0xff
        rec += hexByte(crc)
        writeStr(rec)
    }


    fun done() {
        addEofRecord()
        stream.flush()
    }


}


fun BinaryOutput.saveToIntelHex(stream: OutputStream, width: Int = 32) {
    val hexWriter = IntelHexWriter(stream, width)
    for (page in this.pages) {
        hexWriter.addData(page.startAddress, page.data!!)
    }
    hexWriter.done()
}

fun BinaryOutput.saveToIntelHex(fileName: String, width: Int = 32) {
    saveToIntelHex(FileOutputStream(fileName), width)
}







/*
	int offset = 0;
	while ( offset < length ) {
		int len = 16;
		if ( offset + len > length )
			len = length - offset;
		unsigned char crc = 0;
		std::string s = ":";
		s += HexToStr(len, 1);
		crc += len;
		s += HexToStr(offset, 2);
		crc += (offset >> 8) & 0xFF;
		crc += offset & 0xFF;
		s += HexToStr(0, 1);
		for (int i = 0; i < len; i++) {
			s += HexToStr(data[offset+i], 1);
			crc += data[offset+i];
		}
		crc = - crc;
		s += HexToStr(crc, 1);
		s += "\x0D\x0A";
		offset += len;
		f.Write((void*)s.c_str(), s.length());
	}
	std::string s = ":00000001FF\x0D\x0A";
	f.Write((void*)s.c_str(), s.length());
 */