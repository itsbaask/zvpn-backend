import unittest
from sync.sanitizer import sanitize_server_name, sanitize_openvpn_content
from sync.checker import check_server_health

class TestSanitizerAndChecker(unittest.TestCase):

    def test_sanitize_server_name_strips_spam(self):
        raw = "t.me/somechannel Modded by Rahul 12345"
        clean = sanitize_server_name(raw, "US", "United States", "openvpn", 1)
        self.assertNotIn("t.me", clean)
        self.assertNotIn("Modded", clean)
        self.assertIn("🇺🇸", clean)
        self.assertIn("United States", clean)

    def test_sanitize_openvpn_content_removes_spam_comments(self):
        raw_ovpn = """# Join t.me/spamchannel for free configs
client
dev tun
proto udp
remote 1.2.3.4 1194
"""
        cleaned = sanitize_openvpn_content(raw_ovpn)
        self.assertNotIn("t.me/spamchannel", cleaned)
        self.assertIn("client", cleaned)
        self.assertIn("remote 1.2.3.4 1194", cleaned)

    def test_check_server_health_unreachable_host(self):
        is_alive, latency = check_server_health("127.0.0.1", 9999, timeout_sec=0.2)
        self.assertFalse(is_alive)
        self.assertEqual(latency, 999)

if __name__ == '__main__':
    unittest.main()
