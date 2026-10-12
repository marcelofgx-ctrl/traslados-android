"use strict";
/* PWA v63 home smoke: navigation-only services, preserve backend flow and WebView. */
const assert=require("node:assert/strict"),fs=require("node:fs"),path=require("node:path"),vm=require("node:vm");
const root=path.join(__dirname,".."),read=n=>fs.readFileSync(path.join(root,n),"utf8");
const html=read("index.html"),app=read("app.js"),css=read("styles.css"),sw=read("sw.js");
assert(html.includes('id="view-inicio" class="view active'));
assert(html.includes('id="view-reservar" class="view"'));
assert(!html.includes('<section class="hero">'),"Old marketing hero must not preface the booking view");
assert(html.includes('data-view="inicio"')&&html.includes('data-view="reservar"'));
assert(html.includes('data-view="viajes"')&&html.includes('data-view="cuenta"'));
assert(html.includes('data-service="now"')&&html.includes('data-service="schedule"')&&html.includes('data-service="delivery"'));
assert(html.includes('data-open-view="instalar"')&&html.includes('id="view-instalar"'));
assert(html.includes('id="service-coming-soon"')&&html.includes("PRÓXIMAMENTE"));
assert(html.includes('src="./service-home.js?v=3"')&&html.indexOf('service-home.js?v=3')<html.indexOf("app.js?v=63"));
assert(html.includes('styles.css?v=63')&&html.includes('app.js?v=63'));
assert(sw.includes('traslados-cliente-pwa-v63')&&sw.includes('./service-home.js'));
assert(css.includes(".service-card")&&css.includes(".home-hero")&&css.includes(".booking-back-services"));
assert(app.includes('window.TrasladosServiceHome.mount(document,{'));
assert(app.includes('onSelect:kind=>{')&&app.includes('setWhen(kind);')&&app.includes('bookingSteps.open("route");'));
assert(app.includes('shiftView("reservar");'));
assert(app.includes('rpc("customer_request_pickup_v1"')&&app.includes('customer_create_reservation_v12'));
assert(app.includes('window.TrasladosETA.availability')&&app.includes('customer_trip_chat_v1'));
const listeners=new Map();
const scrolled=[];const notice={hidden:true,scrollIntoView:options=>scrolled.push(options)};
const choices=["now","schedule","delivery"].map(kind=>({
 dataset:{service:kind},attrs:{},setAttribute(key,value){this.attrs[key]=value;},querySelector(){return this.arrow;},arrow:{textContent:"＋"},addEventListener:(event,fn)=>listeners.set("service:"+kind,fn)
}));
const routes=["viajes","instalar","inicio"].map(name=>({
 dataset:{openView:name},addEventListener:(event,fn)=>listeners.set("navigate:"+name,fn)
}));
const doc={
 getElementById:id=>id==="service-coming-soon"?notice:null,
 querySelectorAll:selector=>selector==="[data-service]"?choices:selector==="[data-open-view]"?routes:[],
 querySelector:selector=>selector==='[data-service="delivery"]'?choices[2]:null
};
const ctx={window:{},Set,Error};
vm.runInNewContext(read("service-home.js"),ctx);
const selected=[],visited=[];
const mounted=ctx.window.TrasladosServiceHome.mount(doc,{onSelect:kind=>selected.push(kind),onNavigate:target=>visited.push(target)});
assert(Object.isFrozen(mounted));
listeners.get("service:delivery")();
assert.equal(notice.hidden,false);
assert.equal(choices[2].attrs["aria-expanded"],"true");
assert.equal(choices[2].arrow.textContent,"−");
assert.equal(scrolled.length,1);
assert.equal(scrolled[0].block,"nearest");
assert.equal(scrolled[0].behavior,"smooth");
assert.deepEqual(selected,[],"Delivery preview must not start booking or route");
listeners.get("service:delivery")();
assert.equal(notice.hidden,true,"Second tap collapses the information without a request");
assert.equal(choices[2].attrs["aria-expanded"],"false");
assert.equal(choices[2].arrow.textContent,"＋");
assert.equal(scrolled.length,1,"Collapsing must not scroll");
listeners.get("service:delivery")();
assert.equal(notice.hidden,false,"Delivery can be reopened");
assert.equal(scrolled.length,2,"Reopening scrolls the notice into view");
listeners.get("service:now")();
listeners.get("service:schedule")();
assert.equal(notice.hidden,true);
assert.equal(choices[2].arrow.textContent,"＋","Changing services resets disclosure affordance");
assert.deepEqual(selected,["now","schedule"]);
listeners.get("navigate:instalar")();
listeners.get("navigate:inicio")();
assert.deepEqual(visited,["instalar","inicio"]);
console.log("PASS PWA v63 home: service selector, pending parcel preview, booking modes, navigation and WebView cache");
