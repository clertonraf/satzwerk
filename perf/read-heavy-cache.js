import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8083';
const JSON_HEADERS = { 'Content-Type': 'application/json' };

export const options = {
  scenarios: {
    readHeavy: {
      executor: 'constant-vus',
      vus: Number(__ENV.VUS || 20),
      duration: __ENV.DURATION || '60s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
    'http_req_duration{endpoint:exercise-catalog}': ['p(95)<500'],
    'http_req_duration{endpoint:analytics-heatmap}': ['p(95)<500'],
    'http_req_duration{endpoint:analytics-streak}': ['p(95)<500'],
  },
};

export function setup() {
  const today = new Date().toISOString().slice(0, 10);
  const identity = {
    email: `cache-seed-${Date.now()}-${randomSuffix()}@example.test`,
    displayName: 'Cache Seed User',
  };
  const registerResponse = register(identity);

  check(registerResponse, {
    'setup register status is 201': (r) => r.status === 201,
    'setup register returns access token': (r) => Boolean(r.json('accessToken')),
  });

  if (registerResponse.status !== 201) {
    throw new Error(`Unable to create cache seed user: ${registerResponse.status} ${registerResponse.body}`);
  }

  const accessToken = registerResponse.json('accessToken');
  const exerciseId = createExercise(accessToken, 'Bench Press', 'CHEST');
  const secondExerciseId = createExercise(accessToken, 'Barbell Row', 'BACK');
  const planId = createPlan(accessToken, 'Cache Benchmark Plan');
  const groupId = createGroup(accessToken, planId, 'Full Body', exerciseId);
  attachExercise(accessToken, planId, groupId, secondExerciseId);
  const sessionId = startSession(accessToken, groupId);
  addSetLog(accessToken, sessionId, exerciseId, 1);
  addSetLog(accessToken, sessionId, secondExerciseId, 2);
  completeSession(accessToken, sessionId);

  return {
    accessToken,
    from: today,
    to: today,
  };
}

export default function readHeavy(setupData) {
  const headers = authHeaders(setupData.accessToken);

  const exercisesResponse = http.get(`${BASE_URL}/api/exercises`, {
    headers,
    tags: { endpoint: 'exercise-catalog' },
  });
  check(exercisesResponse, {
    'exercise list status is 200': (r) => r.status === 200,
    'exercise list returns at least two exercises': (r) => Array.isArray(r.json()) && r.json().length >= 2,
  });

  const heatmapResponse = http.get(
    `${BASE_URL}/api/analytics/heatmap?from=${setupData.from}&to=${setupData.to}`,
    {
      headers,
      tags: { endpoint: 'analytics-heatmap' },
    },
  );
  check(heatmapResponse, {
    'heatmap status is 200': (r) => r.status === 200,
    'heatmap returns at least one entry': (r) => Array.isArray(r.json()) && r.json().length >= 1,
  });

  const streakResponse = http.get(`${BASE_URL}/api/analytics/streak`, {
    headers,
    tags: { endpoint: 'analytics-streak' },
  });
  check(streakResponse, {
    'streak status is 200': (r) => r.status === 200,
    'streak includes current streak': (r) => Number.isInteger(r.json('currentStreak')),
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

function createExercise(accessToken, name, muscleGroup) {
  const response = http.post(
    `${BASE_URL}/api/exercises`,
    JSON.stringify({ name, muscleGroup }),
    {
      headers: authHeaders(accessToken),
      tags: { endpoint: 'exercises-create' },
    },
  );

  check(response, {
    [`create exercise ${name} status is 201`]: (r) => r.status === 201,
  });

  if (response.status !== 201) {
    throw new Error(`Unable to create exercise ${name}: ${response.status} ${response.body}`);
  }

  return response.json('id');
}

function createPlan(accessToken, name) {
  const response = http.post(
    `${BASE_URL}/api/plans`,
    JSON.stringify({ name }),
    {
      headers: authHeaders(accessToken),
      tags: { endpoint: 'plans-create' },
    },
  );

  check(response, {
    'create plan status is 201': (r) => r.status === 201,
  });

  if (response.status !== 201) {
    throw new Error(`Unable to create plan: ${response.status} ${response.body}`);
  }

  const planId = response.json('id');
  const activateResponse = http.post(`${BASE_URL}/api/plans/${planId}/activate`, null, {
    headers: authHeaders(accessToken),
    tags: { endpoint: 'plans-activate' },
  });

  check(activateResponse, {
    'activate plan status is 204': (r) => r.status === 204,
  });

  if (activateResponse.status !== 204) {
    throw new Error(`Unable to activate plan: ${activateResponse.status} ${activateResponse.body}`);
  }

  return planId;
}

function createGroup(accessToken, planId, title, exerciseId) {
  const response = http.post(
    `${BASE_URL}/api/plans/${planId}/groups`,
    JSON.stringify({ title }),
    {
      headers: authHeaders(accessToken),
      tags: { endpoint: 'groups-create' },
    },
  );

  check(response, {
    'create group status is 201': (r) => r.status === 201,
  });

  if (response.status !== 201) {
    throw new Error(`Unable to create group: ${response.status} ${response.body}`);
  }

  const groupId = response.json('id');
  attachExercise(accessToken, planId, groupId, exerciseId);
  return groupId;
}

function attachExercise(accessToken, planId, groupId, exerciseId) {
  const response = http.post(
    `${BASE_URL}/api/plans/${planId}/groups/${groupId}/exercises`,
    JSON.stringify({
      exerciseId,
      sets: 4,
      reps: 8,
    }),
    {
      headers: authHeaders(accessToken),
      tags: { endpoint: 'group-exercises-create' },
    },
  );

  check(response, {
    'attach exercise status is 201': (r) => r.status === 201,
  });

  if (response.status !== 201) {
    throw new Error(`Unable to attach exercise: ${response.status} ${response.body}`);
  }
}

function startSession(accessToken, workoutGroupId) {
  const response = http.post(
    `${BASE_URL}/api/sessions`,
    JSON.stringify({ workoutGroupId }),
    {
      headers: authHeaders(accessToken),
      tags: { endpoint: 'sessions-start' },
    },
  );

  check(response, {
    'start session status is 201': (r) => r.status === 201,
  });

  if (response.status !== 201) {
    throw new Error(`Unable to start session: ${response.status} ${response.body}`);
  }

  return response.json('id');
}

function addSetLog(accessToken, sessionId, exerciseId, setNumber) {
  const response = http.post(
    `${BASE_URL}/api/sessions/${sessionId}/set-logs`,
    JSON.stringify({
      exerciseId,
      setNumber,
      weight: '80.0',
      reps: 5,
    }),
    {
      headers: authHeaders(accessToken),
      tags: { endpoint: 'set-logs-create' },
    },
  );

  check(response, {
    'add set log status is 201': (r) => r.status === 201,
  });

  if (response.status !== 201) {
    throw new Error(`Unable to add set log: ${response.status} ${response.body}`);
  }
}

function completeSession(accessToken, sessionId) {
  const response = http.post(
    `${BASE_URL}/api/sessions/${sessionId}/complete`,
    JSON.stringify({ notes: 'Cache benchmark' }),
    {
      headers: authHeaders(accessToken),
      tags: { endpoint: 'sessions-complete' },
    },
  );

  check(response, {
    'complete session status is 200': (r) => r.status === 200,
  });

  if (response.status !== 200) {
    throw new Error(`Unable to complete session: ${response.status} ${response.body}`);
  }
}

function authHeaders(accessToken) {
  return {
    ...JSON_HEADERS,
    Authorization: `Bearer ${accessToken}`,
  };
}

function randomSuffix() {
  return Math.random().toString(16).slice(2, 10);
}
