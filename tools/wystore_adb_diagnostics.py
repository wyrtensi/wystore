#!/usr/bin/env python3
"""Save Wy Store's sanitized diagnostics over adb without opening its UI."""
import argparse
from pathlib import Path
import subprocess

from adb_ui import find_adb


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('-s', '--serial', required=True, help='Explicit adb device serial')
    parser.add_argument('-o', '--output', type=Path, required=True, help='Destination UTF-8 text file')
    args = parser.parse_args()
    result = subprocess.run([find_adb(), '-s', args.serial, 'exec-out', 'content', 'read',
                             '--uri', 'content://app.wystore.agent/diagnostics'], capture_output=True, timeout=60)
    if result.returncode or not result.stdout.startswith('Wy Store'.encode()):
        raise SystemExit('Could not read Wy Store diagnostics. Install a build with the adb diagnostics endpoint.\n'
                         + result.stderr.decode('utf-8', errors='replace'))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_bytes(result.stdout)
    print(f'Diagnostics saved: {args.output.resolve()} ({len(result.stdout)} bytes)')


if __name__ == '__main__':
    main()
