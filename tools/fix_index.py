from pathlib import Path
import re
p=Path('index.html')
s=p.read_text(encoding='utf-8')
marker='/* V35 FIX CADEADO RESET 2026-09-18 */'
if marker not in s:
    s,n=re.subn(r'\.viagem-lock-tag\{[^}]*\}',marker+'\n.viagem-lock-tag{position:fixed;right:12px;top:72px;z-index:2005;width:42px;height:42px;border:1px solid #b8c0c8;border-radius:50%;padding:0;background:#fff;color:#111;font-size:20px;font-weight:800;display:none;align-items:center;justify-content:center;box-shadow:0 3px 12px #0003;cursor:pointer;user-select:none;pointer-events:auto}\n.viagem-lock-tag.active{background:#20262d;color:#fff;border-color:#fff}\nbody.dark .viagem-lock-tag{background:#20262d;color:#fff;border-color:#68727d}\nbody.dark .viagem-lock-tag.active{background:#fff;color:#111;border-color:#fff}\n.av-trip-panel.viagem-panel-locked{pointer-events:none}\n.av-trip-panel.viagem-panel-locked #viagemLockTag{pointer-events:auto}',s,count=1,flags=re.S)
    if n!=1: raise SystemExit('CSS do cadeado não localizado')
    s=s.replace("  document.body.classList.toggle('viagem-scroll-locked',viagemTelaTravada);\n","")
    lock_new='''let viagemTelaTravada=false;
function setViagemTelaTravada(travar){
  viagemTelaTravada=!!travar;
  const tag=$('viagemLockTag'),panel=$('av-trip-panel');
  if(panel)panel.classList.toggle('viagem-panel-locked',viagemTelaTravada);
  if(tag){tag.classList.toggle('active',viagemTelaTravada);tag.innerHTML=viagemTelaTravada?'🔒':'🔓';tag.title=viagemTelaTravada?'Destravar tela da viagem':'Travar tela da viagem';tag.setAttribute('aria-label',tag.title);tag.style.display='flex';}
  try{sessionStorage.setItem('assistente_viagem_tela_travada',viagemTelaTravada?'1':'0')}catch(e){}
}
function toggleViagemTela(){setViagemTelaTravada(!viagemTelaTravada)}
function initViagemTelaLock(){const tag=$('viagemLockTag');if(!tag)return;tag.onclick=toggleViagemTela;tag.onkeydown=e=>{if(e.key==='Enter'||e.key===' '){e.preventDefault();toggleViagemTela()}};try{setViagemTelaTravada(sessionStorage.getItem('assistente_viagem_tela_travada')==='1')}catch(e){setViagemTelaTravada(false)}}

'''
    start=s.find('let viagemTelaTravada=false;');end=s.find('function maintenanceToday(){',start)
    if start<0 or end<0: raise SystemExit('Rotina do cadeado não localizada')
    s=s[:start]+lock_new+s[end:]
    reset_new='''function resetApp(){
  if(!confirm('⚠️ Isso apagará TODOS os dados deste aplicativo neste aparelho, incluindo viagens, abastecimentos, veículos, manutenção, históricos, ajustes e configurações. Deseja realmente resetar?'))return;
  try{if(typeof stopGps==='function')stopGps();}catch(e){}
  try{if(typeof stopTimer==='function')stopTimer();}catch(e){}
  try{if(window.AndroidGPS&&typeof window.AndroidGPS.resetAll==='function')window.AndroidGPS.resetAll();}catch(e){}
  try{localStorage.clear();}catch(e){}
  try{sessionStorage.clear();}catch(e){}
  try{if(indexedDB&&indexedDB.databases){indexedDB.databases().then(dbs=>dbs.forEach(db=>{if(db&&db.name)try{indexedDB.deleteDatabase(db.name)}catch(e){}})).catch(()=>{});}}catch(e){}
  try{state=clone(DEFAULT);}catch(e){}
  try{viagemTelaTravada=false;}catch(e){}
  try{const panel=$('av-trip-panel');if(panel)panel.classList.remove('viagem-panel-locked');}catch(e){}
  try{document.body.classList.remove('dark','viagem-scroll-locked');document.body.style.position='';document.body.style.top='';document.body.style.left='';document.body.style.right='';document.body.style.width='';}catch(e){}
  try{if(typeof render==='function')render();}catch(e){}
  try{if(typeof closeMenu==='function')closeMenu();}catch(e){}
  alert('✅ Aplicativo resetado. Todos os dados locais foram apagados.');
  setTimeout(()=>{try{location.reload();}catch(e){location.href=location.href;}},150);
}
'''
    s,n=re.subn(r'function resetApp\(\)\{.*?\n\}\n\nfunction openMenu',reset_new+'\nfunction openMenu',s,count=1,flags=re.S)
    if n!=1: raise SystemExit('resetApp não localizado')
    p.write_text(s,encoding='utf-8')
print('INDEX pronto')
