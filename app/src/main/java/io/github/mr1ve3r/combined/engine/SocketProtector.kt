package io.github.mr1ve3r.combined.engine

import java.net.DatagramSocket
import java.net.Socket

interface SocketProtector {
    fun protect(socket: Socket): Boolean
    fun protect(socket: DatagramSocket): Boolean
    fun protect(fd: Int): Boolean
}
