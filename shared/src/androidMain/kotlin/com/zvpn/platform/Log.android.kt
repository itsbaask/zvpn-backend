package com.zvpn.platform

actual fun platformLog(tag: String, message: String) {
    android.util.Log.i(tag, message)
    InAppLog.write('I', tag, message, null, android.os.Process.myPid())
}

actual fun platformLogDebug(tag: String, message: String) {
    android.util.Log.d(tag, message)
    InAppLog.write('D', tag, message, null, android.os.Process.myPid())
}

actual fun platformLogWarn(tag: String, message: String, error: Throwable?) {
    android.util.Log.w(tag, message, error)
    InAppLog.write('W', tag, message, error, android.os.Process.myPid())
}

actual fun platformLogError(tag: String, message: String, error: Throwable?) {
    android.util.Log.e(tag, message, error)
    InAppLog.write('E', tag, message, error, android.os.Process.myPid())
}
