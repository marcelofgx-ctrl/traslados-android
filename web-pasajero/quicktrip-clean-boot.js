"use strict";
/* Solo metadatos de instalación: nunca modifica motores ni reservas. */
(function(doc){
  if(new URLSearchParams(location.search).get("ui")!=="clean")return;
  const manifest=doc.querySelector('link[rel="manifest"]');
  if(manifest)manifest.href="./manifest-quicktrip-clean.webmanifest";
  const theme=doc.querySelector('meta[name="theme-color"]');
  if(theme)theme.content="#092830";
  const apple=doc.querySelector('meta[name="apple-mobile-web-app-title"]');
  if(apple)apple.content="Quick Trip TEST";
  doc.title="Quick Trip · Tu viaje";
})(document);
