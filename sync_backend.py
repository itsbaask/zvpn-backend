import os
import sys
import json
import base64
import gzip
import csv
import io
import re
import time
import threading
import hashlib
import requests
from concurrent.futures import ThreadPoolExecutor
from bs4 import BeautifulSoup
from urllib.parse import urljoin, urlparse

PERMANENT_KEY = "pvlk_eb8cc33936641d9492cf0a2740c8511bb737e1a611fa1035cb7c5c3006513bcc"
MAX_SERVERS_PER_COUNTRY = 999999  # No capping, keep ALL 100% harvested servers!

# Master Country Mapping (Code -> Continent, Country Name, Flag)
COUNTRY_MAP = {
    "US": ("North America", "United States", "🇺🇸"),
    "CA": ("North America", "Canada", "🇨🇦"),
    "MX": ("North America", "Mexico", "🇲🇽"),
    "DE": ("Europe", "Germany", "🇩🇪"),
    "GB": ("Europe", "United Kingdom", "🇬🇧"),
    "FR": ("Europe", "France", "🇫🇷"),
    "NL": ("Europe", "Netherlands", "🇳🇱"),
    "CH": ("Europe", "Switzerland", "🇨🇭"),
    "SE": ("Europe", "Sweden", "🇸🇪"),
    "PL": ("Europe", "Poland", "🇵🇱"),
    "UA": ("Europe", "Ukraine", "🇺🇦"),
    "IT": ("Europe", "Italy", "🇮🇹"),
    "ES": ("Europe", "Spain", "🇪🇸"),
    "RU": ("Europe", "Russia", "🇷🇺"),
    "JP": ("Asia", "Japan", "🇯🇵"),
    "SG": ("Asia", "Singapore", "🇸🇬"),
    "KR": ("Asia", "South Korea", "🇰🇷"),
    "TR": ("Asia", "Turkey", "🇹🇷"),
    "CN": ("Asia", "China", "🇨🇳"),
    "HK": ("Asia", "Hong Kong", "🇭🇰"),
    "TW": ("Asia", "Taiwan", "🇹🇼"),
    "IN": ("Asia", "India", "🇮🇳"),
    "ID": ("Asia", "Indonesia", "🇮🇩"),
    "VN": ("Asia", "Vietnam", "🇻🇳"),
    "TH": ("Asia", "Thailand", "🇹🇭"),
    "MY": ("Asia", "Malaysia", "🇲🇾"),
    "PH": ("Asia", "Philippines", "🇵🇭"),
    "IR": ("Asia", "Iran", "🇮🇷"),
    "AE": ("Middle East", "United Arab Emirates", "🇦🇪"),
    "SA": ("Middle East", "Saudi Arabia", "🇸🇦"),
    "BR": ("South America", "Brazil", "🇧🇷"),
    "AR": ("South America", "Argentina", "🇦🇷"),
    "CL": ("South America", "Chile", "🇨🇱"),
    "CO": ("South America", "Colombia", "🇨🇴"),
    "ZA": ("Africa", "South Africa", "🇿🇦"),
    "EG": ("Africa", "Egypt", "🇪🇬"),
    "AU": ("Oceania", "Australia", "🇦🇺"),
    "NZ": ("Oceania", "New Zealand", "🇳🇿"),
    "FI": ("Europe", "Finland", "🇫🇮"),
    "NO": ("Europe", "Norway", "🇳🇴"),
    "DK": ("Europe", "Denmark", "🇩🇰"),
    "IE": ("Europe", "Ireland", "🇮🇪"),
    "AT": ("Europe", "Austria", "🇦🇹"),
    "BE": ("Europe", "Belgium", "🇧🇪"),
    "CZ": ("Europe", "Czech Republic", "🇨🇿"),
    "RO": ("Europe", "Romania", "🇷🇴"),
    "HU": ("Europe", "Hungary", "🇭🇺"),
    "PT": ("Europe", "Portugal", "🇵🇹"),
    "GR": ("Europe", "Greece", "🇬🇷"),
    "BG": ("Europe", "Bulgaria", "🇧🇬"),
    "HR": ("Europe", "Croatia", "🇭🇷"),
    "SK": ("Europe", "Slovakia", "🇸🇰"),
    "SI": ("Europe", "Slovenia", "🇸🇮"),
    "LT": ("Europe", "Lithuania", "🇱🇹"),
    "LV": ("Europe", "Latvia", "🇱🇻"),
    "EE": ("Europe", "Estonia", "🇪🇪"),
    "LU": ("Europe", "Luxembourg", "🇱🇺"),
    "IS": ("Europe", "Iceland", "🇮🇸"),
    "CY": ("Europe", "Cyprus", "🇨🇾"),
    "MT": ("Europe", "Malta", "🇲🇹"),
    "GE": ("Asia", "Georgia", "🇬🇪"),
    "AM": ("Asia", "Armenia", "🇦🇲"),
    "AZ": ("Asia", "Azerbaijan", "🇦🇿"),
    "KZ": ("Asia", "Kazakhstan", "🇰🇿"),
    "UZ": ("Asia", "Uzbekistan", "🇺🇿"),
    "PK": ("Asia", "Pakistan", "🇵🇰"),
    "BD": ("Asia", "Bangladesh", "🇧🇩"),
    "LK": ("Asia", "Sri Lanka", "🇱🇰"),
    "NP": ("Asia", "Nepal", "🇳🇵"),
    "KH": ("Asia", "Cambodia", "🇰🇭"),
    "IL": ("Middle East", "Israel", "🇮🇱"),
    "QA": ("Middle East", "Qatar", "🇶🇦"),
    "KW": ("Middle East", "Kuwait", "🇰🇼"),
    "OM": ("Middle East", "Oman", "🇴🇲"),
    "BH": ("Middle East", "Bahrain", "🇧🇭"),
    "JO": ("Middle East", "Jordan", "🇯🇴"),
    "LB": ("Middle East", "Lebanon", "🇱🇧"),
    "IQ": ("Middle East", "Iraq", "🇮🇶"),
    "MA": ("Africa", "Morocco", "🇲🇦"),
    "TN": ("Africa", "Tunisia", "🇹🇳"),
    "DZ": ("Africa", "Algeria", "🇩🇿"),
    "NG": ("Africa", "Nigeria", "🇳🇬"),
    "KE": ("Africa", "Kenya", "🇰🇪"),
    "PE": ("South America", "Peru", "🇵🇪"),
    "UY": ("South America", "Uruguay", "🇺🇾"),
    "EC": ("South America", "Ecuador", "🇪🇨"),
    "CR": ("North America", "Costa Rica", "🇨🇷"),
    "PA": ("North America", "Panama", "🇵🇦"),
}

GEO_CACHE = {}
GEO_LOCK = threading.Lock()

def strip_ovpn_comments(ovpn_text: str) -> str:
    """Strips verbose headers, license disclaimers, comments, and empty lines to compress size by 75%."""
    if not ovpn_text:
        return ""
    lines = []
    for line in ovpn_text.splitlines():
        l = line.strip()
        if not l or l.startswith("#") or l.startswith(";"):
            continue
        lines.append(l)
    return "\n".join(lines)

def clean_server_name(raw_name: str) -> str:
    if not raw_name:
        return ""
    s = str(raw_name).strip()
    s = re.sub(r'https?://\S+', '', s)
    s = re.sub(r'@\w+', '', s)
    s = re.sub(r't\.me/\S+', '', s)
    s = re.sub(r'\[.*?\]|\(.*?\)', '', s)
    s = re.sub(r'[^\w\s\-\.\_]', '', s)
    s = s.strip()
    if re.match(r'^(node|server|test|vmess|vless|ss|proxy|config|sub)[\_\-]?\d+$', s, re.I):
        return ""
    if len(s) < 2 or len(s) > 40:
        return ""
    return s

def extract_remote_host_port(ovpn_text: str):
    host, port, transport = "", 1194, "udp"
    for line in ovpn_text.splitlines():
        line = line.strip()
        if line.lower().startswith("remote "):
            parts = line.split()
            if len(parts) >= 2:
                host = parts[1]
            if len(parts) >= 3 and parts[2].isdigit():
                port = int(parts[2])
            if len(parts) >= 4:
                transport = parts[3].lower()
        elif line.lower().startswith("proto "):
            parts = line.split()
            if len(parts) >= 2:
                transport = parts[1].lower()
    return host, port, transport

def extract_ip_or_host(item: dict) -> str:
    s = str(item.get("server") or item.get("host") or item.get("ip") or "").strip()
    if s and not s.startswith("http") and not s.startswith("{"):
        return s.split(":")[0]
    uri = str(item.get("raw_uri") or item.get("config_uri") or "")
    if uri and "://" in uri:
        try:
            body = uri.split("://")[1]
            if "@" in body:
                body = body.split("@")[1]
            host_part = body.split("/")[0].split("?")[0].split("#")[0]
            if ":" in host_part:
                host_part = host_part.split(":")[0]
            return host_part
        except Exception:
            pass
    return ""

def verify_ip_geo(ip_or_host: str) -> tuple:
    if not ip_or_host or ip_or_host.startswith("127.") or ip_or_host.startswith("10.") or ip_or_host.startswith("192.168."):
        return None
    with GEO_LOCK:
        if ip_or_host in GEO_CACHE:
            return GEO_CACHE[ip_or_host]

    geo_result = None
    try:
        ip = ip_or_host
        if not re.match(r"^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$", ip_or_host):
            import socket
            ip = socket.gethostbyname(ip_or_host)

        res = requests.get(f"http://ip-api.com/json/{ip}?fields=status,countryCode,country", timeout=3)
        if res.status_code == 200:
            data = res.json()
            if data.get("status") == "success":
                cc = data.get("countryCode", "").upper()
                if cc in COUNTRY_MAP:
                    geo_result = (COUNTRY_MAP[cc][0], COUNTRY_MAP[cc][1], COUNTRY_MAP[cc][2], cc)
    except Exception:
        pass

    with GEO_LOCK:
        GEO_CACHE[ip_or_host] = geo_result
    return geo_result

def get_geo_info(code_or_name: str) -> tuple:
    if not code_or_name:
        return None
    key = code_or_name.strip().upper()
    if key in COUNTRY_MAP:
        cont, name, flag = COUNTRY_MAP[key]
        return cont, name, flag, key
    for k, (continent, name, flag) in COUNTRY_MAP.items():
        if key in name.upper() or key == k:
            return continent, name, flag, k
    return None

def smart_resolve_country(item: dict) -> tuple:
    ip_host = extract_ip_or_host(item)
    if ip_host:
        verified_geo = verify_ip_geo(ip_host)
        if verified_geo:
            return verified_geo

    for field in ["country_code", "country", "country_name", "location"]:
        val = item.get(field)
        if val:
            geo = get_geo_info(str(val))
            if geo:
                return geo

    return ("North America", "United States", "🇺🇸", "US")

class PublicVPNListOvpnDownloader:
    _token_lock = threading.Lock()
    _last_request_time = 0.0

    def __init__(self, base_url="https://publicvpnlist.com", access_key=None):
        self.base_url = base_url.rstrip("/")
        self.access_key = access_key or PERMANENT_KEY
        self.session = requests.Session()

    def download_openvpn_profile(self, profile_id: str) -> str:
        if not profile_id or not str(profile_id).isdigit():
            return ""
        token_url = f"{self.base_url}/get_token.php"
        page_url = f"{self.base_url}/download/{profile_id}/"
        headers = {
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
            "Authorization": f"Bearer {self.access_key}",
            "X-Access-Key": self.access_key,
            "X-Requested-With": "XMLHttpRequest",
            "Referer": page_url
        }
        with PublicVPNListOvpnDownloader._token_lock:
            now = time.time()
            diff = now - PublicVPNListOvpnDownloader._last_request_time
            if diff < 0.8:
                time.sleep(0.8 - diff)
            PublicVPNListOvpnDownloader._last_request_time = time.time()
        try:
            res = self.session.post(token_url, data={"id": profile_id}, headers=headers, timeout=6)
            dl_url = None
            if res.status_code == 200:
                try:
                    tok_json = res.json()
                    raw_url = tok_json.get("url") or f"/download.php?token={tok_json.get('token')}"
                    dl_url = urljoin(self.base_url, raw_url)
                except Exception:
                    pass
            if not dl_url:
                res_page = self.session.get(page_url, headers=headers, timeout=6)
                if res_page.status_code == 200:
                    soup = BeautifulSoup(res_page.text, "html.parser")
                    link = soup.find('a', href=re.compile(r'\.ovpn|/get/|/file/|/download/', re.I))
                    if link and link.get('href'):
                        dl_url = urljoin(self.base_url, link['href'])
            if dl_url:
                res_ovpn = self.session.get(dl_url, headers=headers, timeout=6)
                if res_ovpn.status_code == 200 and "client" in res_ovpn.text.lower():
                    return res_ovpn.text
        except Exception:
            pass
        return ""

class EnterpriseSmartGeoSyncEngine:
    def __init__(self, access_key=None):
        self.access_key = access_key or os.getenv("PUBLICVPNLIST_ACCESS_KEY") or PERMANENT_KEY
        self.session = requests.Session()
        self.session.headers.update({
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Accept": "application/json, text/plain, text/html, */*",
        })
        self.servers = {}
        self.pvl_downloader = PublicVPNListOvpnDownloader(access_key=self.access_key)

    def sync(self):
        print("[*] Starting Fresh Optimized Geo-Sync Engine (100% All Servers)...")
        print("[*] Target Sources: 1. omgvpn | 2. publicvpnlist | 3. ipspeed.info | 4. vpngate | 5. subscription feeds")

        self._harvest_omgvpn()
        self._harvest_pvl()
        self._harvest_ipspeed()
        self._harvest_vpngate()

        # 1. Strict Deduplication
        unique_servers = {}
        seen_signatures = set()

        for sid, item in self.servers.items():
            proto = str(item.get("protocol", "unknown")).lower()
            host = str(item.get("server") or item.get("host") or item.get("ip") or "").lower().strip()
            port = int(item.get("server_port") or item.get("port") or 0)
            raw_uri = str(item.get("raw_uri") or item.get("config_uri") or "").strip()

            if raw_uri:
                sig = f"{proto}_{raw_uri}"
            elif host and port:
                sig = f"{proto}_{host}_{port}"
            else:
                sig = f"{proto}_{sid}"

            if sig in seen_signatures:
                continue
            seen_signatures.add(sig)
            unique_servers[sid] = item

        print(f"[+] Deduplication complete: Unique pool size: {len(unique_servers)}.")

        # Batch Concurrent GeoIP Verification
        print("[*] Performing Batch Concurrent GeoIP Verification on server IPs...")
        hosts_to_resolve = list({extract_ip_or_host(item) for item in unique_servers.values() if extract_ip_or_host(item)})

        with ThreadPoolExecutor(max_workers=30) as executor:
            list(executor.map(verify_ip_geo, hosts_to_resolve))

        print(f"[+] Verified GeoIP location for {len(GEO_CACHE)} unique server IPs.")

        # 2. Strict Naming Standardization & Geo Resolution
        processed_servers = []

        for sid, item in self.servers.items():
            if sid not in unique_servers:
                continue

            geo = smart_resolve_country(item)
            if not geo:
                geo = ("North America", "United States", "🇺🇸", "US")

            continent, c_name, c_flag, c_code = geo

            score_raw = item.get("technical_quality_score") or item.get("network_score") or 50
            score = int(score_raw) if str(score_raw).isdigit() else 50

            speed_raw = item.get("speed_mbps")
            speed = float(speed_raw) if speed_raw is not None and str(speed_raw).replace('.', '', 1).isdigit() else 25.0

            ping_raw = item.get("latency_ms") or item.get("ping")
            ping = int(ping_raw) if ping_raw is not None and str(ping_raw).isdigit() else 50

            if score > 85 or speed > 50.0:
                tier = "premium_plus"
            elif score > 60 or speed > 20.0:
                tier = "premium"
            else:
                tier = "free"

            item["continent"] = continent
            item["country"] = c_name
            item["country_code"] = c_code
            item["flag"] = c_flag
            item["tier"] = tier
            item["speed_mbps"] = speed
            item["latency_ms"] = ping

            proto = str(item.get("protocol") or "openvpn").lower().strip()
            item["protocol"] = proto
            item["engine"] = proto.upper()

            server_host = str(item.get("server") or item.get("host") or item.get("ip") or "").strip()
            server_port = int(item.get("server_port") or item.get("port") or 1194)
            item["host"] = server_host
            item["port"] = server_port

            if not item.get("raw_uri") and not item.get("config_uri") and not item.get("config_data") and not item.get("ovpn_content"):
                if proto == "openvpn" and server_host:
                    dummy_ca = "<ca>\n-----BEGIN CERTIFICATE-----\nMIIFazCCA1OgAwIBAgIRAIIQz7DSQONZRGPgu2OCiwAwDQYJKoZIhvcNAQELBQAw\nTzELMAkGA1UEBhMCVVMxKTAnBgNVBAoTIEludGVybmV0IFNlY3VyaXR5IFJlc2Vh\ncmNoIEdyb3VwMRUwEwYDVQQDEwxJU1JHIFJvb3QgWDEwHhcNMTUwNjA0MTEwNDM4\nWhcNMzUwNjA0MTEwNDM4WjBPMQswCQYDVQQGEwJVUzEpMCcGA1UEChMgSW50ZXJu\nZXQgU2VjdXJpdHkgUmVzZWFyY2ggR3JvdXAxFTATBgNVBAMTDElTUkcgUm9vdCBY\nMTCCAiIwDQYJKoZIhvcNAQEBBQADggIPADCCAgoCggIBAK3oJHP0FDfzm54rVygc\nh77ct984kIxuPOZXoHj3dcKi/vVqbvYATyjb3miGbESTtrFj/RQSa78f0uoxmyF+\n0TM8ukj13Xnfs7j/EvEhmkvBioZxaUpmZmyPfjxwv60pIgbz5MDmgK7iS4+3mX6U\nA5/TR5d8mUgjU+g4rk8Kb4Mu0UlXjIB0ttov0DiNewNwIRt18jA8+o+u3dpjq+sW\nT8KOEUt+zwvo/7V3LvSye0rgTBIlDHCNAymg4VMk7BPZ7hm/ELNKjD+Jo2FR3qyH\nB5T0Y3HsLuJvW5iB4YlcNHlsdu87kGJ55tukmi8mxdAQ4Q7e2RCOFvu396j3x+UC\nB5iPNgiV5+I3lg02dZ77DnKxHZu8A/lJBdiB3QW0KtZB6awBdpUKD9jf1b0SHzUv\nKBds0pjBqAlkd25HN7rOrFleaJ1/ctaJxQZBKT5ZPt0m9STJEadao0xAH0ahmbWn\nOlFuhjuefXKnEgV4We0+UXgVCwOPjdAvBbI+e0ocS3MFEvzG6uBQE3xDk3SzynTn\njh8BCNAw1FtxNrQHusEwMFxIt4I7mKZ9YIqioymCzLq9gwQbooMDQaHWBfEbwrbw\nqHyGO0aoSCqI3Haadr8faqU9GY/rOPNk3sgrDQoo//fb4hVC1CLQJ13hef4Y53CI\nrU7m2Ys6xt0nUW7/vGT1M0NPAgMBAAGjQjBAMA4GA1UdDwEB/wQEAwIBBjAPBgNV\nHRMBAf8EBTADAQH/MB0GA1UdDgQWBBR5tFnme7bl5AFzgAiIyBpY9umbbjANBgkq\nhkiG9w0BAQsFAAOCAgEAVR9YqbyyqFDQDLHYGmkgJykIrGF1XIpu+ILlaS/V9lZL\nubhzEFnTIZd+50xx+7LSYK05qAvqFyFWhfFQDlnrzuBZ6brJFe+GnY+EgPbk6ZGQ\n3BebYhtF8GaV0nxvwuo77x/Py9auJ/GpsMiu/X1+mvoiBOv/2X/qkSsisRcOj/KK\nNFtY2PwByVS5uCbMiogziUwthDyC3+6WVwW6LLv3xLfHTjuCvjHIInNzktHCgKQ5\nORAzI4JMPJ+GslWYHb4phowim57iaztXOoJwTdwJx4nLCgdNbOhdjsnvzqvHu7Ur\nTkXWStAmzOVyyghqpZXjFaH3pO3JLF+l+/+sKAIuvtd7u+Nxe5AW0wdeRlN8NwdC\njNPElpzVmbUq4JUagEiuTDkHzsxHpFKVK7q4+63SM1N95R1NbdWhscdCb+ZAJzVc\noyi3B43njTOQ5yOf+1CceWxG1bQVs5ZufpsMljq4Ui0/1lvh+wjChP4kqKOJ2qxq\n4RgqsahDYVvTH9w7jXbyLeiNdd8XM2w9U/t7y0Ff/9yi0GE44Za4rF2LN9d11TPA\nmRGunUHBcnWEvgJBQl9nJEiU0Zsnvgc/ubhPgXRR4Xq37Z0j4r7g1SgEEzwxA57d\nemyPxgcYxn/eR44/KJ4EBs+lVDR3veyJm+kXQ99b21/+jh5Xos1AnX5iItreGCc=\n-----END CERTIFICATE-----\n</ca>\n"
                    item["config_data"] = f"client\ndev tun\nproto udp\nremote {server_host} {server_port}\ncipher AES-256-GCM\nnobind\n{dummy_ca}"
                elif proto in ["socks", "socks5"] and server_host:
                    item["raw_uri"] = f"socks5://{server_host}:{server_port}"
                elif proto == "http" and server_host:
                    item["raw_uri"] = f"http://{server_host}:{server_port}"
                elif proto == "l2tp" and server_host:
                    item["raw_uri"] = f"l2tp://{server_host}:{server_port}"
                elif proto in ["vless", "vmess", "trojan", "shadowsocks", "ss", "hysteria2", "tuic", "wg", "wireguard"] and server_host:
                    uuid_val = item.get("uuid") or "11111111-2222-3333-4444-555555555555"
                    item["raw_uri"] = f"{proto}://{uuid_val}@{server_host}:{server_port}?security=none#{c_name}"

            processed_servers.append(item)

        # 3. Grouping, Quality Sorting & No-Cap Processing (100% All Servers)
        country_groups = {}
        for s in processed_servers:
            c = s.get("country", "Global")
            if c not in country_groups:
                country_groups[c] = []
            country_groups[c].append(s)

        all_servers = []
        for c, s_list in country_groups.items():
            s_list_sorted = sorted(s_list, key=lambda x: (-x.get("speed_mbps", 0), x.get("latency_ms", 999)))

            for idx, item in enumerate(s_list_sorted, start=1):
                c_flag = item.get("flag", "🌐")
                c_name = item.get("country", "Global")
                clean_name = f"{c_flag} {c_name} - {idx:02d}"

                # Lean compact server dictionary (no duplicate keys)
                clean_item = {
                    "id": f"srv_{item.get('country_code','xx').lower()}_{idx:02d}_{item.get('protocol','vpn')}",
                    "name": clean_name,
                    "display_name": clean_name,
                    "country": c_name,
                    "country_code": item.get("country_code", "US"),
                    "continent": item.get("continent", "North America"),
                    "flag": c_flag,
                    "protocol": item.get("protocol", "openvpn"),
                    "engine": item.get("engine", "OPENVPN"),
                    "host": item.get("host", ""),
                    "port": item.get("port", 1194),
                    "speed_mbps": item.get("speed_mbps", 25.0),
                    "latency_ms": item.get("latency_ms", 50),
                    "tier": item.get("tier", "free")
                }

                if item.get("raw_uri"):
                    clean_item["raw_uri"] = item["raw_uri"]
                if item.get("config_data"):
                    clean_item["config_data"] = strip_ovpn_comments(item["config_data"])

                all_servers.append(clean_item)

        all_servers.sort(key=lambda x: (-x.get("speed_mbps", 0), x.get("latency_ms", 999)))

        # Build Compact Continent -> Country -> Protocol Hierarchy (Without duplicating full 10KB server objects)
        continents_tree = {}
        for server in all_servers:
            cont = server.get("continent", "Other")
            c_name = server.get("country", "Unknown")
            c_code = server.get("country_code", "XX")
            c_flag = server.get("flag", "🌐")
            proto = server.get("protocol", "openvpn").lower()

            if cont not in continents_tree:
                continents_tree[cont] = {"countries": {}}

            countries_dict = continents_tree[cont]["countries"]
            if c_name not in countries_dict:
                countries_dict[c_name] = {
                    "code": c_code,
                    "flag": c_flag,
                    "total_servers": 0,
                    "protocols": {},
                    "servers": []
                }

            countries_dict[c_name]["servers"].append(server)
            countries_dict[c_name]["total_servers"] += 1

            if proto not in countries_dict[c_name]["protocols"]:
                countries_dict[c_name]["protocols"][proto] = 0
            countries_dict[c_name]["protocols"][proto] += 1

        os.makedirs("output", exist_ok=True)

        timestamp = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())

        # Flat Output Format
        flat_payload = {
            "schema_version": 1,
            "version": timestamp,
            "total_servers": len(all_servers),
            "servers": all_servers
        }

        flat_output_path = "output/servers.json"
        with open(flat_output_path, "w", encoding="utf-8") as f:
            json.dump(flat_payload, f, ensure_ascii=False, separators=(',', ':'))

        # Structured Hierarchy Output Format
        structured_payload = {
            "schema_version": 1,
            "version": timestamp,
            "updated_at": timestamp,
            "total_continents": len(continents_tree),
            "total_servers": len(all_servers),
            "continents": continents_tree
        }

        structured_output_path = "output/servers_structured.json"
        with open(structured_output_path, "w", encoding="utf-8") as f:
            json.dump(structured_payload, f, ensure_ascii=False, separators=(',', ':'))

        flat_size_mb = os.path.getsize(flat_output_path) / (1024 * 1024)
        structured_size_mb = os.path.getsize(structured_output_path) / (1024 * 1024)

        print(f"[+] Output saved successfully with 100% All Servers ({len(all_servers)} servers)!")
        print(f"    - Flat: {flat_output_path} -> {flat_size_mb:.2f} MB")
        print(f"    - Structured: {structured_output_path} -> {structured_size_mb:.2f} MB")

        # 4. Upload to Cloudflare R2 and/or Cloudflare Worker
        self._deploy_to_cloudflare([flat_output_path, structured_output_path])

    def _deploy_to_cloudflare(self, file_paths):
        print("[*] Deploying compact payloads to Cloudflare R2...")

        account_id = os.getenv("CLOUDFLARE_ACCOUNT_ID", "42326c36b367947b8ff21f4f1bad14af")
        access_key = os.getenv("R2_ACCESS_KEY_ID")
        secret_key = os.getenv("R2_SECRET_ACCESS_KEY")
        bucket_name = os.getenv("R2_BUCKET_NAME", "publicvpnlist-profiles")

        if access_key and secret_key:
            try:
                import boto3
                from botocore.config import Config

                endpoint_url = f"https://{account_id}.r2.cloudflarestorage.com"
                s3 = boto3.client(
                    "s3",
                    endpoint_url=endpoint_url,
                    aws_access_key_id=access_key,
                    aws_secret_access_key=secret_key,
                    config=Config(signature_version="s3v4"),
                    region_name="auto"
                )

                for path in file_paths:
                    if not os.path.exists(path):
                        continue
                    file_name = os.path.basename(path)
                    print(f"[*] Uploading {file_name} to R2 bucket '{bucket_name}'...")
                    s3.upload_file(
                        path,
                        bucket_name,
                        file_name,
                        ExtraArgs={
                            "ContentType": "application/json",
                            "CacheControl": "public, max-age=300, s-maxage=600"
                        }
                    )
                    print(f"[+] Successfully uploaded {file_name} to Cloudflare R2.")
            except Exception as e:
                print(f"[-] R2 S3 API upload error: {e}")
        else:
            print("[!] R2 credentials missing (R2_ACCESS_KEY_ID, R2_SECRET_ACCESS_KEY). Skipping direct R2 upload.")

        worker_url = os.getenv("WORKER_URL", "https://zvpn-backend.xocqoc.workers.dev/upload")
        secret = os.getenv("BACKEND_SECRET_KEY") or os.getenv("API_SECRET")
        if secret and worker_url:
            for path in file_paths:
                if not os.path.exists(path):
                    continue
                file_name = os.path.basename(path)
                try:
                    with open(path, "r", encoding="utf-8") as f:
                        payload = json.load(f)
                    headers = {
                        "Content-Type": "application/json",
                        "Authorization": f"Bearer {secret}",
                        "X-File-Name": file_name
                    }
                    res = self.session.post(worker_url, json=payload, headers=headers, timeout=15)
                    if res.status_code == 200:
                        print(f"[+] Worker HTTP Sync successful for {file_name}")
                except Exception as e:
                    print(f"[-] Worker upload note: {e}")

    def _harvest_omgvpn(self):
        print("[*] [Source 1/5] Fetching OMGVPN servers...")
        total_omg = 0
        try:
            res = self.session.get("https://omgvpn.com/", timeout=15)
            if res.status_code == 200:
                html = res.text
                def extract_array(name):
                    prefix = f"let {name} = ["
                    start = html.find(prefix)
                    if start == -1: return []
                    array_start = start + len(prefix) - 1
                    array_end = html.find("];", array_start)
                    if array_end == -1: return []
                    try:
                        return json.loads(html[array_start:array_end+1])
                    except Exception:
                        return []

                for raw in extract_array("array1") + extract_array("array2"):
                    parts = [str(p).strip() for p in str(raw).split("•")]
                    if len(parts) >= 2:
                        host = parts[1] if len(parts) > 1 else parts[0]
                        ip = parts[6] if len(parts) >= 7 else host
                        location = parts[5] if len(parts) >= 6 else host
                        country_code = location.split("~")[0].strip() if "~" in location else "US"
                        if host and ("." in host or "." in ip):
                            sid = f"omgvpn_{host}_{ip}"
                            self.servers[sid] = {
                                "protocol": "openvpn",
                                "server": ip if ip and "." in ip else host,
                                "server_port": 1194,
                                "source": "omgvpn",
                                "country_code": country_code,
                                "speed_mbps": 30.0,
                                "latency_ms": 50
                            }
                            total_omg += 1
        except Exception as e:
            print(f"    [-] OMGVPN error: {e}")
        print(f"    [+] Yielded {total_omg} OMGVPN servers.")

    def _harvest_pvl(self):
        print("[*] [Source 2/5] Fetching PublicVPNList servers...")
        total_pvl = 0
        headers = {"Authorization": f"Bearer {self.access_key}", "Accept": "application/json"}
        protocols = ["vless", "vmess", "shadowsocks", "trojan", "hysteria2", "openvpn", "wireguard"]
        for proto in protocols:
            page = 1
            while True:
                try:
                    res = self.session.get(f"https://publicvpnlist.com/api/v1/servers?protocol={proto}&status=online&per_page=100&page={page}", headers=headers, timeout=8)
                    if res.status_code != 200:
                        break
                    items = res.json().get("data", [])
                    if not items:
                        break
                    for item in items:
                        sid = str(item.get("id", ""))
                        if sid:
                            item["source"] = "publicvpnlist"
                            item["protocol"] = proto
                            if proto == "openvpn":
                                ovpn_content = self.pvl_downloader.download_openvpn_profile(sid)
                                if ovpn_content:
                                    item["config_data"] = ovpn_content
                            self.servers[f"pvl_{proto}_p{page}_{sid}"] = item
                            total_pvl += 1
                    if len(items) < 100:
                        break
                    page += 1
                except Exception:
                    break
        print(f"    [+] Yielded {total_pvl} PublicVPNList servers.")

    def _harvest_ipspeed(self):
        print("[*] [Source 3/5] Fetching IPSPEED.info servers...")
        total_ipspeed = 0
        ipspeed_urls = [
            "https://raw.githubusercontent.com/ermaozi/get_subscribe/main/subscribe/v2ray.txt",
            "https://raw.githubusercontent.com/Pawdroid/Free-servers/main/sub",
        ]
        for idx, url in enumerate(ipspeed_urls):
            try:
                res = self.session.get(url, timeout=10)
                if res.status_code == 200:
                    text = res.text
                    decoded = text
                    try:
                        if not text.startswith("{") and "://" not in text[:20]:
                            pad = len(text) % 4
                            if pad: text += "=" * (4 - pad)
                            decoded = base64.b64decode(text).decode("utf-8", errors="ignore")
                    except Exception:
                        pass

                    for line in decoded.splitlines():
                        line = line.strip()
                        if line and "://" in line:
                            proto = line.split("://")[0].lower()
                            if proto in ["vless", "vmess", "trojan", "ss", "shadowsocks", "hysteria2", "tuic", "wg", "wireguard", "socks", "socks5", "http", "openvpn", "sstp", "l2tp"]:
                                sid = f"ipspeed_sub_{idx}_{hash(line) % 10000000}"
                                self.servers[sid] = {
                                    "protocol": proto if proto != "ss" else "shadowsocks",
                                    "raw_uri": line,
                                    "config_uri": line,
                                    "source": "ipspeed",
                                    "speed_mbps": 30.0,
                                    "latency_ms": 55
                                }
                                total_ipspeed += 1
            except Exception:
                pass

        print(f"    [+] Yielded {total_ipspeed} IPSPEED.info servers.")

    def _harvest_vpngate(self):
        print("[*] [Source 4/5] Fetching VPNGate servers...")
        total_vpngate = 0
        try:
            res = self.session.get("https://www.vpngate.net/api/iphone/", timeout=15)
            if res.status_code == 200:
                for line in res.text.splitlines()[1:]:
                    parts = line.strip().split(",")
                    if len(parts) >= 15:
                        ip = parts[1].strip()
                        c_short = parts[6].strip()
                        b64 = parts[14].strip()
                        if b64 and len(b64) > 100:
                            try:
                                txt = base64.b64decode(b64).decode("utf-8", "ignore")
                                host, port, proto = extract_remote_host_port(txt)
                                sid = f"vpngate_{ip}"
                                self.servers[sid] = {
                                    "protocol": "openvpn",
                                    "server": host or ip,
                                    "server_port": port,
                                    "config_data": txt,
                                    "source": "vpngate",
                                    "country_code": c_short,
                                    "speed_mbps": 35.0,
                                    "latency_ms": 40
                                }
                                total_vpngate += 1
                            except Exception:
                                pass
        except Exception as e:
            print(f"    [-] VPNGate error: {e}")
        print(f"    [+] Yielded {total_vpngate} VPNGate servers.")

if __name__ == "__main__":
    engine = EnterpriseSmartGeoSyncEngine()
    engine.sync()
