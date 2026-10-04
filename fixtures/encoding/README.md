# Self-authored strict TXT encoding fixtures

All text and binary fragments here were created for this project and are dedicated to the public domain under CC0-1.0. They contain no third-party book content. `manifest.json` records actual byte lengths and SHA-256 digests.

`bookreader-demo-ko-utf8.txt` is a 21,544-byte Korean demonstration book with twelve sections, emoji, and CRLF newlines. It is intended for manual import and reading checks; it is not inserted into the app's library automatically.

The UTF BOM fixtures encode this exact source (escapes denote code points, not literal backslash text):

```text
\uFEFF  직접 만든 fixture\r\n한글 각 갂\r😀 끝\n\uFEFF보존\r\n
```

`canonical-expected.txt` is UTF-8 without an initial BOM. It removes only the first BOM and replaces CRLF/CR with LF. Spaces, consecutive line breaks, supplementary characters, and the interior U+FEFF remain intact. Canonical positions count UTF-16 code units, using normalization version 1.

`cp949-extension.txt` contains `CP949: 갂\r\n`; U+AC02 has the CP949 bytes `81 41`, which a strict EUC-KR decoder must reject. `euc-kr-common.txt` contains `EUC-KR: 한글 각\r\n`. BOM-less UTF-16 fixtures contain `ASCII and 한글\r\n` and require explicit selection. `invalid-utf8.txt` ends with the incomplete sequence `F0 9F 98`; `binary-masked.txt` starts with the ZIP header `50 4B 03 04`.

`AndroidTxtEncodingTest.androidCp949ExtensionFixtureIsDistinctFromEucKr` constructs the exact CP949 fixture bytes and tests Android's actual charset implementation. Host-JDK tests cannot establish Android charset support.

The supported TXT policy rejects recognized binary file headers, NUL, DEL, and C0 controls other than TAB, LF, CR, vertical tab and form feed. It preserves the accepted whitespace controls. A `.txt` name does not bypass these checks. This policy is a bounded format check, not an exhaustive classifier for every binary file.

`TxtDecoder.validate` scans the whole stream within the 256MiB source limit and reports a late decode error. `probe` only inspects a bounded prefix (64KiB by default) and provides separate manual previews for each encoding; a successful preview is never sufficient to commit an import. UTF-16 without BOM and legacy encodings are never chosen automatically. Explicit conflicting BOMs and unsupported UTF-32 BOMs are rejected.

The caller owns and closes the source stream on success, failure, or cancellation. Output chunks are provisional until full decode succeeds. Cancellation and wall-time checks run before/after IO and output chunks; a blocked provider read must be interrupted/closed by its owner. Neither validation nor preview rewrites the managed original.
