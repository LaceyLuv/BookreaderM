#!/usr/bin/env python3
"""Kill an active generated batch after a durable checkpoint; verify it in a new process."""
from pathlib import Path
import json
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

PACKAGE = 'org.bookreader.mobile'
CLASS = PACKAGE + '.library.LibraryBatchHostProcessTest'
RUNNER = PACKAGE + '.test/androidx.test.runner.AndroidJUnitRunner'
PROOF = 'files/library-batch-host.properties'


def run(root):
    root.mkdir(parents=True, exist_ok=True)
    xml = root / 'TEST-library-batch-host.xml'
    xml.unlink(missing_ok=True)
    commands = []
    active = None

    def adb(label, *args, allow_missing=False):
        command = ['adb', *args]
        result = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=30)
        commands.append(dict(label=label, argv=command, exit_code=result.returncode))
        if not allow_missing or result.returncode == 0:
            (root / f'{label}.log').write_text(result.stdout)
        if result.returncode and not allow_missing:
            raise ValueError(f'{label} exit={result.returncode}')
        return result.stdout.strip() if result.returncode == 0 else None

    def start(method, output):
        command = ['adb', 'shell', 'am', 'instrument', '-w', '-r', '-e', 'class', f'{CLASS}#{method}', RUNNER]
        commands.append(dict(label=method, argv=command))
        return subprocess.Popen(command, stdout=output, stderr=subprocess.STDOUT)

    try:
        # This file is generated solely by the host fixture, never a book or app database.
        adb('remove-old-checkpoint', 'shell', 'run-as', PACKAGE, 'rm', '-f', PROOF)
        with (root / 'interrupted-preparation.log').open('w') as output:
            active = start('prepareActiveBatchForHostKill', output)
            deadline = time.monotonic() + 60
            while time.monotonic() < deadline:
                proof = adb('active-checkpoint', 'shell', 'run-as', PACKAGE, 'cat', PROOF, allow_missing=True)
                if proof and all(f'{key}=' in proof for key in ('bookId', 'sourceHash', 'revision', 'pendingJob', 'ready')):
                    break
                if active.poll() is not None:
                    raise ValueError('Preparation ended before the active checkpoint')
                time.sleep(.5)
            else:
                raise ValueError('No durable active checkpoint within 60s')
            assert 'ready=true' in proof
            old_pid = adb('active-pid', 'shell', 'pidof', PACKAGE)
            assert old_pid and re.fullmatch(r'\d+', old_pid)
            adb('external-force-stop', 'shell', 'am', 'force-stop', PACKAGE)
            absent = adb('pid-after-stop', 'shell', 'sh', '-c', f"'if pidof {PACKAGE}; then exit 1; else echo absent; fi'")
            assert absent == 'absent'
            active.wait(timeout=30)
            active = None
        preparation = (root / 'interrupted-preparation.log').read_text()
        assert f'INSTRUMENTATION_STATUS: class={CLASS}' in preparation
        assert 'INSTRUMENTATION_STATUS: test=prepareActiveBatchForHostKill' in preparation
        assert 'INSTRUMENTATION_STATUS_CODE: 1' in preparation
        assert 'OK (1 test)' not in preparation, 'Preparation must be interrupted, not complete normally'
        method = 'coldStartKeepsCatalogAndRecordsThenResumesPendingEncoding'
        with (root / 'cold-verification.log').open('w') as output:
            active = start(method, output)
            deadline = time.monotonic() + 60
            cold_pid = None
            while time.monotonic() < deadline and active.poll() is None:
                current = adb('cold-pid', 'shell', 'pidof', PACKAGE, allow_missing=True)
                if current and re.fullmatch(r'\d+', current) and current != old_pid:
                    cold_pid = current
                    break
                time.sleep(.2)
            assert cold_pid, 'Verification did not start a new app process'
            code = active.wait(timeout=90)
            active = None
            assert code == 0
        result = (root / 'cold-verification.log').read_text()
        statuses = re.findall(r'^INSTRUMENTATION_STATUS_CODE: (-?\d+)\s*$', result, re.M)
        assert statuses == ['1', '0'], statuses
        assert re.findall(r'^INSTRUMENTATION_STATUS: class=(.*)$', result, re.M) == [CLASS, CLASS]
        assert re.findall(r'^INSTRUMENTATION_STATUS: test=(.*)$', result, re.M) == [method, method]
        assert 'OK (1 test)' in result and re.search(r'^INSTRUMENTATION_CODE: -1\s*$', result, re.M)
        suite = ET.Element('testsuite', name=CLASS, tests='1', failures='0', errors='0', skipped='0')
        ET.SubElement(suite, 'testcase', classname=CLASS, name=method)
        ET.ElementTree(suite).write(xml, encoding='utf-8', xml_declaration=True)
        summary = dict(status='PASS', old_pid=old_pid, cold_pid=cold_pid, active_checkpoint_observed=True,
                       preparation='Interrupted intentionally by host; not counted as a passing JUnit test',
                       claim='Active batch force-stop preserves committed generated book/catalog/reading records and resumes staged encoding',
                       cases=[[CLASS, method]], commands=commands)
        (root / 'summary.json').write_text(json.dumps(summary, indent=2)+'\n')
        print('PASS active batch external force-stop and one cold-process verification test')
    except Exception as failure:
        (root / 'summary.json').write_text(json.dumps(dict(status='FAIL', error=str(failure), commands=commands), indent=2)+'\n')
        raise
    finally:
        if active is not None and active.poll() is None:
            active.terminate()
            active.wait(timeout=10)


if __name__ == '__main__':
    try:
        run(Path(sys.argv[1]))
    except Exception as failure:
        print(f'FAIL library batch host: {failure}', file=sys.stderr)
        sys.exit(65)
