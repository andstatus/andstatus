package org.andstatus.app.util

import java.io.OutputStream

class MyLogVerboseStream(val logTag: String): OutputStream() {
    override fun write(b: Int) {
        // Ignore
    }

    override fun write(b: ByteArray) {
        MyLog.v(logTag, b.toString(Charsets.UTF_8))
    }
}
