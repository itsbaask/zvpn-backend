import unittest
from sync.ovpn import is_valid_ovpn_content, OvpnDownloader, DownloadResult
from sync.source import ServerInfo, MultiSourceHarvester

class TestOvpnDownloadAndSources(unittest.TestCase):

    def test_is_valid_ovpn_content_accepts_complete_profiles(self):
        valid_ovpn = """
client
dev tun
proto udp
remote 1.2.3.4 1194
cipher AES-256-GCM
<ca>
-----BEGIN CERTIFICATE-----
MIID...
-----END CERTIFICATE-----
</ca>
"""
        self.assertTrue(is_valid_ovpn_content(valid_ovpn))

    def test_is_valid_ovpn_content_rejects_fake_stubs(self):
        fake_stub = "client\ndev tun\nremote 1.2.3.4 1194"
        self.assertFalse(is_valid_ovpn_content(fake_stub))

    def test_is_valid_ovpn_content_rejects_html_or_json_errors(self):
        html_error = "<html><body>404 Not Found</body></html>"
        json_error = '{"error": "not_found", "message": "no server"}'
        self.assertFalse(is_valid_ovpn_content(html_error))
        self.assertFalse(is_valid_ovpn_content(json_error))

    def test_server_info_valid_profile_mapping(self):
        valid_server = ServerInfo(
            source_id="test1",
            host="1.2.3.4",
            ovpn_content="client\ndev tun\nremote 1.2.3.4 1194\ncipher AES-256-GCM\n<ca>cert</ca>"
        )
        self.assertTrue(valid_server.has_valid_profile_mapping())

        invalid_stub_server = ServerInfo(
            source_id="test2",
            host="1.2.3.4",
            ovpn_content="client\ndev tun\nremote 1.2.3.4 1194"
        )
        self.assertFalse(invalid_stub_server.has_valid_profile_mapping())

    def test_pvl_download_profile_token_flow(self):
        downloader = OvpnDownloader()
        pvl_server = ServerInfo(
            source_id="pvl_738166",
            source_name="publicvpnlist",
            profile_id="738166",
            profile_source_url="https://publicvpnlist.com/download/738166/"
        )
        res = downloader.download_profile(pvl_server)
        self.assertTrue(res.is_success)
        self.assertEqual(res.target_type, "pvl_token_download")
        self.assertTrue(is_valid_ovpn_content(res.content))
        self.assertIn("BEGIN CERTIFICATE", res.content)

if __name__ == '__main__':
    unittest.main()
