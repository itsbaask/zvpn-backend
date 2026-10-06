from typing import Dict, Set, Any, Tuple, List

class SyncDiff:
    def __init__(
        self,
        new_ids: Set[str],
        removed_ids: Set[str],
        common_ids: Set[str]
    ):
        self.new_ids = new_ids
        self.removed_ids = removed_ids
        self.common_ids = common_ids

    @property
    def has_changes(self) -> bool:
        return len(self.new_ids) > 0 or len(self.removed_ids) > 0

    def to_changes_dict(self, updated_count: int = 0) -> Dict[str, int]:
        return {
            "added": len(self.new_ids),
            "removed": len(self.removed_ids),
            "updated": updated_count
        }

def compute_diff(
    previous_manifest_servers: Dict[str, Any],
    current_source_ids: Set[str]
) -> SyncDiff:
    previous_ids = set(previous_manifest_servers.keys())
    new_ids = current_source_ids - previous_ids
    removed_ids = previous_ids - current_source_ids
    common_ids = current_source_ids & previous_ids

    return SyncDiff(
        new_ids=new_ids,
        removed_ids=removed_ids,
        common_ids=common_ids
    )
