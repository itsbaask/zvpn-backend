import socket
import time
import logging
from typing import Tuple

logger = logging.getLogger("sync.checker")

def check_server_health(host: str, port: int, timeout_sec: float = 2.0) -> Tuple[bool, int]:
    """
    Performs active TCP probe on server host and port.
    Returns (is_alive, measured_latency_ms).
    """
    if not host or host in ["127.0.0.1", "0.0.0.0", "localhost"]:
        return False, 999

    try:
        start_time = time.time()
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.settimeout(timeout_sec)
        result = sock.connect_ex((host, port))
        latency = int((time.time() - start_time) * 1000)
        sock.close()

        if result == 0:
            return True, latency
        else:
            return False, 999
    except Exception as e:
        return False, 999
