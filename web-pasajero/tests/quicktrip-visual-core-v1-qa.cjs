'use strict';
/* QT Visual Core v1. Read-only contracts and optional browser screenshots.
 * No actual backend, GPS, APK, production deployment or new dependencies. */
const fs=require('node:fs');
const path=require('node:path');
const os=require('node:os');
const {spawn,spawnSync}=require('node:child_process');
const root=path.resolve(__dirname,'../..');
const read=p=>fs.readFileSync(path.join(root,p),'utf8');
const catalog=JSON.parse(read('docs/quicktrip-visual-core-v1.catalog.json'));
const report={schema:'qt.visual-core.qa.v1',
  mode:process.argv.includes('--browser')?'browser':'static',
  baseSha:catalog.base_sha,cases:[],screenshots:[],blockedRemoteHosts:[],failures:[]};
const check=(name,ok,details)=>{
  report.cases.push({name,pass:!!ok});
  if(!ok)report.failures.push({name,details:details||'contract mismatch'});
};
const hasToken=(css,t)=>css.replace(/\s+/g,'').toLowerCase()
  .includes((t.name+':'+t.value+';').toLowerCase());
function verifyStatic(){
  const html=read('web-pasajero/index.html');
  const css=read('web-pasajero/styles.css');
  const shell=read('web-pasajero/quicktrip-clean.js');
  const theme=read('web-pasajero/quicktrip-approved-v85-qa.js');
  check('Authorized exact base SHA',
    catalog.base_sha==='d92cf74f364035aca4f47c8cd88c73d99e3f84ac');
  check('Read-only inventory with unapproved visual references',
    catalog.mode==='read-only-inventory' &&
    catalog.visual_baseline==='pending-approved-private-references');
  for(const t of catalog.tokens)check('Observed CSS token '+t.name,hasToken(css,t));
  for(const id of catalog.dom_ids)check('Original HTML #'+id,
    html.includes('id="'+id+'"'));
  for(const item of catalog.components){
    const source=read(item.source);
    for(const marker of item.source_markers)
      check(item.id+' owner '+marker,source.includes(marker));
  }
  check('Preview read-only and form writes blocked',
    html.includes('name="traslados-preview" content="read-only"') &&
    html.includes("form-action 'none'"));
  check('Isolated existing v90 adapters',
    html.includes('quicktrip-qa-route-v90.js?v=90') &&
    html.includes('quicktrip-qa-stage-b-v90.js?v=1'));
  check('Native picker + original send ID remain',
    shell.includes('destPicker.persist') &&
    html.includes('id="urgent-send"'));
  check('Existing theme toggle and light modifier',
    theme.includes('theme-toggle') && theme.includes('qt-v86-light'));
  check('Exactly three canonical services',
    catalog.services.join('|')==='Tu Viaje|Tu Paquete|Tu Viaje Programado');
  // Negative probes: synthetic inputs, never modify code or project files.
  check('NEGATIVE changed token detected',
    !hasToken(css,{name:'--bg',value:'#000000'}));
  check('NEGATIVE missing DOM ID detected',
    !html.includes('id="__qt_missing_control__"'));
  const exactServices=services=>
    Array.isArray(services)&&services.length===3&&
    services.join('|')==='Tu Viaje|Tu Paquete|Tu Viaje Programado';
  check('NEGATIVE fourth service blocked',
    !exactServices([...catalog.services,'Viajar ahora']));
  check('NEGATIVE service missing blocked',
    !exactServices(catalog.services.slice(0,2)));
  if(fs.existsSync(path.join(root,'.git'))){
    const blobs=JSON.parse(read('docs/quicktrip-canonical-contract.json')).protected_blobs;
    for(const [p,expected] of Object.entries(blobs)){
      const got=spawnSync('git',['hash-object',p],{cwd:root,encoding:'utf8'});
      check('Original engine blob '+p,got.status===0 &&
        got.stdout.trim()===expected,got.stderr||got.stdout.trim());
    }
  }else report.gitBlobCheck='SKIP: Git checkout unavailable';
}
const pause=ms=>new Promise(ok=>setTimeout(ok,ms));
async function verifyBrowser(){
  const {chromium}=require('playwright-core');
  const port='8297';
  const url='http://127.0.0.1:'+port+'/web-pasajero/index.html';
  const out=process.env.QA_DIR||path.join(os.tmpdir(),'qt-visual-core-v1');
  fs.mkdirSync(out,{recursive:true});
  const server=spawn('python3',['-m','http.server',port,'--bind','127.0.0.1'],
    {cwd:root,stdio:'ignore'});
  let browser;
  try{
    let ready=false;
    for(let i=0;i<55;i++){
      try{const r=await fetch(url);if(r.ok){ready=true;break;}}catch{}
      await pause(160);
    }
    if(!ready)throw Error('Local isolated HTTP server unavailable');
    browser=await chromium.launch({headless:true,
      executablePath:process.env.CHROME_PATH||'/usr/bin/chromium',
      args:['--no-sandbox','--disable-dev-shm-usage',
        '--host-resolver-rules=MAP * ~NOTFOUND, EXCLUDE 127.0.0.1']});
    for(const [width,height] of [[360,780],[393,852],[412,915]]){
      const ctx=await browser.newContext({
        viewport:{width,height},deviceScaleFactor:3,isMobile:true,
        hasTouch:true,reducedMotion:'reduce',serviceWorkers:'block'});
      await ctx.route('**/*',route=>{
        const u=new URL(route.request().url());
        if(u.hostname==='127.0.0.1'&&u.port===port)return route.continue();
        report.blockedRemoteHosts.push(u.hostname);
        return route.abort('blockedbyclient');
      });
      const page=await ctx.newPage();
      const errors=[];
      page.on('pageerror',e=>errors.push(e.message));
      await page.goto(url,{waitUntil:'load',timeout:25000});
      await page.locator('#view-inicio.active [data-service="now"]')
        .waitFor({state:'visible',timeout:12000});
      const home=await page.evaluate(()=>({
        count:document.querySelectorAll('#view-inicio .service-card').length,
        overflow:Math.max(0,document.documentElement.scrollWidth-innerWidth)
      }));
      check('Three services '+width,home.count===3,home);
      check('Home horizontal fit '+width,home.overflow<=2,home);
      const toggle=page.locator('[data-qt-component="theme-toggle"]');
      await toggle.waitFor({state:'visible',timeout:8000});
      for(const mode of ['dark','light']){
        const current=await page.evaluate(()=>document.body.classList.contains('qt-v86-light'));
        if((mode==='light')!==current)await toggle.click();
        check('Theme '+mode+' '+width,
          await page.evaluate(v=>document.body.classList.contains('qt-v86-light')===v,
            mode==='light'));
        const shot='home-'+width+'-'+mode+'.png';
        await page.screenshot({path:path.join(out,shot),
          fullPage:false,animations:'disabled'});
        report.screenshots.push(shot);
      }
      await page.locator('#view-inicio [data-service="now"]').click();
      await page.locator('#view-viaje.active .qtc-app')
        .waitFor({state:'visible',timeout:12000});
      const ui=await page.evaluate(()=>({
        origin:document.querySelector('#origin-picker')?.getBoundingClientRect().height>0,
        destination:document.querySelector('#destination-picker')?.getBoundingClientRect().height>0,
        nav:!!document.querySelector('.qtc-bottom-nav'),
        disabled:document.querySelector('#urgent-send')?.disabled===true,
        overflow:Math.max(0,document.documentElement.scrollWidth-innerWidth)
      }));
      check('Original input nodes and nav '+width,ui.origin&&ui.destination&&ui.nav,ui);
      check('QA request disabled '+width,ui.disabled,ui);
      check('Trip horizontal fit '+width,ui.overflow<=2,ui);
      for(const selector of ['.qtc-schedule > summary','.qtc-contact > summary']){
        const el=page.locator(selector);
        await el.evaluate(e=>e.scrollIntoView({block:'center',behavior:'instant'}));
        await pause(80);
        const hit=await page.evaluate(sel=>{
          const e=document.querySelector(sel),n=document.querySelector('.qtc-bottom-nav');
          const b=e.getBoundingClientRect(),a=n?.getBoundingClientRect();
          const x=b.left+b.width/2,y=b.top+b.height/2;
          const top=document.elementFromPoint(x,y);
          return {visible:b.width>0&&b.height>0&&y>=0&&y<innerHeight,
            clickable:top===e||e.contains(top),
            covered:!!a&&y>=a.top&&y<=a.bottom};
        },selector);
        check('Bottom-nav hit testing '+width+' '+selector,
          hit.visible&&hit.clickable&&!hit.covered,hit);
      }
      const shot='trip-'+width+'-light.png';
      await page.screenshot({path:path.join(out,shot),
        fullPage:false,animations:'disabled'});
      report.screenshots.push(shot);
      check('No JS exceptions '+width,errors.length===0,errors);
      await ctx.close();
    }
    report.browserEvidenceDir=out;
  }finally{
    if(browser)await browser.close();
    server.kill('SIGTERM');
  }
}
(async()=>{
  try{
    verifyStatic();
    if(!report.failures.length&&report.mode==='browser')await verifyBrowser();
  }catch(e){report.failures.push({name:'execution',details:String(e.stack||e)});}
  report.result=report.failures.length?'FAIL':'PASS';
  if(report.mode==='browser'){
    const out=process.env.QA_DIR||path.join(os.tmpdir(),'qt-visual-core-v1');
    fs.mkdirSync(out,{recursive:true});
    fs.writeFileSync(path.join(out,'QT_VISUAL_CORE_V1_QA_RESULT.json'),
      JSON.stringify(report,null,2)+'\n');
  }
  console.log(JSON.stringify(report,null,2));
  if(report.result!=='PASS')process.exitCode=1;
})();
