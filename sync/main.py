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
    max_failure_ratio = float(os.getenv("MAX_DOWNLOAD_FAILURE_RATIO", "0.50"))
    test_profile_limit = int(os.getenv("TEST_PROFILE_LIMIT", "0"))

    logger.info("Initializing Multi-Source VPN Cloud Sync Engine...")
    logger.info(f"Dry run mode: {config.dry_run}")

    storage = R2StorageAdapter(config)
    harvester = MultiSourceHarvester(access_key=config.access_key)
    downloader = OvpnDownloader(base_url=config.base_url, access_key=config.access_key)

    # 1. Fetch previous manifest from Cloudflare R2
    previous_manifest = storage.get_previous_manifest()
    prev_servers = previous_manifest.get("servers", {})
    prev_count = len(prev_servers)
    logger.info(f"Previous manifest server count in Cloudflare R2: {prev_count}")

    # 2. Harvest servers from all multi-sources
    try:
        discovered_servers = harvester.harvest_all_sources()
        logger.info(f"Harvested {len(discovered_servers)} total active servers from multi-sources.")
    except Exception as e:
        logger.error(f"Multi-source harvesting failed: {e}. Preserving previous R2 state.")
        sys.exit(1)

    # Filter downloadable servers
    downloadable_servers = {
        sid: s for sid, s in discovered_servers.items()
        if s.has_valid_profile_mapping()
    }
    logger.info(f"Filtered downloadable profiles: {len(downloadable_servers)}/{len(discovered_servers)}")

    if not downloadable_servers:
        logger.error("No downloadable profile targets found across all sources. Preserving previous R2 state.")
        sys.exit(1)

    # 3. Validate safety
    try:
        validate_sync_safety(downloadable_servers, prev_count, min_ratio=config.min_server_ratio)
    except SafetyValidationError as e:
        logger.error(f"Safety check failed: {e}. Preserving previous R2 state.")
        sys.exit(1)

    # 4. Compute Diff
    current_ids = set(downloadable_servers.keys())
    diff = compute_diff(prev_servers, current_ids)
    logger.info(f"Diff results: New={len(diff.new_ids)}, Removed={len(diff.removed_ids)}, Common={len(diff.common_ids)}")

    # 5. Process new/updated server profiles
    active_entries: Dict[str, Dict[str, Any]] = {}
    now_iso = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

    # Retain common servers that already exist in previous manifest
    for sid in diff.common_ids:
        prev_info = prev_servers[sid]
        server_meta = downloadable_servers[sid].to_dict()
        active_entries[sid] = {
            "metadata": server_meta,
            "sha256": prev_info.get("sha256", ""),
            "size": prev_info.get("size", 0),
            "updated_at": prev_info.get("updated_at", now_iso)
        }

    # Slice new IDs if TEST_PROFILE_LIMIT is enabled
    target_new_ids = list(diff.new_ids)
    if test_profile_limit > 0 and len(target_new_ids) > test_profile_limit:
        target_new_ids = target_new_ids[:test_profile_limit]
        logger.warning(
            "TEST_PROFILE_LIMIT active: processing only %d profile(s)",
            len(target_new_ids)
        )

    workers = 1 if test_profile_limit == 1 else min(config.max_workers, len(target_new_ids) or 1)

    # Download / Process new server profiles
    download_success_count = 0
    download_fail_count = 0

    def process_new_server(sid: str):
        server_info = downloadable_servers[sid]
        result = downloader.download_profile(server_info)
        if result.is_success and result.content and result.sha256:
            uploaded = storage.upload_profile(server_info.storage_id, result.content, result.sha256)
            if uploaded:
                meta = server_info.to_dict()
                # Embed inline content so app can parse without extra HTTP fetch
                if result.target_type == "inline_decoded_profile" and result.content:
                    meta["ovpn_content"] = result.content
                return sid, {
                    "metadata": meta,
                    "sha256": result.sha256,
                    "size": result.bytes_downloaded,
                    "updated_at": now_iso
                }
        return sid, None

    priority_new_ids = [sid for sid in target_new_ids if downloadable_servers[sid].source_name != "publicvpnlist"]
    pvl_new_ids = [sid for sid in target_new_ids if downloadable_servers[sid].source_name == "publicvpnlist"]

    logger.info(f"Prioritizing {len(priority_new_ids)} non-PublicVPNList servers (VPNGate, Auto-OVPN, VPNBook, Riseup)...")

    # Step A: Process non-PublicVPNList priority servers
    if priority_new_ids:
        with concurrent.futures.ThreadPoolExecutor(max_workers=min(config.max_workers, len(priority_new_ids))) as executor:
            futures = [executor.submit(process_new_server, sid) for sid in priority_new_ids]
            for future in concurrent.futures.as_completed(futures):
                sid, res = future.result()
                if res:
                    active_entries[sid] = res
                    download_success_count += 1
                else:
                    download_fail_count += 1

        # Publish intermediate manifest with priority servers to Cloudflare R2 / KV
        builder = ManifestBuilder()
        changes_dict = diff.to_changes_dict(updated_count=0)
        manifest_data, servers_data = builder.build_manifest_and_servers(active_entries, changes_dict)
        storage.upload_manifest_and_servers(manifest_data, servers_data)
        logger.info(f"Published priority manifest with {len(active_entries)} live servers (VPNGate/VPNBook/Auto-OVPN) to Cloudflare R2!")

    # Step B: Process PublicVPNList servers with 1.5s pacing
    if pvl_new_ids:
        logger.info(f"🤖 [AI Engine] Processing {len(pvl_new_ids)} PublicVPNList servers with 1.5s pacing delay...")
        pvl_workers = 2
        total_pvl = len(pvl_new_ids)
        completed_pvl = 0
        with concurrent.futures.ThreadPoolExecutor(max_workers=pvl_workers) as executor:
            futures = [executor.submit(process_new_server, sid) for sid in pvl_new_ids]
            for future in concurrent.futures.as_completed(futures):
                sid, res = future.result()
                completed_pvl += 1
                if res:
                    active_entries[sid] = res
                    download_success_count += 1
                else:
                    download_fail_count += 1

                if completed_pvl % 25 == 0 or completed_pvl == total_pvl:
                    pct = (completed_pvl / float(total_pvl)) * 100
                    logger.info(f"🤖 [AI Engine Sync Progress] [{completed_pvl}/{total_pvl}] ({pct:.1f}%) | Total Active: {len(active_entries)} | Success: {download_success_count} | Failed: {download_fail_count}")

    logger.info(f"Profiles processed. Success: {download_success_count}, Failed: {download_fail_count}")

    if not active_entries:
        logger.error("No active server entries processed. Preserving previous R2 state.")
        sys.exit(1)

    # 6. Build Manifest & Servers JSON
    builder = ManifestBuilder()
    changes_dict = diff.to_changes_dict(updated_count=0)
    manifest_data, servers_data = builder.build_manifest_and_servers(active_entries, changes_dict)

    # 7. Upload Manifest and Servers JSON to Cloudflare R2 (Atomic Publish)
    published = storage.upload_manifest_and_servers(manifest_data, servers_data)
    if not published:
        logger.error("Failed to publish manifest and servers. Aborting delete step.")
        sys.exit(1)

    # 8. Delete removed server profiles from R2 after successful publish
    if diff.removed_ids and test_profile_limit == 0:
        logger.info(f"Cleaning up {len(diff.removed_ids)} removed profiles from R2...")
        for sid in diff.removed_ids:
            storage.delete_profile(sid)

    logger.info("Multi-source Cloud Sync completed successfully!")
    logger.info(f"Summary: Total Active={len(active_entries)}, Version={manifest_data.get('version')}")

if __name__ == "__main__":
    run_sync()
