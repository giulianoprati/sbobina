# Verifies that the vendored whisper.cpp (app/src/main/cpp/whisper.cpp) is an untouched subset of the
# official release recorded in docs/WHISPER_CPP.md: every file we ship must exist upstream with the same
# SHA-256, and no file may be added. Directories we deliberately left out (examples, models...) are fine.
#
#   .\scripts\verify-whisper.ps1
#
# Exit code 0 = identical, 1 = differences or provenance mismatch. Needs git and tar (both in Windows 10+).

$ErrorActionPreference = 'Stop'

# Keep in sync with docs/WHISPER_CPP.md
$Tag       = 'v1.9.5'
$Commit    = 'd1be6fde11ac6e0407606b4e42fe72d34add8037'
$Repo      = 'https://github.com/ggml-org/whisper.cpp'

$vendored = Join-Path $PSScriptRoot '..\app\src\main\cpp\whisper.cpp' | Resolve-Path | Select-Object -ExpandProperty Path

# 1. The tag must still point at the commit we pinned (guards against a moved or re-created tag).
$line = git ls-remote --tags $Repo "refs/tags/$Tag^{}"
if (-not $line) { throw "Tag $Tag not found in $Repo" }
$remoteCommit = ($line -split '\s+')[0]
if ($remoteCommit -ne $Commit) {
    Write-Host "FAIL: tag $Tag now points to $remoteCommit, expected $Commit" -ForegroundColor Red
    exit 1
}

# 2. Download the official archive into a fresh temp directory and compare file by file.
$work = Join-Path ([IO.Path]::GetTempPath()) ("whisper-verify-" + [Guid]::NewGuid())
New-Item -ItemType Directory $work | Out-Null
try {
    $archive = Join-Path $work 'src.tar.gz'
    Invoke-WebRequest "$Repo/archive/refs/tags/$Tag.tar.gz" -OutFile $archive
    $up = Join-Path $work 'up'
    New-Item -ItemType Directory $up | Out-Null
    tar -xzf $archive -C $up --strip-components=1
    if ($LASTEXITCODE -ne 0) { throw 'tar failed' }

    $problems = @()
    $files = Get-ChildItem $vendored -Recurse -File -Force |
        Where-Object { $_.FullName -notmatch '[\\/]\.git[\\/]' }
    foreach ($f in $files) {
        $rel = $f.FullName.Substring($vendored.Length).TrimStart('\', '/')
        $ref = Join-Path $up $rel
        if (-not (Test-Path -LiteralPath $ref)) { $problems += "ADDED    $rel"; continue }
        if ((Get-FileHash -LiteralPath $f.FullName).Hash -ne (Get-FileHash -LiteralPath $ref).Hash) {
            $problems += "MODIFIED $rel"
        }
    }

    if ($problems.Count -gt 0) {
        Write-Host "FAIL: vendored copy differs from $Tag ($Commit):" -ForegroundColor Red
        $problems | ForEach-Object { Write-Host "  $_" }
        exit 1
    }
    Write-Host "OK: $($files.Count) files identical to whisper.cpp $Tag ($Commit)" -ForegroundColor Green
}
finally {
    Remove-Item $work -Recurse -Force -ErrorAction SilentlyContinue
}
