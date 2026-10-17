"use strict";
(function(){
  async function display(){
    const source=window.TrasladosReleases;
    const catalog=await source.refresh();
    for(const kind of ["cliente","mapa","conductor"]){
      const section=document.getElementById("release-"+kind);
      const label=section?.querySelector("[data-version]");
      const link=section?.querySelector("[data-download]");
      const status=section?.querySelector("[data-status]");
      if(!label||!link||!status)continue;
      const release=catalog?.releases?.[kind];
      if(!source.approvedRelease(release,kind)){
        label.textContent="Sin publicación verificada";
        status.textContent="No fue posible consultar el catálogo. No se ofrecen enlaces de prueba.";
        continue;
      }
      label.textContent=release.displayVersion+" · Versión estable";
      const url=source.urlFor(release);
      if(!url||!await source.availableApk(url)){
        status.textContent="Descarga temporalmente no disponible.";
        continue;
      }
      link.href=url;
      link.download=release.file;
      link.hidden=false;
      status.textContent="Archivo disponible. Android confirmará la instalación.";
    }
  }
  if(document.readyState==="loading")document.addEventListener("DOMContentLoaded",()=>void display(),{once:true});
  else void display();
})();
