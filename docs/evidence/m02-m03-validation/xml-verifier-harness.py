"""Synthetic report rejection checks; this is not Android/JVM/device execution."""
from pathlib import Path
import importlib.util
import json
import tempfile
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[3]
spec = importlib.util.spec_from_file_location("bookreader_xml_check", root / "scripts/check-test-results.py")
checker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checker)
manifest = {"fixture": [["Fixture", "first"], ["Fixture", "second"]]}
valid = '<testcase classname="Fixture" name="first"/><testcase classname="Fixture" name="second[jvm]"/>'
fixtures = {
    "valid": (f'<testsuite tests="2">{valid}</testsuite>', True),
    "missing-required": ('<testsuite tests="1"><testcase classname="Fixture" name="first"/></testsuite>', False),
    "skipped": (f'<testsuite tests="3">{valid}<testcase classname="Other" name="skipped"><skipped/></testcase></testsuite>', False),
    "unexpected-failure": (f'<testsuite tests="3">{valid}<testcase classname="Other" name="bad"><failure/></testcase></testsuite>', False),
    "empty": ('<testsuite tests="0"/>', False),
    "duplicate": (f'<testsuite tests="3">{valid}<testcase classname="Fixture" name="first"/></testsuite>', False),
    "wrong-summary-count": (f'<testsuite tests="999">{valid}</testsuite>', False),
    "failed-summary": (f'<testsuite tests="2" failures="1">{valid}</testsuite>', False),
    "malformed": ('<testsuite>', False),
}
results = []
with tempfile.TemporaryDirectory(prefix="bookreader-xml-fixtures-") as temporary:
    for name, (xml, expected_success) in fixtures.items():
        folder = Path(temporary) / name
        folder.mkdir()
        (folder / "TEST-fixture.xml").write_text(xml)
        try:
            checker.verify("fixture", folder, manifest)
            succeeded = True
        except (ValueError, ET.ParseError):
            succeeded = False
        if succeeded != expected_success:
            raise AssertionError((name, expected_success, succeeded))
        results.append({"fixture": name, "expected_success": expected_success, "actual_success": succeeded})
print(json.dumps(results, indent=2))
print("PASS nine synthetic XML acceptance/rejection checks; no application tests executed")
