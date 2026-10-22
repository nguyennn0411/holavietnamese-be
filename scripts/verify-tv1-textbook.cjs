// Read-only audit: no enrollment, progress, quiz attempts or content mutations.
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const source=require('../src/main/resources/learning/tv1-tap1-course.json');
const assets=require('../src/main/resources/learning/tv1-tap1-assets.json');
const media=require('../src/main/resources/learning/tv1-tap1-media.json');
const base=process.env.DEMO_API_URL||'http://127.0.0.1:8080';
const output=path.resolve(process.env.TV1_OUTPUT_DIR||'.verification/tv1-tap1');
const stable=v=>Array.isArray(v)?v.map(stable):v&&typeof v==='object'?Object.fromEntries(Object.keys(v).sort().map(k=>[k,stable(v[k])])):v;
const equivalent=(a,b)=>assert.deepEqual(stable(a),stable(b));
async function api(route,token,body) {
  const r=await fetch(base+route,{method:body?'POST':'GET',headers:{'Content-Type':'application/json',...(token?{Authorization:`Bearer ${token}`}:{})},...(body?{body:JSON.stringify(body)}:{}),signal:AbortSignal.timeout(45000)});
  const d=await r.json();assert.ok(r.ok&&(!d.code||typeof d.code!=='number'||d.code===1000),`${route}: ${r.status} ${d.message||''}`);return d.result??d;
}
async function login(username,password) {const d=await api('/api/auth/token',null,{username,password});assert.ok(d.token);return d.token;}
function resolve(v) {return Array.isArray(v)?v.map(resolve):v&&typeof v==='object'?Object.fromEntries(Object.entries(v).map(([k,x])=>[k,resolve(x)])):media.urls[v]||v;}
async function main() {
  const token=await login(process.env.DEMO_MANAGER_USERNAME||'contentmanager',process.env.DEMO_MANAGER_PASSWORD||'contentmanager123');
  const courses=[];
  for(let page=0;;page++){const r=await api(`/api/admin/courses?size=100&page=${page}`,token);courses.push(...r.content);if(page+1>=r.totalPages)break;}
  const row=courses.find(c=>c.code===source.course.code);assert.ok(row,'Textbook course missing');
  const course=await api(`/api/admin/courses/${row.id}`,token);
  assert.ok(['DRAFT','PUBLISHED'].includes(course.status));assert.equal(course.modules.length,12);
  assert.equal(course.thumbnailUrl,media.urls[source.course.thumbnailUrl]);
  const sourcePages=new Set(),images=new Set([course.thumbnailUrl]),questionIds=new Set();
  const lessons=[],quizIds=new Set();let activityCount=0,readings=0,speaking=0;
  for(const [mi,module] of course.modules.entries()) {
    const authoredModule=source.modules[mi];assert.equal(module.lessons.length,authoredModule.lessons.length);
    for(const [li,lesson] of module.lessons.entries()) {
      const authored=authoredModule.lessons[li];
      const saved=await api(`/api/admin/lessons/${lesson.id}/preview`,token);
      assert.equal(saved.code,`TV1-T1-L${String(authored.number).padStart(3,'0')}`);assert.equal(saved.titleVi,authored.titleVi);assert.equal(saved.publicationStatus,'PUBLISHED');
      assert.equal(saved.activities.length,authored.activities.length+1+(authored.number===83?1:0));
      assert.ok(!saved.activities.some(a=>a.title?.startsWith('Picture/letter crop from page ')),'Legacy crop activities should have been grouped');
      activityCount+=saved.activities.length;
      for(const expected of authored.activities) {
        const activity=saved.activities.find(a=>a.activityType===expected.activityType&&a.title===expected.titleEn);
        assert.ok(activity,`Missing ${saved.id}: ${expected.titleEn}`);assert.equal(activity.status,'PUBLISHED');assert.equal(activity.isRequired,expected.isRequired);
        equivalent(activity.contentJson,resolve(expected.contentJson));
        if(activity.contentJson.imageUrl) images.add(activity.contentJson.imageUrl);
        for(const illustration of activity.contentJson.illustrations||[])images.add(illustration.imageUrl);
        if(activity.contentJson.imageUrl?.includes('/pages/'))sourcePages.add(activity.contentJson.sourcePdfPage);
        if(activity.title==='Application reading from the textbook')readings++;
        if(activity.title?.startsWith('Speaking:')||activity.title?.startsWith('Storytelling:'))speaking++;
      }
      const attached=saved.activities.find(a=>a.activityType==='QUIZ'&&a.title==='Lesson quiz');assert.ok(attached);
      const quiz=await api(`/api/admin/quizzes/${attached.quizId}`,token);quizIds.add(quiz.id);
      assert.equal(quiz.status,'PUBLISHED');assert.equal(Number(quiz.passingScore),70);assert.equal(quiz.questions.length,authored.questions.length);
      for(const [qi,item] of quiz.questions.entries()) {
        const q=await api(`/api/admin/questions/${item.questionId}`,token),expected=authored.questions[qi];
        assert.equal(q.promptVi,expected.promptVi);assert.equal(q.promptEn,expected.promptEn);
        const version=q.versions.find(v=>Number(v.versionNumber)===Number(q.currentVersion));assert.ok(version);
        equivalent(version.correctAnswerJson,expected.correctAnswerJson);equivalent(version.options,expected.options);
        questionIds.add(q.id);
      }
      lessons.push({id:saved.id,number:authored.number,title:saved.titleVi,quizId:quiz.id,activityCount:saved.activities.length,url:`http://localhost:5173/learn/${course.id}/lesson/${saved.id}`});
      if(authored.number%10===0)console.log(`${authored.number}/83 lessons audited`);
    }
  }
  assert.equal(lessons.length,83);assert.equal(sourcePages.size,173);assert.ok(assets.pages.every(p=>sourcePages.has(p.pdfPage)));
  assert.equal(images.size,557);assert.equal(questionIds.size,409);assert.equal(readings,81);assert.equal(speaking,83);
  const final=await api(`/api/admin/quizzes/${course.finalAssessmentQuizId}`,token);quizIds.add(final.id);
  assert.equal(final.status,'PUBLISHED');assert.equal(final.quizType,'COURSE');assert.equal(final.questions.length,24);assert.equal(quizIds.size,84);
  assert.ok(final.questions.every(q=>questionIds.has(q.questionId)));
  const ready=await api(`/api/admin/courses/${course.id}/readiness`,token);assert.ok(ready.ready,JSON.stringify(ready.issues));
  if(course.status==='PUBLISHED') {const visible=await api(`/api/courses/${course.id}`,null);assert.equal(visible.id,course.id);}
  if(!process.argv.includes('--skip-image-head')) {
    const urls=[...images];let next=0,checked=0;
    await Promise.all(Array.from({length:6},async()=>{while(next<urls.length){const url=urls[next++];const r=await fetch(url,{method:'HEAD',signal:AbortSignal.timeout(30000)});assert.ok(r.ok&&r.headers.get('content-type')?.startsWith('image/'),`Unavailable image: HTTP ${r.status}`);checked++;if(checked%100===0)console.log(`${checked}/557 remote images checked`);}}));
  }
  const report={verified:true,courseId:course.id,status:course.status,sourceSha256:assets.sourceSha256,counts:{modules:12,lessons:83,sourcePages:173,images:557,visualCrops:384,applicationReadings:readings,speakingTopics:speaking,activities:activityCount,questions:409,quizzes:84,finalQuestions:24},lessons};
  fs.mkdirSync(output,{recursive:true});fs.writeFileSync(path.join(output,'verification.json'),JSON.stringify(report,null,2));
  console.log(JSON.stringify({...report,lessons:undefined},null,2));
}
main().catch(error=>{console.error(error.message);process.exitCode=1;});
