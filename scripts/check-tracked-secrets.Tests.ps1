[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$scriptRoot = Split-Path -Parent $PSCommandPath
$scanner = Join-Path $scriptRoot 'check-tracked-secrets.ps1'

function Invoke-Scan([string]$Root) {
    $shell = Get-Command pwsh -ErrorAction SilentlyContinue
    if ($null -eq $shell) {
        $shell = Get-Command powershell -ErrorAction Stop
    }
    $output = (& $shell.Source -NoLogo -NoProfile -File $scanner -RepositoryRoot $Root 2>&1 | Out-String)
    return [pscustomobject]@{
        ExitCode = [int]$LASTEXITCODE
        Output = $output
    }
}

function Assert-Scan([string]$Root, [int]$ExpectedExitCode, [string]$Message) {
    $result = Invoke-Scan $Root
    if ($result.ExitCode -ne $ExpectedExitCode) {
        throw $Message
    }
    return $result.Output
}

$repository = Join-Path ([System.IO.Path]::GetTempPath()) ('masiton-secret-scan-' + [guid]::NewGuid())
$rsa = [System.Security.Cryptography.RSA]::Create(2048)
try {
    New-Item -ItemType Directory -Path $repository | Out-Null
    & git -C $repository init -q --initial-branch=main *> $null
    & git -C $repository config user.email 'secret-scan-test@example.invalid' *> $null
    & git -C $repository config user.name 'secret-scan-test' *> $null

    $privateBytes = $rsa.ExportPkcs8PrivateKey()
    $pkcs1PrivateBytes = $rsa.ExportRSAPrivateKey()
    $publicBytes = $rsa.ExportSubjectPublicKeyInfo()
    $privateBase64 = [Convert]::ToBase64String($privateBytes)
    $publicBase64 = [Convert]::ToBase64String($publicBytes)
    $privatePem = '-----' + 'BEGIN PRIVATE KEY-----' + "`n" +
        (($privateBase64 -split '(.{64})' | Where-Object { $_ }) -join "`n") + "`n" +
        '-----END PRIVATE KEY-----'
    $fakePem = '-----' + 'BEGIN TEST KEY MATERIAL-----' + "`nfirst-line`nsecond-line`n" +
        '-----END TEST KEY MATERIAL-----'
    $openSshPrivatePem = '-----' + 'BEGIN OPENSSH PRIVATE KEY-----' + "`nnot-a-real-key`n" +
        '-----END OPENSSH PRIVATE KEY-----'
    $encryptedPrivatePem = '-----' + 'BEGIN ENCRYPTED PRIVATE KEY-----' + "`nnot-a-real-key`n" +
        '-----END ENCRYPTED PRIVATE KEY-----'

    [System.IO.File]::WriteAllText((Join-Path $repository 'public.key'), $publicBase64)
    [System.IO.File]::WriteAllText((Join-Path $repository 'fake.pem'), $fakePem)
    & git -C $repository add . *> $null
    [void](Assert-Scan $repository 0 '공개키와 비밀키가 아닌 키 material은 거부하지 않아야 합니다.')

    [System.IO.File]::WriteAllText((Join-Path $repository 'raw-private.key'), $privateBase64)
    & git -C $repository add raw-private.key *> $null
    $rawOutput = Assert-Scan $repository 1 'raw PKCS#8 RSA 개인키를 거부해야 합니다.'
    if ($rawOutput.Contains($privateBase64)) {
        throw '검사 결과에 private key material이 출력되었습니다.'
    }

    Remove-Item -LiteralPath (Join-Path $repository 'raw-private.key')
    & git -C $repository add -u *> $null
    [System.IO.File]::WriteAllText((Join-Path $repository 'raw-pkcs1-private.key'),
        [Convert]::ToBase64String($pkcs1PrivateBytes))
    & git -C $repository add raw-pkcs1-private.key *> $null
    [void](Assert-Scan $repository 1 'raw PKCS#1 RSA 개인키를 거부해야 합니다.')

    Remove-Item -LiteralPath (Join-Path $repository 'raw-pkcs1-private.key')
    & git -C $repository add -u *> $null
    [System.IO.File]::WriteAllBytes((Join-Path $repository 'private.der'), $privateBytes)
    & git -C $repository add private.der *> $null
    [void](Assert-Scan $repository 1 'binary PKCS#8 RSA 개인키를 거부해야 합니다.')

    Remove-Item -LiteralPath (Join-Path $repository 'private.der')
    & git -C $repository add -u *> $null
    [System.IO.File]::WriteAllText((Join-Path $repository 'private.pem'), $privatePem)
    & git -C $repository add private.pem *> $null
    $pemOutput = Assert-Scan $repository 1 'RSA PKCS#8 PEM을 거부해야 합니다.'
    if ($pemOutput.Contains($privateBase64)) {
        throw '검사 결과에 PEM private key material이 출력되었습니다.'
    }

    Remove-Item -LiteralPath (Join-Path $repository 'private.pem')
    & git -C $repository add -u *> $null
    [System.IO.File]::WriteAllText((Join-Path $repository 'openssh-private.pem'), $openSshPrivatePem)
    & git -C $repository add openssh-private.pem *> $null
    [void](Assert-Scan $repository 1 'OpenSSH private key armor를 거부해야 합니다.')

    Remove-Item -LiteralPath (Join-Path $repository 'openssh-private.pem')
    & git -C $repository add -u *> $null
    [System.IO.File]::WriteAllText((Join-Path $repository 'encrypted-private.pem'), $encryptedPrivatePem)
    & git -C $repository add encrypted-private.pem *> $null
    [void](Assert-Scan $repository 1 '암호화된 private key armor를 거부해야 합니다.')

    Remove-Item -LiteralPath (Join-Path $repository 'encrypted-private.pem')
    & git -C $repository add -u *> $null
    [System.IO.File]::WriteAllText((Join-Path $repository 'untracked-private.key'), $privateBase64)
    [void](Assert-Scan $repository 0 'untracked fixture는 검사 대상이 아니어야 합니다.')

    Write-Output 'tracked secret scan 회귀 테스트 통과'
    exit 0
} finally {
    $rsa.Dispose()
    if (Test-Path -LiteralPath $repository) {
        Remove-Item -LiteralPath $repository -Recurse -Force -ErrorAction SilentlyContinue
    }
}
