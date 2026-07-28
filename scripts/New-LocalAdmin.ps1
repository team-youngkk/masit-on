[CmdletBinding()]
param(
    # 비밀번호는 인자로 넘기지 않고 실행 중 프롬프트로 입력한다. 명령줄로 넘기면
    # PSReadLine 히스토리 파일(ConsoleHost_history.txt)에 평문으로 남는다.
    [System.Security.SecureString]$Password,
    [string]$LoginId = 'local-admin',
    [string]$ContainerService = 'postgres',
    [string]$Database = 'masiton',
    [string]$DatabaseUser = 'masiton'
)

# 중간 단계가 실패하면 빈 해시 같은 잘못된 값이 저장되지 않도록 즉시 중단한다.
$ErrorActionPreference = 'Stop'

# 로컬 검증용 ADMIN 계정을 만든다. 관리자 계정은 사전 발급 운영 대상이므로 Flyway 기준 데이터에
# 넣지 않는다(docs/05-specs/data/seed-data-plan.md). 이 스크립트로 로컬에서만 생성한다.
# 비밀번호 원문은 프롬프트에서 받아 자식 프로세스 전용 환경 변수로만 전달하고, 생성된 해시는
# stdin으로만 넘긴다. 명령줄 인자로 넘기면 다른 프로세스가 커맨드라인을 조회할 수 있고
# 프로세스 생성 감사 로그(4688)에 원문이 영구 기록된다.

function Assert-LocalDockerEndpoint {
    # 원격 Docker context나 DOCKER_HOST가 설정된 셸에서 실행하면 로컬이 아닌 데이터베이스에
    # ADMIN 계정이 생성된다. 로컬 소켓·명명 파이프가 아니면 중단한다.
    $endpoint = $env:DOCKER_HOST
    if ([string]::IsNullOrWhiteSpace($endpoint)) {
        $endpoint = docker context inspect --format '{{.Endpoints.docker.Host}}'
        if ($LASTEXITCODE -ne 0) {
            throw 'Docker context를 확인할 수 없습니다. Docker Desktop이 실행 중인지 확인하세요.'
        }
    }
    if ($endpoint -notmatch '^(npipe|unix)://') {
        throw "로컬 Docker 엔드포인트가 아닙니다($endpoint). 이 스크립트는 로컬 계정만 만듭니다."
    }
}

function Get-BCryptJar {
    $gradleUserHome = if ([string]::IsNullOrWhiteSpace($env:GRADLE_USER_HOME)) {
        Join-Path $HOME '.gradle'
    } else {
        $env:GRADLE_USER_HOME
    }
    $cache = Join-Path $gradleUserHome 'caches\modules-2\files-2.1\org.springframework.security\spring-security-crypto'
    if (-not (Test-Path -LiteralPath $cache)) {
        throw 'spring-security-crypto가 Gradle 캐시에 없습니다. 먼저 .\gradlew.bat build를 실행하세요.'
    }
    $jar = Get-ChildItem -LiteralPath $cache -Recurse -Filter 'spring-security-crypto-*.jar' |
            Where-Object { $_.Name -notmatch 'sources|javadoc' } |
            Select-Object -First 1
    if ($null -eq $jar) {
        throw 'spring-security-crypto jar를 찾지 못했습니다. 먼저 .\gradlew.bat build를 실행하세요.'
    }
    return $jar.FullName
}

function New-BCryptHash([System.Security.SecureString]$Secret, [string]$Jar) {
    $java = Get-Command java -ErrorAction SilentlyContinue
    if ($null -eq $java) {
        throw 'JDK 21의 java 명령을 찾을 수 없습니다. JDK를 설치하고 PATH를 확인하세요.'
    }

    # 애플리케이션과 같은 BCryptPasswordEncoder 기본 강도(10)로 해시해야 로그인이 성립한다.
    # 원문은 이 프로세스에서만 보이는 환경 변수로 전달한다. 명령줄 인자로 넘기면 커맨드라인이
    # 다른 프로세스에 노출되고, PowerShell 5.1의 파이프는 콘솔 코드 페이지에 따라 BOM을 덧붙여
    # 원문을 조용히 바꿔 버린다(해시는 만들어지지만 로그인이 실패한다).
    $source = Join-Path ([System.IO.Path]::GetTempPath()) ("masiton-local-admin-" + [guid]::NewGuid() + '.java')
    $javaSource = @'
import org.springframework.security.crypto.bcrypt.BCrypt;

class GenerateLocalAdminHash {
    public static void main(String[] args) {
        String password = System.getenv("MASITON_LOCAL_ADMIN_PASSWORD");
        if (password == null || password.isBlank()) {
            System.err.println("MASITON_LOCAL_ADMIN_PASSWORD is required");
            System.exit(2);
        }
        System.out.println(BCrypt.hashpw(password, BCrypt.gensalt(10)));
    }
}
'@
    [System.IO.File]::WriteAllText($source, $javaSource, [System.Text.UTF8Encoding]::new($false))

    $bstr = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($Secret)
    try {
        $env:MASITON_LOCAL_ADMIN_PASSWORD = [System.Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
        $hash = & $java.Source '-cp' $Jar $source
        if ($LASTEXITCODE -ne 0) {
            throw '비밀번호 해시 생성에 실패했습니다.'
        }
        # java 출력이 여러 줄이면 해시가 깨진 상태로 저장되므로 BCrypt 형식 한 줄만 허용한다.
        $matched = @($hash | Where-Object { $_ -match '^\$2[aby]\$' })
        if ($matched.Count -ne 1) {
            throw '비밀번호 해시 형식을 확인할 수 없습니다.'
        }
        return $matched[0].Trim()
    } finally {
        Remove-Item -LiteralPath Env:\MASITON_LOCAL_ADMIN_PASSWORD -ErrorAction SilentlyContinue
        [System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
        Remove-Item -LiteralPath $source -Force -ErrorAction SilentlyContinue
    }
}

Assert-LocalDockerEndpoint

if ($null -eq $Password) {
    $Password = Read-Host -Prompt "로컬 관리자 비밀번호" -AsSecureString
}
if ($Password.Length -eq 0) {
    throw '비밀번호가 비어 있습니다.'
}

$hash = New-BCryptHash -Secret $Password -Jar (Get-BCryptJar)

# login_id UNIQUE 제약에 맞춰 재실행해도 계정이 중복되지 않게 한다.
# 값은 psql 인용 보간(:'name')으로 넘겨 SQL 리터럴로 감싼다. 해시도 명령줄 대신 stdin으로만
# 전달해 프로세스 커맨드라인에 남기지 않는다.
$quotedLoginId = $LoginId.Replace("'", "''")
$quotedHash = $hash.Replace("'", "''")
$sql = @"
\set loginId '$quotedLoginId'
\set passwordHash '$quotedHash'
INSERT INTO admin_account (id, login_id, password_hash, role, active, created_at, updated_at)
VALUES (gen_random_uuid(), :'loginId', :'passwordHash', 'ADMIN', true, now(), now())
ON CONFLICT (login_id) DO NOTHING;
"@

$result = $sql | docker compose exec -T $ContainerService psql -U $DatabaseUser -d $Database -v ON_ERROR_STOP=1 -f -
if ($LASTEXITCODE -ne 0) {
    throw "관리자 계정 생성에 실패했습니다. docker compose up -d $ContainerService로 컨테이너가 떠 있는지 확인하세요."
}

if ($result -match 'INSERT 0 1') {
    Write-Host "관리자 계정 '$LoginId'을 생성했습니다."
} else {
    Write-Host "관리자 계정 '$LoginId'이 이미 있어 그대로 두었습니다."
}
