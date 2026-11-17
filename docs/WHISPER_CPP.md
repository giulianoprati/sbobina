# Vendored whisper.cpp

| | |
|---|---|
| Upstream | https://github.com/ggml-org/whisper.cpp |
| Version | `v1.9.5` |
| Commit | `d1be6fde11ac6e0407606b4e42fe72d34add8037` (the tag is annotated: object `d2a764c648779feb36d44363f212a29b00aad1af`) |
| Location | `app/src/main/cpp/whisper.cpp` |
| Local changes | **None.** Only whole upstream directories were left out (`bindings`, `ci`, `examples`, `grammars`, `media`, `models`, `samples`, `scripts`, `tests`) |

whisper.cpp parses the model file in native code, so its provenance matters (see the threat model, T21).

## Verify

```powershell
.\scripts\verify-whisper.ps1
```

The script checks that the tag still points at the pinned commit, downloads the official archive and
compares the SHA-256 of every vendored file. It fails on any modified or added file.

## Update

1. Copy the new release over `app/src/main/cpp/whisper.cpp` (same omitted directories).
2. Update `$Tag` and `$Commit` in `scripts/verify-whisper.ps1`, and the table above.
3. Run the script, build, and run a transcription on a device.
4. Re-read the threat model, TB4 and TB6: an update reopens the review of the model parser.
