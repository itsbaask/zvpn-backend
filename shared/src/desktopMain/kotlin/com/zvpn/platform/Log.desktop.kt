package com.zvpn.platform

actual fun platformLog(tag: String, message: String) {
    println("I/$tag: $message")
    InAppLog.write('I', tag, message, null, ProcessHandle.current().pid().toInt())
}

actual fun platformLogDebug(tag: String, message: String) {
    println("D/$tag: $message")
    InAppLog.write('D', tag, message, null, ProcessHandle.current().pid().toInt())
}

actual fun platformLogWarn(tag: String, message: String, error: Throwable?) {
    println("W/$tag: $message")
    InAppLog.write('W', tag, message, error, ProcessHandle.current().pid().toInt())
}

actual fun platformLogError(tag: String, message: String, error: Throwable?) {
    println("E/$tag: $message")
    error?.printStackTrace()
    InAppLog.write('E', tag, message, error, ProcessHandle.current().pid().toInt())
}
