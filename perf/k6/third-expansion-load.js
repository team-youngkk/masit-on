// NFR-PERFORMANCE-007 자연어 검색·코스 경로 부하 검증 시나리오.
// 기본값은 SCENARIO=course이며, 공개 읽기 arm은 SCENARIO=public-read로 명시 선택한다.
// 요청률·VU 상한은 공용 LOAD.rate·LOAD.vus 프로필에서 선택한다.
//
// public-read는 POST /api/restaurants/natural-language-search만 호출한다.
// 자연어 검색은 client address별 요청 제한을 받으므로 PUBLIC_READ_MODE로 검증
// 목적을 나눈다. contract는 제한 아래 저속으로 계약·지연을 검증하고, throughput은
// 상한을 넘겨 포화 거동만 관찰한다. throughput은 단일 client에서 rate-limit 응답이
// 다수를 차지하므로 성능 인증 근거가 아니다.
// course는 POST /api/restaurants/course-routes만 호출하며, 측정 전용 예산으로
// Mobility production quota·rate-limit 설정을 완화하지 않고 실행량만 제한한다.
// Kakao Mobility는 측정 대상 환경의 WireMock Stub이어야 한다. 실제 Kakao·YouTube
// API나 계약에 없는 내부 메트릭·health endpoint를 호출하지 않는다. course ID를
// 생략하면 좌표가 계약상 포함된 공개 map-points 응답에서 자동 선택한다.
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
const PUBLIC_READ_MODE = __ENV.PUBLIC_READ_MODE || 'contract';

if (!['public-read', 'course'].includes(SCENARIO)) {
    throw new Error(`지원하지 않는 SCENARIO=${SCENARIO}. public-read 또는 course를 사용한다.`);
}
if (!['internal', 'external'].includes(COURSE_METRIC_MODE)) {
    throw new Error(`지원하지 않는 COURSE_METRIC_MODE=${COURSE_METRIC_MODE}. internal 또는 external을 사용한다.`);
}
if (!['contract', 'throughput'].includes(PUBLIC_READ_MODE)) {
    throw new Error(`지원하지 않는 PUBLIC_READ_MODE=${PUBLIC_READ_MODE}. contract 또는 throughput을 사용한다.`);
}

// 이 값은 RedisNaturalLanguageRateLimitStore의 client address별 창 60초·상한 60건을
// 복제한다. production 설정을 완화하지 않고 측정 전용 실행량만 이 경계 아래로 둔다.
// API 계약은 429 NATURAL_LANGUAGE_RATE_LIMITED만 정의하므로 이 수치를 응답 계약으로
// 다루지 않는다.
const NATURAL_LANGUAGE_LIMIT_PER_MINUTE = 60;
// 상한과 같은 값이면 창 경계에서 429가 나올 수 있어 계약 검증 상한을 하나 낮춘다.
// 다만 제한기가 세는 시각은 k6의 도착 예정 시각이 아니라 서버 처리 시각이다. 상한에
// 가까운 값으로 실행하면 GC pause 같은 지연으로 밀린 요청이 한 창에 겹쳐 애플리케이션
// 결함 없이 429가 날 수 있다. warmup과 measured도 같은 client 키를 공유한다.
// 기본값 30건/분은 그 여유를 두 배로 둔 값이므로 특별한 이유 없이 올리지 않는다.
const NATURAL_LANGUAGE_REQUESTS_PER_MINUTE = integerEnv(
    'NATURAL_LANGUAGE_REQUESTS_PER_MINUTE',
    30,
    1,
    NATURAL_LANGUAGE_LIMIT_PER_MINUTE - 1,
);
const NATURAL_LANGUAGE_CONTRACT_VUS = integerEnv('NATURAL_LANGUAGE_CONTRACT_VUS', 5, 1, 50);
const NATURAL_LANGUAGE_CONTRACT_MEASURED_DURATION =
    __ENV.NATURAL_LANGUAGE_CONTRACT_MEASURED_DURATION || '10m';

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
// 자연어 arm의 429를 별도로 세지 않으면 실패 응답을 사후에 rate-limit으로 확정할 수
// 없다. 이슈 #190의 6,783건이 그 사례였다.
const naturalLanguageRateLimitResponses = new Counter('natural_language_rate_limit_responses');
const serverErrorRate = new Rate('server_error_rate');
const unexpectedStatus = new Counter('unexpected_status_count');
const measuredSamples = new Counter('measured_samples');
const performanceSamples = new Counter('performance_samples');

// 기본값을 contract로 둔다. 제한을 넘기는 실행은 결과 해석에 별도 근거가 필요하므로
// 명시 선택으로만 들어가게 한다.
const publicReadOptions = PUBLIC_READ_MODE === 'contract'
    ? loadScenarios({
          rate: NATURAL_LANGUAGE_REQUESTS_PER_MINUTE,
          timeUnit: '1m',
          vus: NATURAL_LANGUAGE_CONTRACT_VUS,
          measuredDuration: NATURAL_LANGUAGE_CONTRACT_MEASURED_DURATION,
          warmupExec: 'publicReadWarmUp',
          measuredExec: 'publicReadMeasured',
      })
    : loadScenarios({
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
        : PUBLIC_READ_MODE === 'contract'
        ? {
              // 요청 제한 아래에서는 429가 하나도 없어야 계약 검증이 성립한다.
              duration_natural_language_search: ['p(95)<800'],
              // 판정은 측정 구간으로 한다. warmup 429는 직전 실행의 잔여 창처럼
              // 애플리케이션과 무관한 원인일 수 있어 요약에만 남긴다.
              'natural_language_rate_limit_responses{phase:measured}': ['count==0'],
              server_error_rate: ['rate<0.01'],
              'http_req_failed{phase:measured}': ['rate<0.01'],
              dropped_iterations: ['count==0'],
          }
        : {
              // throughput은 단일 client에서 429가 다수를 차지하는 것이 정상이므로
              // 지연·실패율을 통과 기준으로 걸지 않는다. 서버 오류와 부하 생성기
              // 포화만 판정한다.
              server_error_rate: ['rate<0.01'],
              dropped_iterations: ['count==0'],
          },
};

export function setup() {
    if (SCENARIO === 'public-read') {
        return {};
    }

    const mapPointsResponse = http.get(`${BASE_URL}/api/restaurants/map-points`);
    if (mapPointsResponse.status !== 200) {
        throw new Error(`좌표 포함 공개 맛집 조회 실패 (status ${mapPointsResponse.status}). 측정 대상 환경을 확인한다.`);
    }

    const items = mapPointsResponse.json('items') || [];
    const listedIds = items
        .filter((item) => item.id && item.coordinate?.latitude && item.coordinate?.longitude)
        .map((item) => item.id);
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

function recordNaturalLanguageFailure(response, phase) {
    if (response.status === 429 || responseCode(response) === 'NATURAL_LANGUAGE_RATE_LIMITED') {
        naturalLanguageRateLimitResponses.add(1, { phase });
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
    } else if (endpoint === 'natural_language_search') {
        recordNaturalLanguageFailure(response, record ? 'measured' : 'warmup');
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
    if (SCENARIO === 'public-read' && PUBLIC_READ_MODE === 'contract') {
        lines.push(
            `NFR-PERFORMANCE-007 참고 관찰 — public-read / 계약 검증(요청률 ${NATURAL_LANGUAGE_REQUESTS_PER_MINUTE}건/분 `
            + `/ 동시성 상한 ${NATURAL_LANGUAGE_CONTRACT_VUS}) 결과`
        );
        lines.push(
            `*** 이 실행은 NFR-PERFORMANCE-007의 성능 인증이 아니다. 단일 client address의 요청 제한`
            + `(${NATURAL_LANGUAGE_LIMIT_PER_MINUTE}건/분) 아래에서 계약과 지연만 관찰하며, 해당 요구사항의 검증 방법인 `
            + '50 VU / 20 RPS와 200 VU / 80 RPS에 미치지 못한다.'
        );
        lines.push(`계약 검증은 공용 LOAD 프로필을 쓰지 않는다. LOAD_PROFILE=${LOAD_PROFILE} 값은 이 결과에 반영되지 않았다.`);
    } else {
        lines.push(`NFR-PERFORMANCE-007 ${SCENARIO} / ${LOAD.label}(요청률 ${SCENARIO === 'course' ? COURSE_REQUESTS_PER_SECOND : LOAD.rate} RPS / 동시성 상한 ${SCENARIO === 'course' ? COURSE_VUS : LOAD.vus}) 결과`);
    }
    if (SCENARIO === 'public-read' && PUBLIC_READ_MODE === 'throughput') {
        lines.push(
            '*** throughput 모드는 요청 제한을 넘겨 포화 거동만 관찰한다. 단일 client address에서는 '
            + `${NATURAL_LANGUAGE_LIMIT_PER_MINUTE}건/분을 넘는 요청이 429가 되므로, 아래 지연 지표와 [통과] 표기를 `
            + '성능 인증 근거로 쓰지 않는다. 성능 판정은 다중 client source가 확보된 환경에서만 한다.'
        );
    }
    if (SCENARIO === 'course') {
        lines.push(`코스 측정 프로필: ${COURSE_METRIC_MODE} (${COURSE_DURATION_METRIC})`);
        lines.push(`코스 측정 전용 예산: preflight ${COURSE_PREFLIGHT_REQUEST_BUDGET}건 + warmup 최대 ${COURSE_ACTUAL_WARMUP_REQUEST_BUDGET}건 + measured 최대 ${COURSE_ACTUAL_MEASURED_REQUEST_BUDGET}건 = ${COURSE_TOTAL_REQUEST_BUDGET}건 (< Mobility monthly quota ${MOBILITY_MONTHLY_QUOTA})`);
        lines.push('production quota/rate-limit 설정은 변경하지 않으며, quota·rate-limit 응답은 성능 표본에 포함하지 않는다.');
    }
    const measured = metricValue(data, 'measured_samples', 'count');
    const performance = metricValue(data, 'performance_samples', 'count');
    if (measured === 0) {
        lines.push('*** 측정 구간 표본이 0건이다. 측정이 성립하지 않았으므로 아래 [통과] 표기를 판정 근거로 쓰지 않는다.');
    } else if (performance === 0) {
        lines.push('*** 200 응답이 0건이라 성능 표본이 없다. 아래 지연 지표와 [통과] 표기를 판정 근거로 쓰지 않는다.');
    } else if (performance < measured) {
        // 200 응답만 Trend에 들어가므로, 실패 비중이 크면 p95는 살아남은 소수 표본의
        // 값이다. #190에서 419건짜리 p95가 통과로 읽힌 경로를 막는다.
        lines.push(
            `*** 측정 표본 ${measured}건 중 성능 표본은 ${performance}건`
            + `(${(performance / measured * 100).toFixed(1)}%)뿐이다. 아래 지연 지표는 200 응답만의 값이므로 `
            + '전체 요청의 성능으로 해석하지 않는다.'
        );
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
    // k6는 threshold를 건 하위 지표만 집계한다. 없는 지표를 0으로 출력하면 실패를
    // 0%로 오독하게 되므로 미집계 사실을 그대로 적는다.
    const measuredFailed = data.metrics['http_req_failed{phase:measured}'];
    lines.push(measuredFailed
        ? `  http_req_failed (측정 구간): ${formatPercent(metricValue(data, 'http_req_failed{phase:measured}', 'rate'))} ${thresholdVerdict(measuredFailed)}`
        : '  http_req_failed (측정 구간): 집계하지 않음 — 이 모드는 실패율을 판정 기준으로 쓰지 않는다. 아래 전체 값을 본다.');
    lines.push(`  http_req_failed (전체): ${formatPercent(metricValue(data, 'http_req_failed', 'rate'))}`);
    lines.push(`  200 아닌 응답: ${metricValue(data, 'unexpected_status_count', 'count')}건`);
    if (SCENARIO === 'public-read') {
        const rateLimited = metricValue(data, 'natural_language_rate_limit_responses', 'count');
        const unexpected = metricValue(data, 'unexpected_status_count', 'count');
        const measuredRateLimited = data.metrics['natural_language_rate_limit_responses{phase:measured}'];
        lines.push(`  자연어 rate-limit 응답(429, warmup 포함 전체): ${rateLimited}건`);
        if (measuredRateLimited) {
            lines.push(
                `  자연어 rate-limit 응답(429, 측정 구간): ${metricValue(data, 'natural_language_rate_limit_responses{phase:measured}', 'count')}건 `
                + `${thresholdVerdict(measuredRateLimited)}`
            );
        }
        lines.push(`  rate-limit으로 설명되지 않는 200 아닌 응답: ${unexpected - rateLimited}건`);
    }
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
