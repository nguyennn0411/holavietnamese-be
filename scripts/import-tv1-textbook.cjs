// Import only this new textbook course through normal author/reviewer APIs.
// node scripts/import-tv1-textbook.cjs --apply [--publish]
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const source = require('../src/main/resources/learning/tv1-tap1-course.json');
const assets = require('../src/main/resources/learning/tv1-tap1-assets.json');
const media = require('../src/main/resources/learning/tv1-tap1-media.json');
const base = process.env.DEMO_API_URL || 'http://127.0.0.1:8080';
const apply = process.argv.includes('--apply'), publish = process.argv.includes('--publish');
const output = path.resolve(process.env.TV1_OUTPUT_DIR || '.verification/tv1-tap1');
const code = source.course.code;
function stable(v) { return Array.isArray(v)?v.map(stable):v&&typeof v==='object'?Object.fromEntries(Object.keys(v).sort().map(k=>[k,stable(v[k])])):v; }
function same(a,b) { return JSON.stringify(stable(a))===JSON.stringify(stable(b)); }
function resolveMedia(value) {
  if(Array.isArray(value)) return value.map(resolveMedia);
  if(value&&typeof value==='object') return Object.fromEntries(Object.entries(value).map(([k,v])=>[k,resolveMedia(v)]));
  if(typeof value==='string'&&value.startsWith('/media/tv1-tap1/')) { assert.ok(media.urls[value],`Unuploaded image: ${value}`); return media.urls[value]; }
  return value;
}
async function api(route, token, method='GET', body) {
  const r=await fetch(base+route,{method,headers:{'Content-Type':'application/json',...(token?{Authorization:`Bearer ${token}`}:{})},
    ...(body===undefined?{}:{body:JSON.stringify(body)}),signal:AbortSignal.timeout(45000)});
  const raw=await r.text();let data;
  try {data=raw?JSON.parse(raw):null;}catch {throw Error(`${route}: invalid HTTP ${r.status} response`);}
  assert.ok(r.ok&&(!data||typeof data.code!=='number'||data.code===1000),`${route}: HTTP ${r.status}: ${data?.message||'rejected'}`);
  return data?.result??data;
}
async function login(username,password) { const r=await api('/api/auth/token',null,'POST',{username,password});assert.ok(r.token,'Login failed');return r.token; }
async function list(route,token) {
  const result=[];
  for(let page=0;;page++) {const r=await api(`${route}${route.includes('?')?'&':'?'}size=100&page=${page}`,token);result.push(...r.content);if(page+1>=r.totalPages||!r.content.length) return result;}
}
async function main() {
  assert.equal(source.source.sha256,assets.sourceSha256);assert.equal(media.sourceSha256,assets.sourceSha256);
  const imageUris=assets.pages.flatMap(p=>[p.image,...p.illustrations.map(c=>c.image)]);
  assert.equal(imageUris.length,557);assert.ok(imageUris.every(uri=>media.urls[uri]?.startsWith('https://res.cloudinary.com/')));
  const lessons=source.modules.flatMap(m=>m.lessons);
  assert.equal(lessons.length,83);
  console.log(JSON.stringify({code,modules:source.modules.length,lessons:lessons.length,images:imageUris.length,questions:lessons.reduce((n,l)=>n+l.questions.length,0),apply,publish}));
  if(!apply) return;
  const manager=await login(process.env.DEMO_MANAGER_USERNAME||'contentmanager',process.env.DEMO_MANAGER_PASSWORD||'contentmanager123');
  const admin=await login(process.env.DEMO_ADMIN_USERNAME||'admin',process.env.DEMO_ADMIN_PASSWORD||'admin123');
  // Course search covers title/description, so resolve its stable code from the paginated list.
  const existing=(await list('/api/admin/courses',manager)).find(c=>c.code===code);
  let course=existing?await api(`/api/admin/courses/${existing.id}`,manager):await api('/api/admin/courses',manager,'POST',resolveMedia(source.course));
  assert.equal(course.slug,source.course.slug);
  fs.mkdirSync(output,{recursive:true});
  fs.writeFileSync(path.join(output,'course.json'),JSON.stringify({courseId:course.id,code,sourceSha256:assets.sourceSha256},null,2));
  if(course.status==='PUBLISHED') {console.log(`Existing published course ${course.id} retained. Run verify-tv1-textbook.cjs to audit it.`);return;}
  assert.ok(['DRAFT','REJECTED'].includes(course.status),'Do not modify a course awaiting review');
  const knownQuizzes=await list('/api/admin/quizzes?q=TV1-T1-',manager);
  const manifest={sourceSha256:assets.sourceSha256,courseId:course.id,code,courseUrl:`http://localhost:5173/courses/${course.id}`,modules:[]};
  const questionIds=new Map();
  async function ensureActivity(lessonId,activity,known) {
    const payload=resolveMedia(activity);
    const previous=known.find(a=>a.activityType===payload.activityType&&a.title===payload.titleEn);
    if(previous) {
      assert.ok(same(previous.contentJson,payload.contentJson),`Existing activity content differs: ${lessonId} / ${payload.titleEn}`);
      assert.equal(previous.isRequired,payload.isRequired);
      return previous.id;
    }
    const result=await api(`/api/admin/lessons/${lessonId}/activities`,manager,'POST',payload);
    known.push({...payload,id:result.id,title:payload.titleEn});return result.id;
  }
  async function quiz(lessonId,titleEn,titleVi,last=false) {
    let q=knownQuizzes.find(q=>q.title===titleEn&&Number(q.courseId)===Number(course.id));
    if(!q) {q=await api('/api/admin/quizzes',manager,'POST',{titleEn,titleVi,descriptionVi:'Đạt ít nhất 70%. Có thể làm lại; giữ đúng chữ và dấu khi nhập đáp án.',descriptionEn:'Score at least 70%. Retries are allowed. Preserve Vietnamese letter and tone marks.',quizType:last?'COURSE':'LESSON',courseId:course.id,lessonId,passingScore:70,timeLimitMinutes:null,maxAttempts:null,randomizeQuestions:false});knownQuizzes.push(q);}
    return api(`/api/admin/quizzes/${q.id}`,manager);
  }
  for(const [moduleIndex,moduleData] of source.modules.entries()) {
    let module=course.modules.find(m=>m.title===moduleData.metadata.titleEn);
    if(!module) {const added=await api(`/api/admin/courses/${course.id}/modules`,manager,'POST',moduleData.metadata);module={id:added.id,title:moduleData.metadata.titleEn,lessons:[]};course.modules.push(module);}
    const moduleResult={id:module.id,title:moduleData.metadata.titleVi,lessons:[]};
    for(const lessonData of moduleData.lessons) {
      const n=String(lessonData.number).padStart(3,'0'),lessonCode=`TV1-T1-L${n}`;
      let row=module.lessons.find(l=>l.code===lessonCode);
      if(!row) {
        const added=await api(`/api/admin/modules/${module.id}/lessons`,manager,'POST',{
          code:lessonCode,slug:`tieng-viet-1-tap-1-bai-${n}`,titleEn:lessonData.titleEn,titleVi:lessonData.titleVi,
          descriptionEn:lessonData.descriptionEn,descriptionVi:lessonData.descriptionVi,lessonType:lessonData.lessonType,
          estimatedMinutes:lessonData.estimatedMinutes,prerequisiteIds:[],learningObjectiveEn:lessonData.objectiveEn,learningObjectiveVi:lessonData.objectiveVi});
        row={id:added.id,code:lessonCode};module.lessons.push(row);
      }
      const preview=await api(`/api/admin/lessons/${row.id}/preview`,manager);
      const known=preview.activities;
      if(process.argv.includes('--compact-images')) {
        // Reorganise only this import's optional picture activities while the new course is still a draft.
        const cropUrls=new Set(assets.pages.flatMap(p=>p.illustrations.map(c=>media.urls[c.image])));
        for(const old of [...known]) if(old.title?.startsWith('Picture/letter crop from page ') && old.activityType==='IMAGE'
            && old.isRequired===false && cropUrls.has(old.contentJson.imageUrl)) {
          await api(`/api/admin/activities/${old.id}`,manager,'DELETE');
          known.splice(known.findIndex(a=>a.id===old.id),1);
        }
      }
      for(const activity of lessonData.activities) await ensureActivity(row.id,activity,known);
      const assessment=await quiz(row.id,`${lessonCode} — Lesson quiz`,`${lessonData.titleVi} — Kiểm tra cuối bài`);
      const bank=await list(`/api/admin/questions?topic=TV1_T1_L${n}`,manager);
      const ids=[];
      for(const authored of lessonData.questions) {
        let question=bank.find(q=>q.prompt===authored.promptEn&&q.questionType===authored.questionType);
        if(!question) {question=await api('/api/admin/questions',manager,'POST',resolveMedia(authored));bank.push(question);}
        else {
          const detail=await api(`/api/admin/questions/${question.id}`,manager),version=detail.versions.find(v=>Number(v.versionNumber)===Number(detail.currentVersion));
          assert.ok(same(version.correctAnswerJson,authored.correctAnswerJson)&&same(version.options,authored.options),`Question differs: ${question.id}`);
        }
        if(!assessment.questions.some(q=>Number(q.questionId)===Number(question.id)))
          await api(`/api/admin/quizzes/${assessment.id}/questions`,manager,'POST',{questionId:question.id,points:1});
        ids.push(question.id);
      }
      questionIds.set(lessonData.number,ids);
      if(assessment.status!=='PUBLISHED') await api(`/api/admin/quizzes/${assessment.id}/status`,manager,'PATCH',{status:'PUBLISHED'});
      await ensureActivity(row.id,{activityType:'QUIZ',titleVi:'Kiểm tra cuối bài',titleEn:'Lesson quiz',instructionVi:'Hoàn thành quiz để kiểm tra bài học.',instructionEn:'Complete the quiz to check your learning.',contentJson:{},isRequired:true,maxScore:0,status:'PUBLISHED',quizId:assessment.id,grammarTopicIds:[]},known);
      const order=lessonData.activities.map(a=>known.find(k=>k.activityType===a.activityType&&k.title===a.titleEn).id);
      order.push(known.find(a=>a.activityType==='QUIZ'&&Number(a.quizId)===Number(assessment.id)).id);
      order.push(...known.filter(a=>!order.includes(a.id)).map(a=>a.id));
      if(!same(order,known.map(a=>a.id))) await api(`/api/admin/lessons/${row.id}/activities/reorder`,manager,'PUT',{ids:order});
      if(preview.publicationStatus!=='PUBLISHED') await api(`/api/admin/lessons/${row.id}/status`,manager,'PATCH',{status:'PUBLISHED'});
      moduleResult.lessons.push({id:row.id,number:lessonData.number,title:lessonData.titleVi,sourcePdfPages:lessonData.sourcePdfPages,quizId:assessment.id,questionIds:ids,activityCount:known.length});
      if(lessonData.number%5===0||lessonData.number===83) console.log(`${lessonData.number}/83 lessons saved`);
      fs.writeFileSync(path.join(output,'manifest.json'),JSON.stringify({...manifest,modules:[...manifest.modules,moduleResult]},null,2));
    }
    manifest.modules.push(moduleResult);
  }
  const finalLesson=manifest.modules.at(-1).lessons.at(-1);
  const finalQuiz=await quiz(finalLesson.id,'TV1-T1-FINAL — Full-volume assessment','Đánh giá cuối tập 1',true);
  const finalIds=[];
  for(const module of source.modules) {
    finalIds.push(questionIds.get(module.lessons[0].number)[0]);
    finalIds.push(questionIds.get(module.lessons.at(-1).number).at(-1));
  }
  assert.equal(new Set(finalIds).size,24);
  for(const id of finalIds) if(!finalQuiz.questions.some(q=>Number(q.questionId)===Number(id)))
    await api(`/api/admin/quizzes/${finalQuiz.id}/questions`,manager,'POST',{questionId:id,points:1});
  if(finalQuiz.status!=='PUBLISHED') await api(`/api/admin/quizzes/${finalQuiz.id}/status`,manager,'PATCH',{status:'PUBLISHED'});
  const last=await api(`/api/admin/lessons/${finalLesson.id}/preview`,manager);
  await ensureActivity(finalLesson.id,{activityType:'QUIZ',titleVi:'Kiểm tra cuối tập 1',titleEn:'Final volume assessment',instructionVi:'Ôn tập đủ 12 học phần trước khi làm.',instructionEn:'Review all 12 modules first.',contentJson:{},isRequired:true,maxScore:0,status:'PUBLISHED',quizId:finalQuiz.id,grammarTopicIds:[]},last.activities);
  await api(`/api/admin/courses/${course.id}/final-assessment`,manager,'PATCH',{quizId:finalQuiz.id});
  const ready=await api(`/api/admin/courses/${course.id}/readiness`,manager);
  assert.ok(ready.ready,JSON.stringify(ready.issues));
  const saved=await api(`/api/admin/courses/${course.id}`,manager);
  assert.equal(saved.modules.length,12);assert.equal(saved.modules.flatMap(m=>m.lessons).length,83);
  manifest.finalQuizId=finalQuiz.id;manifest.finalQuestionIds=finalIds;manifest.counts={modules:12,lessons:83,images:557,questions:409,quizzes:84};manifest.status=saved.status;
  if(publish) {
    await api(`/api/admin/courses/${course.id}/submit`,manager,'POST');
    await api(`/api/admin/courses/${course.id}/review`,admin,'POST',{decision:'APPROVE',note:'Đối chiếu đủ 83 bài, 173 trang PDF, 384 vùng hình; có bài đọc, thẻ từ, luyện nói và quiz. Nguồn PDF do người dùng cung cấp; người dùng đã cho phép tải ảnh lên Cloudinary.'});
    manifest.status='PUBLISHED';
  }
  fs.writeFileSync(path.join(output,'manifest.json'),JSON.stringify(manifest,null,2));
  console.log(JSON.stringify({courseId:course.id,status:manifest.status,ready:ready.ready,counts:manifest.counts,manifest:path.join(output,'manifest.json')}));
}
main().catch(error=>{console.error(error.message);console.error('Existing records were retained. Rerun --apply to resume the new course safely.');process.exitCode=1;});
