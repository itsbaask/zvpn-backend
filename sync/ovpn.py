import hashlib
import re
import logging
import requests
from typing import Optional, Tuple, Any, Dict
from urllib.parse import urlparse, urlsplit, urlunsplit, urljoin
from bs4 import BeautifulSoup
import threading

try:
    import cloudscraper
    HAS_CLOUDSCRAPER = True
except ImportError:
    HAS_CLOUDSCRAPER = False

logger = logging.getLogger(__name__)

class ProfileDownloadError(Exception):
    """Raised when profile download target resolution fails."""
    pass

class DownloadResult:
    def __init__(
        self,
        source_id: str,
        target_type: str,
        target_url: str,
        http_status: Optional[int] = None,
        content_type: Optional[str] = None,
        bytes_downloaded: int = 0,
        sha256: Optional[str] = None,
        content: Optional[str] = None,
        error: Optional[str] = None,
    ):
        self.source_id = source_id
        self.target_type = target_type
        self.target_url = target_url
        self.http_status = http_status
        self.content_type = content_type
        self.bytes_downloaded = bytes_downloaded
        self.sha256 = sha256
        self.content = content
        self.error = error

    @property
    def is_success(self) -> bool:
        return bool(self.content and self.sha256 and not self.error)

def safe_url(url: str) -> str:
    if not url or not isinstance(url, str):
        return ""
    try:
        parts = urlsplit(url.strip())
        return urlunsplit((parts.scheme, parts.netloc, parts.path, "", ""))
    except Exception:
        return url[:60]

def minify_ovpn(content: str) -> str:
    lines = []
    for line in content.splitlines():
        stripped = line.strip()
        if stripped and not stripped.startswith(("#", ";")):
            lines.append(stripped)
    return "\n".join(lines)

def compute_sha256(content: str) -> str:
    return hashlib.sha256(content.encode("utf-8")).hexdigest()

def extract_numeric_profile_id(val: str) -> str:
    if not val or not isinstance(val, str):
        return ""
    val_str = val.strip()
    if val_str.isdigit():
        return val_str
    m = re.search(r'/download/(\d+)/?', val_str) or re.search(r'/server/(\d+)/?', val_str) or re.search(r'id=(\d+)', val_str)
    if m:
        return m.group(1)
    return ""

def is_valid_ovpn_content(text: str) -> bool:
    if not text or len(text) < 30:
        return False
    lower = text.lower()
    if "<html" in lower and "</html" in lower:
        return False
    if "application/json" in lower and "error" in lower and "client" not in lower:
        return False
    has_directive = "client" in lower or "dev tun" in lower or "dev tap" in lower or "remote " in lower or "proto " in lower
    has_crypto = (
        "<ca>" in lower or "ca " in lower or
        "<secret>" in lower or "<key>" in lower or "secret " in lower or
        "crypto" in lower or "<cert>" in lower or
        "cipher " in lower or "data-ciphers" in lower or
        "auth-user-pass" in lower or "remote-cert-tls" in lower
    )
    return has_directive and has_crypto

PERMANENT_KEY = "pvlk_eb8cc33936641d9492cf0a2740c8511bb737e1a611fa1035cb7c5c3006513bcc"

class OvpnDownloader:
    _token_lock = threading.Lock()
    _last_pvl_request_time = 0.0

    def __init__(
        self,
        base_url: str = "https://publicvpnlist.com",
        access_key: Optional[str] = None,
        session: Optional[requests.Session] = None
    ):
        self.base_url = base_url.rstrip("/")
        self.access_key = (access_key or "").strip() or PERMANENT_KEY
        if session:
            self.session = session
        else:
            self.session = requests.Session()

    def _get_auth_headers(self) -> Dict[str, str]:
        headers = {
            "Accept": "application/x-openvpn-profile, text/plain, application/json, text/html, */*",
            "User-Agent": "ZVPN-Cloud-Sync/1.0",
            "Referer": f"{self.base_url}/"
        }
        if self.access_key:
            headers["Authorization"] = f"Bearer {self.access_key}"
            headers["X-Access-Key"] = self.access_key
        return headers

    def resolve_download_target(self, target: Any) -> Tuple[str, str]:
        source_id = getattr(target, "source_id", getattr(target, "server_id", str(target)))
        profile_url = getattr(target, "profile_source_url", "")
        page_url = getattr(target, "server_page_url", "")

        if profile_url and profile_url.startswith("http") and "publicvpnlist.com/download/" not in profile_url:
            return "profile_source_url", profile_url

        if page_url and page_url.startswith("http") and "/api/v1/" not in page_url and "publicvpnlist.com/download/" not in page_url:
            return "server_page_url", page_url

        raise ProfileDownloadError(f"No direct profile download URL for source_id={source_id}")

    def download_profile(self, target: Any) -> DownloadResult:
        source_id = getattr(target, "source_id", getattr(target, "server_id", str(target)))
        protocol = getattr(target, "protocol", "openvpn").lower()

        if protocol != "openvpn":
            config_uri = getattr(target, "config_uri", "") or getattr(target, "profile_source_url", "")
            if not config_uri or "://" not in config_uri:
                return DownloadResult(
                    source_id=source_id,
                    target_type="unresolved",
                    target_url="",
                    error=f"Invalid or unresolved {protocol} connection URI",
                )
            sha256_val = getattr(target, "config_sha256", "") or compute_sha256(config_uri)
            return DownloadResult(
                source_id=source_id,
                target_type="modern_protocol_uri",
                target_url="inline",
                http_status=200,
                content_type=f"application/x-{protocol}-config",
                bytes_downloaded=len(config_uri.encode("utf-8")),
                sha256=sha256_val,
                content=config_uri
            )

        ovpn_inline = getattr(target, "ovpn_content", "")
        if ovpn_inline and len(ovpn_inline) >= 30 and is_valid_ovpn_content(ovpn_inline):
            minified = minify_ovpn(ovpn_inline)
            sha256_val = compute_sha256(minified)
            return DownloadResult(
                source_id=source_id,
                target_type="inline_decoded_profile",
                target_url="inline",
                http_status=200,
                content_type="application/x-openvpn-profile",
                bytes_downloaded=len(minified.encode("utf-8")),
                sha256=sha256_val,
                content=minified
            )

        profile_id = getattr(target, "profile_id", "") or extract_numeric_profile_id(getattr(target, "profile_source_url", "")) or extract_numeric_profile_id(source_id)
        source_name = str(getattr(target, "source_name", ""))
        target_url_check = str(getattr(target, "profile_source_url", ""))

        if profile_id and profile_id.isdigit() and ("publicvpnlist" in source_name or "publicvpnlist.com" in target_url_check):
            token_headers = {
                "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                "Content-Type": "application/x-www-form-urlencoded",
                "X-Requested-With": "XMLHttpRequest",
                "Referer": f"{self.base_url}/download/{profile_id}/"
            }
            try:
                tok_res = self.session.post(
                    f"{self.base_url}/get_token.php",
                    data={"id": profile_id},
                    headers=token_headers,
                    timeout=(10, 25)
                )
                if tok_res.status_code == 200:
                    tok_data = tok_res.json()
                    tok_val = tok_data.get("token")
                    raw_dl = tok_data.get("url") or f"/download.php?token={tok_val}"
                    dl_url = urljoin(self.base_url, raw_dl)

                    ovpn_res = self.session.get(dl_url, headers=token_headers, timeout=(10, 30))
                    if ovpn_res.status_code == 200 and is_valid_ovpn_content(ovpn_res.text):
                        minified = minify_ovpn(ovpn_res.text)
                        sha256_val = compute_sha256(minified)
                        return DownloadResult(
                            source_id=source_id,
                            target_type="pvl_token_download",
                            target_url=dl_url,
                            http_status=200,
                            content_type="application/x-openvpn-profile",
                            bytes_downloaded=len(minified.encode("utf-8")),
                            sha256=sha256_val,
                            content=minified
                        )
            except Exception as e:
                logger.warning(f"PVL token download failed for ID {profile_id}: {e}")

        try:
            target_type, target_url = self.resolve_download_target(target)
        except ProfileDownloadError as e:
            return DownloadResult(
                source_id=source_id,
                target_type="unresolved",
                target_url="",
                error=str(e)
            )

        headers = self._get_auth_headers()
        try:
            res = self.session.get(target_url, headers=headers, timeout=(15, 90), allow_redirects=True)
            if res and res.status_code == 200 and is_valid_ovpn_content(res.text):
                minified = minify_ovpn(res.text)
                sha256_val = compute_sha256(minified)
                return DownloadResult(
                    source_id=source_id,
                    target_type=target_type,
                    target_url=safe_url(target_url),
                    http_status=200,
                    content_type=res.headers.get("Content-Type", ""),
                    bytes_downloaded=len(minified.encode("utf-8")),
                    sha256=sha256_val,
                    content=minified
                )
        except Exception as e:
            pass

        return DownloadResult(
            source_id=source_id,
            target_type="failed",
            target_url=safe_url(target_url),
            error="Download failed or invalid OpenVPN profile content"
        )
