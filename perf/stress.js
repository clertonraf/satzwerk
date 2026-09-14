import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8083';
const JSON_HEADERS = { 'Content-Type': 'application/json' };
const DEFAULT_MIXED_STAGES = [
  { duration: '30s', target: 10 },
  { duration: '45s', target: 25 },
  { duration: '45s', target: 50 },
  { duration: '30s', target: 0 },
];
const MIXED_START_VUS = Number(__ENV.MIXED_START_VUS || 0);
const MIXED_GRACEFUL_RAMP_DOWN = __ENV.MIXED_GRACEFUL_RAMP_DOWN || '15s';
const MIXED_STAGES = loadMixedStages();
const SUMMARY_ENABLED = envBoolean('SUMMARY_ENABLED', true);
const SUMMARY_DURATION = __ENV.SUMMARY_DURATION || '60s';
const SUMMARY_START_TIME =
  __ENV.SUMMARY_START_TIME || addDurations(totalStagesDuration(MIXED_STAGES), MIXED_GRACEFUL_RAMP_DOWN);

export const options = {
  scenarios: buildScenarios(),
  thresholds: buildThresholds(),
};

export function setup() {
  const identity = {
    email: `summary-seed-${Date.now()}-${randomSuffix()}@example.test`,
    displayName: 'Summary Seed User',
  };
  const response = register(identity);

  check(response, {
    'setup register status is 201': (r) => r.status === 201,
    'setup register returns access token': (r) => Boolean(r.json('accessToken')),
  });

  if (response.status !== 201) {
    throw new Error(`Unable to create summary seed user: ${response.status} ${response.body}`);
  }

  return { accessToken: response.json('accessToken') };
}

export function mixed() {
  const identity = uniqueIdentity('mixed');
  const registerResponse = register(identity);

  const registerOk = check(registerResponse, {
    'register status is 201': (r) => r.status === 201,
    'register returns access token': (r) => Boolean(r.json('accessToken')),
  });

  if (!registerOk) {
    return;
  }

  const accessToken = registerResponse.json('accessToken');
  const createExerciseResponse = http.post(
    `${BASE_URL}/api/exercises`,
    JSON.stringify({
      name: `Bench Press ${exec.vu.idInTest}-${exec.scenario.iterationInTest}`,
      muscleGroup: 'CHEST',
      description: 'CI load test exercise',
    }),
    {
      headers: authHeaders(accessToken),
      tags: { endpoint: 'exercises-create' },
    },
  );

  check(createExerciseResponse, {
    'create exercise status is 201': (r) => r.status === 201,
    'create exercise returns id': (r) => Boolean(r.json('id')),
  });

  const listExercisesResponse = http.get(`${BASE_URL}/api/exercises`, {
    headers: authHeaders(accessToken),
    tags: { endpoint: 'exercises-list' },
  });

  check(listExercisesResponse, {
    'list exercises status is 200': (r) => r.status === 200,
    'list exercises returns array': (r) => Array.isArray(r.json()),
  });

  const analyticsSummaryResponse = http.get(`${BASE_URL}/api/analytics/summary`, {
    headers: authHeaders(accessToken),
    tags: { endpoint: 'analytics-summary' },
  });

  check(analyticsSummaryResponse, {
    'summary status is 200': (r) => r.status === 200,
    'summary includes totalSessions': (r) => Number.isInteger(r.json('totalSessions')),
  });

  sleep(1);
}

export function summaryOnly(setupData) {
  const response = http.get(`${BASE_URL}/api/analytics/summary`, {
    headers: authHeaders(setupData.accessToken),
    tags: { endpoint: 'analytics-summary' },
  });

  check(response, {
    'summary-only status is 200': (r) => r.status === 200,
    'summary-only includes totalSessions': (r) => Number.isInteger(r.json('totalSessions')),
    'summary-only includes currentStreak': (r) => Number.isInteger(r.json('currentStreak')),
  });

  sleep(0.5);
}

function buildScenarios() {
  const scenarios = {
    mixed: {
      executor: 'ramping-vus',
      exec: 'mixed',
      startVUs: MIXED_START_VUS,
      stages: MIXED_STAGES,
      gracefulRampDown: MIXED_GRACEFUL_RAMP_DOWN,
    },
  };

  if (SUMMARY_ENABLED) {
    scenarios.summary = {
      executor: 'constant-vus',
      exec: 'summaryOnly',
      vus: Number(__ENV.SUMMARY_VUS || 20),
      duration: SUMMARY_DURATION,
      startTime: SUMMARY_START_TIME,
    };
  }

  return scenarios;
}

function buildThresholds() {
  const thresholds = {
    http_req_failed: [rateThreshold('HTTP_REQ_FAILED_THRESHOLD_RATE', '<', 0.01)],
    checks: [rateThreshold('CHECKS_THRESHOLD_RATE', '>', 0.99)],
  };

  const mixedDurationThreshold = durationThreshold('MIXED_P95_THRESHOLD_MS', 500);
  if (mixedDurationThreshold) {
    thresholds['http_req_duration{scenario:mixed}'] = [mixedDurationThreshold];
  }

  if (SUMMARY_ENABLED) {
    const summaryDurationThreshold = durationThreshold('SUMMARY_P95_THRESHOLD_MS', 350);
    if (summaryDurationThreshold) {
      thresholds['http_req_duration{scenario:summary}'] = [summaryDurationThreshold];
    }
  }

  return thresholds;
}

function register(identity) {
  return http.post(
    `${BASE_URL}/api/auth/register`,
    JSON.stringify({
      email: identity.email,
      password: 'password123',
      displayName: identity.displayName,
    }),
    {
      headers: JSON_HEADERS,
      tags: { endpoint: 'auth-register' },
    },
  );
}

function uniqueIdentity(prefix) {
  const timestamp = Date.now();
  const vu = exec.vu.idInTest;
  const iteration = exec.scenario.iterationInTest;

  return {
    email: `${prefix}-${vu}-${iteration}-${timestamp}-${randomSuffix()}@example.test`,
    displayName: `Load User ${vu}-${iteration}`,
  };
}

function randomSuffix() {
  return Math.random().toString(16).slice(2, 10);
}

function authHeaders(accessToken) {
  return {
    ...JSON_HEADERS,
    Authorization: ['Bearer', accessToken].join(' '),
  };
}

function loadMixedStages() {
  if (__ENV.MIXED_STAGES_FILE) {
    return parseStages(open(__ENV.MIXED_STAGES_FILE), `file ${__ENV.MIXED_STAGES_FILE}`);
  }

  if (__ENV.MIXED_STAGES_JSON) {
    return parseStages(__ENV.MIXED_STAGES_JSON, 'MIXED_STAGES_JSON');
  }

  return DEFAULT_MIXED_STAGES;
}

function parseStages(value, source) {
  const stages = JSON.parse(value);

  if (!Array.isArray(stages) || stages.length === 0) {
    throw new Error(`${source} must describe a non-empty JSON array of k6 stages.`);
  }

  stages.forEach((stage, index) => {
    if (!stage || typeof stage.duration !== 'string' || typeof stage.target !== 'number') {
      throw new Error(
        `${source} stage #${index + 1} must include {\"duration\": string, \"target\": number}.`,
      );
    }
  });

  return stages;
}

function totalStagesDuration(stages) {
  return secondsToDurationString(
    stages.reduce((totalSeconds, stage) => totalSeconds + durationToSeconds(stage.duration), 0),
  );
}

function addDurations(left, right) {
  return secondsToDurationString(durationToSeconds(left) + durationToSeconds(right));
}

function durationToSeconds(duration) {
  const matches = duration.matchAll(/(\d+)(ms|s|m|h)/g);
  let totalSeconds = 0;

  for (const match of matches) {
    const amount = Number(match[1]);
    const unit = match[2];

    if (unit === 'ms') {
      totalSeconds += amount / 1000;
    } else if (unit === 's') {
      totalSeconds += amount;
    } else if (unit === 'm') {
      totalSeconds += amount * 60;
    } else if (unit === 'h') {
      totalSeconds += amount * 3600;
    }
  }

  if (totalSeconds === 0) {
    throw new Error(`Unsupported k6 duration string: ${duration}`);
  }

  return totalSeconds;
}

function secondsToDurationString(totalSeconds) {
  const roundedTotalSeconds = Math.round(totalSeconds * 1000) / 1000;
  const hours = Math.floor(roundedTotalSeconds / 3600);
  const minutes = Math.floor((roundedTotalSeconds % 3600) / 60);
  const seconds = Math.round((roundedTotalSeconds % 60) * 1000) / 1000;
  const parts = [];

  if (hours > 0) {
    parts.push(`${hours}h`);
  }

  if (minutes > 0) {
    parts.push(`${minutes}m`);
  }

  if (seconds > 0 || parts.length === 0) {
    const secondsLabel = Number.isInteger(seconds) ? seconds.toFixed(0) : `${seconds}`;
    parts.push(`${secondsLabel}s`);
  }

  return parts.join('');
}

function durationThreshold(envName, defaultMs) {
  const rawValue = __ENV[envName];
  if (rawValue === undefined) {
    return `p(95)<${defaultMs}`;
  }

  if (isDisabled(rawValue)) {
    return null;
  }

  return `p(95)<${Number(rawValue)}`;
}

function rateThreshold(envName, operator, defaultValue) {
  const rawValue = __ENV[envName];
  const numericValue = rawValue === undefined ? defaultValue : Number(rawValue);
  return `rate${operator}${numericValue}`;
}

function envBoolean(name, defaultValue) {
  const rawValue = __ENV[name];
  if (rawValue === undefined) {
    return defaultValue;
  }

  return !isDisabled(rawValue);
}

function isDisabled(value) {
  return ['0', 'false', 'no', 'off', 'disabled', 'none'].includes(String(value).toLowerCase());
}
