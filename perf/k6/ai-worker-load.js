// AI Worker 운영 부하 검증.
//
// 티켓팅의 좌석 경쟁률을 재현하는 시나리오가 아니라, 관리자 작업 제출량과
// 비동기 Worker의 처리·대기열 소진을 분리해 관찰한다. Gemini·Kakao·YouTube는
// 모두 측정 전용 WireMock fixture를 향한다.
import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const ADMIN_EMAIL = __ENV.ADMIN_EMAIL || 'load-admin@example.com';
const ADMIN_PASSWORD = __ENV.ADMIN_PASSWORD || '';
const RATE = Number(__ENV.AI_SUBMIT_RATE || 10);
const DURATION = __ENV.AI_SUBMIT_DURATION || '20s';
const PREALLOCATED_VUS = Number(__ENV.AI_SUBMIT_PREALLOCATED_VUS || 50);
const MAX_VUS = Number(__ENV.AI_SUBMIT_MAX_VUS || 200);
const RUN_ID = __ENV.AI_LOAD_RUN_ID || `${Date.now()}`;

const submitErrors = new Rate('ai_submit_error_rate');
const submitSamples = new Counter('ai_submit_samples');
const acceptedJobs = new Counter('ai_accepted_jobs');
const reusedJobs = new Counter('ai_reused_jobs');
const submitDuration = new Trend('ai_submit_duration', true);

export const options = {
    scenarios: {
        ai_submit: {
            executor: 'constant-arrival-rate',
            rate: RATE,
            timeUnit: '1s',
            duration: DURATION,
            preAllocatedVUs: PREALLOCATED_VUS,
            maxVUs: MAX_VUS,
        },
    },
    thresholds: {
        ai_submit_error_rate: ['rate<0.01'],
        'http_req_failed{endpoint:ai_submit}': ['rate<0.01'],
        'http_req_duration{endpoint:ai_submit}': ['p(95)<1000'],
        dropped_iterations: ['count==0'],
    },
};

export function setup() {
    if (!ADMIN_PASSWORD) {
        throw new Error('ADMIN_PASSWORD is required for the isolated AI Worker load test.');
    }
    const response = http.post(
        `${BASE_URL}/api/auth/tokens`,
        JSON.stringify({ email: ADMIN_EMAIL, password: ADMIN_PASSWORD }),
        { headers: { 'Content-Type': 'application/json' }, tags: { endpoint: 'login' } },
    );
    check(response, { 'admin login succeeds': (r) => r.status === 200 });
    if (response.status !== 200) {
        throw new Error(`Admin login failed: status=${response.status}`);
    }
    return { accessToken: response.json('accessToken') };
}

export default function (data) {
    const sequence = `${__VU}-${__ITER}-${RUN_ID}`;
    const response = http.post(
        `${BASE_URL}/api/admin/ai/video-extractions`,
        JSON.stringify({
            videoUrl: 'https://www.youtube.com/watch?v=fixtureVid1',
            supplementText: `isolated-ai-load-${sequence}`,
            idempotencyKey: `isolated-ai-load-${sequence}`,
        }),
        {
            headers: {
                'Content-Type': 'application/json',
                Authorization: `Bearer ${data.accessToken}`,
            },
            tags: { endpoint: 'ai_submit' },
        },
    );

    submitSamples.add(1);
    submitDuration.add(response.timings.duration, { endpoint: 'ai_submit' });
    const accepted = response.status === 202 || response.status === 200;
    check(response, { 'AI job accepted or reused': () => accepted });
    acceptedJobs.add(accepted ? 1 : 0);
    reusedJobs.add(response.status === 200 ? 1 : 0);
    submitErrors.add(!accepted || response.status >= 500 || response.status === 0, { endpoint: 'ai_submit' });
}
