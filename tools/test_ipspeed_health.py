import os
import sys
import json
import base64
import time
import socket
from collections import Counter
from concurrent.futures import ThreadPoolExecutor, as_completed

def parse_server(uri, proto):
    host, port = None, None
    try:
        if proto == 'vmess':
            b = uri[8:].split('#')[0]
            b += '=' * (-len(b) % 4)
            data = json.loads(base64.b64decode(b).decode('utf-8', errors='ignore'))
            host = data.get('add')
            port = int(data.get('port'))
        elif proto == 'ssr':
            b = uri[6:].split('#')[0]
            b += '=' * (-len(b) % 4)
            dec = base64.urlsafe_b64decode(b).decode('utf-8', errors='ignore')
            parts = dec.split(':')
            host = parts[0]
            port = int(parts[1])
        elif proto in ['ss', 'shadowsocks']:
            body = uri.split('://', 1)[1].split('#')[0]
            if '@' in body:
                hp = body.rsplit('@', 1)[1].split('/')[0].split('?')[0]
            else:
                b = body + '=' * (-len(body) % 4)
                dec = base64.urlsafe_b64decode(b).decode('utf-8', errors='ignore')
                hp = dec.rsplit('@', 1)[1] if '@' in dec else dec
            parts = hp.split(':')
            host = parts[0]
            port = int(parts[1])
        else: # trojan, vless, hysteria2, http
            body = uri.split('://', 1)[1].split('#')[0].split('?')[0].split('/')[0]
            if '@' in body:
                body = body.rsplit('@', 1)[1]
            parts = body.split(':')
            host = parts[0]
            port = int(parts[1])
    except Exception:
        pass
    return host, port

def test_single_server(item):
    uri = item.get('raw_uri', '')
    proto = item.get('protocol', 'unknown')
    sid = item.get('source_id', '')

    host, port = parse_server(uri, proto)
    if not host or not port or port <= 0 or port > 65535:
        return {
            'id': sid,
            'protocol': proto,
            'host': host,
            'port': port,
            'status': 'INVALID_CONFIG',
            'latency_ms': None
        }

    start_time = time.time()
    try:
        s = socket.create_connection((host, port), timeout=2.5)
        latency = round((time.time() - start_time) * 1000)
        s.close()
        return {
            'id': sid,
            'protocol': proto,
            'host': host,
            'port': port,
            'status': 'ONLINE',
            'latency_ms': latency
        }
    except socket.timeout:
        return {
            'id': sid,
            'protocol': proto,
            'host': host,
            'port': port,
            'status': 'TIMEOUT',
            'latency_ms': None
        }
    except ConnectionRefusedError:
        return {
            'id': sid,
            'protocol': proto,
            'host': host,
            'port': port,
            'status': 'REFUSED',
            'latency_ms': None
        }
    except socket.gaierror:
        return {
            'id': sid,
            'protocol': proto,
            'host': host,
            'port': port,
            'status': 'DNS_FAIL',
            'latency_ms': None
        }
    except Exception as e:
        return {
            'id': sid,
            'protocol': proto,
            'host': host,
            'port': port,
            'status': f'ERROR_{type(e).__name__}',
            'latency_ms': None
        }

def main():
    snapshot_path = 'output/snapshot.json'
    if not os.path.exists(snapshot_path):
        print(f"Error: {snapshot_path} not found.")
        sys.exit(1)

    with open(snapshot_path, 'r', encoding='utf-8') as f:
        snapshot = json.load(f)

    ipspeed_servers = [s for s in snapshot if s.get('source_name') == 'ipspeed']
    total = len(ipspeed_servers)
    print(f"Starting Health Check for all {total} IPspeed servers with 200 concurrent threads...")

    results = []
    completed = 0
    t0 = time.time()

    with ThreadPoolExecutor(max_workers=200) as executor:
        futures = {executor.submit(test_single_server, s): s for s in ipspeed_servers}
        for future in as_completed(futures):
            res = future.result()
            results.append(res)
            completed += 1
            if completed % 1000 == 0 or completed == total:
                elapsed = time.time() - t0
                print(f"Checked {completed}/{total} servers in {elapsed:.1f}s...")

    total_time = time.time() - t0
    print(f"\nCompleted testing in {total_time:.2f} seconds.")

    # Status Breakdown
    status_counter = Counter(r['status'] for r in results)
    print("\n=== STATUS BREAKDOWN ===")
    for st, count in status_counter.most_common():
        pct = (count / total) * 100
        print(f"  {st:15}: {count:5} ({pct:5.1f}%)")

    online = [r for r in results if r['status'] == 'ONLINE']
    online_count = len(online)
    print(f"\nTotal ONLINE / WORKING Servers: {online_count} / {total} ({(online_count/total)*100:.1f}%)")

    # Online breakdown by protocol
    proto_online = Counter(r['protocol'] for r in online)
    proto_total = Counter(r['protocol'] for r in results)

    print("\n=== ONLINE SERVERS BY PROTOCOL ===")
    for p in proto_total:
        on = proto_online.get(p, 0)
        tot = proto_total[p]
        pct = (on / tot) * 100 if tot else 0
        print(f"  {p:12}: {on:4} / {tot:4} working ({pct:5.1f}%)")

    if online:
        latencies = [r['latency_ms'] for r in online if r['latency_ms'] is not None]
        avg_lat = sum(latencies) / len(latencies)
        min_lat = min(latencies)
        max_lat = max(latencies)
        print(f"\nLatency Statistics (Working Servers):")
        print(f"  Fastest: {min_lat} ms")
        print(f"  Average: {avg_lat:.1f} ms")
        print(f"  Highest: {max_lat} ms")

        # Top 10 fastest servers
        fastest_10 = sorted(online, key=lambda x: x['latency_ms'])[:10]
        print("\nTop 10 Fastest Working Servers:")
        for idx, s in enumerate(fastest_10, 1):
            print(f"  {idx:2}. {s['protocol']:8} -> {s['host']}:{s['port']} (Ping: {s['latency_ms']} ms)")

    # Save detailed audit file
    os.makedirs('output', exist_ok=True)
    report_path = 'output/ipspeed_health_report.json'
    with open(report_path, 'w', encoding='utf-8') as f:
        json.dump({
            'total_tested': total,
            'online_count': online_count,
            'offline_count': total - online_count,
            'duration_sec': round(total_time, 2),
            'status_summary': dict(status_counter),
            'protocol_summary': {p: {'online': proto_online.get(p, 0), 'total': proto_total[p]} for p in proto_total},
            'results': results
        }, f, indent=2)
    print(f"\nDetailed report saved to {report_path}")

if __name__ == '__main__':
    main()
