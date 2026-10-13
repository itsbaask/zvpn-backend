import json
from collections import Counter

print("=== REAL SOURCE-OF-TRUTH VERIFICATION AUDIT (verify_countries.py) ===")

with open("output/servers.json", "r", encoding="utf-8") as f:
    data = json.load(f)

servers = data.get("servers", [])
print(f"Total Servers Loaded: {len(servers)}\n")

source_counts = Counter()
null_code_counts = Counter()
source_type_counts = Counter()
mismatches = []
samples_by_source = {}

for s in servers:
    src = s.get("source_name", "unknown")
    cs = s.get("country_source", "none")
    stored_cc = s.get("country_code")
    stored_cn = s.get("country_name")

    source_counts[src] += 1
    source_type_counts[(src, cs)] += 1

    if not stored_cc or stored_cc == "None" or stored_cc == "XX":
        null_code_counts[src] += 1

    raw_item = s.get("raw_data") or {}
    raw_cc = raw_item.get("country_code") or raw_item.get("countryCode")
    raw_cn = raw_item.get("country_name") or raw_item.get("countryName") or raw_item.get("country") or raw_item.get("location")

    if raw_cc and str(raw_cc).strip().upper() not in ["NONE", "NULL", "", "UNKNOWN", "XX"]:
        if str(raw_cc).strip().upper() != stored_cc:
            mismatches.append((src, s.get("source_id"), raw_cc, stored_cc, raw_cn, stored_cn))

    if src not in samples_by_source:
        samples_by_source[src] = []
    if len(samples_by_source[src]) < 5:
        samples_by_source[src].append({
            "source_id": s.get("source_id"),
            "raw_country_code": raw_cc,
            "stored_country_code": stored_cc,
            "raw_country_name": raw_cn,
            "stored_country_name": stored_cn,
            "continent": s.get("continent"),
            "host": s.get("host")
        })

print("1. Server Counts by Source:")
for src, count in sorted(source_counts.items(), key=lambda x: -x[1]):
    print(f"   • {src}: {count} servers")

print("\n2. Country Source (country_source) Breakdown per Source:")
for (src, cs), count in sorted(source_type_counts.items()):
    print(f"   • Source: {src:15s} | country_source: {cs:20s} -> {count} servers")

print("\n3. Null/Empty Country Code Counts by Source:")
for src in sorted(source_counts.keys()):
    count = null_code_counts.get(src, 0)
    print(f"   • {src}: {count} null/empty/XX country codes")

print(f"\n4. Total Mismatches Between Raw Source Code & Stored Code: {len(mismatches)}")
for m in mismatches[:10]:
    print(f"   ⚠️ WARNING MISMATCH [{m[0]} | {m[1]}]: Raw CC='{m[2]}' -> Stored CC='{m[3]}'")

print("\n5. First 5 Samples for Each Source:")
for src, samples in samples_by_source.items():
    print(f"\n   🔹 Source: {src.upper()}")
    for idx, sample in enumerate(samples, start=1):
        print(f"      Sample {idx}: ID='{sample['source_id']}' | Raw CC='{sample['raw_country_code']}' | Stored CC='{sample['stored_country_code']}' | Stored Name='{sample['stored_country_name']}' | Continent='{sample['continent']}' | Host='{sample['host']}'")
