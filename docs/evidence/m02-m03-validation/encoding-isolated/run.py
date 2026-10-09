"""Encoding-only host compiler/JUnit check. This is not a Gradle or Android-device check."""
from pathlib import Path
import hashlib
import json
import subprocess
import tempfile

root = Path(__file__).resolve().parents[4]
output = Path(__file__).resolve().parent
cache = Path('/workspace/toolchains/gradle-user/caches/modules-2/files-2.1')

def jar(pattern):
    return str(next(cache.glob(pattern)))

compiler = jar('org.jetbrains.kotlin/kotlin-compiler-embeddable/2.2.20/*/*.jar')
stdlib = jar('org.jetbrains.kotlin/kotlin-stdlib/2.2.20/*/*.jar')
reflect = jar('org.jetbrains.kotlin/kotlin-reflect/2.1.20/*/*.jar')
coroutines = jar('org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm/1.10.2/*/*.jar')
annotations = jar('org.jetbrains/annotations/13.0/*/*.jar')
junit = jar('junit/junit/4.13.2/*/*.jar')
hamcrest = jar('org.hamcrest/hamcrest-core/1.3/*/*.jar')
java = '/workspace/toolchains/jdk-17.0.16+8/bin/java'
classes = Path('/tmp/bookreader-encoding-check/classes')
classes.mkdir(parents=True, exist_ok=True)
sources = sorted(root.glob('androidApp/src/main/kotlin/org/bookreader/mobile/encoding/*.kt'))
sources += sorted(root.glob('androidApp/src/test/kotlin/org/bookreader/mobile/encoding/*.kt'))
# Historical-source rerun after the current-source ICU fix. Never use these 13 cases as current verification.
source_commit = 'fdd9ac15182af4e4c1a21a814c5d9c2aa3393808'
source_labels = [str(path.relative_to(root)) for path in sources]
historical = tempfile.TemporaryDirectory(prefix='bookreader-encoding-fdd9ac1-')
sources = []
for label in source_labels:
    content = subprocess.check_output(['git', 'show', source_commit + ':' + label], cwd=root)
    path = Path(historical.name) / Path(label).name
    path.write_bytes(content)
    sources.append(path)
classpath = ':'.join([stdlib, junit, hamcrest, annotations])
compile_command = [java, '-cp', ':'.join([compiler, stdlib, reflect, coroutines, annotations]),
                   'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-stdlib', '-no-reflect',
                   '-jvm-target', '17', '-classpath', classpath, '-d', str(classes)] + list(map(str, sources))
test_command = [java, '-cp', str(classes) + ':' + classpath,
                'org.junit.runner.JUnitCore', 'org.bookreader.mobile.encoding.TxtDecoderTest']
results = [{'check': 'historical-source-rerun', 'sourceCommit': source_commit, 'note': 'Rerun to preserve historical 13-case evidence after isolated logs were superseded by ICU fix; original full native CI XML remains unchanged.'}]
for name, command in [('compile', compile_command), ('junit', test_command)]:
    result = subprocess.run(command, cwd=root, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    (output / f'{name}.log').write_text(result.stdout + '\nEXIT ' + str(result.returncode) + '\n')
    results.append({'check': name, 'command': command, 'exitCode': result.returncode})
    print(result.stdout, f'{name.upper()}_EXIT {result.returncode}')
    if result.returncode:
        (output / 'results.json').write_text(json.dumps(results, indent=2) + '\n')
        raise SystemExit(result.returncode)

manifest = json.loads((root / 'fixtures/encoding/manifest.json').read_text())
for fixture in manifest['files']:
    data = (root / 'fixtures/encoding' / fixture['path']).read_bytes()
    assert len(data) == fixture['sizeBytes']
    assert hashlib.sha256(data).hexdigest() == fixture['sha256']
results.append({'check': 'fixture-manifest', 'fixtureCount': len(manifest['files']), 'exitCode': 0})
results.append({'check': 'source-identities', 'sha256': {
    label: hashlib.sha256(path.read_bytes()).hexdigest() for label, path in zip(source_labels, sources)
}})
(output / 'results.json').write_text(json.dumps(results, indent=2) + '\n')
print('FIXTURE_MANIFEST_PASS', len(manifest['files']))
historical.cleanup()
