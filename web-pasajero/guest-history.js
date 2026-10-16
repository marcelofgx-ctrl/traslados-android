"use strict";
/* v76 — passenger trip history reconciler.
 * Only merge rows authorized by an authenticated customer session or by
 * the per-reservation public token stored in this SAME browser.
 * Never search by phone, name, booking code or guessed identifiers.
 */
(function(root){
  function merge(accountRows,receipts,results){
    const rows=Array.isArray(accountRows)?accountRows.slice():[];
    const seen=new Set(rows.map(r=>String(r?.id||"")).filter(Boolean));
    if(!Array.isArray(receipts)||!Array.isArray(results))return rows;
    results.forEach((result,index)=>{
      const proof=receipts[index];
      if(!proof||typeof proof.token!=="string"||!proof.token)return;
      if(!result||result.status!=="fulfilled"||!result.value)return;
      const r=result.value,id=String(r.id||"");
      if(!id||seen.has(id))return;
      seen.add(id);
      rows.push({...r,__guestToken:proof.token});
    });
    return rows;
  }
  root.TrasladosGuestHistory=Object.freeze({merge});
})(typeof window!=="undefined"?window:globalThis);
