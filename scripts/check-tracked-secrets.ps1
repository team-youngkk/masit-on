[CmdletBinding()]
param(
    [string]$RepositoryRoot = (Get-Location).Path
)

$ErrorActionPreference = 'Stop'

function Read-GitOutputBytes([string]$Root) {
    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = 'git'
    $startInfo.WorkingDirectory = $Root
    $startInfo.Arguments = '-C . ls-files -z --cached'
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true

    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    [void]$process.Start()
    $output = New-Object System.IO.MemoryStream
    $process.StandardOutput.BaseStream.CopyTo($output)
    $errorOutput = $process.StandardError.ReadToEnd()
    $process.WaitForExit()
    if ($process.ExitCode -ne 0) {
        throw 'git ls-files 실행에 실패했습니다.'
    }
    return $output.ToArray()
}

function Get-TrackedPaths([string]$Root) {
    $bytes = Read-GitOutputBytes $Root
    if ($bytes.Length -eq 0) {
        return @()
    }
    $text = [System.Text.Encoding]::UTF8.GetString($bytes)
    return @($text -split [char]0 | Where-Object { $_ -ne '' })
}

function Read-DerElement([byte[]]$Bytes, [ref]$Offset) {
    if ($Offset.Value -ge $Bytes.Length) {
        return $null
    }

    $tag = $Bytes[$Offset.Value]
    $Offset.Value++
    if ($Offset.Value -ge $Bytes.Length) {
        return $null
    }

    $lengthByte = $Bytes[$Offset.Value]
    $Offset.Value++
    if (($lengthByte -band 0x80) -eq 0) {
        $length = [int]$lengthByte
    } else {
        $lengthBytes = $lengthByte -band 0x7f
        if ($lengthBytes -eq 0 -or $lengthBytes -gt 4 -or
                $Offset.Value + $lengthBytes -gt $Bytes.Length) {
            return $null
        }
        $length = 0
        for ($index = 0; $index -lt $lengthBytes; $index++) {
            $length = ($length -shl 8) -bor $Bytes[$Offset.Value]
            $Offset.Value++
        }
    }

    $contentOffset = $Offset.Value
    $end = $contentOffset + $length
    if ($end -gt $Bytes.Length) {
        return $null
    }
    $Offset.Value = $end
    return [pscustomobject]@{
        Tag = $tag
        ContentOffset = $contentOffset
        End = $end
        Length = $length
    }
}

function Get-DerChildren([byte[]]$Bytes, $Element) {
    if ($null -eq $Element -or $Element.Tag -ne 0x30) {
        return $null
    }
    $offset = $Element.ContentOffset
    $children = [System.Collections.Generic.List[object]]::new()
    while ($offset -lt $Element.End) {
        $child = Read-DerElement $Bytes ([ref]$offset)
        if ($null -eq $child) {
            return $null
        }
        [void]$children.Add($child)
    }
    if ($offset -ne $Element.End) {
        return $null
    }
    return $children.ToArray()
}

function Test-DerInteger($Element) {
    return $null -ne $Element -and $Element.Tag -eq 0x02 -and $Element.Length -gt 0
}

function Test-RsaPkcs1PrivateKey([byte[]]$Bytes) {
    try {
        $offset = 0
        $outer = Read-DerElement $Bytes ([ref]$offset)
        if ($null -eq $outer -or $offset -ne $Bytes.Length) {
            return $false
        }
        $children = @(Get-DerChildren $Bytes $outer)
        if ($children.Count -lt 9 -or -not (Test-DerInteger $children[0])) {
            return $false
        }
        $version = $Bytes[$children[0].ContentOffset]
        if ($children[0].Length -ne 1 -or ($version -ne 0 -and $version -ne 1)) {
            return $false
        }
        if ($children[1].Tag -ne 0x02 -or $children[1].Length -lt 64) {
            return $false
        }
        for ($index = 2; $index -lt 9; $index++) {
            if (-not (Test-DerInteger $children[$index])) {
                return $false
            }
        }
        return $true
    } catch {
        return $false
    }
}

function Test-BytesEqual([byte[]]$Left, [byte[]]$Right) {
    if ($Left.Length -ne $Right.Length) {
        return $false
    }
    for ($index = 0; $index -lt $Left.Length; $index++) {
        if ($Left[$index] -ne $Right[$index]) {
            return $false
        }
    }
    return $true
}

function Test-RsaPkcs8PrivateKey([byte[]]$Bytes) {
    try {
        $offset = 0
        $outer = Read-DerElement $Bytes ([ref]$offset)
        if ($null -eq $outer -or $offset -ne $Bytes.Length) {
            return $false
        }
        $children = @(Get-DerChildren $Bytes $outer)
        if ($children.Count -lt 3 -or -not (Test-DerInteger $children[0]) -or
                $children[1].Tag -ne 0x30) {
            return $false
        }
        $algorithm = @(Get-DerChildren $Bytes $children[1])
        $rsaOid = [byte[]](0x2a, 0x86, 0x48, 0x86, 0xf7, 0x0d, 0x01, 0x01, 0x01)
        if ($algorithm.Count -lt 1 -or $algorithm[0].Tag -ne 0x06) {
            return $false
        }
        $oid = $Bytes[$algorithm[0].ContentOffset..($algorithm[0].End - 1)]
        if (-not (Test-BytesEqual $oid $rsaOid)) {
            return $false
        }
        $privateKey = $children | Where-Object { $_.Tag -eq 0x04 } | Select-Object -First 1
        if ($null -eq $privateKey) {
            return $false
        }
        $inner = $Bytes[$privateKey.ContentOffset..($privateKey.End - 1)]
        return Test-RsaPkcs1PrivateKey $inner
    } catch {
        return $false
    }
}

function Test-RsaPrivateKeyBytes([byte[]]$Bytes) {
    return (Test-RsaPkcs8PrivateKey $Bytes) -or (Test-RsaPkcs1PrivateKey $Bytes)
}

function Convert-Base64Candidate([string]$Candidate) {
    $normalized = $Candidate -replace '\s', ''
    if ($normalized.Length -lt 80 -or ($normalized.Length % 4) -eq 1) {
        return $null
    }
    try {
        return [Convert]::FromBase64String($normalized)
    } catch {
        return $null
    }
}

function Find-RsaPrivateKeyMaterial([string]$Text) {
    $begin = '-----' + 'BEGIN '
    $end = '-----END '
    foreach ($label in @('RSA PRIVATE KEY', 'PRIVATE KEY', 'ENCRYPTED PRIVATE KEY',
            'OPENSSH PRIVATE KEY', 'EC PRIVATE KEY', 'DSA PRIVATE KEY')) {
        $marker = $begin + $label + '-----'
        if ([regex]::IsMatch($Text, [regex]::Escape($marker))) {
            return $true
        }
    }

    foreach ($label in @('RSA PRIVATE KEY', 'PRIVATE KEY')) {
        $pattern = [regex]::Escape($begin + $label + '-----') +
            '(?<body>.*?)' + [regex]::Escape($end + $label + '-----')
        foreach ($match in [regex]::Matches($Text, $pattern, [System.Text.RegularExpressions.RegexOptions]::Singleline)) {
            $body = $match.Groups['body'].Value -replace '\s', '' -replace '\\[rn]', ''
            $bytes = Convert-Base64Candidate $body
            if ($null -ne $bytes -and (Test-RsaPrivateKeyBytes $bytes)) {
                return $true
            }
        }
    }

    foreach ($match in [regex]::Matches($Text, '[A-Za-z0-9+/]{80,}={0,2}')) {
        $bytes = Convert-Base64Candidate $match.Value
        if ($null -ne $bytes -and (Test-RsaPrivateKeyBytes $bytes)) {
            return $true
        }
    }
    return $false
}

try {
    $root = (Resolve-Path -LiteralPath $RepositoryRoot).Path
    $trackedPaths = @(Get-TrackedPaths $root)
    $hits = [System.Collections.Generic.List[string]]::new()

    foreach ($relativePath in $trackedPaths) {
        $file = Join-Path $root $relativePath
        if (-not (Test-Path -LiteralPath $file -PathType Leaf)) {
            throw '추적 파일을 읽을 수 없습니다.'
        }
        $bytes = [System.IO.File]::ReadAllBytes($file)
        $text = [System.Text.Encoding]::UTF8.GetString($bytes)
        if ((Test-RsaPrivateKeyBytes $bytes) -or (Find-RsaPrivateKeyMaterial $text)) {
            [void]$hits.Add($relativePath)
        }
    }

    if ($hits.Count -gt 0) {
        Write-Error ("추적 파일에서 RSA 개인키 material이 발견되었습니다 (파일 {0}개): {1}" -f
            $hits.Count, ($hits -join ', '))
        exit 1
    }

    Write-Output ("추적 파일 비밀 검사 통과 (검사 파일 {0}개)" -f $trackedPaths.Count)
    exit 0
} catch {
    Write-Error '추적 파일 비밀 검사 실행에 실패했습니다.'
    exit 2
}
