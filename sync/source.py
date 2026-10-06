import base64
import hashlib
import json
import logging
import os
import re
from pathlib import Path
from typing import Any, Dict, List, Optional, Set, Tuple
from urllib.parse import urlparse

import requests
from bs4 import BeautifulSoup
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry

from sync.sanitizer import sanitize_server_name, sanitize_openvpn_content

logger = logging.getLogger(__name__)

class SourceUnavailableError(Exception):
    pass

class CountryNormalizer:
    ISO_COUNTRIES: Dict[str, Tuple[str, str]] = {
        "AF": ("Afghanistan", "🇦🇫"), "AL": ("Albania", "🇦🇱"), "DZ": ("Algeria", "🇩🇿"),
        "AR": ("Argentina", "🇦🇷"), "AU": ("Australia", "🇦🇺"), "AT": ("Austria", "🇦🇹"),
        "AZ": ("Azerbaijan", "🇦🇿"), "BH": ("Bahrain", "🇧🇭"), "BD": ("Bangladesh", "🇧🇩"),
        "BE": ("Belgium", "🇧🇪"), "BR": ("Brazil", "🇧🇷"), "BG": ("Bulgaria", "🇧🇬"),
        "CA": ("Canada", "🇨🇦"), "CL": ("Chile", "🇨🇱"), "CN": ("China", "🇨🇳"),
        "CO": ("Colombia", "🇨🇴"), "HR": ("Croatia", "🇭🇷"), "CY": ("Cyprus", "🇨🇾"),
        "CZ": ("Czech Republic", "🇨🇿"), "DK": ("Denmark", "🇩🇰"), "EG": ("Egypt", "🇪🇬"),
        "EE": ("Estonia", "🇪🇪"), "FI": ("Finland", "🇫🇮"), "FR": ("France", "🇫🇷"),
        "DE": ("Germany", "🇩🇪"), "GR": ("Greece", "🇬🇷"), "HK": ("Hong Kong", "🇭🇰"),
        "HU": ("Hungary", "🇭🇺"), "IN": ("India", "🇮🇳"), "ID": ("Indonesia", "🇮🇩"),
        "IR": ("Iran", "🇮🇷"), "IQ": ("Iraq", "🇮🇶"), "IE": ("Ireland", "🇮🇪"),
        "IL": ("Israel", "🇮🇱"), "IT": ("Italy", "🇮🇹"), "JP": ("Japan", "🇯🇵"),
        "JO": ("Jordan", "🇯🇴"), "KZ": ("Kazakhstan", "🇰🇿"), "KR": ("South Korea", "🇰🇷"),
        "KW": ("Kuwait", "🇰🇼"), "LB": ("Lebanon", "🇱🇧"), "MY": ("Malaysia", "🇲🇾"),
        "MX": ("Mexico", "🇲🇽"), "NL": ("Netherlands", "🇳🇱"), "NZ": ("New Zealand", "🇳🇿"),
        "NO": ("Norway", "🇳🇴"), "OM": ("Oman", "🇴🇲"), "PK": ("Pakistan", "🇵🇰"),
        "PS": ("Palestine", "🇵🇸"), "PH": ("Philippines", "🇵🇭"), "PL": ("Poland", "🇵🇱"),
        "PT": ("Portugal", "🇵🇹"), "QA": ("Qatar", "🇶🇦"), "RO": ("Romania", "🇷🇴"),
        "RU": ("Russia", "🇷🇺"), "SA": ("Saudi Arabia", "🇸🇦"), "SG": ("Singapore", "🇸🇬"),
        "ZA": ("South Africa", "🇿🇦"), "ES": ("Spain", "🇪🇸"), "SE": ("Sweden", "🇸🇪"),
        "CH": ("Switzerland", "🇨🇭"), "TW": ("Taiwan", "🇹🇼"), "TH": ("Thailand", "🇹🇭"),
        "TR": ("Turkey", "🇹🇷"), "UA": ("Ukraine", "🇺🇦"), "AE": ("United Arab Emirates", "🇦🇪"),
        "GB": ("United Kingdom", "🇬🇧"), "US": ("United States", "🇺🇸"), "VN": ("Vietnam", "🇻🇳")
    }

    @classmethod
    def normalize(cls, country_raw: str, code_hint: str = "") -> Tuple[str, str, str]:
        code = (code_hint or "").strip().upper()
        if code and code in cls.ISO_COUNTRIES:
            name, flag = cls.ISO_COUNTRIES[code]
            return code, name, flag

        raw_str = (country_raw or "").strip()
        if len(raw_str) == 2 and raw_str.upper() in cls.ISO_COUNTRIES:
            c = raw_str.upper()
            name, flag = cls.ISO_COUNTRIES[c]
            return c, name, flag

        for iso_code, (official_name, flag) in cls.ISO_COUNTRIES.items():
            if official_name.upper() in raw_str.upper() or raw_str.upper() in official_name.upper():
                return iso_code, official_name, flag

        return "UN", "Global", "🌐"

class ServerInfo:
    def __init__(
        self,
        source_id: str,
        source_name: str = "vpngate",
        profile_id: str = "",
        profile_source_url: str = "",
        server_page_url: str = "",
        country_code: str = "XX",
        country_name: str = "Unknown",
        host: str = "",
        port: int = 1194,
        transport: str = "udp",
        protocol: str = "openvpn",
        transport_security: str = "",
        exit_ip: str = "",
        speed_mbps: float = 0.0,
        latency_ms: int = 0,
        network_score: int = 0,
        status: str = "verified",
        checked_at: str = "",
        config_sha256: str = "",
        ovpn_content: str = "",
        config_uri: str = "",
    ):
        self.source_id = str(source_id).strip()
        self.source_name = str(source_name).strip()
        self.profile_id = str(profile_id).strip() if profile_id else ""
        self.profile_source_url = profile_source_url.strip() if profile_source_url else ""
        self.server_page_url = server_page_url.strip() if server_page_url else ""

        iso_code, norm_name, flag = CountryNormalizer.normalize(country_name, country_code)
        self.country_code = iso_code
        self.country_name = norm_name
        self.flag = flag

        self.host = host or ""
        self.port = int(port or 1194)
        self.transport = (transport or "udp").lower()
        self.protocol = (protocol or "openvpn").lower()
        self.transport_security = (transport_security or "").lower()
        self.exit_ip = exit_ip or self.host
        self.speed_mbps = float(speed_mbps or 0.0)
        self.latency_ms = int(latency_ms or 0)
        self.network_score = int(network_score or 0)
        self.status = status or "verified"
        self.checked_at = checked_at or ""
        self.config_sha256 = config_sha256 or ""
        self.ovpn_content = sanitize_openvpn_content(ovpn_content or "")
        self.config_uri = config_uri or ""

        self.name = sanitize_server_name(
            raw_name=self.source_id,
            country_code=self.country_code,
            country_name=self.country_name,
            protocol=self.protocol
        )

    @property
    def deduplication_key(self) -> str:
        if self.config_sha256:
            return f"hash_{self.config_sha256}"
        if self.host and self.port:
            return f"{self.protocol}_{self.host.lower()}_{self.port}_{self.transport}"
        return self.source_id

    def has_valid_profile_mapping(self) -> bool:
        if self.protocol != "openvpn":
            return True
        if self.ovpn_content and len(self.ovpn_content) >= 30:
            return True
        if self.profile_source_url.startswith("http"):
            return True
        return False

    def to_dict(self) -> Dict[str, Any]:
        return {
            "id": self.source_id,
            "source_id": self.source_id,
            "source_name": self.source_name,
            "name": self.name,
            "country_code": self.country_code,
            "country_name": self.country_name,
            "flag": self.flag,
            "host": self.host,
            "port": self.port,
            "transport": self.transport,
            "protocol": self.protocol,
            "transport_security": self.transport_security,
            "exit_ip": self.exit_ip,
            "speed_mbps": self.speed_mbps,
            "latency_ms": self.latency_ms,
            "status": self.status,
            "config_sha256": self.config_sha256,
            "config_uri": self.config_uri,
        }

PERMANENT_KEY = "pvlk_eb8cc33936641d9492cf0a2740c8511bb737e1a611fa1035cb7c5c3006513bcc"

class MultiSourceHarvester:
    def __init__(self, access_key: Optional[str] = None):
        self.access_key = (access_key or "").strip() or PERMANENT_KEY
        self.session = requests.Session()
        self.session.headers.update({
            "User-Agent": "ZVPN-Enterprise-Sync/1.0",
            "Accept": "application/json, text/html, */*"
        })

    def harvest_all_sources(self) -> Dict[str, ServerInfo]:
        harvested: Dict[str, ServerInfo] = {}
        seen_dedup_keys: Set[str] = set()

        def add_server(server: ServerInfo):
            dedup_key = server.deduplication_key
            if dedup_key in seen_dedup_keys:
                return
            seen_dedup_keys.add(dedup_key)
            harvested[server.source_id] = server

        # 1. PublicVPNList v1 API (ALL protocols)
        try:
            pvl_servers = self._fetch_publicvpnlist_v1_api()
            logger.info(f"PublicVPNList v1 harvested {len(pvl_servers)} multi-protocol servers.")
            for s in pvl_servers.values():
                add_server(s)
        except Exception as e:
            logger.warning(f"PublicVPNList v1 harvest error: {e}")

        # 2. VPNGate Official CSV
        try:
            vpngate_servers = self._fetch_vpngate_csv()
            logger.info(f"VPNGate harvested {len(vpngate_servers)} servers.")
            for s in vpngate_servers.values():
                add_server(s)
        except Exception as e:
            logger.warning(f"VPNGate harvest error: {e}")

        # 3. Auto-OVPN GitHub
        try:
            auto_servers = self._fetch_auto_ovpn_github()
            logger.info(f"Auto-OVPN harvested {len(auto_servers)} servers.")
            for s in auto_servers.values():
                add_server(s)
        except Exception as e:
            logger.warning(f"Auto-OVPN harvest error: {e}")

        # 4. VPNBook
        try:
            vpnbook_servers = self._fetch_vpnbook()
            logger.info(f"VPNBook harvested {len(vpnbook_servers)} servers.")
            for s in vpnbook_servers.values():
                add_server(s)
        except Exception as e:
            logger.warning(f"VPNBook harvest error: {e}")

        # 5. Riseup VPN
        try:
            riseup_servers = self._fetch_riseup_vpn()
            logger.info(f"Riseup VPN harvested {len(riseup_servers)} servers.")
            for s in riseup_servers.values():
                add_server(s)
        except Exception as e:
            logger.warning(f"Riseup VPN harvest error: {e}")

        # 6. PublicVPNList Legacy Native API
        try:
            native_servers = self._fetch_publicvpnlist_native()
            logger.info(f"PublicVPNList Legacy Native API harvested {len(native_servers)} servers.")
            for s in native_servers.values():
                add_server(s)
        except Exception as e:
            logger.warning(f"PublicVPNList Legacy Native API harvest error: {e}")

        # 7. OMGVPN (3,000+ Multi-source Servers)
        try:
            omgvpn_servers = self._fetch_omgvpn()
            logger.info(f"OMGVPN harvested {len(omgvpn_servers)} servers.")
            for s in omgvpn_servers.values():
                add_server(s)
        except Exception as e:
            logger.warning(f"OMGVPN harvest error: {e}")

        return harvested

    def _fetch_publicvpnlist_v1_api(self) -> Dict[str, ServerInfo]:
        servers: Dict[str, ServerInfo] = {}
        protocols = [
            "openvpn", "vless", "vmess", "shadowsocks", "trojan",
            "hysteria", "hysteria2", "wireguard", "tuic", "anytls", "socks5", "http"
        ]
        headers = {
            "Authorization": f"Bearer {self.access_key}",
            "Accept": "application/json"
        }

        for proto in protocols:
            url = f"https://publicvpnlist.com/api/v1/servers?protocol={proto}&status=online&per_page=100"
            try:
                res = self.session.get(url, headers=headers, timeout=(10, 25))
                if res.status_code == 200:
                    payload = res.json()
                    data_list = payload.get("data", [])
                    for item in data_list:
                        if not isinstance(item, dict):
                            continue
                        sid = str(item.get("id") or "").strip()
                        if not sid:
                            continue

                        ip = str(item.get("ip") or item.get("exit_ip") or "")
                        port = int(item.get("port") or 443)
                        transport = str(item.get("transport") or "tcp").lower()
                        c_code = str(item.get("country_code") or "").upper()
                        c_name = str(item.get("country_name") or "")
                        speed = float(item.get("speed_mbps") or 0.0)
                        latency = int(item.get("latency_ms") or 0)
                        config_uri = str(item.get("config_uri") or item.get("config_download_url") or "")

                        servers[sid] = ServerInfo(
                            source_id=sid,
                            source_name="publicvpnlist_v1",
                            country_code=c_code,
                            country_name=c_name,
                            host=ip,
                            port=port,
                            transport=transport,
                            protocol=proto,
                            exit_ip=ip,
                            speed_mbps=speed,
                            latency_ms=latency,
                            config_uri=config_uri
                        )
            except Exception as e:
                logger.warning(f"Failed to fetch protocol {proto} from publicvpnlist: {e}")

        return servers

    def _fetch_publicvpnlist_native(self) -> Dict[str, ServerInfo]:
        url = "https://publicvpnlist.com/local/api/vpn-data.php"
        res = self.session.get(url, headers={"X-Requested-With": "XMLHttpRequest"}, timeout=(10, 25))
        if res.status_code != 200:
            return {}

        servers: Dict[str, ServerInfo] = {}
        data = res.json()
        if isinstance(data, list):
            for item in data:
                if not isinstance(item, dict) or item.get("active") is not True:
                    continue
                sid_num = str(item.get("id", ""))
                if not sid_num:
                    continue

                sid = f"pvl_{sid_num}"
                host = str(item.get("host") or item.get("ip") or "")
                port = int(item.get("port") or 1194)
                dl_url = f"https://publicvpnlist.com/download/{sid_num}/"

                servers[sid] = ServerInfo(
                    source_id=sid,
                    source_name="publicvpnlist",
                    profile_id=sid_num,
                    profile_source_url=dl_url,
                    server_page_url=dl_url,
                    country_code=str(item.get("country") or "XX").upper(),
                    country_name=str(item.get("countryName") or "Global"),
                    host=host,
                    port=port,
                    transport=str(item.get("proto") or "udp").lower(),
                    protocol="openvpn",
                    speed_mbps=float(item.get("speed") or 0.0)
                )

        return servers

    def _fetch_vpngate_csv(self) -> Dict[str, ServerInfo]:
        url = "https://www.vpngate.net/api/iphone/"
        res = self.session.get(url, timeout=(10, 25))
        if res.status_code != 200:
            return {}

        servers: Dict[str, ServerInfo] = {}
        for line in res.text.splitlines():
            line_str = line.strip()
            if not line_str or line_str.startswith("*") or line_str.startswith("#"):
                continue
            parts = line_str.split(",")
            if len(parts) >= 15:
                ip = parts[1].strip()
                country_long = parts[5].strip()
                country_short = parts[6].strip()
                ovpn_b64 = parts[14].strip()

                if ovpn_b64 and len(ovpn_b64) > 100:
                    try:
                        ovpn_text = base64.b64decode(ovpn_b64).decode("utf-8", "ignore")
                        sid = f"vpngate_{ip}_1194_udp"
                        servers[sid] = ServerInfo(
                            source_id=sid,
                            source_name="vpngate",
                            country_code=country_short,
                            country_name=country_long,
                            host=ip,
                            port=1194,
                            transport="udp",
                            protocol="openvpn",
                            ovpn_content=ovpn_text
                        )
                    except Exception:
                        pass
        return servers

    def _fetch_auto_ovpn_github(self) -> Dict[str, ServerInfo]:
        url = "https://raw.githubusercontent.com/9xN/auto-ovpn/main/json/data.json"
        res = self.session.get(url, timeout=(10, 25))
        if res.status_code != 200:
            return {}

        servers: Dict[str, ServerInfo] = {}
        data = res.json()
        items = data if isinstance(data, list) else []
        for idx, item in enumerate(items):
            if isinstance(item, dict) and "openvpn_configdata_base64" in item:
                ovpn_b64 = item["openvpn_configdata_base64"]
                host = item.get("ip", "")
                if ovpn_b64 and host:
                    try:
                        ovpn_text = base64.b64decode(ovpn_b64).decode("utf-8", "ignore")
                        sid = f"autoovpn_{host}_1194_udp"
                        servers[sid] = ServerInfo(
                            source_id=sid,
                            source_name="auto_ovpn",
                            country_code=item.get("countryshort", "XX"),
                            country_name=item.get("countrylong", "Global"),
                            host=host,
                            port=1194,
                            protocol="openvpn",
                            ovpn_content=ovpn_text
                        )
                    except Exception:
                        pass
        return servers

    def _fetch_vpnbook(self) -> Dict[str, ServerInfo]:
        url = "https://www.vpnbook.com/freevpn/openvpn"
        res = self.session.get(url, timeout=(10, 25))
        if res.status_code != 200:
            return {}

        soup = BeautifulSoup(res.text, "html.parser")
        page_text = soup.get_text()

        username = "vpnbook"
        password = ""
        pass_match = re.search(r'Password\s*([a-zA-Z0-9]+)', page_text, re.I)
        if pass_match:
            password = pass_match.group(1).strip()

        server_matches = re.findall(r'([A-Za-z]+\s+Server\s+\d+)[^\w]*([a-z0-9\.-]+\.vpnbook\.com)', page_text, re.I)
        servers: Dict[str, ServerInfo] = {}
        for label, host in server_matches:
            sid = f"vpnbook_{host.replace('.', '_')}_1194_udp"
            ovpn_config = f"client\ndev tun\nproto udp\remote {host} 1194\n<auth-user-pass>\n{username}\n{password}\n</auth-user-pass>"
            servers[sid] = ServerInfo(
                source_id=sid,
                source_name="vpnbook",
                host=host,
                port=1194,
                protocol="openvpn",
                ovpn_content=ovpn_config
            )
        return servers

    def _fetch_riseup_vpn(self) -> Dict[str, ServerInfo]:
        url = "https://black.riseup.net/3/config/eip-service.json"
        servers: Dict[str, ServerInfo] = {}
        try:
            res = self.session.get(url, timeout=(10, 25))
            if res.status_code == 200:
                data = res.json()
                for gw in data.get("gateways", []):
                    ip = gw.get("ip_address")
                    if ip:
                        sid = f"riseup_{ip}_1194_udp"
                        servers[sid] = ServerInfo(
                            source_id=sid,
                            source_name="riseup_vpn",
                            host=ip,
                            port=1194,
                            protocol="openvpn",
                            ovpn_content=f"client\ndev tun\nremote {ip} 1194"
                        )
        except Exception:
            pass
        return servers

    def _fetch_omgvpn(self) -> Dict[str, ServerInfo]:
        url = "https://omgvpn.com/"
        servers: Dict[str, ServerInfo] = {}
        try:
            res = self.session.get(url, timeout=(10, 25))
            if res.status_code != 200:
                return servers

            html = res.text
            def extract_array(name: str) -> List[str]:
                pattern = rf"\blet\s+{re.escape(name)}\s*=\s*(\[.*?\]);"
                match = re.search(pattern, html, re.S)
                if not match:
                    return []
                try:
                    return [str(v).strip() for v in json.loads(match.group(1)) if str(v).strip()]
                except Exception:
                    return []

            arr1 = extract_array("array1")
            arr2 = extract_array("array2")

            for raw in arr1:
                cleaned = raw.strip().rstrip("↓").strip()
                parts = [p.strip().rstrip("↓").strip() for p in cleaned.split("•")]
                if len(parts) >= 7:
                    hostname = parts[1]
                    ipv4 = parts[6]
                    host = hostname or ipv4
                    if host:
                        sid = f"omgvpn_a1_{host}"
                        servers[sid] = ServerInfo(
                            source_id=sid,
                            source_name="omgvpn_vpngate",
                            host=host,
                            port=1194,
                            protocol="openvpn",
                            ovpn_content=f"client\ndev tun\nremote {host} 1194"
                        )

            for raw in arr2:
                cleaned = raw.strip().rstrip("↓").strip()
                parts = [p.strip().rstrip("↓").strip() for p in cleaned.split("•")]
                if len(parts) >= 6:
                    hostname = parts[3]
                    ipv4 = parts[5]
                    host = hostname or ipv4
                    if host:
                        sid = f"omgvpn_a2_{host}"
                        servers[sid] = ServerInfo(
                            source_id=sid,
                            source_name="omgvpn_giamping",
                            host=host,
                            port=1194,
                            protocol="openvpn",
                            ovpn_content=f"client\ndev tun\nremote {host} 1194"
                        )
        except Exception:
            pass
        return servers
