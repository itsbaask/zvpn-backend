import os
import sys
import json
import random
import socket
import urllib.parse
from collections import Counter, defaultdict
import ipaddress
import geoip2.database

MMDB_PATH = "GeoLite2-Country.mmdb"
STAGING_JSON = "output/servers_staging.json"
REPORT_CSV = "audit_report.csv"

CLOUDFLARE_NETWORKS = [
    ipaddress.ip_network("173.245.48.0/20"),
    ipaddress.ip_network("103.21.244.0/22"),
    ipaddress.ip_network("103.22.200.0/22"),
    ipaddress.ip_network("103.31.4.0/22"),
    ipaddress.ip_network("141.101.64.0/18"),
    ipaddress.ip_network("108.162.192.0/18"),
    ipaddress.ip_network("190.93.240.0/20"),
    ipaddress.ip_network("188.114.96.0/20"),
    ipaddress.ip_network("197.234.240.0/22"),
    ipaddress.ip_network("198.41.128.0/17"),
    ipaddress.ip_network("162.158.0.0/15"),
    ipaddress.ip_network("104.16.0.0/13"),
    ipaddress.ip_network("104.24.0.0/14"),
    ipaddress.ip_network("172.64.0.0/13"),
    ipaddress.ip_network("131.0.72.0/22"),
]

def is_cloudflare_ip(ip_str: str) -> bool:
    try:
        ip_obj = ipaddress.ip_address(ip_str)
        return any(ip_obj in net for net in CLOUDFLARE_NETWORKS)
    except ValueError:
        return False

def resolve_host_ip(host_str: str) -> tuple[str | None, bool]:
    if not host_str or len(host_str) > 253 or "{" in host_str or " " in host_str or '"' in host_str:
        return None, False
    host_str = host_str.strip()
    try:
        ipaddress.ip_address(host_str)
        return host_str, False
    except ValueError:
        try:
            resolved = socket.gethostbyname(host_str)
            return resolved, True
        except (socket.error, UnicodeEncodeError, Exception):
            return None, True

def extract_fragment(uri: str) -> str:
    if "#" in uri:
        return urllib.parse.unquote(uri.split("#", 1)[1]).strip()
    return ""

def classify_fragment_pattern(fragment: str) -> str:
    f_lower = fragment.lower()
    if "rjsxrd" in f_lower or "rostunnel" in f_lower or "vlesstrojan" in f_lower or "ростуннель" in f_lower:
        return "rjsxrd / RosTunnel"
    if "raydikalx" in f_lower:
        return "@Raydikalx"
    if "v2nodes" in f_lower:
        return "v2nodes"
    if "v2go" in f_lower:
        return "v2go"
    if "soskey" in f_lower:
        return "@SOSkey"
    if "farah_vpn" in f_lower:
        return "Farah_VPN"
    if "vlessforu" in f_lower:
        return "VLESSFORU"
    if "vpnplusee" in f_lower:
        return "vpnplusee"
    if "rayviranet" in f_lower:
        return "RayViraNet"
    if "solvpn" in f_lower:
        return "SolVPN"
    if "opengw" in f_lower:
        return "OPENGW"
    if "cidr" in f_lower:
        return "CIDR"
    return "OTHER"

def main():
    if not os.path.exists(MMDB_PATH):
        print("ERROR: GeoLite2-Country.mmdb file not found!")
        sys.exit(1)

    if not os.path.exists(STAGING_JSON):
        print(f"ERROR: {STAGING_JSON} not found!")
        sys.exit(1)

    with open(STAGING_JSON, "r", encoding="utf-8") as f:
        data = json.load(f)

    servers = data.get("servers", [])
    assigned_servers = [s for s in servers if s.get("country_code") and s.get("country_code") not in ["None", "XX"]]

    random.seed(42)
    sampled_all = [(s, s.get("country_source", "none")) for s in random.sample(assigned_servers, min(1000, len(assigned_servers)))]
    print(f"Loaded {len(servers)} total servers | Sampled {len(sampled_all)} Assigned Servers for Audit (Seed=42).\n")

    reader = geoip2.database.Reader(MMDB_PATH)

    pattern_stats = defaultdict(lambda: {"total": 0, "evaluated": 0, "matched": 0, "excluded": 0, "unresolved": 0})
    total_evaluated = 0
    total_matched = 0
    total_excluded = 0
    total_unresolved = 0

    report_rows = []

    for server, frag_type in sampled_all:
        sid = server.get("id")
        src_name = server.get("source_name", "unknown")
        stored_cc = server.get("country_code")
        raw_uri = server.get("raw_uri") or server.get("config_uri") or ""
        fragment = extract_fragment(raw_uri)
        pattern = classify_fragment_pattern(fragment)
        host = server.get("host") or ""

        pattern_stats[pattern]["total"] += 1

        ip, is_domain = resolve_host_ip(host)

        if not ip:
            total_unresolved += 1
            pattern_stats[pattern]["unresolved"] += 1
            report_rows.append({
                "id": sid, "host": host, "ip": "UNRESOLVED", "stored_code": stored_cc,
                "geoip_code": "N/A", "fragment_pattern": pattern, "source_name": src_name,
                "status": "UNRESOLVED", "fragment": fragment
            })
            continue

        if is_cloudflare_ip(ip):
            total_excluded += 1
            pattern_stats[pattern]["excluded"] += 1
            report_rows.append({
                "id": sid, "host": host, "ip": ip, "stored_code": stored_cc,
                "geoip_code": "CLOUDFLARE_ANYCAST", "fragment_pattern": pattern, "source_name": src_name,
                "status": "EXCLUDED", "fragment": fragment
            })
            continue

        try:
            geo_res = reader.country(ip)
            geoip_cc = geo_res.country.iso_code
        except Exception:
            geoip_cc = None

        total_evaluated += 1
        pattern_stats[pattern]["evaluated"] += 1

        is_match = (stored_cc == geoip_cc)
        if is_match:
            total_matched += 1
            pattern_stats[pattern]["matched"] += 1
            status = "MATCH"
        else:
            status = "MISMATCH"

        report_rows.append({
            "id": sid, "host": host, "ip": ip, "stored_code": stored_cc,
            "geoip_code": geoip_cc or "UNKNOWN", "fragment_pattern": pattern,
            "source_name": src_name, "status": status, "fragment": fragment
        })

    reader.close()

    # Save to audit_report.csv
    with open(REPORT_CSV, "w", encoding="utf-8") as f:
        f.write("id,host,ip,stored_code,geoip_code,fragment_pattern,source_name,status,fragment\n")
        for row in report_rows:
            frag_clean = row['fragment'].replace('"', '""')
            f.write(f'"{row["id"]}","{row["host"]}","{row["ip"]}","{row["stored_code"]}","{row["geoip_code"]}","{row["fragment_pattern"]}","{row["source_name"]}","{row["status"]}","{frag_clean}"\n')

    # Print Report Table
    print("=== FULL PATTERN AUDIT REPORT (audit_geoip.py - Seed=42) ===")
    print(f"Total Sampled: {len(sampled_all)} | Total Evaluated: {total_evaluated} | Anycast Excluded: {total_excluded} | Unresolved: {total_unresolved}")

    overall_pct = (total_matched / total_evaluated * 100) if total_evaluated > 0 else 0
    print(f"\n📌 OVERALL ACCURACY (Current Staging): {overall_pct:.2f}% ({total_matched}/{total_evaluated})\n")

    print(f"{'PATTERN':22s} | {'COUNT':6s} | {'EVAL':6s} | {'MATCH %':8s} | {'EXCLUDED (CF)':14s}")
    print("-" * 70)
    for pat, pdata in sorted(pattern_stats.items(), key=lambda x: -x[1]["total"]):
        eval_c = pdata["evaluated"]
        match_c = pdata["matched"]
        excl_c = pdata["excluded"]
        tot_c = pdata["total"]
        pct = (match_c / eval_c * 100) if eval_c > 0 else 0.0
        print(f"{pat:22s} | {tot_c:6d} | {eval_c:6d} | {pct:7.2f}% | {excl_c:14d}")

if __name__ == "__main__":
    main()
