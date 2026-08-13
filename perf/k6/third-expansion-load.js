// NFR-PERFORMANCE-007 자연어 검색·코스 경로 부하 검증 시나리오.
// 기본값은 SCENARIO=course이며, 공개 읽기 arm은 SCENARIO=public-read로 명시 선택한다.
// 요청률·VU 상한은 공용 LOAD.rate·LOAD.vus 프로필에서 선택한다.
//
// public-read는 POST /api/restaurants/natural-language-search만 호출한다.
// course는 POST /api/restaurants/course-routes만 호출하며, 측정 전용 예산으로
// Mobility production quota·rate-limit 설정을 완화하지 않고 실행량만 제한한다.
// Kakao Mobility는 측정 대상 환경의 WireMock Stub이어야 한다. 실제 Kakao·YouTube
// API나 계약에 없는 내부 메트릭·health endpoint를 호출하지 않는다.
import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import {
    LOAD,
    LOAD_PROFILE,
    integerEnv,
    loadScenarios,
    metricValue,
    formatMetric,
    formatPercent,
    thresholdVerdict,
    writeSummary,
} from './load-profile.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const RESULT_DIR = __ENV.RESULT_DIR || 'perf/k6/results';
const SCENARIO = __ENV.SCENARIO || 'course';
const COURSE_METRIC_MODE = __ENV.COURSE_METRIC_MODE || 'internal';

if (!['public-read', 'course'].includes(SCENARIO)) {
    throw new Error(`지원하지 않는 SCENARIO=${SCENARIO}. public-read 또는 course를 사용한다.`);
}
if (!['internal', 'external'].includes(COURSE_METRIC_MODE)) {
    throw new Error(`지원하지 않는 COURSE_METRIC_MODE=${COURSE_METRIC_MODE}. internal 또는 external을 사용한다.`);
}

const COURSE_DURATION_METRIC = COURSE_METRIC_MODE === 'internal'
    ? 'duration_course_route_internal_observed'
    : 'duration_course_route_external_included';

// 이 값은 KakaoMobilityProperties의 production monthlyQuota 상한 1,000과
// 서비스 requestsPerSecond 기본값 20을 복제한다. production 설정을 변경하지 않고,
// 측정 전용 실행량만 이 경계 아래로 제한한다.
const MOBILITY_MONTHLY_QUOTA = 1000;
const COURSE_REQUESTS_PER_SECOND = integerEnv(
    'COURSE_REQUESTS_PER_SECOND',
    LOAD_PROFILE === 'normal' ? 5 : 10,
    1,
    20,
);
// external 코스 응답의 허용 p95가 5초이므로, 요청률과 같은 VU 수만 두면
// 1초를 넘는 정상 응답에서 arrival-rate executor가 dropped iteration을 만든다.
// 5초 동안의 동시 요청 + 여유 1 VU를 기본 상한으로 둔다. 이 값은 production
// 설정이 아니라 측정 전용 k6 실행 상한이다.
const COURSE_VUS = integerEnv(
    'COURSE_VUS',
    Math.min(LOAD.vus, COURSE_REQUESTS_PER_SECOND * 5 + 1),
    1,
    LOAD.vus,
);
const COURSE_PREFLIGHT_REQUEST_BUDGET = 1;
const COURSE_WARMUP_REQUEST_BUDGET = integerEnv('COURSE_WARMUP_REQUEST_BUDGET', 200, 1, 398);
const COURSE_MEASURED_REQUEST_BUDGET = integerEnv('COURSE_MEASURED_REQUEST_BUDGET', 600, 1, 600);

function durationForBudget(requestBudget, rate, name) {
    const seconds = Math.floor(requestBudget / rate);
    if (seconds < 1) {
        throw new Error(`${name}은 COURSE_REQUESTS_PER_SECOND 이상이어야 한다.`);
    }
    return `${seconds}s`;
}

const COURSE_WARMUP_DURATION = durationForBudget(
    COURSE_WARMUP_REQUEST_BUDGET,
    COURSE_REQUESTS_PER_SECOND,
    'COURSE_WARMUP_REQUEST_BUDGET',
);
const COURSE_MEASURED_DURATION = durationForBudget(
    COURSE_MEASURED_REQUEST_BUDGET,
    COURSE_REQUESTS_PER_SECOND,
    'COURSE_MEASURED_REQUEST_BUDGET',
);
const COURSE_ACTUAL_WARMUP_REQUEST_BUDGET = COURSE_REQUESTS_PER_SECOND * Number.parseInt(COURSE_WARMUP_DURATION, 10);
const COURSE_ACTUAL_MEASURED_REQUEST_BUDGET = COURSE_REQUESTS_PER_SECOND * Number.parseInt(COURSE_MEASURED_DURATION, 10);
const COURSE_TOTAL_REQUEST_BUDGET = COURSE_PREFLIGHT_REQUEST_BUDGET
    + COURSE_ACTUAL_WARMUP_REQUEST_BUDGET
    + COURSE_ACTUAL_MEASURED_REQUEST_BUDGET;

if (SCENARIO === 'course' && COURSE_TOTAL_REQUEST_BUDGET >= MOBILITY_MONTHLY_QUOTA) {
    throw new Error(
        `코스 측정 전용 예산(${COURSE_TOTAL_REQUEST_BUDGET})이 Mobility monthly quota ${MOBILITY_MONTHLY_QUOTA} 미만이어야 한다. `
        + 'production quota/rate-limit 설정을 완화하지 말고 예산을 낮춘다.'
    );
}

const naturalLanguageDuration = new Trend('duration_natural_language_search', true);
const courseInternalDuration = new Trend('duration_course_route_internal_observed', true);
const courseTotalDuration = new Trend('duration_course_route_external_included', true);
const courseTimeoutViolations = new Counter('course_timeout_violations');
const courseProviderBlockedResponses = new Counter('course_provider_blocked_responses');
const courseServiceRateLimitResponses = new Counter('course_service_rate_limit_responses');
const serverErrorRate = new Rate('server_error_rate');
const unexpectedStatus = new Counter('unexpected_status_count');
const measuredSamples = new Counter('measured_samples');
const performanceSamples = new Counter('performance_samples');

const publicReadOptions = loadScenarios({
    warmupExec: 'publicReadWarmUp',
    measuredExec: 'publicReadMeasured',
});
const courseOptions = loadScenarios({
    rate: COURSE_REQUESTS_PER_SECOND,
    vus: COURSE_VUS,
    warmupDuration: COURSE_WARMUP_DURATION,
    measuredDuration: COURSE_MEASURED_DURATION,
    measuredStartTime: COURSE_WARMUP_DURATION,
    warmupExec: 'courseWarmUp',
    measuredExec: 'courseMeasured',
});

export const options = {
    scenarios: SCENARIO === 'course' ? courseOptions : publicReadOptions,
    thresholds: SCENARIO === 'course'
        ? {
              ...(LOAD_PROFILE === 'normal' ? { [COURSE_DURATION_METRIC]: [COURSE_METRIC_MODE === 'internal' ? 'p(95)<500' : 'p(95)<5000'] } : {}),
              course_timeout_violations: ['count==0'],
              course_provider_blocked_responses: ['count==0'],
              course_service_rate_limit_responses: ['count==0'],
              server_error_rate: ['rate<0.01'],
              'http_req_failed{phase:measured}': ['rate<0.01'],
              dropped_iterations: ['count==0'],
          }
        : {
              ...(LOAD_PROFILE === 'normal' ? { duration_natural_language_search: ['p(95)<800'] } : {}),
              server_error_rate: ['rate<0.01'],
              'http_req_failed{phase:measured}': ['rate<0.01'],
              dropped_iterations: ['count==0'],
          },
};

export function setup() {
    if (SCENARIO === 'public-read') {
        return {};
    }

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

    // setup preflight 1건도 monthly quota 예산에 포함한다. 실패 원인은 warmup·측정
    // 표본과 섞지 않고 provider 차단/rate-limit counter에 별도로 남긴다.
    const preflight = postCourse(courseRestaurantIds);
    recordCourseFailure(preflight, 'preflight');
    if (preflight.status !== 200) {
        throw new Error(
            `코스 경로 사전 검증 실패 (status ${preflight.status}). `
            + '좌표·공개 상태·WireMock Stub을 확인한다.'
        );
    }

    return { courseRestaurantIds };
}

export function publicReadWarmUp() {
    callNaturalLanguageSearch(false);
}

export function publicReadMeasured() {
    callNaturalLanguageSearch(true);
}

export function courseWarmUp(data) {
    callCourseRoute(data.courseRestaurantIds, false);
}

export function courseMeasured(data) {
    callCourseRoute(data.courseRestaurantIds, true);
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
    evaluate(response, 'natural_language_search', naturalLanguageDuration, record);
}

function callCourseRoute(ids, record) {
    const response = postCourse(ids);
    const courseDuration = COURSE_METRIC_MODE === 'internal'
        ? courseInternalDuration
        : courseTotalDuration;
    evaluate(response, 'course_route', courseDuration, record);
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

function responseCode(response) {
    try {
        return response.json('code') || '';
    } catch (_) {
        return '';
    }
}

function recordCourseFailure(response, phase) {
    const code = responseCode(response);
    if (response.status === 429 || code === 'COURSE_ROUTE_RATE_LIMITED') {
        courseServiceRateLimitResponses.add(1, { phase });
    } else if (response.status === 502 || code === 'COURSE_ROUTE_PROVIDER_UNAVAILABLE') {
        // 공개 API는 Mobility의 PROVIDER_BLOCKED를 provider unavailable로 통합한다.
        courseProviderBlockedResponses.add(1, { phase });
    }
}

function evaluate(response, endpoint, trend, record) {
    const ok = check(response, {
        [`${endpoint} 200 응답`]: (r) => r.status === 200,
    });
    if (!ok) {
        unexpectedStatus.add(1, { endpoint, phase: record ? 'measured' : 'warmup' });
    }
    if (endpoint === 'course_route') {
        recordCourseFailure(response, record ? 'measured' : 'warmup');
    }
    if (!record) {
        return;
    }

    measuredSamples.add(1, { endpoint });
    if (endpoint === 'course_route'
        && (response.status === 0 || response.timings.duration > 5000)) {
        courseTimeoutViolations.add(1, { phase: 'measured' });
    }
    serverErrorRate.add(response.status >= 500 || response.status === 0, { endpoint });

    // quota/rate-limit 응답은 실패 원인 확인용 counter에만 남긴다. 즉시 429/502가
    // 빠른 성능 표본으로 Trend p95를 낮춰 보이게 하지 않는다.
    if (response.status !== 200) {
        return;
    }
    trend.add(response.timings.duration);
    performanceSamples.add(1, { endpoint });
}

export function handleSummary(data) {
    return writeSummary(data, RESULT_DIR, renderText);
}

function renderText(data) {
    const lines = [];
    lines.push(`NFR-PERFORMANCE-007 ${SCENARIO} / ${LOAD.label}(요청률 ${SCENARIO === 'course' ? COURSE_REQUESTS_PER_SECOND : LOAD.rate} RPS / 동시성 상한 ${SCENARIO === 'course' ? COURSE_VUS : LOAD.vus}) 결과`);
    if (SCENARIO === 'course') {
        lines.push(`코스 측정 프로필: ${COURSE_METRIC_MODE} (${COURSE_DURATION_METRIC})`);
        lines.push(`코스 측정 전용 예산: preflight ${COURSE_PREFLIGHT_REQUEST_BUDGET}건 + warmup 최대 ${COURSE_ACTUAL_WARMUP_REQUEST_BUDGET}건 + measured 최대 ${COURSE_ACTUAL_MEASURED_REQUEST_BUDGET}건 = ${COURSE_TOTAL_REQUEST_BUDGET}건 (< Mobility monthly quota ${MOBILITY_MONTHLY_QUOTA})`);
        lines.push('production quota/rate-limit 설정은 변경하지 않으며, quota·rate-limit 응답은 성능 표본에 포함하지 않는다.');
    }
    if (metricValue(data, 'measured_samples', 'count') === 0) {
        lines.push('*** 측정 구간 표본이 0건이다. 측정이 성립하지 않았으므로 아래 [통과] 표기를 판정 근거로 쓰지 않는다.');
    }

    lines.push('');
    lines.push('[엔드포인트별 응답 시간 — 측정 구간의 200 응답만]');
    const metrics = SCENARIO === 'course'
        ? [
              ['코스 경로 내부 관측값(WireMock 무지연 전제)', 'duration_course_route_internal_observed'],
              ['코스 경로 외부 호출 포함', 'duration_course_route_external_included'],
          ]
        : [['자연어 검색', 'duration_natural_language_search']];
    for (const [label, name] of metrics) {
        const metric = data.metrics[name];
        if (!metric) {
            lines.push(`  ${label}: 데이터 없음`);
            continue;
        }
        const v = metric.values;
        lines.push(
            `  ${label}: avg=${formatMetric(v.avg)}ms med=${formatMetric(v.med)}ms p95=${formatMetric(v['p(95)'])}ms max=${formatMetric(v.max)}ms ${thresholdVerdict(metric)}`
        );
    }
    lines.push(`  측정 요청 표본: ${metricValue(data, 'measured_samples', 'count')}건 / 성능 표본(200 응답): ${metricValue(data, 'performance_samples', 'count')}건`);
    lines.push('');
    lines.push('[오류·부하 조건]');
    lines.push(`  서버 오류율(5xx): ${formatPercent(metricValue(data, 'server_error_rate', 'rate'))} ${thresholdVerdict(data.metrics.server_error_rate)}`);
    lines.push(`  http_req_failed (측정 구간): ${formatPercent(metricValue(data, 'http_req_failed{phase:measured}', 'rate'))} ${thresholdVerdict(data.metrics['http_req_failed{phase:measured}'])}`);
    lines.push(`  http_req_failed (전체): ${formatPercent(metricValue(data, 'http_req_failed', 'rate'))}`);
    lines.push(`  200 아닌 응답: ${metricValue(data, 'unexpected_status_count', 'count')}건`);
    if (SCENARIO === 'course') {
        lines.push(`  Mobility quota/provider 차단 응답(502): ${metricValue(data, 'course_provider_blocked_responses', 'count')}건 ${thresholdVerdict(data.metrics.course_provider_blocked_responses)}`);
        lines.push(`  서비스 rate-limit 응답(429): ${metricValue(data, 'course_service_rate_limit_responses', 'count')}건 ${thresholdVerdict(data.metrics.course_service_rate_limit_responses)}`);
        lines.push(`  코스 5초 초과: ${metricValue(data, 'course_timeout_violations', 'count')}건 ${thresholdVerdict(data.metrics.course_timeout_violations)}`);
    }
    lines.push(`  dropped_iterations: ${metricValue(data, 'dropped_iterations', 'count')}건 ${thresholdVerdict(data.metrics.dropped_iterations)}`);
    lines.push('');
    lines.push('threshold를 하나라도 위반하면 k6는 종료 코드 99로 끝난다.');
    return lines.join('\n');
}
