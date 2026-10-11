#!/usr/bin/env python3
"""Allocate only inside the dedicated 8 MiB mount, and report actual kernel errno."""
import errno
import json
import os
from pathlib import Path
import sys


def main():
    root = Path(os.environ["BOOKREADER_ENOSPC_ROOT"]).resolve(strict=True)
    target = Path(sys.argv[1]).resolve()
    # Never trust an arbitrary environment path as permission to exhaust a filesystem.
    mounts = [line.split() for line in Path("/proc/mounts").read_text().splitlines()]
    assert any(row[:3] == ["bookreader-enospc", str(root), "tmpfs"] for row in mounts)
    assert target.parent.parent == root and not target.exists()
    stat = os.statvfs(root)
    capacity = stat.f_blocks * stat.f_frsize
    assert capacity == 8 * 1024 * 1024
    block = b"\x5a" * stat.f_frsize
    written = 0
    observed_errno = None
    with target.open("xb", buffering=0) as filler:
        try:
            # One extra block is sufficient to prove exhaustion; never grow without a cap.
            for _ in range(stat.f_blocks + 1):
                count = filler.write(block)
                assert count == len(block)
                written += count
            raise AssertionError("bounded allocation did not reach ENOSPC")
        except OSError as failure:
            if failure.errno != errno.ENOSPC:
                raise
            observed_errno = failure.errno
    after = os.statvfs(root)
    assert after.f_bavail == 0
    print(json.dumps(dict(errno=observed_errno, capacity_bytes=capacity,
                         written_bytes=written, available_bytes=after.f_bavail * after.f_frsize)))


if __name__ == "__main__":
    main()
