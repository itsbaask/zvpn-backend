import os
import sys
import logging
import concurrent.futures
from datetime import datetime, timezone
from typing import Dict, Any

from sync.config import Config
from sync.source import MultiSourceHarvester, ServerInfo
from sync.ovpn import OvpnDownloader, DownloadResult
from sync.diff import compute_diff
from sync.safety import validate_sync_safety, SafetyValidationError
from sync.manifest import ManifestBuilder
from sync.storage import R2StorageAdapter

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s"
)
logger = logging.getLogger("sync.main")

def run_sync():
    config = Config()
    logger.info("Initializing Multi-Source VPN Cloud Sync Engine...")

    storage = R2StorageAdapter(config)
    harvester = MultiSourceHarvester(access_key=config.access_key)
    downloader = OvpnDownloader(base_url=config.base_url, access_key=config.access_key)

    # 1. Fetch previous manifest
    previous_manifest = storage.get_previous_manifest()
    prev_servers = previous_manifest.get("servers", {})
    prev_count = len(prev_servers)

    # 2. Harvest servers from ALL multi-sources
    try:
        discovered_servers = harvester.harvest_all_sources()
        logger.info(f"Harvested {len(discovered_servers)} total active servers from multi-sources.")
    except Exception as e:
        logger.error(f"Multi-source harvesting failed: {e}")
        sys.exit(1)

    downloadable_servers = {
        sid: s for sid, s in discovered_servers.items()
        if s.has_valid_profile_mapping()
    }

    if not downloadable_servers:
        logger.error("No downloadable profile targets found across all sources.")
        sys.exit(1)

    # 3. Safety Check
    try:
        validate_sync_safety(downloadable_servers, prev_count, min_ratio=config.min_server_ratio)
    except SafetyValidationError as e:
        logger.error(f"Safety check failed: {e}")
        sys.exit(1)

    # 4. Compute Diff
    current_ids = set(downloadable_servers.keys())
    diff = compute_diff(prev_servers, current_ids)

    # 5. Fast Parallel Server Processing
    active_entries: Dict[str, Dict[str, Any]] = {}
    now_iso = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

    for sid in diff.common_ids:
        prev_info = prev_servers[sid]
        server_meta = downloadable_servers[sid].to_dict()
        active_entries[sid] = {
            "metadata": server_meta,
            "sha256": prev_info.get("sha256", ""),
            "size": prev_info.get("size", 0),
            "updated_at": prev_info.get("updated_at", now_iso)
        }

    target_new_ids = list(diff.new_ids)

    def process_server(sid: str):
        server_info = downloadable_servers[sid]
        result = downloader.download_profile(server_info)
        if result.is_success and result.content and result.sha256:
            meta = server_info.to_dict()
            return sid, {
                "metadata": meta,
                "sha256": result.sha256,
                "size": result.bytes_downloaded,
                "updated_at": now_iso
            }
        return sid, None

    if target_new_ids:
        with concurrent.futures.ThreadPoolExecutor(max_workers=32) as executor:
            futures = [executor.submit(process_server, sid) for sid in target_new_ids]
            for future in concurrent.futures.as_completed(futures):
                sid, res = future.result()
                if res:
                    active_entries[sid] = res

    # 6. Build Manifest & Publish
    builder = ManifestBuilder()
    changes_dict = diff.to_changes_dict(updated_count=0)
    manifest_data, servers_data = builder.build_manifest_and_servers(active_entries, changes_dict)

    published = storage.upload_manifest_and_servers(manifest_data, servers_data)
    if not published:
        logger.error("Failed to publish manifest and servers.")
        sys.exit(1)

    logger.info("Multi-source Cloud Sync completed successfully!")
    logger.info(f"Summary: Total Active={len(active_entries)}, Version={manifest_data.get('version')}")

if __name__ == "__main__":
    run_sync()
