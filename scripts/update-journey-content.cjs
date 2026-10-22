// Opt-in content update through the normal author/reviewer APIs. No schema or progress writes.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const base = process.env.DEMO_API_URL || 'http://127.0.0.1:8080';
const source = require('../src/main/resources/demo/integration-journey.json');
const apply = process.argv.includes('--apply');

async function request(route, token, method = 'GET', body) {
  const response = await fetch(base + route, {
    method, headers: {'Content-Type':'application/json', ...(token ? {Authorization:`Bearer ${token}`} : {})},
    ...(body === undefined ? {} : {body:JSON.stringify(body)}),
    signal: AbortSignal.timeout(30000),
  });
  const raw = await response.text();
  let data;
  try { data = raw ? JSON.parse(raw) : null; } catch { throw new Error(`${route}: invalid response (HTTP ${response.status})`); }
  if(!response.ok || (data && typeof data.code === 'number' && data.code !== 1000)) {
    const error = new Error(`${route}: HTTP ${response.status}, ${data?.message || 'request rejected'}`);
    error.status=response.status;
    error.apiCode=data?.error || data?.code;
    throw error;
  }
  return data?.result ?? data;
}
async function login(username, password) {
  const data = await request('/api/auth/token', null, 'POST', {username,password});
  assert.ok(data.token, 'Login failed');
  return data.token;
}
function select(data, keys) { return Object.fromEntries(keys.map(k => [k, data[k] ?? null])); }
const courseKeys = ['code','slug','titleEn','titleVi','descriptionEn','descriptionVi','level','thumbnailUrl','estimatedMinutes','learningOutcomes','isFree'];
const moduleKeys = ['titleEn','titleVi','descriptionEn','descriptionVi'];
const lessonKeys = ['code','slug','titleEn','titleVi','descriptionEn','descriptionVi','lessonType','estimatedMinutes','prerequisiteIds','learningObjectiveEn','learningObjectiveVi'];
const activityKeys = ['activityType','titleEn','titleVi','instructionEn','instructionVi','contentJson','isRequired','maxScore','status','quizId','grammarTopicIds'];
const questionKeys = ['questionType','promptEn','promptVi','explanationEn','explanationVi','difficulty','topic','imageUrl','audioUrl','status','correctAnswerJson','options'];
const destinationKeys = ['nameVi','nameEn','slug','region','shortDescriptionVi','shortDescriptionEn','descriptionVi','descriptionEn','imageUrl','status'];
function activityBody(a) { return {...select(a, activityKeys), grammarTopicIds:(a.grammarTopics || []).map(g=>g.id)}; }
function questionBody(q) {
  const version = q.versions.find(v=>Number(v.versionNumber)===Number(q.currentVersion));
  assert.ok(version, 'Current question version missing');
  return select({...q,correctAnswerJson:version.correctAnswerJson,options:version.options}, questionKeys);
}
function stable(value) {
  if (Array.isArray(value)) return value.map(stable);
  if (value && typeof value === 'object') return Object.fromEntries(Object.keys(value).sort().map(k=>[k,stable(value[k])]));
  return value;
}
function same(a,b) { return JSON.stringify(stable(a))===JSON.stringify(stable(b)); }

async function main() {
  const admin = await login(process.env.DEMO_ADMIN_USERNAME || 'admin',process.env.DEMO_ADMIN_PASSWORD || 'admin123');
  const manager = await login(process.env.DEMO_MANAGER_USERNAME || 'contentmanager',process.env.DEMO_MANAGER_PASSWORD || 'contentmanager123');
  const list = await request('/api/admin/courses?size=100',admin);
  const found = list.content.find(c=>c.code===source.course.code);
  assert.ok(found,'Run the integration demo seed first');
  assert.equal(found.status,'PUBLISHED','Only run against a published demo course');
  const course = await request(`/api/admin/courses/${found.id}`,manager);
  assert.equal(course.modules.length,source.stops.length);
  const plan = [];
  function add(route,before,after,role='manager') { if(!same(before,after)) plan.push({route,before,after,role}); }
  add(`/api/admin/courses/${course.id}`,select(course,courseKeys),select(source.course,courseKeys));
  const snapshot = {createdAt:new Date().toISOString(),base,revision:source.contentRevision,course,destinations:[],lessons:[],quizzes:[],questions:[]};
  for(const [i,stop] of source.stops.entries()) {
    const module = course.modules[i];
    assert.equal(module.lessons.length,stop.lessons.length);
    add(`/api/admin/modules/${module.id}`,select(module,moduleKeys),select(stop.module,moduleKeys));
    const destinations = await request('/api/admin/destinations',admin);
    const destination = destinations.find(d=>d.slug===stop.destination.slug);
    assert.ok(destination,'Linked destination missing');
    const linked = await request(`/api/admin/destinations/${destination.id}/content`,admin);
    assert.deepEqual(linked.lessons.map(l=>Number(l.id)).sort(),module.lessons.map(l=>Number(l.id)).sort());
    snapshot.destinations.push({destination,linked});
    add(`/api/admin/destinations/${destination.id}`,select(destination,destinationKeys),
      {...select(destination,destinationKeys),...select(stop.destination,['shortDescriptionVi','shortDescriptionEn','descriptionVi','descriptionEn'])},'admin');
    for(const [j,content] of stop.lessons.entries()) {
      const lesson = await request(`/api/admin/lessons/${module.lessons[j].id}/preview`,manager);
      snapshot.lessons.push(lesson);
      add(`/api/admin/lessons/${lesson.id}`,select(lesson,lessonKeys),{
        ...select(lesson,lessonKeys),titleVi:content.titleVi,titleEn:content.titleEn,
        descriptionVi:content.descriptionVi,descriptionEn:content.descriptionEn,estimatedMinutes:content.estimatedMinutes,
        learningObjectiveVi:content.objectiveVi,learningObjectiveEn:content.objectiveEn,
      });
      for(const type of ['TEXT','VOCABULARY','DIALOGUE']) {
        const activity = lesson.activities.find(a=>a.activityType===type && a.titleEn?.endsWith(' — Extended practice'))
          || lesson.activities.find(a=>a.activityType===type);
        assert.ok(activity,`Missing ${type} activity`);
        const before = activityBody(activity);
        const after = structuredClone(before);
        if(type==='TEXT') {
          // Keep the persisted Vietnamese heading; older runtimes only update the English activity heading.
          after.titleVi=before.titleVi;
          after.titleEn='Local culture and communication practice' + (before.titleEn?.endsWith(' — Extended practice')?' — Extended practice':'');
          after.contentJson={...before.contentJson,textVi:content.textVi,textEn:content.textEn};
        } else if(type==='DIALOGUE') {
          after.contentJson={...before.contentJson,lines:content.dialogue};
        } else {
          assert.equal(before.contentJson.items.length,content.words.length);
          after.contentJson.items=before.contentJson.items.map((word,k)=>{
            assert.equal(word.wordVi,content.words[k].wordVi,'Vocabulary identity must stay unchanged');
            assert.ok(linked.vocabulary.some(w=>Number(w.id)===Number(word.masterVocabularyId)));
            return {...word,...content.words[k]};
          });
        }
        add(`/api/admin/activities/${activity.id}`,before,after);
      }
      const quizActivity = lesson.activities.find(a=>a.activityType==='QUIZ');
      const quiz = await request(`/api/admin/quizzes/${quizActivity.quizId}`,manager);
      snapshot.quizzes.push(quiz);
      assert.equal(Number(quiz.courseId),Number(course.id));
      assert.equal(quiz.questions.length,content.questions.length);
      for(const [k,item] of quiz.questions.entries()) {
        const question = await request(`/api/admin/questions/${item.questionId}`,manager);
        snapshot.questions.push(question);
        add(`/api/admin/questions/${question.id}`,questionBody(question),select(content.questions[k],questionKeys));
      }
    }
  }
  // Existing final quiz shares the first question in each lesson: retain those links and its score policy.
  snapshot.quizzes.push(await request(`/api/admin/quizzes/${course.finalAssessmentQuizId}`,manager));
  console.log(JSON.stringify({courseId:course.id,revision:source.contentRevision,changes:plan.length,apply,routes:plan.map(p=>p.route)},null,2));
  if(!apply || !plan.length) return;
  const backupDir = path.resolve(process.env.DEMO_BACKUP_DIR || '.verification/journey-content');
  fs.mkdirSync(backupDir,{recursive:true});
  const backup = path.join(backupDir,`before-${Date.now()}.json`);
  fs.writeFileSync(backup,JSON.stringify({...snapshot,plan},null,2),{flag:'wx'});
  console.log(`Content backup: ${backup}`);
  const applied = [], created = [];
  let drafted = false;
  try {
    await request(`/api/admin/courses/${course.id}/status`,admin,'PATCH',{status:'DRAFT'});
    drafted = true;
    for(const operation of plan) {
      // Include the current operation in rollback even if its response is lost after a server commit.
      applied.push(operation);
      try {
        await request(operation.route,operation.role==='admin'?admin:manager,'PUT',operation.after);
      } catch(error) {
        if(error.status!==409 || !operation.route.startsWith('/api/admin/activities/')
          || !error.message.includes('Archive this activity')) throw error;
        // Existing progress makes content immutable. Add an optional extension; keep the required original intact.
        const original = snapshot.lessons.flatMap(l=>l.activities).find(a=>operation.route===`/api/admin/activities/${a.id}`);
        const body={...operation.after,isRequired:false,
          titleEn:operation.after.titleEn+' — Extended practice',titleVi:operation.after.titleVi+' — Mở rộng'};
        const added = await request(`/api/admin/lessons/${original.lessonId}/activities`,manager,'POST',body);
        created.push(`/api/admin/activities/${added.id}`);
        fs.writeFileSync(backup,JSON.stringify({...snapshot,plan,created},null,2));
        console.log(`Retained activity ${original.id}; optional extension ${added.id} added.`);
      }
    }
    const ready = await request(`/api/admin/courses/${course.id}/readiness`,manager);
    assert.ok(ready.ready,JSON.stringify(ready.issues));
    await request(`/api/admin/courses/${course.id}/submit`,manager,'POST');
    await request(`/api/admin/courses/${course.id}/review`,admin,'POST',{
      decision:'APPROVE',note:'Bổ sung văn hóa, du lịch, hội thoại và câu hỏi theo từng địa điểm; giữ nguyên liên kết và tiến độ.'
    });
    drafted = false;
    console.log(`Published updated course ${course.id}; original IDs retained; ${created.length} optional extensions added.`);
  } catch(error) {
    console.error(`Update failed: ${error.message}`);
    if(drafted) {
      try {
        await request(`/api/admin/courses/${course.id}/status`,admin,'PATCH',{status:'DRAFT'});
        for(const route of [...created].reverse()) await request(route,manager,'DELETE');
        for(const operation of [...applied].reverse())
          await request(operation.route,operation.role==='admin'?admin:manager,'PUT',operation.before);
        await request(`/api/admin/courses/${course.id}/submit`,manager,'POST');
        await request(`/api/admin/courses/${course.id}/review`,admin,'POST',{decision:'APPROVE',note:'Khôi phục nội dung từ bản sao sau khi cập nhật không thành công.'});
        console.error('Previous content restored and published. Question version history was retained.');
      } catch(rollback) { console.error(`Rollback needs attention: ${rollback.message}. Backup: ${backup}`); }
    }
    throw error;
  }
}
main().catch(error=>{console.error(error.message);process.exitCode=1;});
