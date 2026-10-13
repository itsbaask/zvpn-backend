import sys
sys.path.insert(0, "C:/VPN")
import unittest
import sync_backend

class TestCountryExtraction(unittest.TestCase):

    def test_fragment_extraction(self):
        cases = [
            ("NO-Oslo-1", "NO", "token"),
            ("IN_node", "IN", "token"),
            ("TR-Istanbul", "TR", "token"),
            ("RELAY-DE", "DE", "token"),
            ("vless-BG-3", "BG", "token"),
            ("Fast IN Server", None, None),
            ("NO limit server", None, None),
            ("IT support", None, None),
            ("US | @Frog_V2ray", None, None),
            ("[🌐] @Firmfox", None, None),
            ("🇳🇱 [BL] Server", "NL", "flag"),
            ("🇨🇷 TM (@x)", "CR", "flag"),
            ("🇬🇪 [HY] Грузия", "GE", "flag"),
        ]
        for frag, expected_cc, expected_kind in cases:
            cc, kind = sync_backend.extract_cc_from_fragment(frag)
            self.assertEqual(cc, expected_cc)
            self.assertEqual(kind, expected_kind)

    def test_vmess_base64_decoding(self):
        vmess_test_obj = {"ps": "NL 🇳🇱 | @Raydikalx"}
        vmess_b64 = sync_backend.base64.b64encode(sync_backend.json.dumps(vmess_test_obj).encode("utf-8")).decode("utf-8")
        vmess_uri = f"vmess://{vmess_b64}"
        v_frag = sync_backend.get_fragment(vmess_uri)
        v_cc, v_kind = sync_backend.extract_cc_from_fragment(v_frag)
        self.assertEqual(v_frag, "NL 🇳🇱 | @Raydikalx")
        self.assertEqual(v_cc, "NL")
        self.assertEqual(v_kind, "flag")

    def test_trusted_fragment_patterns_filter(self):
        raw_item = {
            "raw_uri": "vless://1111@1.2.3.4:443#DE 🇩🇪 | @Raydikalx"
        }

        # Case A: Untrusted pattern (TRUSTED_FRAGMENT_PATTERNS empty or not matching)
        sync_backend.TRUSTED_FRAGMENT_PATTERNS = []
        code, name, source = sync_backend.resolve_source_country(raw_item, "publicvpnlist", "pvl_01")
        self.assertIsNone(code)
        self.assertEqual(source, "none")

        # Case B: Trusted pattern (matching TRUSTED_FRAGMENT_PATTERNS)
        sync_backend.TRUSTED_FRAGMENT_PATTERNS = ["@Raydikalx"]
        code_trusted, name_trusted, source_trusted = sync_backend.resolve_source_country(raw_item, "publicvpnlist", "pvl_01")
        self.assertEqual(code_trusted, "DE")
        self.assertEqual(source_trusted, "uri_fragment_flag")

        # Reset
        sync_backend.TRUSTED_FRAGMENT_PATTERNS = []

if __name__ == "__main__":
    unittest.main()
