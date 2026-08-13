const profiles = {
    normal: { label: '정상 부하', rate: 20, vus: 50 },
    max: { label: '최대 부하', rate: 80, vus: 200 },
};

export const LOAD_PROFILE = __ENV.LOAD_PROFILE || 'normal';
export const LOAD = profiles[LOAD_PROFILE];
export const WARMUP_DURATION = __ENV.WARMUP_DURATION || '60s';
export const MEASURED_DURATION = __ENV.MEASURED_DURATION || '5m';

if (!LOAD) {
    throw new Error(`지원하지 않는 LOAD_PROFILE=${LOAD_PROFILE}. normal 또는 max를 사용한다.`);
}

export function loadScenarios({
    rate = LOAD.rate,
    vus = LOAD.vus,
    warmupDuration = WARMUP_DURATION,
    measuredDuration = MEASURED_DURATION,
    measuredStartTime = warmupDuration,
    warmupExec = 'warmUp',
    measuredExec = 'measured',
} = {}) {
    return {
        warm_up: {
            executor: 'constant-arrival-rate',
            rate,
            timeUnit: '1s',
            duration: warmupDuration,
            preAllocatedVUs: vus,
            maxVUs: vus,
            exec: warmupExec,
            tags: { phase: 'warmup' },
        },
        measured: {
            executor: 'constant-arrival-rate',
            rate,
            timeUnit: '1s',
            duration: measuredDuration,
            preAllocatedVUs: vus,
            maxVUs: vus,
            startTime: measuredStartTime,
            exec: measuredExec,
            tags: { phase: 'measured' },
        },
    };
}

export function integerEnv(name, fallback, min, max) {
    const raw = __ENV[name];
    const value = raw === undefined || raw === '' ? fallback : Number(raw);
    if (!Number.isInteger(value) || value < min || value > max) {
        throw new Error(`${name}은 ${min} 이상 ${max} 이하의 정수여야 한다.`);
    }
    return value;
}

export function writeSummary(data, resultDir, renderText) {
    const text = renderText(data);
    return {
        stdout: text,
        [`${resultDir}/summary.json`]: JSON.stringify(data, null, 2),
        [`${resultDir}/summary.txt`]: text,
    };
}

export function metricValue(data, metric, key) {
    const found = data.metrics[metric];
    if (!found || found.values[key] === undefined) {
        return 0;
    }
    return found.values[key];
}

export function formatMetric(value) {
    return value === undefined ? '-' : Number(value).toFixed(1);
}

export function formatPercent(value) {
    return `${(value * 100).toFixed(3)}%`;
}

export function thresholdVerdict(metric) {
    if (!metric || !metric.thresholds) {
        return '';
    }
    const failed = Object.keys(metric.thresholds).filter((key) => !metric.thresholds[key].ok);
    return failed.length === 0 ? '[통과]' : `[위반: ${failed.join(', ')}]`;
}
