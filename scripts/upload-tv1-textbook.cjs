// node --env-file=.env.cloudinary scripts/upload-tv1-textbook.cjs
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const assert = require('node:assert/strict');
const root = path.resolve(__dirname, '..');
const assets = require('../src/main/resources/learning/tv1-tap1-assets.json');
const media = path.join(root, 'src/main/resources/static/media/tv1-tap1');
const cacheFile = path.join(root, '.verification/tv1-tap1/cloudinary.json');
const output = path.join(root, 'src/main/resources/learning/tv1-tap1-media.json');
const cloud = process.env.CLOUDINARY_CLOUD_NAME, key = process.env.CLOUDINARY_API_KEY, secret = process.env.CLOUDINARY_API_SECRET;
assert.ok(cloud && key && secret, 'Load the local .env.cloudinary credentials first');
assert.match(cloud, /^[\w-]+$/);
const files = assets.pages.flatMap(p => [p.image, ...p.illustrations.map(c => c.image)]);
assert.equal(new Set(files).size, files.length);
const cache = fs.existsSync(cacheFile) ? JSON.parse(fs.readFileSync(cacheFile, 'utf8')) : {};
const urls = {};
fs.mkdirSync(path.dirname(cacheFile), {recursive:true});
let done = 0, cursor = 0;
function save() {
  fs.writeFileSync(cacheFile, JSON.stringify(cache, null, 2));
  fs.writeFileSync(output, JSON.stringify({sourceSha256:assets.sourceSha256,urls}, null, 2)+'\n');
}
async function upload(uri) {
  const relative = uri.replace('/media/tv1-tap1/', '');
  const filename = path.resolve(media, relative);
  assert.ok(filename.startsWith(media + path.sep));
  const bytes = fs.readFileSync(filename), sha = crypto.createHash('sha256').update(bytes).digest('hex');
  if(cache[uri]?.sha256 === sha) { urls[uri] = cache[uri].url; return; }
  const publicId = `hola-vietnamese/courses/tv1-tap1/${assets.sourceSha256.slice(0,12)}/${relative.replace(/\.jpg$/, '')}-${sha.slice(0,10)}`;
  for(let attempt=0;attempt<3;attempt++) {
    try {
      const params = {public_id:publicId,timestamp:String(Math.floor(Date.now()/1000))};
      const sign = Object.entries(params).sort(([a],[b])=>a.localeCompare(b)).map(([k,v])=>`${k}=${v}`).join('&');
      const form = new FormData();
      for(const [k,v] of Object.entries(params)) form.set(k,v);
      form.set('api_key',key);form.set('signature',crypto.createHash('sha1').update(sign+secret).digest('hex'));
      form.set('file',new Blob([bytes],{type:'image/jpeg'}),path.basename(filename));
      const response = await fetch(`https://api.cloudinary.com/v1_1/${cloud}/image/upload`,{method:'POST',body:form,signal:AbortSignal.timeout(60000)});
      const result = await response.json();
      assert.ok(response.ok, `Image upload HTTP ${response.status}: ${result.error?.message || 'rejected'}`);
      assert.equal(result.resource_type,'image'); assert.equal(result.public_id,publicId);
      assert.ok(result.secure_url?.startsWith(`https://res.cloudinary.com/${cloud}/image/upload/`));
      urls[uri]=result.secure_url;cache[uri]={sha256:sha,url:result.secure_url};save();return;
    } catch(error) { if(attempt===2) throw new Error(`${relative}: ${error.message}`); }
  }
}
(async()=>{
  console.log(`Uploading/reusing ${files.length} textbook images.`);
  await Promise.all(Array.from({length:5},async()=>{
    while(cursor<files.length) {
      const uri=files[cursor++];await upload(uri);done++;
      if(done%25===0 || done===files.length) console.log(`${done}/${files.length} images ready`);
    }
  }));
  assert.equal(Object.keys(urls).length,files.length);save();console.log('All images saved to the public media manifest.');
})().catch(error=>{save();console.error(error.message);process.exitCode=1;});
