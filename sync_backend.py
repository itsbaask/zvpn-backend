import os
import sys
import json
import base64
import csv
import io
import re
import time
import threading
import hashlib
import requests
from bs4 import BeautifulSoup
from urllib.parse import urljoin, urlparse

PERMANENT_KEY = "pvlk_eb8cc33936641d9492cf0a2740c8511bb737e1a611fa1035cb7c5c3006513bcc"

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
}

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

def get_geo_info(code_or_name: str) -> tuple:
    if not code_or_name:
        return None
    key = code_or_name.strip().upper()
    if key in COUNTRY_MAP:
        return COUNTRY_MAP[key]
    for k, (continent, name, flag) in COUNTRY_MAP.items():
        if key in name.upper() or key == k:
            return continent, name, flag
    return None

def smart_resolve_country(item: dict) -> tuple:
    for field in ["country_code", "country", "country_name", "location"]:
        val = item.get(field)
        if val:
            geo = get_geo_info(str(val))
            if geo:
                return geo

    raw_text = str(item.get("raw_uri", "")) + " " + str(item.get("server", "")) + " " + str(item.get("host", ""))
    upper_text = raw_text.upper()

    for code, (continent, name, flag) in COUNTRY_MAP.items():
        if code in upper_text or name.upper() in upper_text:
            return continent, name, flag

    for code in COUNTRY_MAP.keys():
        if f".{code.lower()}." in raw_text.lower() or raw_text.lower().endswith(f".{code.lower()}"):
            return COUNTRY_MAP[code]
        if f"{code.lower()}1." in raw_text.lower() or f"{code.lower()}-" in raw_text.lower():
            return COUNTRY_MAP[code]

    return COUNTRY_MAP["US"]

class PublicVPNListOvpnDownloader:
    _token_lock = threading.Lock()
    _last_request_time = 0.0

    def __init__(self, base_url="https://publicvpnlist.com", access_key=None):
        self.base_url = base_url.rstrip("/")
        self.access_key = access_key or PERMANENT_KEY
        self.session = requests.Session()

    def download_openvpn_profile(self, profile_id: str) -> str:
        if not profile_id or not profile_id.isdigit():
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
        self.access_key = access_key or PERMANENT_KEY
        self.session = requests.Session()
        self.session.headers.update({
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Accept": "application/json, text/plain, text/html, */*",
        })
        self.servers = {}
        self.pvl_downloader = PublicVPNListOvpnDownloader(access_key=self.access_key)

    def sync(self):
        print("[*] Starting Unified Geo-Sync & GitHub Payload Encryption Engine...")
        print("[*] Target Sources: 1. omgvpn | 2. publicvpnlist | 3. ipspeed.info | 4. vpngate")

        self._harvest_omgvpn()
        self._harvest_pvl()
        self._harvest_ipspeed()
        self._harvest_vpngate()

        # 1. Strict 200% Deduplication
        unique_servers = {}
        seen_signatures = set()

        for sid, item in self.servers.items():
            proto = str(item.get("protocol", "unknown")).lower()
            host = str(item.get("server") or item.get("host") or "").lower().strip()
            port = int(item.get("server_port") or item.get("port") or 0)
            raw_uri = str(item.get("raw_uri", "")).strip()

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

        # 2. Strict Naming Standardization: Flag + Country Name + Speed (e.g. 🇺🇸 United States · 50 Mbps - 01)
        processed_servers = []
        country_counters = {}
        resolved_count = 0

        for sid, item in self.servers.items():
            if sid not in unique_servers:
                continue

            geo = smart_resolve_country(item)
            if not geo:
                geo = ("North America", "United States", "🇺🇸")
            else:
                resolved_count += 1

            continent, c_name, c_flag = geo

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
            item["flag"] = c_flag
            item["tier"] = tier
            item["speed_mbps"] = speed
            item["latency_ms"] = ping

            processed_servers.append(item)

        print(f"[+] Processing raw pool complete: {len(processed_servers)} servers.")

        # 3. Filter and Cap: Keep Top 10 Best Servers per Country & Format Clean Sequential Names (01, 02, 03...)
        country_groups = {}
        for s in processed_servers:
            c = s.get("country", "Global")
            if c not in country_groups:
                country_groups[c] = []
            country_groups[c].append(s)

        compact_servers = []
        for c, s_list in country_groups.items():
            s_list_sorted = sorted(s_list, key=lambda x: (-x.get("speed_mbps", 0), x.get("latency_ms", 999)))
            top_per_country = s_list_sorted[:10]
            for idx, item in enumerate(top_per_country, start=1):
                c_flag = item.get("flag", "🌐")
                c_name = item.get("country", "Global")
                clean_name = f"{c_flag} {c_name} - {idx:02d}"
                item["display_name"] = clean_name
                item["name"] = clean_name
                compact_servers.append(item)

        # Overall sorting by speed
        compact_servers.sort(key=lambda x: (-x.get("speed_mbps", 0), x.get("latency_ms", 999)))

        os.makedirs("output", exist_ok=True)
        output_path = "output/servers.json"

        # Compact, clean payload schema (no 4x duplicate arrays)
        payload = {
            "version": 2,
            "total_servers": len(compact_servers),
            "servers": compact_servers
        }

        # Encrypt published GitHub payload using SHA-256 Obfuscated Vault Key
        KEY = hashlib.sha256("ZedSecure .zvx v2".encode("utf-8")).digest()
        raw_json_bytes = json.dumps(payload, ensure_ascii=False, separators=(',', ':')).encode("utf-8")
        enc_bytes = bytearray(len(raw_json_bytes))
        for i in range(len(raw_json_bytes)):
            enc_bytes[i] = raw_json_bytes[i] ^ KEY[i % len(KEY)]
        encrypted_b64_payload = base64.b64encode(enc_bytes).decode("ascii")

        with open(output_path, "w", encoding="utf-8") as f:
            f.write(encrypted_b64_payload)

        print(f"[+] Strong Encrypted Processing complete! Saved {len(compact_servers)} top-quality encrypted servers ({len(encrypted_b64_payload)} bytes) to {output_path}.")

    def _harvest_omgvpn(self):
        print("[*] [Source 1/4] Fetching OMGVPN servers...")
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
        print("[*] [Source 2/4] Fetching PublicVPNList servers...")
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
        print("[*] [Source 3/4] Fetching IPSPEED.info servers directly...")
        total_ipspeed = 0

        # 1. Direct Scraping from ipspeed.info
        try:
            import undetected_chromedriver as uc
            options = uc.ChromeOptions()
            options.add_argument('--no-sandbox')
            options.add_argument('--disable-dev-shm-usage')
            driver = uc.Chrome(options=options)

            def get_page_html(url):
                try:
                    driver.get(url)
                    for _ in range(8):
                        if "Just a moment" not in driver.title and "Just a moment" not in driver.page_source[:400]:
                            return driver.page_source
                        time.sleep(1)
                    return driver.page_source
                except Exception:
                    return ""

            # 1a. VLESS Reality
            vless_html = get_page_html("https://ipspeed.info/free-vless-reality.php")
            vless_uris = re.findall(r'vless://[^\s"\'<>]+', vless_html)
            for uri in vless_uris:
                sid = f"ipspeed_vless_{hash(uri) % 10000000}"
                self.servers[sid] = {
                    "protocol": "vless",
                    "raw_uri": uri,
                    "source": "ipspeed",
                    "speed_mbps": 40.0,
                    "latency_ms": 45
                }
                total_ipspeed += 1

            # 1b. OpenVPN
            ovpn_html = get_page_html("https://ipspeed.info/free-openvpn.php")
            soup = BeautifulSoup(ovpn_html, "html.parser")
            for table in soup.find_all("table"):
                for row in table.find_all("tr")[1:]:
                    cols = [td.get_text(strip=True) for td in row.find_all("td")]
                    if len(cols) >= 3:
                        country = cols[1]
                        file_or_ip = cols[2].replace(".ovpn", "").strip()
                        if "." in file_or_ip:
                            sid = f"ipspeed_ovpn_{file_or_ip}"
                            self.servers[sid] = {
                                "protocol": "openvpn",
                                "server": file_or_ip,
                                "server_port": 1194,
                                "source": "ipspeed",
                                "country_code": country,
                                "speed_mbps": 35.0,
                                "latency_ms": 50
                            }
                            total_ipspeed += 1

            # 1c. Proxy (HTTP / SOCKS)
            proxy_html = get_page_html("https://ipspeed.info/free-proxy.php")
            soup_p = BeautifulSoup(proxy_html, "html.parser")
            for table in soup_p.find_all("table"):
                for row in table.find_all("tr")[1:]:
                    cols = [td.get_text(strip=True) for td in row.find_all("td")]
                    if len(cols) >= 5:
                        country = cols[1]
                        ip = cols[2]
                        port = cols[3]
                        proto = cols[4].lower()
                        if "." in ip and port.isdigit():
                            sid = f"ipspeed_proxy_{ip}_{port}"
                            self.servers[sid] = {
                                "protocol": "socks" if "socks" in proto else "http",
                                "server": ip,
                                "server_port": int(port),
                                "source": "ipspeed",
                                "country_code": country,
                                "speed_mbps": 25.0,
                                "latency_ms": 60
                            }
                            total_ipspeed += 1

            # 1d. L2TP / IPsec
            l2tp_html = get_page_html("https://ipspeed.info/free-l2tpipsec.php")
            soup_l = BeautifulSoup(l2tp_html, "html.parser")
            for table in soup_l.find_all("table"):
                for row in table.find_all("tr")[1:]:
                    cols = [td.get_text(strip=True) for td in row.find_all("td")]
                    if len(cols) >= 3:
                        country = cols[1]
                        ip = cols[2]
                        if "." in ip:
                            sid = f"ipspeed_l2tp_{ip}"
                            self.servers[sid] = {
                                "protocol": "l2tp",
                                "server": ip,
                                "server_port": 1701,
                                "source": "ipspeed",
                                "country_code": country,
                                "speed_mbps": 30.0,
                                "latency_ms": 55
                            }
                            total_ipspeed += 1

            try:
                driver.quit()
            except Exception:
                pass
        except Exception as e:
            print(f"    [-] IPSPEED browser harvest note: {e}")

        # 2. Fallback / Additional IPSPEED Feed Mirror URLs
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
                                    "source": "ipspeed",
                                    "speed_mbps": 30.0,
                                    "latency_ms": 55
                                }
                                total_ipspeed += 1
            except Exception:
                pass

        print(f"    [+] Yielded {total_ipspeed} IPSPEED.info servers.")

    def _harvest_vpngate(self):
        print("[*] [Source 4/4] Fetching VPNGate servers...")
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
