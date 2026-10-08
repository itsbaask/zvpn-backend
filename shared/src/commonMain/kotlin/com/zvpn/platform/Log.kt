package com.zvpn.platform

expect fun platformLog(tag: String, message: String)
expect fun platformLogDebug(tag: String, message: String)
expect fun platformLogWarn(tag: String, message: String, error: Throwable? = null)
expect fun platformLogError(tag: String, message: String, error: Throwable? = null)
