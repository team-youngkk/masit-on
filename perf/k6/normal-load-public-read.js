// NFR-PERFORMANCE-006 공개 조회 부하 검증 시나리오.
// 대상은 이슈 #148 완료 조건인 공개 조회 2종(인기 맛집, 공개 큐레이션)이며,
// 큐레이션은 목록·상세가 별도 경로라 실제로 호출하는 엔드포인트는 셋이다.
// 부하 프로필의 요청률·VU 상한은 공용 LOAD.rate·LOAD.vus에서 선택한다.
//
// 판정 기준은 NFR-PERFORMANCE-006이 원문이다.
//   - 엔드포인트별 p95 500ms 이하
//   - 서버 오류율(5xx) 1% 미만
// 부하 모델(프로필별 LOAD.vus·LOAD.rate)과 측정 환경은 RV-NFR-011, 기준 데이터 규모는 RV-NFR-002가 정한다.
//
// p95 측정 대상은 "애플리케이션 서버 내부 처리" 시간이다. 이 스크립트가 재는
// http_req_duration은 DNS·TCP·TLS를 제외한 값이긴 하나 요청 전송과 응답 수신의
// 네트워크 왕복을 포함하므로 서버 내부 처리 시간과 같지 않다. 수치를 임의로
// 보정하지 않는 대신, 부하 생성기를 대상 서버와 같은 네트워크(같은 VPC 또는 같은
// 호스트)에 두어 왕복 지연을 무시할 수 있는 수준으로 낮춘 상태에서만 이 판정을
// 유효한 것으로 본다. 다른 리전이나 사무실 네트워크에서 돌린 결과는 이 기준으로
// 판정하지 않는다.
//
// Kakao·YouTube 외부 연동은 WireMock Stub으로 대체한 환경을 전제한다. 실제 외부
// API를 호출하는 환경에서 실행하지 않는다.
import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import {
    LOAD,
    LOAD_PROFILE,
    loadScenarios,
    metricValue,
    formatMetric,
    formatPercent,
    thresholdVerdict,
    writeSummary,
} from './load-profile.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const RESULT_DIR = __ENV.RESULT_DIR || 'perf/k6/results';
// 엔드포인트별로 따로 판정해야 하므로 Trend를 엔드포인트마다 둔다. 세 경로의 비용이
// 서로 달라 하나로 합치면 느린 쪽이 빠른 쪽에 가려진다.
// 워밍업 구간에서는 이 Trend에 값을 넣지 않는다. threshold 판정 대상을 측정 구간으로
// 한정하기 위해서다.
const popularDuration = new Trend('duration_restaurants_popular', true);
const curationListDuration = new Trend('duration_curations_list', true);
const curationDetailDuration = new Trend('duration_curations_detail', true);

// 계약상 "서버 오류율"은 5xx 기준이다. http_req_failed는 4xx도 실패로 세므로
// 5xx만 보는 지표를 따로 둔다.
const serverErrorRate = new Rate('server_error_rate');

// 정상 시나리오에서는 200 외의 응답이 나올 경로가 없다. 나온다면 시나리오가 계약과
// 어긋난 것이므로 실패 건수를 남겨 결과를 사후 추적할 수 있게 한다.
const unexpectedStatus = new Counter('unexpected_status_count');

// k6 v2의 Trend 요약 값에는 표본 수가 들어 있지 않다(med·min·max·avg·백분위만 있다).
// p95를 몇 개 표본으로 낸 값인지 모르면 수치를 신뢰할 근거가 없으므로 따로 센다.
// 세 엔드포인트를 iteration마다 번갈아 호출하므로 엔드포인트별 표본은 이 값의 1/3이다.
const measuredSamples = new Counter('measured_samples');

export const options = {
    // 계약값은 RV-NFR-011의 LOAD.vus·LOAD.rate 프로필이다.
    // 두 값을 문자 그대로 동시에
    // 성립시킬 수는 없다. 도착률과 동시 활성 VU의 관계는 Little's law로
    // `LOAD.rate × 평균 응답시간`이다. 활성 VU 상한을 모두 채우려면 평균 응답이
    // p95 기준보다 훨씬 커질 수 있으므로, 이 모델은 동시 처리 사용자 수를
    // 그대로 재현한다고 해석하지 않는다.
    //
    // 그래서 이 모델이 실제로 고정하는 것은 **요청률과 동시성 상한**이다.
    // VU 상한을 "동시에 요청을 처리 중인 사용자 수"가 아니라 "부하를 만드는 가상
    // 사용자 풀의 크기"로 해석한다. 결과를 활성 동시 사용자 수 검증으로 읽지 않는다.
    //
    // constant-arrival-rate를 쓰는 이유는 도착률을 서버 응답 속도와 무관하게
    // 유지하기 때문이다. constant-vus는 서버가 느려지면 RPS가 같이 떨어져 계약값을
    // 재지 못한다.
    //
    // preAllocatedVUs와 maxVUs를 같은 값으로 둔다. maxVUs를 크게 잡으면 서버가
    // 느려질 때 k6가 VU를 늘려 부하 조건이 조용히 상한을 넘는다. LOAD.vus에 묶어 두면
    // 대신 dropped_iterations가 쌓이므로, 요청률을 만들어내지 못했다는 사실이
    // 지표로 드러난다. 그래서 dropped_iterations도 판정 대상이다.
    // JIT 컴파일, 커넥션 풀·JPA 2차 준비, 캐시 초기 적재가 초반 응답 시간을
    // 끌어올려 p95를 오염시킨다. 같은 부하로 먼저 돌리되 지표는 버린다.
    scenarios: loadScenarios(),
    thresholds: {
        // NFR-PERFORMANCE-006의 p95 합격 기준은 정상 부하에 적용한다.
        // 최대 부하는 RV-NFR-001의 용량·오류 확산 확인이므로 p95를 같은 합격
        // 기준으로 강제하지 않고 결과 수치만 보존한다.
        ...(LOAD_PROFILE === 'normal'
            ? {
                  duration_restaurants_popular: ['p(95)<500'],
                  duration_curations_list: ['p(95)<500'],
                  duration_curations_detail: ['p(95)<500'],
              }
            : {}),
        'server_error_rate': ['rate<0.01'],
        'http_req_failed{phase:measured}': ['rate<0.01'],
        'dropped_iterations': ['count==0'],
    },
};

// 상세 조회 대상 식별자를 하드코딩하지 않는다. 게시 큐레이션은 운영 데이터라
// 환경마다 다르고, 없는 식별자를 넣으면 404 경로를 재게 되어 측정이 무의미해진다.
export function setup() {
    const response = http.get(`${BASE_URL}/api/curations`);
    if (response.status !== 200) {
        throw new Error(`게시 큐레이션 목록 조회 실패 (status ${response.status}). 측정 대상 환경을 확인한다.`);
    }

    const items = response.json('items') || [];
    const curationIds = items.map((item) => item.curationId).filter(Boolean);
    if (curationIds.length === 0) {
        throw new Error('게시된 큐레이션이 0건이라 상세 조회 시나리오를 실행할 수 없다. 측정 대상 환경에 게시 데이터를 준비한다.');
    }

    // 기준 데이터가 적재되지 않은 DB를 재면 인기 맛집은 빈 items를 200으로 돌려주고
    // p95는 수 ms가 나온다. 즉 아무것도 없는 환경에서 전 threshold가 통과해 종료 코드
    // 0으로 끝난다. 판정을 사람 눈에 맡기지 않는 것이 이 시나리오의 목적이므로
    // 여기서 막는다. 집계 결과는 서버가 상위 20건으로 고정한다.
    const popular = http.get(`${BASE_URL}/api/restaurants/popular`);
    if (popular.status !== 200) {
        throw new Error(`인기 맛집 조회 실패 (status ${popular.status}). 측정 대상 환경을 확인한다.`);
    }
    const popularCount = (popular.json('items') || []).length;
    if (popularCount < 20) {
        throw new Error(
            `인기 맛집 집계가 ${popularCount}건이라 기준 데이터가 적재되지 않은 환경으로 보인다. perf/seed/를 먼저 적재한다.`
        );
    }

    return { curationIds };
}

export function warmUp(data) {
    runOneRequest(data, false);
}

export function measured(data) {
    runOneRequest(data, true);
}

// 한 iteration에서 세 엔드포인트를 모두 호출하면 도착률이 실제로는 세 배의 RPS가
// 된다. 계약이 정한 LOAD.rate를 지키기 위해 iteration마다 한 경로만 호출하고 세 경로를
// 균등하게 번갈아 돈다.
function runOneRequest(data, record) {
    const slot = exec.scenario.iterationInTest % 3;
    if (slot === 0) {
        callPopularRestaurants(record);
    } else if (slot === 1) {
        callCurationList(record);
    } else {
        callCurationDetail(data, record);
    }
}

function callPopularRestaurants(record) {
    // 이 엔드포인트는 쿼리 파라미터를 하나라도 붙이면 400 INVALID_REQUEST를 준다.
    // 캐시 버스팅용 파라미터도 붙일 수 없다.
    const response = http.get(`${BASE_URL}/api/restaurants/popular`, {
        tags: { endpoint: 'restaurants_popular' },
    });
    evaluate(response, 'restaurants_popular', popularDuration, record);
}

function callCurationList(record) {
    const response = http.get(`${BASE_URL}/api/curations`, {
        tags: { endpoint: 'curations_list' },
    });
    evaluate(response, 'curations_list', curationListDuration, record);
}

function callCurationDetail(data, record) {
    const ids = data.curationIds;
    // iterationInTest를 그대로 나누지 않는다. 이 함수는 iterationInTest % 3 === 2인
    // iteration에서만 호출되므로, 게시 큐레이션이 3건이면 인덱스가 2 하나로 고정돼
    // 나머지 두 건은 한 번도 측정되지 않는다(gcd(3, 3) > 1). 게시 건수는 스키마상
    // 1~5 어디든 될 수 있으므로 상세 호출 순번으로 나눠 항상 전부 순회하게 한다.
    const detailCallIndex = Math.floor(exec.scenario.iterationInTest / 3);
    const curationId = ids[detailCallIndex % ids.length];
    const response = http.get(`${BASE_URL}/api/curations/${curationId}`, {
        tags: { endpoint: 'curations_detail' },
    });
    evaluate(response, 'curations_detail', curationDetailDuration, record);
}

function evaluate(response, endpoint, trend, record) {
    const ok = check(response, {
        [`${endpoint} 200 응답`]: (r) => r.status === 200,
    });
    if (!ok) {
        unexpectedStatus.add(1, { endpoint, phase: record ? 'measured' : 'warmup' });
    }

    if (!record) {
        return;
    }
    trend.add(response.timings.duration);
    // k6는 연결 실패·타임아웃에 status 0을 준다. `>= 500`만 보면 애플리케이션이 죽어
    // 모든 연결이 거절되는 상황에서 "서버 오류율 0%"를 찍는다. 그 숫자가 검증 결과
    // 문서로 옮겨지므로 오류로 센다.
    serverErrorRate.add(response.status >= 500 || response.status === 0, { endpoint });
    measuredSamples.add(1);
}

export function handleSummary(data) {
    return writeSummary(data, RESULT_DIR, renderText);
}

function renderText(data) {
    const lines = [];
    lines.push(`NFR-PERFORMANCE-006 ${LOAD.label}(요청률 ${LOAD.rate} RPS / 동시성 상한 ${LOAD.vus}) 결과`);

    // 표본이 없으면 threshold는 위반할 값이 없어 전부 [통과]로 찍힌다. 그 출력을
    // 그대로 검증 결과 문서에 옮기면 측정하지 않은 것을 통과로 기록하게 된다.
    if (metricValue(data, 'measured_samples', 'count') === 0) {
        lines.push('');
        lines.push('*** 측정 구간 표본이 0건이다. 측정이 성립하지 않았으므로 아래 [통과] 표기를');
        lines.push('*** 판정 근거로 쓰지 않는다. 실행 로그에서 중단 원인을 확인한다.');
    }
    // 대상 주소를 요약에 남기지 않는다. 이 파일은 공개 저장소의 워크플로 아티팩트로
    // 올라갈 수 있고, 측정 인스턴스는 검증 게이트 없이 8080을 노출한 상태다.
    // 어느 환경을 쟀는지는 검증 결과 문서에 기록한다.
    lines.push('');
    lines.push('[엔드포인트별 응답 시간 — 측정 구간만]');
    for (const [label, name] of [
        ['인기 맛집', 'duration_restaurants_popular'],
        ['큐레이션 목록', 'duration_curations_list'],
        ['큐레이션 상세', 'duration_curations_detail'],
    ]) {
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
    const samples = metricValue(data, 'measured_samples', 'count');
    lines.push(`  측정 구간 표본: 합계 ${samples}건 (엔드포인트당 약 ${Math.floor(samples / 3)}건)`);
    lines.push('');
    lines.push('[오류·부하 조건]');
    lines.push(`  서버 오류율(5xx): ${formatPercent(metricValue(data, 'server_error_rate', 'rate'))} ${thresholdVerdict(data.metrics.server_error_rate)}`);
    lines.push(`  http_req_failed (측정 구간): ${formatPercent(metricValue(data, 'http_req_failed{phase:measured}', 'rate'))} ${thresholdVerdict(data.metrics['http_req_failed{phase:measured}'])}`);
    lines.push(`  http_req_failed (전체): ${formatPercent(metricValue(data, 'http_req_failed', 'rate'))}`);
    lines.push(`  200 아닌 응답: ${metricValue(data, 'unexpected_status_count', 'count')}건`);
    lines.push(`  dropped_iterations: ${metricValue(data, 'dropped_iterations', 'count')}건 ${thresholdVerdict(data.metrics.dropped_iterations)}`);
    lines.push('');
    lines.push('threshold를 하나라도 위반하면 k6는 종료 코드 99로 끝난다.');
    lines.push('');
    return lines.join('\n');
}
