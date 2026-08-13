// NFR-PERFORMANCE-007 자연어 검색·코스 경로 부하 검증 시나리오.
// LOAD_PROFILE=normal은 50명·20 RPS, LOAD_PROFILE=max는 200명·80 RPS다.
//
// 호출하는 경로는 Accepted API 계약에 정의된 공개 POST 두 개뿐이다.
//   - POST /api/restaurants/natural-language-search
//   - POST /api/restaurants/course-routes
//
// Kakao Mobility는 측정 대상 환경의 WireMock Stub이어야 한다. 이 스크립트는
// 실제 Kakao·YouTube API나 계약에 없는 내부 메트릭·health endpoint를 호출하지 않는다.
// 판정에 쓰는 실행 위치·데이터·외부 연동 원칙은 ADR-PERF-001과 RV-NFR-011을 따른다.
import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const RESULT_DIR = __ENV.RESULT_DIR || 'perf/k6/results';
const LOAD_PROFILE = __ENV.LOAD_PROFILE || 'normal';
const LOAD = {
    normal: { label: '정상 부하', rate: 20, vus: 50 },
    max: { label: '최대 부하', rate: 80, vus: 200 },
}[LOAD_PROFILE];

if (!LOAD) {
    throw new Error(`지원하지 않는 LOAD_PROFILE=${LOAD_PROFILE}. normal 또는 max를 사용한다.`);
}

// 내부 관측과 외부 호출 포함 측정은 같은 실행에서 섞지 않는다.
// internal은 외부 지연이 없는 WireMock Stub 실행, external은 외부 지연이
// 설정된 WireMock Stub 실행에서 각각 별도로 수행한다.
const COURSE_METRIC_MODE = __ENV.COURSE_METRIC_MODE || 'internal';
if (!['internal', 'external'].includes(COURSE_METRIC_MODE)) {
    throw new Error(`지원하지 않는 COURSE_METRIC_MODE=${COURSE_METRIC_MODE}. internal 또는 external을 사용한다.`);
}
const COURSE_DURATION_METRIC = COURSE_METRIC_MODE === 'internal'
    ? 'duration_course_route_internal_observed'
    : 'duration_course_route_external_included';

const naturalLanguageDuration = new Trend('duration_natural_language_search', true);
// 이 Trend는 WireMock에 의도적인 외부 지연이 없는 측정 환경에서 애플리케이션
// 경로의 관측값으로 사용한다. k6가 네트워크 왕복을 포함한다는 한계는 사후 보정하지
// 않고, 같은 VPC의 측정 전용 환경에서만 내부 p95 판정에 사용한다.
const courseInternalDuration = new Trend('duration_course_route_internal_observed', true);
const courseTotalDuration = new Trend('duration_course_route_external_included', true);
const courseTimeoutViolations = new Counter('course_timeout_violations');
const serverErrorRate = new Rate('server_error_rate');
const unexpectedStatus = new Counter('unexpected_status_count');
const measuredSamples = new Counter('measured_samples');

export const options = {
    scenarios: {
        warm_up: {
            executor: 'constant-arrival-rate',
            rate: LOAD.rate,
            timeUnit: '1s',
            duration: '60s',
            preAllocatedVUs: LOAD.vus,
            maxVUs: LOAD.vus,
            exec: 'warmUp',
            tags: { phase: 'warmup' },
        },
        measured: {
            executor: 'constant-arrival-rate',
            rate: LOAD.rate,
            timeUnit: '1s',
            duration: '5m',
            preAllocatedVUs: LOAD.vus,
            maxVUs: LOAD.vus,
            startTime: '60s',
            exec: 'measured',
            tags: { phase: 'measured' },
        },
    },
    thresholds: {
        // NFR-PERFORMANCE-007의 p95 합격 기준은 정상 부하에 적용한다.
        // 최대 부하는 RV-NFR-001에 따라 용량·오류 확산을 확인하고, p95 원시
        // 수치는 summary에 보존하되 정상 부하 기준으로 성공/실패를 판정하지 않는다.
        ...(LOAD_PROFILE === 'normal'
            ? {
                  duration_natural_language_search: ['p(95)<800'],
                  [COURSE_DURATION_METRIC]: [COURSE_METRIC_MODE === 'internal' ? 'p(95)<500' : 'p(95)<5000'],
              }
            : {}),
        // This is a request-level boundary: one course request over five seconds
        // is a failure even when aggregate p95 remains below the boundary.
        'course_timeout_violations': ['count==0'],
        'server_error_rate': ['rate<0.01'],
        'http_req_failed{phase:measured}': ['rate<0.01'],
        'dropped_iterations': ['count==0'],
    },
};

export function setup() {
    // 코스 ID를 고정하거나 UUID 생성 규칙을 전제하지 않는다. 기본값은 계약된
    // 공개 목록 응답에서 얻고, 좌표가 준비된 측정 환경에서는 쉼표로 명시적으로
    // 주입할 수 있다(COURSE_RESTAURANT_IDS=opaque-id-1,opaque-id-2).
    const listResponse = http.get(`${BASE_URL}/api/restaurants?page=1&size=50`);
    if (listResponse.status !== 200) {
        throw new Error(`공개 맛집 목록 조회 실패 (status ${listResponse.status}). 측정 대상 환경을 확인한다.`);
    }

    const items = listResponse.json('items') || [];
    const listedIds = items.map((item) => item.id).filter(Boolean);
    const configuredIds = (__ENV.COURSE_RESTAURANT_IDS || '')
        .split(',')
        .map((id) => id.trim())
        .filter(Boolean);
    const courseRestaurantIds = configuredIds.length > 0 ? configuredIds : listedIds.slice(0, 5);
    if (courseRestaurantIds.length < 2 || courseRestaurantIds.length > 5) {
        throw new Error(
            '코스 측정에는 좌표가 준비된 공개 맛집 식별자 2~5개가 필요하다. '
            + 'COURSE_RESTAURANT_IDS 또는 측정 대상 데이터를 확인한다.'
        );
    }

    // setup 사전 검증은 측정 지표에 포함하지 않는다. 좌표 누락·외부 Stub 미설정
    // 환경에서 80 RPS를 먼저 보내지 않고, 실행 조건을 명확히 실패시킨다.
    const preflight = postCourse(courseRestaurantIds);
    if (preflight.status !== 200) {
        throw new Error(
            `코스 경로 사전 검증 실패 (status ${preflight.status}). `
            + '좌표·공개 상태·WireMock Stub을 확인한다.'
        );
    }

    return { courseRestaurantIds };
}

export function warmUp(data) {
    runOneRequest(data, false);
}

export function measured(data) {
    runOneRequest(data, true);
}

// 한 iteration에서 두 경로를 모두 호출하면 요청률이 두 배가 된다. 계약의 총
// 요청률을 유지하기 위해 iteration마다 하나의 경로만 균등하게 호출한다.
function runOneRequest(data, record) {
    if (exec.scenario.iterationInTest % 2 === 0) {
        callNaturalLanguageSearch(record);
    } else {
        callCourseRoute(data.courseRestaurantIds, record);
    }
}

function callNaturalLanguageSearch(record) {
    const body = JSON.stringify({
        sentence: '성수에서 한식집 찾아줘',
        filters: {
            query: null,
            district: null,
            category: null,
            creatorId: null,
            tags: [],
        },
        page: 1,
        size: 20,
    });
    const response = http.post(`${BASE_URL}/api/restaurants/natural-language-search`, body, {
        headers: { 'Content-Type': 'application/json' },
        tags: { endpoint: 'natural_language_search' },
    });
    evaluate(response, 'natural_language_search', record, naturalLanguageDuration);
}

function callCourseRoute(ids, record) {
    const response = postCourse(ids);
    const courseDuration = COURSE_METRIC_MODE === 'internal'
        ? courseInternalDuration
        : courseTotalDuration;
    evaluate(response, 'course_route', record, courseDuration);
}

function postCourse(ids) {
    return http.post(
        `${BASE_URL}/api/restaurants/course-routes`,
        JSON.stringify({ restaurantIds: ids }),
        {
            headers: { 'Content-Type': 'application/json' },
            tags: { endpoint: 'course_route' },
            timeout: '5s',
        },
    );
}

function evaluate(response, endpoint, record, ...trends) {
    const ok = check(response, {
        [`${endpoint} 200 응답`]: (r) => r.status === 200,
    });
    if (!ok) {
        unexpectedStatus.add(1, { endpoint, phase: record ? 'measured' : 'warmup' });
    }
    if (!record) {
        return;
    }

    if (endpoint === 'course_route'
        && (response.status === 0 || response.timings.duration > 5000)) {
        courseTimeoutViolations.add(1, { phase: 'measured' });
    }

    // 선택한 측정 프로필의 Trend 하나에만 기록한다. 응답 시간에 임의 보정은
    // 하지 않으며, 두 프로필의 결과를 합쳐 내부 p95를 계산하지 않는다.
    for (const trend of trends) {
        trend.add(response.timings.duration);
    }
    serverErrorRate.add(response.status >= 500 || response.status === 0, { endpoint });
    measuredSamples.add(1, { endpoint });
}

export function handleSummary(data) {
    const text = renderText(data);
    return {
        stdout: text,
        [`${RESULT_DIR}/summary.json`]: JSON.stringify(data, null, 2),
        [`${RESULT_DIR}/summary.txt`]: text,
    };
}

function renderText(data) {
    const lines = [];
    lines.push(`NFR-PERFORMANCE-007 ${LOAD.label}(요청률 ${LOAD.rate} RPS / 동시성 상한 ${LOAD.vus}) 결과`);
    lines.push(`코스 측정 프로필: ${COURSE_METRIC_MODE} (${COURSE_DURATION_METRIC})`);
    if (get(data, 'measured_samples', 'count') === 0) {
        lines.push('');
        lines.push('*** 측정 구간 표본이 0건이다. 측정이 성립하지 않았으므로 아래 [통과] 표기를');
        lines.push('*** 판정 근거로 쓰지 않는다. 실행 로그에서 중단 원인을 확인한다.');
    }

    lines.push('');
    lines.push('[엔드포인트별 응답 시간 — 측정 구간만]');
    for (const [label, name] of [
        ['자연어 검색', 'duration_natural_language_search'],
        ['코스 경로 내부 관측값(WireMock 무지연 전제)', 'duration_course_route_internal_observed'],
        ['코스 경로 외부 호출 포함', 'duration_course_route_external_included'],
    ]) {
        const metric = data.metrics[name];
        if (!metric) {
            lines.push(`  ${label}: 데이터 없음`);
            continue;
        }
        const v = metric.values;
        lines.push(
            `  ${label}: avg=${fmt(v.avg)}ms med=${fmt(v.med)}ms p95=${fmt(v['p(95)'])}ms max=${fmt(v.max)}ms ${verdict(metric)}`
        );
    }
    lines.push(`  측정 구간 표본: 합계 ${get(data, 'measured_samples', 'count')}건`);
    lines.push('');
    lines.push('[오류·부하 조건]');
    lines.push(`  서버 오류율(5xx): ${pct(get(data, 'server_error_rate', 'rate'))} ${verdict(data.metrics.server_error_rate)}`);
    lines.push(`  http_req_failed (측정 구간): ${pct(get(data, 'http_req_failed{phase:measured}', 'rate'))} ${verdict(data.metrics['http_req_failed{phase:measured}'])}`);
    lines.push(`  http_req_failed (전체): ${pct(get(data, 'http_req_failed', 'rate'))}`);
    lines.push(`  200 아닌 응답: ${get(data, 'unexpected_status_count', 'count')}건`);
    lines.push(`  코스 5초 초과: ${get(data, 'course_timeout_violations', 'count')}건 ${verdict(data.metrics.course_timeout_violations)}`);
    lines.push(`  dropped_iterations: ${get(data, 'dropped_iterations', 'count')}건 ${verdict(data.metrics.dropped_iterations)}`);
    lines.push('');
    lines.push('threshold를 하나라도 위반하면 k6는 종료 코드 99로 끝난다.');
    lines.push('');
    return lines.join('\n');
}

function get(data, metric, key) {
    const found = data.metrics[metric];
    if (!found || found.values[key] === undefined) {
        return 0;
    }
    return found.values[key];
}

function fmt(value) {
    return value === undefined ? '-' : value.toFixed(1);
}

function pct(value) {
    return `${(value * 100).toFixed(3)}%`;
}

function verdict(metric) {
    if (!metric || !metric.thresholds) {
        return '';
    }
    const failed = Object.keys(metric.thresholds).filter((key) => !metric.thresholds[key].ok);
    return failed.length === 0 ? '[통과]' : `[위반: ${failed.join(', ')}]`;
}
