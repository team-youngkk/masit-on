[CmdletBinding()]
param(
    [string]$EnvFile = (Join-Path (Get-Location) '.env')
)

if (-not (Test-Path -LiteralPath $EnvFile)) {
    throw ".env 파일이 없습니다. 먼저 Copy-Item .env.example .env를 실행하세요."
}

function Get-EnvValue([string]$Content, [string]$Name) {
    $match = [regex]::Match($Content, "(?m)^$([regex]::Escape($Name))=(.*)$")
    if ($match.Success) {
        return $match.Groups[1].Value.Trim()
    }
    return $null
}

function Set-EnvValue([string]$Content, [string]$Name, [string]$Value) {
    return [regex]::Replace($Content, "(?m)^$([regex]::Escape($Name))=.*$", "$Name=$Value")
}

function New-LocalJwtKeyPair {
    $java = Get-Command java -ErrorAction SilentlyContinue
    if ($null -eq $java) {
        throw 'JDK 21의 java 명령을 찾을 수 없습니다. JDK를 설치하고 PATH를 확인하세요.'
    }

    $source = Join-Path ([System.IO.Path]::GetTempPath()) ("masiton-local-jwt-" + [guid]::NewGuid() + '.java')
    $javaSource = @'
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

class GenerateLocalJwt {
    static String pem(String label, byte[] bytes) {
        String encoded = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(bytes);
        return "-----BEGIN " + label + "-----\\n" + encoded.replace("\n", "\\n") + "\\n-----END " + label + "-----";
    }

    public static void main(String[] args) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        System.out.println(pem("PRIVATE KEY", keyPair.getPrivate().getEncoded()));
        System.out.println(pem("PUBLIC KEY", keyPair.getPublic().getEncoded()));
    }
}
'@
    [System.IO.File]::WriteAllText($source, $javaSource, [System.Text.UTF8Encoding]::new($false))
    try {
        $keys = & $java.Source $source
        if ($LASTEXITCODE -ne 0 -or $keys.Count -ne 2) {
            throw '로컬 JWT 키 생성에 실패했습니다. JDK 21의 java 명령을 확인하세요.'
        }
        return $keys
    } finally {
        Remove-Item -LiteralPath $source -Force -ErrorAction SilentlyContinue
    }
}

function New-LocalMemberActionMailKey {
    $key = [byte[]]::new(32)
    $generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $generator.GetBytes($key)
        return [Convert]::ToBase64String($key)
    } finally {
        $generator.Dispose()
    }
}

$content = [System.IO.File]::ReadAllText((Resolve-Path -LiteralPath $EnvFile))
$privateKey = Get-EnvValue $content 'JWT_PRIVATE_KEY_PEM'
$publicKey = Get-EnvValue $content 'JWT_PUBLIC_KEY_PEM'

if ([string]::IsNullOrWhiteSpace($privateKey) -or [string]::IsNullOrWhiteSpace($publicKey)) {
    $privateKey, $publicKey = New-LocalJwtKeyPair
    $content = Set-EnvValue $content 'JWT_PRIVATE_KEY_PEM' $privateKey
    $content = Set-EnvValue $content 'JWT_PUBLIC_KEY_PEM' $publicKey
    [System.IO.File]::WriteAllText(
            (Resolve-Path -LiteralPath $EnvFile),
            $content,
            [System.Text.UTF8Encoding]::new($false)
    )
}

$keyId = Get-EnvValue $content 'JWT_KEY_ID'
if ([string]::IsNullOrWhiteSpace($keyId)) {
    $keyId = 'local-1'
    $content = Set-EnvValue $content 'JWT_KEY_ID' $keyId
    [System.IO.File]::WriteAllText(
            (Resolve-Path -LiteralPath $EnvFile),
            $content,
            [System.Text.UTF8Encoding]::new($false)
    )
}

$memberActionMailKeyId = Get-EnvValue $content 'MEMBER_ACTION_MAIL_ACTIVE_KEY_ID'
if ([string]::IsNullOrWhiteSpace($memberActionMailKeyId)) {
    $memberActionMailKeyId = 'local-1'
    $content = Set-EnvValue $content 'MEMBER_ACTION_MAIL_ACTIVE_KEY_ID' $memberActionMailKeyId
}

$memberActionMailKey = Get-EnvValue $content 'MEMBER_ACTION_MAIL_ACTIVE_KEY'
if ([string]::IsNullOrWhiteSpace($memberActionMailKey)) {
    $memberActionMailKey = New-LocalMemberActionMailKey
    $content = Set-EnvValue $content 'MEMBER_ACTION_MAIL_ACTIVE_KEY' $memberActionMailKey
}

[System.IO.File]::WriteAllText(
        (Resolve-Path -LiteralPath $EnvFile),
        $content,
        [System.Text.UTF8Encoding]::new($false)
)

$env:JWT_KEY_ID = $keyId
$env:JWT_PRIVATE_KEY_PEM = $privateKey
$env:JWT_PUBLIC_KEY_PEM = $publicKey
$env:MEMBER_ACTION_MAIL_ACTIVE_KEY_ID = $memberActionMailKeyId
$env:MEMBER_ACTION_MAIL_ACTIVE_KEY = $memberActionMailKey

Write-Host '로컬 JWT와 회원 Action 메일 AES 키를 준비했고 현재 PowerShell 세션에 설정했습니다.'
