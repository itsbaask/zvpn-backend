import json
import hashlib
import re
from datetime import datetime, timezone
from typing import Dict, List, Any, Tuple

SUPPORTED_PROTOCOLS = {"openvpn", "vless", "vmess", "trojan", "shadowsocks", "hysteria2", "tuic", "wireguard", "hysteria", "anytls", "socks5", "http"}

# ─────────────────────────────────────────────────────────────────────────────
# Server Display Name Sanitization
# ─────────────────────────────────────────────────────────────────────────────

# Internal fields never sent to the app
_STRIP_FIELDS = {
    "source_name", "source_id", "profile_id", "storage_id",
    "profile_source_url", "server_page_url", "config_sha256",
    "ovpn_content",                   # large — not needed after upload
    "deduplication_key", "raw_id",
    "exit_ip",                         # same as host, redundant
    "network_score",                   # internal sorting only
}

# Protocol → clean display label
_PROTO_LABELS = {
    "openvpn":     "OpenVPN",
    "vless":       "VLESS",
    "vmess":       "VMess",
    "trojan":      "Trojan",
    "shadowsocks": "Shadowsocks",
    "hysteria2":   "Hysteria2",
    "hysteria":    "Hysteria",
    "tuic":        "TUIC",
    "wireguard":   "WireGuard",
    "anytls":      "AnyTLS",
    "socks5":      "SOCKS5",
    "http":        "HTTP",
}

# Tier → sort order (lower = shown first)
_TIER_ORDER = {"free": 0, "premium": 1, "premium_plus": 2}


def _build_display_name(country_name: str, protocol: str) -> str:
    """
    Returns a clean, professional server display name.
    Example: 'Japan — VLESS'  |  'United States — OpenVPN'
    """
    country = (country_name or "").strip()
    if not country or country.lower() in ("unknown", "global", "xx", ""):
        country = "Global"
    proto_label = _PROTO_LABELS.get((protocol or "").lower(), (protocol or "VPN").upper())
    return f"{country} — {proto_label}"


def _clean_server_for_app(srv: dict) -> dict:
    """
    Returns a sanitised copy of a server dict safe to send to the app.
    - Removes all internal/source fields
    - Ensures display_name, flag, protocol_label are set
    - Normalises speed/ping to clean integers
    """
    out = {k: v for k, v in srv.items() if k not in _STRIP_FIELDS}

    proto = (out.get("protocol") or "openvpn").lower()
    country = (out.get("country_name") or "Global").strip()
    flag = (out.get("flag") or "").strip()

    # Build clean display name
    out["display_name"] = _build_display_name(country, proto)
    out["protocol_label"] = _PROTO_LABELS.get(proto, proto.upper())

    # Normalise numeric fields
    out["speed_mbps"] = round(float(out.get("speed_mbps") or 0.0), 1)
    out["latency_ms"] = int(out.get("latency_ms") or 0)
    out["port"]       = int(out.get("port") or 1194)

    # Ensure country is clean
    if not country or country.lower() in ("unknown", "global", ""):
        out["country_name"] = "Global"
        out["country_code"] = "XX"
    out["country_code"] = (out.get("country_code") or "XX").upper()[:2]

    return out


def generate_version_string(ts: datetime) -> str:
    iso_ts = ts.strftime("%Y-%m-%dT%H:%M:%SZ")
    random_suffix = hashlib.sha256(iso_ts.encode("utf-8")).hexdigest()[:6]
    return f"{iso_ts}-{random_suffix}"

def resolve_engine_type(protocol: str) -> str:
    p = (protocol or "").upper()
    if p in {"VLESS", "VMESS", "TROJAN", "SHADOWSOCKS", "HYSTERIA2", "HYSTERIA", "TUIC", "WIREGUARD", "ANYTLS", "SOCKS5", "HTTP"}:
        return "XRAY"
    return "OPENVPN"

def is_valid_host(host: str) -> bool:
    if not host or not isinstance(host, str):
        return False
    h = host.strip().lower()
    if not h or h in ("127.0.0.1", "0.0.0.0", "localhost", "0", "::1", "unknown", "n/a"):
        return False
    return True

class ManifestBuilder:
    def __init__(self, schema_version: int = 1):
        self.schema_version = schema_version

    def build_manifest_and_servers(
        self,
        server_entries: Dict[str, Dict[str, Any]],
        changes: Dict[str, int],
        now: datetime = None
    ) -> Tuple[Dict[str, Any], Dict[str, Any]]:
        """
        Builds manifest.json and servers.json supporting all multi-protocols
        (OpenVPN, VLESS, VMess, Trojan, Shadowsocks, Hysteria2) with deduplication,
        quality sorting, and rich metadata.
        """
        if now is None:
            now = datetime.now(timezone.utc)

        version_str = generate_version_string(now)
        iso_now = now.strftime("%Y-%m-%dT%H:%M:%SZ")

        # Step 1: Filter and Deduplicate Entries Across All Multi-Protocols
        deduped_entries: Dict[str, Dict[str, Any]] = {}
        seen_hosts: Dict[str, Tuple[str, float]] = {}

        for sid, info in server_entries.items():
            meta = info.get("metadata", {})
            protocol = (meta.get("protocol") or "openvpn").lower()
            if protocol not in SUPPORTED_PROTOCOLS:
                continue

            host = meta.get("host") or meta.get("exit_ip") or meta.get("id") or ""
            port = int(meta.get("port") or 1194)
            transport = (meta.get("transport") or "udp").lower()

            if not is_valid_host(host):
                continue

            speed_val = float(meta.get("speed_mbps", 0.0) or 0.0)
            score_val = float(meta.get("network_score", 0) or 0)
            lat_val = float(meta.get("latency_ms", 100) or 100)
            source_boost = 500.0 if "giamping" in str(meta.get("source_name", "")) else 0.0

            comp_score = (speed_val * 100.0) + (score_val / 1000.0) + max(0.0, 100.0 - lat_val) + source_boost

            host_clean = re.sub(r'[^a-z0-9]', '', host.lower())
            dedup_key = f"{protocol}_{host_clean}_{port}_{transport}"

            if dedup_key in seen_hosts:
                prev_sid, prev_score = seen_hosts[dedup_key]
                if comp_score > prev_score:
                    del deduped_entries[prev_sid]
                    seen_hosts[dedup_key] = (sid, comp_score)
                    deduped_entries[sid] = info
            else:
                seen_hosts[dedup_key] = (sid, comp_score)
                deduped_entries[sid] = info

        # Step 2: Country-Based Tier Distribution Matrix
        from collections import defaultdict
        country_groups = defaultdict(list)

        for sid, info in deduped_entries.items():
            meta = info["metadata"]
            country = meta.get("country_name") or "Unknown"
            speed_val = float(meta.get("speed_mbps", 0.0) or 0.0)
            score_val = float(meta.get("network_score", 0) or 0)
            lat_val = float(meta.get("latency_ms", 100) or 100)
            source_boost = 500.0 if "giamping" in str(meta.get("source_name", "")) else 0.0
            comp_score = (speed_val * 100.0) + (score_val / 1000.0) + max(0.0, 100.0 - lat_val) + source_boost
            country_groups[country].append((sid, comp_score))

        PREMIUM_COUNTRIES = {
            "United States", "United Kingdom", "Germany", "France", "Netherlands",
            "Switzerland", "Sweden", "Norway", "Singapore", "Japan", "Australia",
            "Canada", "South Korea", "Hong Kong"
        }
        tier_assignment = {}
        for country, s_list in country_groups.items():
            s_list.sort(key=lambda x: x[1], reverse=True)
            n = len(s_list)
            is_premium_country = country in PREMIUM_COUNTRIES
            if is_premium_country:
                n_premium_plus = max(1, int(n * 0.10))
                for idx, (sid, _) in enumerate(s_list):
                    tier_assignment[sid] = "premium_plus" if idx < n_premium_plus else "premium"
            else:
                n_free = max(2, int(n * 0.15))
                n_premium = max(2, int(n * 0.60))
                for idx, (sid, _) in enumerate(s_list):
                    if idx < n_free:
                        tier_assignment[sid] = "free"
                    elif idx < n_premium:
                        tier_assignment[sid] = "premium"
                    else:
                        tier_assignment[sid] = "premium_plus"

        # Step 3: Build Manifest and Server DTOs
        manifest_servers = {}
        servers_list = []

        for sid, info in deduped_entries.items():
            meta = info["metadata"]
            sha256_val = info.get("sha256", "")
            size_val = info.get("size", 0)
            updated_at_val = info.get("updated_at", iso_now)

            pid = meta.get("profile_id")
            storage_id = str(pid) if (pid and str(pid).isdigit()) else sid.removeprefix("pvl_")
            profile_rel_path = f"profiles/{storage_id}.ovpn"
            raw_url = meta.get("profile_url") or f"/v1/profiles/{storage_id}.ovpn"
            profile_full_url = raw_url if raw_url.startswith("http") else f"https://zvpn-backend.xocqoc.workers.dev{raw_url}"

            manifest_servers[sid] = {
                "profile": profile_rel_path,
                "sha256": sha256_val,
                "size": size_val,
                "updated_at": updated_at_val
            }

            proto_upper = (meta.get("protocol") or "OPENVPN").upper()
            engine_upper = resolve_engine_type(proto_upper)

            srv_copy = dict(meta)
            srv_copy["id"] = sid
            srv_copy["engine"] = engine_upper
            srv_copy["protocol"] = proto_upper
            srv_copy["profile_url"] = profile_full_url if proto_upper == "OPENVPN" else (meta.get("config_uri") or profile_full_url)
            srv_copy["config_uri"] = meta.get("config_uri") or ""
            srv_copy["profile_sha256"] = sha256_val
            srv_copy["tier"] = tier_assignment.get(sid, "free")
            srv_copy["status"] = meta.get("status") or "verified"
            srv_copy["checked_at"] = meta.get("checked_at") or iso_now

            # Clean: strip internal fields, add display_name, protocol_label
            srv_copy = _clean_server_for_app(srv_copy)
            servers_list.append(srv_copy)

        # Step 4: Quality Sorting (by tier → speed → latency → country)
        def sort_key(s):
            tier_order = _TIER_ORDER.get(s.get("tier", "free"), 0)
            speed = float(s.get("speed_mbps", 0.0) or 0.0)
            ping = float(s.get("latency_ms", 999) or 999)
            country = str(s.get("country_name", ""))
            return (tier_order, -speed, ping, country)

        servers_list.sort(key=sort_key)

        best_server_id = servers_list[0]["id"] if servers_list else None

        servers_json_data = {
            "schema_version": self.schema_version,
            "version": version_str,
            "best_automatic_server_id": best_server_id,
            "count": len(servers_list),
            "servers": servers_list
        }

        servers_bytes = json.dumps(servers_json_data, sort_keys=True).encode("utf-8")
        servers_sha256 = hashlib.sha256(servers_bytes).hexdigest()

        manifest_data = {
            "schema_version": self.schema_version,
            "version": version_str,
            "generated_at": iso_now,
            "best_automatic_server_id": best_server_id,
            "source_snapshot": servers_sha256[:16],
            "count": len(manifest_servers),
            "servers_url": "/v1/servers.json",
            "profiles_prefix": "/v1/profiles/",
            "manifest_sha256": servers_sha256,
            "changes": {
                "added": changes.get("added", 0),
                "removed": changes.get("removed", 0),
                "updated": changes.get("updated", 0)
            },
            "servers": manifest_servers
        }

        return manifest_data, servers_json_data
