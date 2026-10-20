const assert = require('node:assert/strict');
const base = process.env.DEMO_API_URL || 'http://127.0.0.1:8080';
const frontend = process.env.DEMO_FRONTEND_URL || 'http://localhost:5173';
const code = 'DEMO-VIETNAM-LINKS';

async function request(path, token, body) {
  const response = await fetch(base + path, {
    method: body ? 'POST' : 'GET',
    headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}), 'Content-Type': 'application/json' },
    ...(body ? { body: JSON.stringify(body) } : {}),
  });
  const data = await response.json();
  assert.ok(response.ok && (typeof data.code !== 'number' || data.code === 1000), `${path}: HTTP ${response.status}, ${data.message || 'request rejected'}`);
  return data.result ?? data;
}

async function login(username, password) {
  const result = await request('/api/auth/token', null, { username, password });
  assert.ok(result.token, `Login failed for ${username}`);
  return result.token;
}

async function main() {
  const admin = await login(process.env.DEMO_ADMIN_USERNAME || 'admin', process.env.DEMO_ADMIN_PASSWORD || 'admin123');
  const manager = await login(process.env.DEMO_MANAGER_USERNAME || 'contentmanager', process.env.DEMO_MANAGER_PASSWORD || 'contentmanager123');
  const learner = await login(process.env.DEMO_LEARNER_USERNAME || 'learner', process.env.DEMO_LEARNER_PASSWORD || 'learner123');
  const list = await request('/api/admin/courses?size=100', admin);
  const course = list.content.find(c => c.code === code);
  assert.ok(course, 'Integration demo course not found');
  assert.equal(course.status, 'PUBLISHED');
  const managerCourse = await request(`/api/admin/courses/${course.id}`, manager);
  assert.equal(managerCourse.code, code);
  const config = await request('/api/admin/journey', admin);
  const stops = config.stops.filter(s => s.destination.slug.startsWith('demo-links-'));
  assert.equal(stops.length, 3);
  const state = await request('/api/me/journey', learner);
  const manifest = { verified: true, code, courseId: course.id, courseUrl: `${frontend}/courses/${course.id}`, stops: [] };
  let totalLessons = 0, totalWords = 0;
  for (const stop of stops) {
    const destination = stop.destination;
    const content = await request(`/api/admin/destinations/${destination.id}/content`, admin);
    assert.equal(content.lessons.length, 2);
    assert.equal(content.vocabulary.length, 6);
    const vocabularyIds = new Set(content.vocabulary.map(w => w.id));
    const lessons = [];
    for (const lesson of content.lessons) {
      assert.equal(lesson.courseId, course.id);
      const preview = await request(`/api/admin/lessons/${lesson.id}/preview`, manager);
      const items = preview.activities.find(a => a.activityType === 'VOCABULARY').contentJson.items;
      assert.equal(items.length, 3);
      for (const item of items) {
        assert.ok(vocabularyIds.has(item.masterVocabularyId), `Word is not linked to its lesson's destination: ${item.wordVi}`);
        const word = await request(`/api/vocabulary/${item.masterVocabularyId}`, learner);
        assert.equal(word.word, item.wordVi);
      }
      lessons.push({ id: lesson.id, title: lesson.titleVi || lesson.title, url: `${frontend}/learn/${course.id}/lesson/${lesson.id}`, vocabularyIds: items.map(w => w.masterVocabularyId) });
    }
    totalLessons += lessons.length;
    totalWords += vocabularyIds.size;
    manifest.stops.push({ id: destination.id, name: destination.nameVi, status: state.stops.find(s => s.destination.id === destination.id)?.status,
      url: `${frontend}/destinations/${destination.id}`, lessons, vocabulary: content.vocabulary.map(w => ({ id: w.id, word: w.word, url: `${frontend}/vocabulary/${w.id}` })) });
  }
  manifest.counts = { stops: stops.length, lessons: totalLessons, vocabulary: totalWords };
  console.log(JSON.stringify(manifest, null, 2));
}
main().catch(error => { console.error(error.message); process.exitCode = 1; });
