import json
import logging
import requests
from typing import Optional, Dict, Any, List
import boto3
from botocore.config import Config as BotoConfig
from botocore.exceptions import ClientError
from sync.config import Config

logger = logging.getLogger(__name__)

class R2StorageAdapter:
    def __init__(self, config: Config):
        self.config = config
        self.bucket = config.r2_bucket_name
        self.dry_run = config.dry_run
        self.s3_client = None

        if not self.dry_run and config.r2_access_key_id and config.r2_secret_access_key:
            endpoint_url = config.get_r2_endpoint_url()
            self.s3_client = boto3.client(
                "s3",
                endpoint_url=endpoint_url,
                aws_access_key_id=config.r2_access_key_id,
                aws_secret_access_key=config.r2_secret_access_key,
                config=BotoConfig(signature_version="s3v4")
            )

    def get_previous_manifest(self) -> Dict[str, Any]:
        if not self.s3_client:
            logger.info("No S3 client configured or dry-run. Returning empty manifest.")
            return {}

        try:
            res = self.s3_client.get_object(Bucket=self.bucket, Key="v1/manifest.json")
            body = res["Body"].read().decode("utf-8")
            return json.loads(body)
        except ClientError as e:
            if e.response['Error']['Code'] in ('NoSuchKey', '404'):
                logger.info("No previous manifest found in R2.")
            else:
                logger.warning(f"Error fetching previous manifest from R2: {e}")
            return {}
        except Exception as e:
            logger.warning(f"Failed to read manifest: {e}")
            return {}

    def upload_profile(self, server_id: str, content: str, sha256: str) -> bool:
        key = f"v1/profiles/{server_id}.ovpn"
        if self.dry_run or not self.s3_client:
            logger.info(f"[DRY-RUN] Upload profile {key}")
            return True

        try:
            self.s3_client.put_object(
                Bucket=self.bucket,
                Key=key,
                Body=content.encode("utf-8"),
                ContentType="application/x-openvpn-profile",
                ContentDisposition=f'attachment; filename="{server_id}.ovpn"',
                CacheControl="public, max-age=86400, s-maxage=604800, immutable",
                Metadata={"sha256": sha256}
            )
            return True
        except Exception as e:
            logger.error(f"Failed to upload profile {key} to R2: {e}")
            return False

    def upload_manifest_and_servers(self, manifest_data: Dict[str, Any], servers_data: Dict[str, Any]) -> bool:
        if self.dry_run:
            logger.info("[DRY-RUN] Skipping S3/KV publish.")
            return True

        # Upload directly to Cloudflare Worker upload endpoint if secret configured
        secret = self.config.backend_secret_key
        if secret and self.config.cloudflare_account_id:
            worker_upload_url = "https://zvpn-backend.xocqoc.workers.dev/upload"
            try:
                headers = {
                    "Authorization": f"Bearer {secret}",
                    "Content-Type": "application/json"
                }
                res = requests.post(worker_upload_url, json=servers_data, headers=headers, timeout=30)
                if res.status_code == 200:
                    logger.info("Successfully uploaded SERVER_LIST to Cloudflare Worker KV via /upload!")
            except Exception as e:
                logger.warning(f"Worker upload endpoint error: {e}")

        if not self.s3_client:
            logger.info("S3 client not configured. Proceeding with Worker upload.")
            return True

        try:
            manifest_json = json.dumps(manifest_data, indent=2).encode("utf-8")
            servers_json = json.dumps(servers_data, indent=2).encode("utf-8")

            # 1. Upload servers.json
            self.s3_client.put_object(
                Bucket=self.bucket,
                Key="v1/servers.json",
                Body=servers_json,
                ContentType="application/json; charset=utf-8",
                CacheControl="public, max-age=300, s-maxage=3600, stale-while-revalidate=86400"
            )

            # 2. Upload manifest.json
            self.s3_client.put_object(
                Bucket=self.bucket,
                Key="v1/manifest.json",
                Body=manifest_json,
                ContentType="application/json; charset=utf-8",
                CacheControl="public, max-age=60, s-maxage=300, stale-while-revalidate=3600",
                Metadata={"manifest_sha256": manifest_data.get("manifest_sha256", "")}
            )
            return True
        except Exception as e:
            logger.error(f"Failed to upload manifest/servers to R2: {e}")
            return False

    def delete_profile(self, server_id: str) -> bool:
        key = f"v1/profiles/{server_id}.ovpn"
        if self.dry_run or not self.s3_client:
            logger.info(f"[DRY-RUN] Delete profile {key}")
            return True

        try:
            self.s3_client.delete_object(Bucket=self.bucket, Key=key)
            return True
        except Exception as e:
            logger.warning(f"Failed to delete profile {key} from R2: {e}")
            return False

    def delete_profiles_batch(self, server_ids: Any) -> bool:
        if not server_ids:
            return True
        if self.dry_run or not self.s3_client:
            logger.info(f"[DRY-RUN] Batch delete {len(server_ids)} profiles from R2")
            return True

        ids_list = list(server_ids)
        batch_size = 1000
        for i in range(0, len(ids_list), batch_size):
            chunk = ids_list[i:i + batch_size]
            delete_keys = [{'Key': f"v1/profiles/{sid}.ovpn"} for sid in chunk]
            try:
                self.s3_client.delete_objects(
                    Bucket=self.bucket,
                    Delete={'Objects': delete_keys, 'Quiet': True}
                )
            except Exception as e:
                logger.warning(f"Failed to batch delete profiles chunk from R2: {e}")
        return True
