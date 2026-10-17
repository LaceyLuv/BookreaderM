#!/usr/bin/env python3
"""Read-only APK packaging audit. This is not a physical-device or release approval gate."""
import argparse, hashlib, json, os, pathlib, re, struct, subprocess, zipfile
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('apk', type=pathlib.Path)
parser.add_argument('--source-sha', required=True)
parser.add_argument('--output', type=pathlib.Path, required=True)
parser.add_argument('--unsigned', action='store_true', help='Audit an intentionally unsigned local APK; signature verification remains separately reported.')
args = parser.parse_args()
assert re.fullmatch(r'[0-9a-f]{40}', args.source_sha), 'Exact build source SHA required'
assert args.apk.is_file() and args.apk.stat().st_size <= 512 * 1024 * 1024
sdk = pathlib.Path(os.environ.get('ANDROID_HOME', os.environ.get('ANDROID_SDK_ROOT', '')))
build_tools = sdk / 'build-tools' / '35.0.0'
def command(*values):
    result = subprocess.run([str(v) for v in values], text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    return {'command': [str(v) for v in values], 'exit_code': result.returncode, 'output': result.stdout}
manifest = command(build_tools / 'aapt2', 'dump', 'xmltree', '--file', 'AndroidManifest.xml', args.apk)
assert manifest['exit_code'] == 0, manifest['output']
alignment = command(build_tools / 'zipalign', '-c', '-P', '16', '4', args.apk)
signer = command(build_tools / 'apksigner', 'verify', '--print-certs', args.apk)
permissions = set()
element = ''
for line in manifest['output'].splitlines():
    if 'E: ' in line: element = line.split('E: ', 1)[1].split()[0]
    if element in ('uses-permission', 'uses-permission-sdk-23') and 'android:name' in line:
        permissions.update(re.findall(r'android\.permission\.[A-Z_]+', line))
permissions = sorted(permissions)
forbidden = [p for p in permissions if p != 'android.permission.ACCESS_NETWORK_STATE']
backup_disabled = bool(re.search(r'android:allowBackup[^\n]*=false', manifest['output']))
cleartext_disabled = bool(re.search(r'android:usesCleartextTraffic[^\n]*=false', manifest['output']))
abis, native = set(), []
with zipfile.ZipFile(args.apk) as archive:
    for info in archive.infolist():
        if not info.filename.startswith('lib/') or not info.filename.endswith('.so'): continue
        assert info.file_size <= 64 * 1024 * 1024
        data = archive.read(info)
        assert data[:4] == b'\x7fELF', info.filename
        bits, encoding = data[4], data[5]
        endian = '<' if encoding == 1 else '>'
        assert bits in (1, 2) and encoding in (1, 2)
        if bits == 2:
            offset = struct.unpack_from(endian + 'Q', data, 32)[0]
            size, count = struct.unpack_from(endian + 'HH', data, 54)
            layout = endian + 'IIQQQQQQ'
        else:
            offset = struct.unpack_from(endian + 'I', data, 28)[0]
            size, count = struct.unpack_from(endian + 'HH', data, 42)
            layout = endian + 'IIIIIIII'
        assert count <= 4096 and offset + size * count <= len(data)
        segments = []
        for index in range(count):
            header = struct.unpack_from(layout, data, offset + index * size)
            if header[0] != 1: continue
            file_offset, address, align = (header[2], header[3], header[7]) if bits == 2 else (header[1], header[2], header[7])
            segments.append({'alignment': align, 'offset': file_offset, 'address': address,
                'aligned_16kb': align >= 16384 and file_offset % 16384 == address % 16384})
        assert segments, info.filename
        abis.add(info.filename.split('/')[1])
        native.append({'path': info.filename, 'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest(),
            'load_segments': segments, 'pass': all(s['aligned_16kb'] for s in segments)})
signature_status = 'PASS' if signer['exit_code'] == 0 else 'NOT_SIGNED' if args.unsigned else 'FAIL'
status = not forbidden and backup_disabled and cleartext_disabled and alignment['exit_code'] == 0 and (signer['exit_code'] == 0 or args.unsigned) and all(n['pass'] for n in native)
report = {'status': 'PASS' if status else 'FAIL', 'scope': 'static APK permissions, backup flags, signature verification, ZIP and ELF 16KB alignment',
    'signature_status': signature_status, 'intentionally_unsigned': args.unsigned,
    'source_sha': args.source_sha, 'apk': str(args.apk), 'bytes': args.apk.stat().st_size,
    'sha256': hashlib.sha256(args.apk.read_bytes()).hexdigest(), 'permissions': permissions, 'unexpected_permissions': forbidden,
    'backup_disabled': backup_disabled, 'cleartext_disabled': cleartext_disabled, 'abis': sorted(abis), 'native': native,
    'commands': {'manifest': manifest, 'alignment': alignment, 'signature': signer},
    'physical_16kb_device': 'NOT_RUN', 'physical_performance': 'NOT_RUN', 'release_approval': 'NOT_RUN'}
args.output.parent.mkdir(parents=True, exist_ok=True)
args.output.write_text(json.dumps(report, indent=2) + '\n')
print(json.dumps({k: report[k] for k in ('status', 'source_sha', 'sha256', 'abis', 'unexpected_permissions', 'backup_disabled', 'cleartext_disabled')}))
raise SystemExit(0 if status else 1)
