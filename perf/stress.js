import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8083';
const JSON_HEADERS = { 'Content-Type': 'application/json' };

export const options = {
  scenarios: {
    mixed: {
      executor: 'ramping-vus',
      exec: 'mixed',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 10 },
        { duration: '45s', target: 25 },
        { duration: '45s', target: 50 },
        { duration: '30s', target: 0 },
      ],
      gracefulRampDown: '15s',
    },
    summary: {
      executor: 'constant-vus',
      exec: 'summaryOnly',
      vus: 20,
      duration: '60s',
      startTime: '2m45s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
    // CI runs on a shared runner against a single docker-compose backend, so the
    // gate is intentionally tolerant of some noise while still catching obvious
    // regressions in pool starvation or slow dashboard queries.
    'http_req_duration{scenario:mixed}': ['p(95)<500'],
    'http_req_duration{scenario:summary}': ['p(95)<350'],
  },
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
