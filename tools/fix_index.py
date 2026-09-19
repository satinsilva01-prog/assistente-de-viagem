from pathlib import Path
import re
p=Path('index.html')
s=p.read_text(encoding='utf-8')

# Manter o reset robusto para navegador e WebView/Android.
reset_new='''function resetApp(){
  if(!confirm('⚠️ Isso apagará TODOS os dados deste aplicativo neste aparelho, incluindo viagens, abastecimentos, veículos, manutenção, históricos, ajustes e configurações. Deseja realmente resetar?'))return;
  try{if(typeof stopGps==='function')stopGps();}catch(e){}
  try{if(typeof stopTimer==='function')stopTimer();}catch(e){}
  try{if(window.AndroidGPS&&typeof window.AndroidGPS.resetAll==='function')window.AndroidGPS.resetAll();}catch(e){}
  try{localStorage.clear();}catch(e){}
  try{sessionStorage.clear();}catch(e){}
  try{if(indexedDB&&indexedDB.databases){indexedDB.databases().then(dbs=>dbs.forEach(db=>{if(db&&db.name)try{indexedDB.deleteDatabase(db.name)}catch(e){}})).catch(()=>{});}}catch(e){}
  try{state=clone(DEFAULT);}catch(e){}
  try{document.body.classList.remove('dark','viagem-scroll-locked');document.body.style.position='';document.body.style.top='';document.body.style.left='';document.body.style.right='';document.body.style.width='';}catch(e){}
  try{if(typeof render==='function')render();}catch(e){}
  try{if(typeof closeMenu==='function')closeMenu();}catch(e){}
  alert('✅ Aplicativo resetado. Todos os dados locais foram apagados.');
  setTimeout(()=>{try{location.reload();}catch(e){location.href=location.href;}},150);
}
'''
s,n=re.subn(r'function resetApp\(\)\{.*?\n\}\n\nfunction openMenu',reset_new+'\nfunction openMenu',s,count=1,flags=re.S)
if n!=1:
    raise SystemExit('resetApp não localizado')

# Remover efetivamente o cadeado da interface e impedir que qualquer lógica antiga
# de bloqueio torne a aba/módulo inoperante.
lock_disable='''\n<style id="viagem-simplificacao-2026-09-19">\n/* O cadeado foi eliminado nesta versão. */\n#viagemLockTag,.viagem-lock-tag{display:none!important;pointer-events:none!important;visibility:hidden!important}\n.av-trip-panel.viagem-panel-locked{pointer-events:auto!important}\n.av-trip-panel.viagem-panel-locked *{pointer-events:auto!important}\n/* O painel do veículo deixa de ficar fixo/sticky e passa a acompanhar a rolagem normal. */\n#av-trip-panel,.av-trip-panel{position:static!important;top:auto!important;bottom:auto!important;left:auto!important;right:auto!important}\n</style>\n'''
if 'id="viagem-simplificacao-2026-09-19"' not in s:
    s=s.replace('</head>',lock_disable+'</head>',1)

p.write_text(s,encoding='utf-8')
print('INDEX pronto: reset preservado; cadeado removido; painel não fixo.')
