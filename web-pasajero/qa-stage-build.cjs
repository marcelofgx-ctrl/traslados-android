// Quick Trip v86 QA static-only packaging. No network, credentials or backend.
'use strict';
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const root = __dirname;
const out = path.join(root, 'dist');
const allowed = new Set(['.html','.css','.js','.json','.webmanifest','.svg','.png','.webp','.ico']);
const skipNames = new Set(['vercel.json','qa-stage-build.cjs']);
function copyFile(file, rel) {
  if (!allowed.has(path.extname(file).toLowerCase()) || skipNames.has(rel)) return;
  const dest = path.join(out, rel);
  fs.mkdirSync(path.dirname(dest), {recursive:true});
  fs.copyFileSync(file, dest);
  const hash = p => crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
  if (hash(file) !== hash(dest)) throw Error('Byte mismatch: '+rel);
}
function recurse(source, rel) {
  if (!fs.existsSync(source)) return;
  for (const entry of fs.readdirSync(source,{withFileTypes:true})) {
    const itemRel = path.join(rel,entry.name), item = path.join(source,entry.name);
    if (entry.isDirectory()) recurse(item,itemRel);
    else if (entry.isFile()) copyFile(item,itemRel);
  }
}
fs.rmSync(out,{recursive:true,force:true});
fs.mkdirSync(out,{recursive:true});
for (const entry of fs.readdirSync(root,{withFileTypes:true})) {
  if (entry.isFile()) copyFile(path.join(root,entry.name),entry.name);
}
for (const name of ['icons','data']) recurse(path.join(root,name),name);
const required=['index.html','offline.html','manifest.webmanifest','sw.js','quicktrip-canonical-v86.css','quicktrip-approved-v85-qa.js','app.js','icons/icon-192.png','icons/icon-512.png'];
for (const rel of required) if (!fs.existsSync(path.join(out,rel))) throw Error('Missing required file: '+rel);
const html=fs.readFileSync(path.join(out,'index.html'),'utf8');
if (!html.includes('quicktrip-qa-isolation') || !html.includes("connect-src https://direcciones.ide.uy https://router.project-osrm.org") || !html.includes('quicktrip-canonical-v86.css?v=86')) throw Error('Missing v90 QA-only IDE/OSRM CSP protections');
const files=[];
function collect(dir,rel='') {
  for(const e of fs.readdirSync(dir,{withFileTypes:true})) {
    const n=path.join(rel,e.name);
    if(e.isDirectory()) collect(path.join(dir,e.name),n);
    else files.push(n.replace(/\\/g,'/'));
  }
}
collect(out);
if(files.some(n=>/\.apk$|\.aab$|downloads\//i.test(n))) throw Error('Unexpected Android/history payload');
console.log('QA static-only build OK: '+files.length+' assets, byte-for-byte source copies, no APK');
