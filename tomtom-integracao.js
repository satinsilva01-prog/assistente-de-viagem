/* Assistente de Viagem V35 — integração TomTom
   Objetivo: consultar limite da via automaticamente, com economia de consultas.
   A API key é armazenada somente no armazenamento local do aparelho.
*/
(function(){
  'use strict';
  const KEY='assistente_tomtom_api_key_v35';
  const CFG='assistente_tomtom_cfg_v35';
  const MIN_QUERY_MS=90000;
  const MIN_MOVE_M=180;
  const MAX_STALE_MS=180000;
  const WATCH_MS=5000;
  let watchTimer=null, lastPos=null, lastQuery=0, busy=false, running=false;
  let lastRoad='', lastLimit='';

  function el(id){return document.getElementById(id)}
  function dist(a,b){
    const R=6371000, p1=a.lat*Math.PI/180,p2=b.lat*Math.PI/180;
    const dp=(b.lat-a.lat)*Math.PI/180, dl=(b.lon-a.lon)*Math.PI/180;
    const x=Math.sin(dp/2)**2+Math.cos(p1)*Math.cos(p2)*Math.sin(dl/2)**2;
    return 2*R*Math.asin(Math.sqrt(x));
  }
  function headingDelta(a,b){
    if(a==null||b==null||!Number.isFinite(a)||!Number.isFinite(b)) return 0;
    let d=Math.abs(a-b)%360; return d>180?360-d:d;
  }
  function loadCfg(){try{return JSON.parse(localStorage.getItem(CFG)||'{}')}catch(e){return {}}}
  function saveCfg(c){localStorage.setItem(CFG,JSON.stringify(c||{}))}
  function key(){return localStorage.getItem(KEY)||''}
  function setKey(v){localStorage.setItem(KEY,v.trim())}

  function ensureUI(){
    if(document.getElementById('tomtomMenuBtn')) return;
    const menu=document.querySelector('.av-menu-panel');
    if(menu){
      const b=document.createElement('button'); b.id='tomtomMenuBtn'; b.type='button'; b.textContent='🛣️ Velocidade da via';
      b.addEventListener('click',openModal); menu.appendChild(b);
    }
    const style=document.createElement('style'); style.textContent=`
      #tomtomModal{display:none;position:fixed;inset:0;z-index:1300;background:#0008;align-items:center;justify-content:center;padding:18px}
      #tomtomModal.open{display:flex}.tt-dialog{width:min(460px,100%);background:#fff;color:#17202a;border-radius:17px;padding:18px;box-shadow:0 10px 35px #0005}.tt-dialog h3{margin:0 0 8px}.tt-dialog p{font-size:13px;line-height:1.4;color:#667085}.tt-dialog input{width:100%;box-sizing:border-box;padding:12px;border:1px solid #cfd5db;border-radius:10px;font-size:16px;margin:5px 0 10px}.tt-actions{display:grid;grid-template-columns:1fr 1fr;gap:8px}.tt-status{margin-top:10px;padding:10px;border-radius:10px;background:#f3f4f6;font-size:13px}.tt-ok{background:#dcfce7}.tt-warn{background:#fff7ed}.tt-mini{font-size:11px;color:#667085;margin-top:7px}
      body.dark .tt-dialog{background:#1a2027;color:#f4f6f8}.tt-dialog .tt-actions button{border:0;border-radius:10px;padding:11px;font-weight:800}.tt-save{background:#16a34a;color:#fff}.tt-close{background:#e5e7eb;color:#17202a}
    `; document.head.appendChild(style);
    const modal=document.createElement('div'); modal.id='tomtomModal'; modal.innerHTML=`<div class="tt-dialog">
      <h3>🛣️ Velocidade da via</h3>
      <p>A consulta automática usa o GPS e a TomTom. A API Key fica somente neste aparelho. O sistema evita consultas repetidas enquanto você permanece no mesmo trecho.</p>
      <label>API Key TomTom</label><input id="tomtomKeyInput" type="password" autocomplete="off" placeholder="Cole sua chave aqui">
      <div class="tt-actions"><button class="tt-save" id="tomtomSave">Salvar chave</button><button class="tt-close" id="tomtomClose">Fechar</button></div>
      <div class="tt-status" id="tomtomStatus">Aguardando configuração.</div><div class="tt-mini">Proteção do teste: mínimo de 90 s entre consultas e nova consulta após deslocamento significativo.</div>
    </div>`; document.body.appendChild(modal);
    document.getElementById('tomtomSave').onclick=()=>{const v=document.getElementById('tomtomKeyInput').value.trim();if(!v){status('Informe a API Key.','warn');return}setKey(v);status('Chave salva neste aparelho.','ok');setTimeout(closeModal,500)};
    document.getElementById('tomtomClose').onclick=closeModal;
  }
  function openModal(){ensureUI(); const m=el('tomtomModal'); if(!m)return; el('tomtomKeyInput').value=key(); m.classList.add('open');}
  function closeModal(){const m=el('tomtomModal');if(m)m.classList.remove('open')}
  function status(t,c){const s=el('tomtomStatus');if(s){s.textContent=t;s.className='tt-status '+(c==='ok'?'tt-ok':c==='warn'?'tt-warn':'')}}

  function updateVehiclePanel(road,limit,speed){
    const candidates=['velocidadeVia','velocidadeDaVia','limiteVelocidade','avVelocidadeVia','speedLimit'];
    let target=null; for(const id of candidates){if(el(id)){target=el(id);break}}
    if(target) target.textContent=limit||'—';
    const textNodes=[...document.querySelectorAll('body *')].filter(x=>x.children.length===0);
    for(const n of textNodes){if((n.textContent||'').trim()==='Velocidade da via' && n.nextElementSibling){const v=n.nextElementSibling;if(v.textContent!==undefined)v.textContent=limit||'—';}}
    window.assistenteTomTom={road:road||'',limit:limit||'',gpsSpeed:speed==null?null:speed};
  }

  async function query(pos){
    const k=key(); if(!k) return false;
    const now=Date.now(); if(now-lastQuery<MIN_QUERY_MS) return false;
    if(busy)return false; busy=true;
    try{
      const lat=pos.coords.latitude, lon=pos.coords.longitude;
      const url='https://api.tomtom.com/search/2/reverseGeocode/'+encodeURIComponent(lat+','+lon)+'.json?key='+encodeURIComponent(k)+'&returnSpeedLimit=true&radius=100&language=pt-BR';
      const r=await fetch(url,{headers:{Accept:'application/json'}}); const d=await r.json().catch(()=>({}));
      if(!r.ok) throw new Error('TomTom HTTP '+r.status);
      const item=d.addresses&&d.addresses[0]; if(!item) throw new Error('via não encontrada');
      const a=item.address||{}, road=a.streetName||a.streetNameAndNumber||a.freeformAddress||'';
      const lim=a.speedLimit||''; const sp=pos.coords.speed==null?null:pos.coords.speed*3.6;
      lastRoad=road;lastLimit=lim;lastQuery=Date.now();
      updateVehiclePanel(road,lim,sp);
      saveCfg({road,limit:lim,at:lastQuery,lat,lon});
      return true;
    }catch(e){console.warn('TomTom:',e);return false}finally{busy=false}
  }

  function onPosition(pos){
    const p={lat:pos.coords.latitude,lon:pos.coords.longitude,heading:pos.coords.heading};
    const moved=lastPos?dist(lastPos,p):Infinity;
    const turn=lastPos?headingDelta(lastPos.heading,p.heading):0;
    const stale=Date.now()-lastQuery>MAX_STALE_MS;
    // Primeira posição: consulta. Depois: só consulta com deslocamento relevante/curva e respeitando intervalo.
    const should=!lastQuery || (stale&&moved>=MIN_MOVE_M) || (moved>=MIN_MOVE_M&&turn>=45);
    if(should) query(pos);
    lastPos=p;
    const sp=pos.coords.speed==null?null:pos.coords.speed*3.6;
    updateVehiclePanel(lastRoad,lastLimit,sp);
  }
  function start(){
    if(running)return; running=true;
    if(!key()){openModal(); running=false; return}
    if(!navigator.geolocation){console.warn('GPS indisponível');return}
    const tick=()=>navigator.geolocation.getCurrentPosition(onPosition,()=>{}, {enableHighAccuracy:true,timeout:10000,maximumAge:5000});
    tick(); watchTimer=setInterval(tick,WATCH_MS);
  }
  function stop(){running=false;if(watchTimer){clearInterval(watchTimer);watchTimer=null}}

  function hookStart(){
    const b=el('btnIniciar'); if(b&&!b.__ttHook){b.__ttHook=true;b.addEventListener('click',()=>setTimeout(start,500))}
    const stopIds=['btnFinalizar','btnParar','btnFimViagem']; for(const id of stopIds){const x=el(id);if(x&&!x.__ttHook){x.__ttHook=true;x.addEventListener('click',stop)}}
  }
  function init(){ensureUI();hookStart();setInterval(hookStart,2000)}
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',init);else init();
  window.assistenteTomTomStart=start; window.assistenteTomTomStop=stop;
})();
