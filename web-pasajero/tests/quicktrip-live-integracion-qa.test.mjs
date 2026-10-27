import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

const html=readFileSync(new URL('../quicktrip-live-qa.html',import.meta.url),'utf8');
const js=readFileSync(new URL('../quicktrip-live-console-qa.js',import.meta.url),'utf8');
const css=readFileSync(new URL('../quicktrip-live-console-qa.css',import.meta.url),'utf8');
const android=readFileSync(new URL('../../mapatrayectos/src/qa/AndroidManifest.xml',import.meta.url),'utf8');
const gradle=readFileSync(new URL('../../mapatrayectos/build.gradle',import.meta.url),'utf8');
const sender=readFileSync(new URL('../../mapatrayectos/src/qa/java/uy/com/mapatrayectos/qa/LiveQaNet.java',import.meta.url),'utf8');

test('separate QA passenger screen and no production scripts',()=>{
  assert.match(html,/name="quicktrip-qa-isolation"/);
  assert.match(html,/name="traslados-preview" content="read-only"/);
  assert.match(html,/quicktrip-live-console-qa\.js/);
  assert.match(html,/quicktrip-live-qa\.js/);
  assert.doesNotMatch(html,/src="\.\/app\.js"/);
  assert.doesNotMatch(html,/zetaudvvutlouiqxopvg|marcelof-gx\.workers\.dev/i);
  assert.match(html,/noindex,nofollow/);
});
test('QA passenger token stays in memory with no client storage',()=>{
  assert.match(js,/credentials:"omit"/);
  assert.match(js,/cache:"no-store"/);
  assert.match(js,/quicktrip-qa/);
  assert.doesNotMatch(js,/localStorage|sessionStorage|IndexedDB/);
  assert.doesNotMatch(js,/supabase\.co|\.workers\.dev\/api\/public/);
  assert.match(js,/Sin conexión|GPS pendiente/);
});
test('authenticated GPS required for vehicle marker',()=>{
  assert.match(js,/!position\?\.available/);
  assert.match(js,/!Array\.isArray\(ge\)/);
  assert.match(js,/ge\.every/);
  assert.match(js,/routeMode:mode/);
  assert.match(js,/render\(\$\("qa-map"\)/);
  assert.match(css,/\.premium-real-map/);
});
test('Android QA package and manifest have only QA entrypoints',()=>{
  assert.match(gradle,/applicationIdSuffix '\.qa'/);
  assert.match(gradle,/versionNameSuffix '-TESTING'/);
  assert.match(android,/tools:node="replace"/);
  assert.match(android,/android:allowBackup="false"/);
  assert.match(android,/LiveQaActivity/);
  assert.match(android,/android:label="Mapa Testing"/);
  assert.match(android,/android:icon="@drawable\/mapa_testing_icon"/);
  assert.match(android,/LiveQaTrackingService/);
  assert.doesNotMatch(android,/ReservationMonitorService|\.TrackingService"|uy\.com\.traslados\.conductor\.MainActivity/);
  assert.match(sender,/quicktrip-\(\?:live-\)\?qa/);
  assert.doesNotMatch(sender,/zetaudvvutlouiqxopvg|supabase\.co/);
});
