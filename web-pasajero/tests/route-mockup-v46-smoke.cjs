"use strict";
// Comprobación de fidelidad al boceto aprobado y comportamiento móvil.
// Sin red, sin GPS y sin reservas reales.
const assert=require("node:assert/strict");
const fs=require("node:fs");
const path=require("node:path");
const dir=path.resolve(__dirname,"..");
const read=n=>fs.readFileSync(path.join(dir,n),"utf8");
const js=read("app.js"),css=read("styles.css"),html=read("index.html"),sw=read("sw.js");
assert(html.includes('src="./app.js?v=75"')&&html.includes('href="./styles.css?v=75"'));
assert(sw.includes("traslados-cliente-pwa-v75"));
assert(js.includes('placeholder.textContent="Otros"'),"Native department select uses compact label");
assert(js.includes('select.setAttribute("aria-label","Otros departamentos de Uruguay")'));
assert(js.includes('for(const d of DEPTS.filter(x=>x!=="Montevideo"&&x!=="Canelones"))'),"All other 17 departments remain available");
assert(js.includes('row.classList.add(name==="origin"?"loc-search-origin":"loc-search-standard")'),
  "Origin has a third action, destination has only search");
assert(js.includes('const search=button(row,"Buscar","tiny-btn location-search-action"'),
  "Search action must share the input row");
assert(js.includes('const geo=name==="origin"?button(row,"⌖ Usar ubicación actual","tiny-btn location-use-current"'),
  "GPS button must be inside SAME row as origin search");
assert(!js.includes('name==="origin"?button(controls,"⌖ Usar ubicación actual"'),
  "No GPS button on a separate line");
assert(js.includes('geo.setAttribute("aria-label","Usar mi ubicación actual como origen")'));
assert(js.includes('search.setAttribute("aria-label","Buscar dirección o lugar")'));
assert(js.includes('input.addEventListener("keydown",event=>{')&&js.includes('if(event.key==="Enter")'),"Keyboard Enter launches the existing lookup");
assert(js.includes('clearTimeout(debounce);')&&js.includes('void lookup();'),"Enter must cancel debounce before immediate search");
assert(css.includes('.loc-search.loc-search-origin{'),"Dedicated 3-column grid");
assert(css.includes('grid-template-columns:minmax(0,1fr) 47px 47px'),"Origin icons fit a 3-column row");
assert(css.includes('grid-template-columns:minmax(0,1fr) 44px 44px'),"Small-screen origin icons have stable widths");
assert(css.includes('grid-template-columns:minmax(0,1fr) 47px'),"Destination has 2-column row");
assert(css.includes('font-size:0!important;line-height:0!important'),"Icon-only visual controls");
assert(css.includes('.loc-search .location-use-current::before'),"Custom GPS vector");
assert(css.includes('.loc-search .location-search-action::before'),"Custom search vector");
assert(css.includes('.departments select:has(option:checked:not([value=""]))'),"Selected other department is visually distinct");
assert(css.includes('.wayline span::before')&&css.includes('.wayline span::after'));
assert(css.includes('button:focus-visible'),"Keyboard focus retained on icon controls");
assert(css.includes('prefers-reduced-motion:reduce'),"No forced animation");
assert(js.includes('window.TrasladosAddress.findAcross(q,dept)')&&js.includes('window.TrasladosGeo.find(q,null)'));
assert(js.includes('getCurrentPosition(async p=>'),"GPS consent flow retained");
assert(js.includes('window.TrasladosBookingContracts.registeredReservation'));
assert(js.includes('window.TrasladosBookingContracts.guestReservation'));
console.log("PASS v46: mockup fidelity, three-in-a-row, departments, keyboard, accessibility, engines");
