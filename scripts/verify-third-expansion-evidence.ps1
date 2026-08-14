[CmdletBinding()]
param(
    [string]$RepositoryRoot
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($RepositoryRoot)) {
    $RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
} else {
    $RepositoryRoot = (Resolve-Path -LiteralPath $RepositoryRoot).Path
}

$manifestRelativePath = 'docs/08-planning/third-expansion-evidence-manifest.txt'
$finalGateRelativePath = 'docs/08-planning/third-expansion-final-gate-result.md'
$manifestPath = Join-Path $RepositoryRoot $manifestRelativePath

if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) {
    throw "Evidence manifest not found: $manifestRelativePath"
}

$head = (& git -C $RepositoryRoot rev-parse --verify HEAD 2>$null).Trim()
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($head)) {
    throw 'Unable to resolve current HEAD'
}

$manifestLines = [IO.File]::ReadAllLines($manifestPath, [Text.UTF8Encoding]::new($false))
$entries = @(
    foreach ($line in $manifestLines) {
        if ($line -match '^(?<path>[^\t]+)\t(?<hash>[0-9a-fA-F]{64})$') {
            [pscustomobject]@{
                Path = $matches.path.Replace('\', '/')
                ExpectedHash = $matches.hash.ToLowerInvariant()
            }
        }
    }
)

if ($entries.Count -eq 0) {
    throw 'Manifest contains no SHA-256 file entries'
}

$excludedPaths = @($manifestRelativePath, $finalGateRelativePath)
$listedExcludedPaths = @($entries | Where-Object { $_.Path -in $excludedPaths })
if ($listedExcludedPaths.Count -gt 0) {
    $paths = $listedExcludedPaths.Path -join ', '
    throw "Manifest and final-gate result must not be aggregate entries: $paths"
}

$duplicatePaths = @($entries | Group-Object Path | Where-Object Count -gt 1)
if ($duplicatePaths.Count -gt 0) {
    throw "Manifest contains duplicate paths: $($duplicatePaths.Name -join ', ')"
}

$rootWithSeparator = $RepositoryRoot.TrimEnd('\', '/') + [IO.Path]::DirectorySeparatorChar
$aggregateLines = [Collections.Generic.List[string]]::new()
$actualHashes = @{}
$mismatches = [Collections.Generic.List[string]]::new()

foreach ($entry in $entries) {
    if ([IO.Path]::IsPathRooted($entry.Path) -or $entry.Path.Split('/') -contains '..') {
        throw "Manifest path is not repository-relative: $($entry.Path)"
    }

    $filePath = [IO.Path]::GetFullPath((Join-Path $RepositoryRoot $entry.Path))
    if (-not $filePath.StartsWith($rootWithSeparator, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Manifest path escapes repository root: $($entry.Path)"
    }
    if (-not (Test-Path -LiteralPath $filePath -PathType Leaf)) {
        throw "Manifest file does not exist: $($entry.Path)"
    }

    $actualHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $filePath).Hash.ToLowerInvariant()
    $actualHashes[$entry.Path] = $actualHash
    $aggregateLines.Add("$($entry.Path)`t$actualHash")
    if ($actualHash -ne $entry.ExpectedHash) {
        $mismatches.Add("$($entry.Path): expected $($entry.ExpectedHash), actual $actualHash")
    }
}

$aggregateText = [string]::Join("`n", $aggregateLines)
$sha256 = [Security.Cryptography.SHA256]::Create()
try {
    $aggregateHash = ([BitConverter]::ToString(
        $sha256.ComputeHash([Text.Encoding]::UTF8.GetBytes($aggregateText))
    ) -replace '-', '').ToLowerInvariant()
} finally {
    $sha256.Dispose()
}

$aggregateMatches = $false
$aggregateRecords = @(
    foreach ($line in $manifestLines) {
        if ($line -match '^aggregate_sha256:\s*(?<hash>[0-9a-fA-F]{64})\s*$') {
            [pscustomobject]@{ Hash = $matches.hash.ToLowerInvariant() }
        }
    }
)
if ($aggregateRecords.Count -eq 0) {
    throw 'Manifest does not contain aggregate_sha256'
}
if ($aggregateRecords.Count -gt 1) {
    throw 'Manifest contains multiple aggregate_sha256 values'
}
$expectedAggregate = $aggregateRecords[0].Hash
$aggregateMatches = $aggregateHash -eq $expectedAggregate

$finalGatePath = Join-Path $RepositoryRoot $finalGateRelativePath
if (-not (Test-Path -LiteralPath $finalGatePath -PathType Leaf)) {
    throw "final-gate result document does not exist: $finalGateRelativePath"
}
$finalGateLines = [IO.File]::ReadAllLines($finalGatePath, [Text.UTF8Encoding]::new($false))
$fingerprintRecords = @(
    foreach ($line in $finalGateLines) {
        if ($line -match '^evidence_fingerprint:\s*(?<hash>[0-9a-fA-F]{64})\s*$') {
            [pscustomobject]@{ Hash = $matches.hash.ToLowerInvariant() }
        }
    }
)
if ($fingerprintRecords.Count -ne 1) {
    throw 'final-gate result must contain exactly one evidence_fingerprint value'
}
$expectedFingerprint = $fingerprintRecords[0].Hash
$fingerprintMatches = $aggregateHash -eq $expectedFingerprint

Write-Output "Evidence manifest HEAD: $head"
Write-Output "File SHA-256 entries: $($entries.Count)"
foreach ($entry in $entries) {
    $actualHash = $actualHashes[$entry.Path]
    $status = if ($actualHash -eq $entry.ExpectedHash) { 'PASS' } else { 'FAIL' }
    Write-Output "[$status] $($entry.Path)"
}
Write-Output "aggregate_sha256 expected=$expectedAggregate actual=$aggregateHash"
Write-Output "final-gate evidence_fingerprint expected=$expectedFingerprint actual=$aggregateHash"

foreach ($mismatch in $mismatches) {
    Write-Error $mismatch
}
if (($mismatches.Count -gt 0) -or (-not $aggregateMatches) -or (-not $fingerprintMatches)) {
    throw 'aggregate_sha256 does not match the listed files at current HEAD'
}

Write-Output 'Evidence manifest verification passed (manifest and final-gate result are excluded)'
