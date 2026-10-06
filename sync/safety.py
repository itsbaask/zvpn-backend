import logging
from typing import Dict, Any, Set

logger = logging.getLogger(__name__)

class SafetyValidationError(Exception):
    """Raised when safety validation checks fail."""
    pass

def validate_source_id(value: Any) -> bool:
    if value is None:
        return False
    val_str = str(value).strip()
    if not val_str or len(val_str) > 200:
        return False
    return True

def validate_sync_safety(
    discovered_servers: Dict[str, Any],
    previous_count: int,
    min_ratio: float = 0.10,
    allow_empty_override: bool = False
) -> None:
    current_count = len(discovered_servers)

    # Check 1: Non-zero count
    if current_count == 0:
        if not allow_empty_override:
            raise SafetyValidationError(
                "Safety Violation: Discovered 0 servers from source. Aborting sync to prevent mass deletion."
            )

    # Check 2: Valid non-empty source IDs
    invalid_ids = []
    for sid in discovered_servers.keys():
        if not validate_source_id(sid):
            invalid_ids.append(sid)

    if invalid_ids:
        raise SafetyValidationError(
            f"Safety Violation: {len(invalid_ids)} invalid or empty source IDs found in source. Sample: {invalid_ids[:5]}"
        )

    logger.info(
        f"Safety validation passed: source IDs valid={current_count} invalid=0 duplicates=0. "
        f"Server count: {current_count} (Previous: {previous_count})"
    )
