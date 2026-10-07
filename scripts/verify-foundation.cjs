// Real HTTP verification. Creates a uniquely named local QA learner and saves only its own credentials
// under ignored .verification, allowing restart verification without touching existing learners.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict'),crypto=require('node:crypto');
const base=process.env.FOUNDATION_API_URL||'http://127.0.0.1:18080/api';
const root=path.resolve(__dirname,'..'),dir=path.join(root,'.verification');
const source=JSON.parse(fs.readFileSync(path.join(root,'src/main/resources/learning/foundation-course.json'),'utf8'));
const authored=source.modules.flatMap(m=>m.lessons);
const accountFile=path.join(dir,process.argv.includes('--browser-user')?'foundation-browser-account.json':'foundation-runtime-account.json');
let token;
async function api(url,method='GET',body,status=200){
 const r=await fetch(base+url,{method,headers:{'Content-Type':'application/json',...(token?{Authorization:'Bearer '+token}:{})},...(body===undefined?{}:{body:JSON.stringify(body)})});
 const raw=await r.text(); let data;try{data=JSON.parse(raw)}catch{data=raw}
 assert.equal(r.status,status,`${method} ${url}: ${raw.slice(0,220)}`);return data;
}
function answer(key){return key.acceptedAnswers?{text:key.acceptedAnswers[0]}:key;}
async function main(){
 fs.mkdirSync(dir,{recursive:true});
 let account;
 if(process.argv.includes('--restart-check'))account=JSON.parse(fs.readFileSync(accountFile,'utf8'));
 else {
  account={username:'foundationqa'+Date.now(),password:'Fqa!'+crypto.randomBytes(15).toString('hex')};
  await api('/auth/register','POST',{...account,email:account.username+'@example.test',fullName:'Foundation Curriculum QA',nativeLanguage:'en',targetLevel:'A1'});
  fs.writeFileSync(accountFile,JSON.stringify(account));
 }
 token=(await api('/auth/token','POST',account)).result.token;
 const course=(await api('/courses?page=0&size=100')).content.find(c=>c.code===source.course.code);
 assert(course,'Persisted foundation course missing');
 if(process.argv.includes('--restart-check')){
  const progress=await api('/courses/'+course.id+'/progress');
  assert.equal(progress.completedLessons,48);assert.equal(progress.status,'COMPLETED');
  console.log(JSON.stringify({restartPersistence:true,courseId:course.id,completedLessons:48}));return;
 }
 await api('/courses/'+course.id+'/enroll','POST',undefined,201);
 let detail=await api('/courses/'+course.id);
 assert.equal(detail.modules.length,10);
 const lessons=detail.modules.flatMap(m=>m.lessons);assert.equal(lessons.length,48);
 if(process.argv.includes('--browser-user')){
  console.log(JSON.stringify({username:account.username,courseId:course.id,lessonId:lessons[0].id,credentialsFile:accountFile}));return;
 }
 await api('/lessons/'+lessons[1].id,'GET',undefined,403);
 const checkpointCounts=[];
 for(let i=0;i<lessons.length;i++){
  const l=await api('/lessons/'+lessons[i].id);assert(l.activities.length>=7);
  assert(!JSON.stringify(l).includes('correctAnswer'));
  await api('/lessons/'+l.id+'/start','POST');
  for(const a of l.activities){
   if(a.activityType==='QUIZ'){
    const info=await api('/quizzes/'+a.quizId);
    if(l.activities.filter(a=>a.activityType==='QUIZ').length===2)checkpointCounts.push(info.questionCount);
    let attempt=await api('/quizzes/'+a.quizId+'/start','POST');
    assert(!JSON.stringify(attempt).match(/correctAnswer|acceptedAnswers|isCorrect/));
    // Exercise a real failed attempt and resumable retry at the course entry.
    if(i===0){
     const failed=await api('/quiz-attempts/'+attempt.id+'/submit','POST');assert.equal(failed.passed,false);
     assert.equal(failed.resultBandEn,'Needs Review');
     assert.equal((await api('/lessons/'+l.id+'/progress')).completed,false);
     attempt=await api('/quizzes/'+a.quizId+'/start','POST');
    }
    for(const q of attempt.questions){
     // Identical Vietnamese gaps can have different English contexts (pupil vs university student).
     let expected=authored.flatMap(l=>l.questions).find(x=>x.promptVi===q.promptVi && x.promptEn===q.promptEn && x.questionType===q.questionType && (x.audioUrl||null)===(q.audioUrl||null) && JSON.stringify(x.options)===JSON.stringify(q.options));
     if(q.questionType==='MATCHING')expected={correctAnswerJson:authored[i].practice.correctAnswer};
     assert(expected,'Missing authored answer for '+q.promptVi);
     await api('/quiz-attempts/'+attempt.id+'/answers/'+q.questionId,'PUT',{answer:answer(expected.correctAnswerJson)});
    }
    const result=await api('/quiz-attempts/'+attempt.id+'/submit','POST');assert.equal(result.percentage,100,`Lesson ${i+1}: ${JSON.stringify(result.questions.filter(q=>!q.isCorrect))}`);assert.equal(result.passed,true);
   }else{
    const response=await api('/activities/'+a.id+'/complete','POST',{answer:a.activityType==='PRACTICE'?answer(authored[i].practice.correctAnswer):{}});
    assert.equal(response.activityCompleted,true);
   }
  }
  assert.equal((await api('/lessons/'+l.id+'/progress')).completed,true);
  const p=await api('/courses/'+course.id+'/progress');assert.equal(p.completedLessons,i+1);
  if(i<47)assert.equal(p.status,'IN_PROGRESS');
 }
 const progress=await api('/courses/'+course.id+'/progress');assert.equal(progress.status,'COMPLETED');assert.equal(progress.progressPercentage,100);
 const report={courseId:course.id,modules:10,lessons:48,completedLessons:progress.completedLessons,status:progress.status,realHttp:true,checkpointQuizSizes:checkpointCounts,verifiedAt:new Date().toISOString()};
 fs.writeFileSync(path.join(dir,'foundation-runtime-result.json'),JSON.stringify(report,null,2));
 console.log(JSON.stringify(report));
}
main().catch(e=>{console.error(e);process.exitCode=1});
