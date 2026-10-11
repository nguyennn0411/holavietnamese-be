// Real API verification uses one separate QA learner; never changes existing learners.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict'),crypto=require('node:crypto');
const base=process.env.PRONUNCIATION_API_URL||'http://127.0.0.1:18080/api';
const root=path.resolve(__dirname,'..'),dir=path.join(root,'.verification');
const source=JSON.parse(fs.readFileSync(path.join(root,'src/main/resources/learning/pronunciation-course.json'),'utf8'));
const authored=source.modules.flatMap(m=>m.lessons),bank=[...authored.flatMap(l=>l.questions),...source.finalQuestions];
const accountFile=path.join(dir,'pronunciation-runtime-account.json');
let token;
async function api(url,method='GET',body,status=200){
 const r=await fetch(base+url,{method,headers:{'Content-Type':'application/json',...(token?{Authorization:'Bearer '+token}:{})},...(body===undefined?{}:{body:JSON.stringify(body)})});
 const raw=await r.text();let data;try{data=JSON.parse(raw)}catch{data=raw}
 assert.equal(r.status,status,`${method} ${url}: ${raw.slice(0,250)}`);return data;
}
const answer=key=>key.acceptedAnswers?{text:key.acceptedAnswers[0]}:key;
async function main(){
 fs.mkdirSync(dir,{recursive:true});
 let account;
 if(process.argv.includes('--restart-check'))account=JSON.parse(fs.readFileSync(accountFile,'utf8'));
 else{
  account={username:'pronqa'+Date.now(),password:'Pqa!'+crypto.randomBytes(15).toString('hex')};
  await api('/auth/register','POST',{...account,email:account.username+'@example.test',fullName:'Pronunciation Curriculum QA',nativeLanguage:'en',targetLevel:'A0'});
  fs.writeFileSync(accountFile,JSON.stringify(account));
 }
 token=(await api('/auth/token','POST',account)).result.token;
 const course=(await api('/courses?page=0&size=100')).content.find(c=>c.code===source.course.code);
 assert(course,'Persisted pronunciation course missing');
 if(process.argv.includes('--restart-check')){
  const progress=await api('/courses/'+course.id+'/progress');
  assert.equal(progress.completedLessons,33);assert.equal(progress.status,'COMPLETED');
  console.log(JSON.stringify({restartPersistence:true,courseId:course.id,completedLessons:33}));return;
 }
 await api('/courses/'+course.id+'/enroll','POST',undefined,201);
 const detail=await api('/courses/'+course.id);
 assert.equal(detail.modules.length,8);
 const lessons=detail.modules.flatMap(m=>m.lessons);assert.equal(lessons.length,33);
 await api('/lessons/'+lessons[1].id,'GET',undefined,200);
 let quizzes=0,audioChecked=false;
 for(let i=0;i<lessons.length;i++){
  const l=await api('/lessons/'+lessons[i].id);
  assert(!JSON.stringify(l).includes('correctAnswer'));
  await api('/lessons/'+l.id+'/start','POST');
  let quizIndex=0;
  for(const a of l.activities){
   if(a.activityType==='QUIZ'){
    let attempt=await api('/quizzes/'+a.quizId+'/start','POST');
    assert(!JSON.stringify(attempt).match(/correctAnswer|acceptedAnswers|isCorrect/));
    if(i===0 || quizIndex===1){
     const failed=await api('/quiz-attempts/'+attempt.id+'/submit','POST');assert.equal(failed.passed,false);
     assert.equal((await api('/lessons/'+l.id+'/progress')).completed,false);
     if(i<32)await api('/lessons/'+lessons[i+1].id,'GET',undefined,200);
     attempt=await api('/quizzes/'+a.quizId+'/start','POST');
    }
    const isFinal=i===32&&quizIndex===2;
    let finalPoints=0;
    for(const q of attempt.questions){
     const expected=(isFinal?source.finalQuestions:bank).find(x=>x.promptVi===q.promptVi&&x.promptEn===q.promptEn&&x.questionType===q.questionType&&(x.audioUrl||null)===(q.audioUrl||null)&&JSON.stringify(x.options)===JSON.stringify(q.options));
     assert(expected,'Missing authored answer: '+q.promptVi);
     if(isFinal){if(finalPoints+expected.points>70)continue;finalPoints+=expected.points;}
     await api('/quiz-attempts/'+attempt.id+'/answers/'+q.questionId,'PUT',{answer:answer(expected.correctAnswerJson)});
    }
    const result=await api('/quiz-attempts/'+attempt.id+'/submit','POST');
    assert.equal(result.percentage,isFinal?70:100);assert.equal(result.passed,true);quizzes++;quizIndex++;
   }else{
    const authoredActivity=authored[i].activities.find(x=>x.activityType===a.activityType);
    assert(authoredActivity);
    if(a.activityType==='AUDIO'&&!audioChecked){
     const response=await fetch(base.replace(/\/api$/,'')+a.contentJson.audioUrl);
     assert.equal(response.status,200);const bytes=Buffer.from(await response.arrayBuffer());
     assert.equal(bytes.toString('ascii',0,4),'RIFF');assert(bytes.length>1000);audioChecked=true;
    }
    const key=authoredActivity.contentJson.correctAnswer;
    const response=await api('/activities/'+a.id+'/complete','POST',{answer:key?answer(key):{}});
    assert.equal(response.activityCompleted,true);
   }
  }
  assert.equal((await api('/lessons/'+l.id+'/progress')).completed,true);
 }
 const progress=await api('/courses/'+course.id+'/progress');
 assert.equal(progress.completedLessons,33);assert.equal(progress.status,'COMPLETED');assert.equal(quizzes,42);
 const report={courseId:course.id,code:course.code,modules:8,lessons:33,quizzes,finalQuestions:50,finalPassPercentage:70,completedLessons:progress.completedLessons,status:progress.status,audioChecked,realHttp:true,verifiedAt:new Date().toISOString()};
 fs.writeFileSync(path.join(dir,'pronunciation-runtime-result.json'),JSON.stringify(report,null,2));
 console.log(JSON.stringify(report));
}
main().catch(e=>{console.error(e);process.exitCode=1});
