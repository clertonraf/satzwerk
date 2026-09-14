import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution';
import { Counter, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8085';
const JSON_HEADERS = { 'Content-Type': 'application/json' };
const READER_VUS = Number(__ENV.READER_VUS || 15);
const WRITER_VUS = Number(__ENV.WRITER_VUS || 5);
const DURATION = __ENV.DURATION || '45s';

const readerExerciseListDuration = new Trend('reader_exercise_list_duration', true);
const readerHeatmapDuration = new Trend('reader_heatmap_duration', true);
const readerStreakDuration = new Trend('reader_streak_duration', true);
const readerTotalDuration = new Trend('reader_total_duration', true);
const readerExerciseListRequests = new Counter('reader_exercise_list_requests');
const readerHeatmapRequests = new Counter('reader_heatmap_requests');
const readerStreakRequests = new Counter('reader_streak_requests');

export const options = {
  setupTimeout: '120s',
  scenarios: {
    readers: {
      executor: 'constant-vus',
      exec: 'readers',
      vus: READER_VUS,
      duration: DURATION,
    },
    writers: {
      executor: 'constant-vus',
      exec: 'writers',
      vus: WRITER_VUS,
      duration: DURATION,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
  },
};

export function setup() {
  const readerIdentity = uniqueIdentity('reader-seed', 0);
  const accessToken = register(readerIdentity).json('accessToken');
  const exerciseId = createExercise(accessToken, `Reader Bench ${randomSuffix()}`);
  const planId = createPlan(accessToken, `Reader Plan ${randomSuffix()}`);
  activatePlan(accessToken, planId);
  const workoutGroupId = createGroup(accessToken, planId, `Reader Group ${randomSuffix()}`);
  addWorkoutExercise(accessToken, planId, workoutGroupId, exerciseId);
  const sessionId = startSession(accessToken, workoutGroupId);
  addSetLog(accessToken, sessionId, exerciseId, 1);

  return {
    accessToken,
    today: new Date().toISOString().slice(0, 10),
  };
}

export function readers(setupData) {
  const headers = authHeaders(setupData.accessToken);

  const exerciseList = http.get(`${BASE_URL}/api/exercises`, {
    headers,
    tags: { endpoint: 'exercise-list', role: 'reader' },
  });
  readerExerciseListDuration.add(exerciseList.timings.duration);
  readerTotalDuration.add(exerciseList.timings.duration);
  readerExerciseListRequests.add(1);
  check(exerciseList, {
    'reader exercise list status is 200': (r) => r.status === 200,
    'reader exercise list returns array': (r) => Array.isArray(r.json()),
  });

  const heatmap = http.get(
    `${BASE_URL}/api/analytics/heatmap?from=${setupData.today}&to=${setupData.today}`,
    {
      headers,
      tags: { endpoint: 'analytics-heatmap', role: 'reader' },
    },
  );
  readerHeatmapDuration.add(heatmap.timings.duration);
  readerTotalDuration.add(heatmap.timings.duration);
  readerHeatmapRequests.add(1);
  check(heatmap, {
    'reader heatmap status is 200': (r) => r.status === 200,
    'reader heatmap returns array': (r) => Array.isArray(r.json()),
  });

  const streak = http.get(`${BASE_URL}/api/analytics/streak`, {
    headers,
    tags: { endpoint: 'analytics-streak', role: 'reader' },
  });
  readerStreakDuration.add(streak.timings.duration);
  readerTotalDuration.add(streak.timings.duration);
  readerStreakRequests.add(1);
  check(streak, {
    'reader streak status is 200': (r) => r.status === 200,
    'reader streak returns streak values': (r) => Number.isInteger(r.json('currentStreak')),
  });

  sleep(0.2);
}

export function writers() {
  const identity = uniqueIdentity('writer', exec.scenario.iterationInTest);
  const registerResponse = register(identity);
  const registered = check(registerResponse, {
    'writer register status is 201': (r) => r.status === 201,
    'writer register returns access token': (r) => Boolean(r.json('accessToken')),
  });
  if (!registered) {
    return;
  }

  const accessToken = registerResponse.json('accessToken');
  const exerciseId = createExercise(accessToken, `Writer Bench ${randomSuffix()}`);
  const planId = createPlan(accessToken, `Writer Plan ${randomSuffix()}`);
  activatePlan(accessToken, planId);
  const workoutGroupId = createGroup(accessToken, planId, `Writer Group ${randomSuffix()}`);
  addWorkoutExercise(accessToken, planId, workoutGroupId, exerciseId);
  const sessionId = startSession(accessToken, workoutGroupId);
  addSetLog(accessToken, sessionId, exerciseId, 1);

  sleep(0.2);
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
      tags: { endpoint: 'auth-register', role: identity.role },
    },
  );
}

function createExercise(accessToken, name) {
  const response = http.post(
    `${BASE_URL}/api/exercises`,
    JSON.stringify({
      name,
      muscleGroup: 'CHEST',
      description: 'Cache contention benchmark',
    }),
    {
      headers: authHeaders(accessToken),
      tags: { endpoint: 'exercise-create', role: 'writer' },
    },
  );

  check(response, {
    'create exercise status is 201': (r) => r.status === 201,
    'create exercise returns id': (r) => Boolean(r.json('id')),
  });

  return response.json('id');
}

function createPlan(accessToken, name) {
  const response = http.post(
    `${BASE_URL}/api/plans`,
    JSON.stringify({ name }),
    {
      headers: authHeaders(accessToken),
      tags: { endpoint: 'plan-create', role: 'writer' },
    },
  );

  check(response, {
    'create plan status is 201': (r) => r.status === 201,
    'create plan returns id': (r) => Boolean(r.json('id')),
  });

  return response.json('id');
}

function activatePlan(accessToken, planId) {
  const response = http.post(`${BASE_URL}/api/plans/${planId}/activate`, null, {
    headers: authHeaders(accessToken),
    tags: { endpoint: 'plan-activate', role: 'writer' },
  });

  check(response, {
    'activate plan status is 204': (r) => r.status === 204,
  });
}

function createGroup(accessToken, planId, title) {
  const response = http.post(
    `${BASE_URL}/api/plans/${planId}/groups`,
    JSON.stringify({ title }),
    {
      headers: authHeaders(accessToken),
      tags: { endpoint: 'group-create', role: 'writer' },
    },
  );

  check(response, {
    'create group status is 201': (r) => r.status === 201,
    'create group returns id': (r) => Boolean(r.json('id')),
  });

  return response.json('id');
}

function addWorkoutExercise(accessToken, planId, workoutGroupId, exerciseId) {
  const response = http.post(
    `${BASE_URL}/api/plans/${planId}/groups/${workoutGroupId}/exercises`,
    JSON.stringify({
      exerciseId,
      sets: 4,
      reps: 8,
    }),
    {
      headers: authHeaders(accessToken),
      tags: { endpoint: 'plan-exercise-create', role: 'writer' },
    },
  );

  check(response, {
    'create workout exercise status is 201': (r) => r.status === 201,
  });
}

function startSession(accessToken, workoutGroupId) {
  const response = http.post(
    `${BASE_URL}/api/sessions`,
    JSON.stringify({ workoutGroupId }),
    {
      headers: authHeaders(accessToken),
      tags: { endpoint: 'session-create', role: 'writer' },
    },
  );

  check(response, {
    'start session status is 201': (r) => r.status === 201,
    'start session returns id': (r) => Boolean(r.json('id')),
  });

  return response.json('id');
}

function addSetLog(accessToken, sessionId, exerciseId, setNumber) {
  const response = http.post(
    `${BASE_URL}/api/sessions/${sessionId}/set-logs`,
    JSON.stringify({
      exerciseId,
      setNumber,
      weight: 80,
      reps: 5,
    }),
    {
      headers: authHeaders(accessToken),
      tags: { endpoint: 'set-log-create', role: 'writer' },
    },
  );

  check(response, {
    'add set log status is 201': (r) => r.status === 201,
  });
}

function uniqueIdentity(prefix, iteration) {
  const vu = exec.vu.idInTest;
  const timestamp = Date.now();
  const suffix = randomSuffix();

  return {
    email: `${prefix}-${vu}-${iteration}-${timestamp}-${suffix}@example.test`,
    displayName: `${prefix} ${vu}-${iteration}`,
    role: prefix.startsWith('reader') ? 'reader' : 'writer',
  };
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
