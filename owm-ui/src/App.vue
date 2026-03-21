<template>
  <div class="app">
    <header class="hdr">
      <span class="logo">&#9889; OpenWRT NetCtrl</span>
      <nav v-if="token" class="tabs">
        <button @click="tab='dashboard'" :class="['tab',tab==='dashboard'&&'active']">&#128241; Роутеры</button>
        <button @click="switchMap" :class="['tab',tab==='map'&&'active']">&#128506; Карта</button>
        <button @click="tab='users';loadUsers()" :class="['tab',tab==='users'&&'active']">&#128100; Админы</button>
      </nav>
      <div class="hdr-r">
        <span class="srv" :class="serverOk?'ok':'err'">{{ serverOk?'&#9679; онлайн':'&#10007; офлайн' }}</span>
        <button v-if="token" @click="logout" class="btn-sm">Выйти</button>
      </div>
    </header>

    <div v-if="!token" class="login-wrap">
      <div class="login-card">
        <div class="login-title">OpenWRT NetCtrl</div>
        <input v-model="lUser" class="field" placeholder="Логин"/>
        <input v-model="lPass" class="field" type="password" placeholder="Пароль" @keyup.enter="doLogin"/>
        <button @click="doLogin" class="btn-primary">Войти</button>
        <div v-if="lErr" class="txt-err">{{ lErr }}</div>
      </div>
    </div>

    <div v-else class="main">

      <!-- DASHBOARD -->
      <div v-show="tab==='dashboard'">
        <div class="toolbar">
          <span class="sec">Роутеры ({{ agents.length }})</span>
          <button @click="loadAll" class="btn-sm">&#8635; Обновить</button>
          <button @click="openAdd" class="btn-primary" style="font-size:13px;padding:5px 14px">+ Добавить</button>
        </div>
        <div class="grid">
          <div v-for="a in agents" :key="a.agent_id" class="card" :class="a.online?'card-on':'card-off'">
            <div class="card-hdr">
              <span class="dot" :class="a.online?'dot-g':'dot-r'"></span>
              <span class="cname">{{ cfgs[a.agent_id]&&cfgs[a.agent_id].display_name || a.agent_id }}</span>
              <span class="badge" :class="a.online?'badge-on':'badge-off'">{{ a.online?'online':'offline' }}</span>
            </div>
            <div v-if="cfgs[a.agent_id]&&cfgs[a.agent_id].description" class="card-desc">{{ cfgs[a.agent_id].description }}</div>
            <div v-if="cfgs[a.agent_id]&&cfgs[a.agent_id].address" class="card-addr">&#128205; {{ cfgs[a.agent_id].address }}</div>
            <div v-if="a.online&&metrics[a.agent_id]" class="mblock">
              <div class="mrow"><span>Load</span><span>{{ metrics[a.agent_id].load1.toFixed(2) }}</span></div>
              <div class="mrow"><span>Mem</span><span>{{ fMem(metrics[a.agent_id].mem_free) }}</span></div>
              <div class="mrow"><span>Uptime</span><span>{{ fUp(metrics[a.agent_id].uptime) }}</span></div>
              <div v-if="metrics[a.agent_id].temperature" class="mrow"><span>Темп.</span><span>{{ metrics[a.agent_id].temperature.toFixed(1) }} °C</span></div>
              <div v-if="metrics[a.agent_id].wifi_clients!=null" class="mrow"><span>WiFi клиенты</span><span>{{ metrics[a.agent_id].wifi_clients }}</span></div>
              <div v-if="metrics[a.agent_id].wan_rx!=null" class="mrow"><span>WAN ↓↑</span><span>{{ fTraffic(metrics[a.agent_id].wan_rx) }} / {{ fTraffic(metrics[a.agent_id].wan_tx) }}</span></div>
            </div>
            <div class="card-btns">
              <button @click="openDetail(a)" class="btn-act" title="Графики">&#128202;</button>
              <button @click="openSSH(a)" class="btn-act" :disabled="!a.online">SSH</button>
              <button @click="openLuCI(a)" class="btn-act" :disabled="!a.online">LuCI</button>
              <button @click="openSets(a)" class="btn-act">&#9881;</button>
              <button @click="deleteRouter(a)" class="btn-act" style="color:#F85149;border-color:#F85149;margin-left:auto">&#10005;</button>
            </div>
          </div>
        </div>
      </div>

      <!-- MAP -->
      <div v-show="tab==='map'" class="map-layout">
        <button @click="sidebarOpen=!sidebarOpen" class="map-toggle">{{ sidebarOpen ? '◀' : '▶ Роутеры' }}</button>
        <div class="map-sidebar" v-show="sidebarOpen">
          <div class="map-sidebar-title">Роутеры</div>
          <div v-for="a in agents" :key="a.agent_id"
            :class="['map-router-item', mapSelected===a.agent_id&&'map-router-selected']"
            @click="selectMapRouter(a)">
            <span class="dot" :class="a.online?'dot-g':'dot-r'" style="flex-shrink:0"></span>
            <div style="flex:1;overflow:hidden">
              <div style="font-size:12px;font-weight:600;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">
                {{ cfgs[a.agent_id]&&cfgs[a.agent_id].display_name || a.agent_id }}
              </div>
              <div v-if="cfgs[a.agent_id]&&cfgs[a.agent_id].address" style="font-size:10px;color:#8B949E;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">
                &#128205; {{ cfgs[a.agent_id].address }}
              </div>
              <div v-else style="font-size:10px;color:#D29922">без координат</div>
            </div>
          </div>
          <div v-if="mapSelected" class="map-hint">
            &#128205; Кликни на карту чтобы назначить место
            <button @click="mapSelected=null" class="btn-sm" style="margin-top:6px;width:100%">Отмена</button>
          </div>
        </div>
        <div id="map"></div>
      </div>

      <!-- USERS -->
      <div v-show="tab==='users'" style="max-width:480px;padding-top:4px">
        <div class="toolbar"><span class="sec">Администраторы</span></div>
        <div style="display:flex;flex-direction:column;gap:8px">
          <div v-for="u in userList" :key="u" style="display:flex;align-items:center;justify-content:space-between;background:#161B22;border:1px solid #30363D;border-radius:8px;padding:8px 12px">
            <span style="font-size:13px">&#128100; {{ u }}</span>
            <button :disabled="u==='admin'" @click="deleteUser(u)" class="btn-act" style="color:#F85149;border-color:#F85149">&#10005;</button>
          </div>
        </div>
        <div style="margin-top:16px;display:flex;flex-direction:column;gap:8px">
          <div class="sec" style="margin-bottom:4px">Добавить / Изменить пароль</div>
          <input v-model="newUser.username" class="field" placeholder="Логин"/>
          <input v-model="newUser.password" class="field" type="password" placeholder="Пароль"/>
          <input v-model="newUser.confirm" class="field" type="password" placeholder="Подтвердить пароль"/>
          <button @click="saveUser" class="btn-primary" style="width:fit-content">Сохранить</button>
          <div v-if="userMsg" :style="userOk?{color:'#3FB950'}:{color:'#F85149'}">{{ userMsg }}</div>
        </div>
      </div>

    </div>

    <!-- SSH DRAWER -->
    <div v-if="termAgent" class="drawer">
      <div class="drawer-hdr">
        <span style="color:#00D4FF">SSH &#8212; {{ termAgent.agent_id }}</span>
        <button @click="closeSSH" class="btn-x">&#10005;</button>
      </div>
      <div id="terminal"></div>
    </div>

    <!-- SETTINGS MODAL -->
    <div v-if="setsAgent" class="overlay" @click.self="setsAgent=null">
      <div class="modal">
        <div class="mtitle">&#9881; Настройки &#8212; {{ setsAgent.agent_id }}</div>
        <input v-model="sf.display_name" class="field" placeholder="Отображаемое имя"/>
        <input v-model="sf.description"  class="field" placeholder="Описание"/>
        <input v-model="sf.local_ip"     class="field" placeholder="Локальный IP (192.168.1.1)"/>
        <input v-model="sf.luci_url"     class="field" placeholder="LuCI URL (http://192.168.1.1)"/>
        <input v-model="sf.ssh_pass"     class="field" type="password" placeholder="SSH пароль (root)"/>
        <div style="display:flex;gap:6px">
          <input v-model="sf.address" class="field" placeholder="Адрес"/>
          <button @click="geocodeSf" class="btn-sm" style="white-space:nowrap" :disabled="!sf.address">&#128205;</button>
        </div>
        <div v-if="sf.lat&&sf.lng" style="font-size:11px;color:#3FB950">&#10003; {{ Number(sf.lat).toFixed(4) }}, {{ Number(sf.lng).toFixed(4) }}</div>
        <div style="display:flex;gap:8px;margin-top:4px">
          <button @click="saveSets" class="btn-primary">Сохранить</button>
          <button @click="setsAgent=null" class="btn-sm">Отмена</button>
        </div>
      </div>
    </div>

    <!-- ADD ROUTER MODAL -->
    <div v-if="addOpen" class="overlay" @click.self="addOpen=false">
      <div class="modal">
        <div class="mtitle">&#43; Добавить роутер</div>
        <input v-model="add.agent_id"     class="field" placeholder="ID агента (например: router-office)"/>
        <input v-model="add.display_name" class="field" placeholder="Название (необязательно)"/>
        <input v-model="add.description"  class="field" placeholder="Описание (необязательно)"/>
        <input v-model="add.host"         class="field" placeholder="IP роутера (192.168.1.1)"/>
        <input v-model="add.ssh_pass"     class="field" type="password" placeholder="SSH пароль root"/>
        <div style="display:flex;gap:6px">
          <input v-model="add.address" class="field" placeholder="Адрес (необязательно)"/>
          <button @click="geocodeAdd" class="btn-sm" style="white-space:nowrap" :disabled="!add.address">&#128205;</button>
        </div>
        <div v-if="add.lat&&add.lng" style="font-size:11px;color:#3FB950">&#10003; {{ add.lat.toFixed(4) }}, {{ add.lng.toFixed(4) }}</div>
        <div v-if="addStatus" :style="addOk?{color:'#3FB950'}:{color:'#F85149'}">{{ addStatus }}</div>
        <div style="display:flex;gap:8px;margin-top:4px">
          <button @click="doAdd" class="btn-primary" :disabled="addLoading">{{ addLoading?'Деплой...':'Установить агент' }}</button>
          <button @click="addOpen=false" class="btn-sm">Отмена</button>
        </div>
      </div>
    </div>

    <!-- CHARTS MODAL -->
    <div v-if="detailAgent" class="overlay" @click.self="detailAgent=null">
      <div class="modal" style="width:100%;max-width:620px;height:auto;max-height:none;overflow-y:visible;border-radius:0">
        <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:4px">
          <div class="mtitle">&#128202; {{ cfgs[detailAgent.agent_id]&&cfgs[detailAgent.agent_id].display_name || detailAgent.agent_id }}</div>
          <button @click="detailAgent=null" class="btn-x">&#10005;</button>
        </div>
        <div style="display:flex;gap:6px;margin-bottom:12px">
          <button v-for="p in periods" :key="p.v" @click="setPeriod(p.v)"
            :class="['btn-sm',detailPeriod===p.v&&'active']">{{ p.l }}</button>
        </div>
        <div style="display:flex;flex-direction:column;gap:16px">
          <div>
            <div style="font-size:11px;color:#8B949E;margin-bottom:4px;text-transform:uppercase;letter-spacing:.08em">Load Average</div>
            <div style="width:100%;background:#0D1117;border-radius:4px"><svg viewBox="0 0 320 100" preserveAspectRatio="none" style="width:100%;height:100px;display:block"><defs><linearGradient id="gLoad" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stop-color="#00D4FF" stop-opacity="0.18"/><stop offset="100%" stop-color="#00D4FF" stop-opacity="0"/></linearGradient></defs><line x1="4" y1="5" x2="316" y2="5" stroke="#21262D" stroke-width="0.5"/><line x1="4" y1="86" x2="316" y2="86" stroke="#21262D" stroke-width="0.5"/><polyline v-if="chartData.load.length>1" :points="svgArea(chartData.load)" stroke="none" style="fill:url(#gLoad)"/><polyline v-if="chartData.load.length>1" :points="svgPoints(chartData.load)" stroke="#00D4FF" stroke-width="2" fill="none"/><text v-if="chartData.load.length" x="316" y="16" text-anchor="end" fill="#00D4FF" font-size="10" font-family="monospace">{{chartData.load.length ? chartData.load[chartData.load.length-1].toFixed(2) : ""}}</text><text v-if="chartData.load.length>1" x="4" y="16" fill="#555E6B" font-size="9" font-family="monospace">{{chartData.load.length>1 ? "max:"+Math.max.apply(null,chartData.load).toFixed(2) : ""}}</text><text v-if="chartData.labels.length" x="4" y="99" fill="#555E6B" font-size="9" font-family="monospace">{{chartData.labels[0]}}</text><text v-if="chartData.labels.length" x="316" y="99" text-anchor="end" fill="#555E6B" font-size="9" font-family="monospace">{{chartData.labels[chartData.labels.length-1]}}</text></svg></div>
          </div>
          <div>
            <div style="font-size:11px;color:#8B949E;margin-bottom:4px;text-transform:uppercase;letter-spacing:.08em">RAM Free (MB)</div>
            <div style="width:100%;background:#0D1117;border-radius:4px"><svg viewBox="0 0 320 100" preserveAspectRatio="none" style="width:100%;height:100px;display:block"><defs><linearGradient id="gMem" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stop-color="#3FB950" stop-opacity="0.18"/><stop offset="100%" stop-color="#3FB950" stop-opacity="0"/></linearGradient></defs><line x1="4" y1="5" x2="316" y2="5" stroke="#21262D" stroke-width="0.5"/><line x1="4" y1="86" x2="316" y2="86" stroke="#21262D" stroke-width="0.5"/><polyline v-if="chartData.mem.length>1" :points="svgArea(chartData.mem)" stroke="none" style="fill:url(#gMem)"/><polyline v-if="chartData.mem.length>1" :points="svgPoints(chartData.mem)" stroke="#3FB950" stroke-width="2" fill="none"/><text v-if="chartData.mem.length" x="316" y="16" text-anchor="end" fill="#3FB950" font-size="10" font-family="monospace">{{chartData.mem.length ? chartData.mem[chartData.mem.length-1]+"MB" : ""}}</text><text v-if="chartData.mem.length>1" x="4" y="16" fill="#555E6B" font-size="9" font-family="monospace">{{chartData.mem.length>1 ? "max:"+Math.max.apply(null,chartData.mem)+"MB" : ""}}</text><text v-if="chartData.labels.length" x="4" y="99" fill="#555E6B" font-size="9" font-family="monospace">{{chartData.labels[0]}}</text><text v-if="chartData.labels.length" x="316" y="99" text-anchor="end" fill="#555E6B" font-size="9" font-family="monospace">{{chartData.labels[chartData.labels.length-1]}}</text></svg></div>
          </div>
          <div>
            <div style="font-size:11px;color:#8B949E;margin-bottom:4px;text-transform:uppercase;letter-spacing:.08em">Uptime (часы)</div>
            <div style="width:100%;background:#0D1117;border-radius:4px"><svg viewBox="0 0 320 100" preserveAspectRatio="none" style="width:100%;height:100px;display:block"><defs><linearGradient id="gUp" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stop-color="#D29922" stop-opacity="0.18"/><stop offset="100%" stop-color="#D29922" stop-opacity="0"/></linearGradient></defs><line x1="4" y1="5" x2="316" y2="5" stroke="#21262D" stroke-width="0.5"/><line x1="4" y1="86" x2="316" y2="86" stroke="#21262D" stroke-width="0.5"/><polyline v-if="chartData.up.length>1" :points="svgArea(chartData.up)" stroke="none" style="fill:url(#gUp)"/><polyline v-if="chartData.up.length>1" :points="svgPoints(chartData.up)" stroke="#D29922" stroke-width="2" fill="none"/><text v-if="chartData.up.length" x="316" y="16" text-anchor="end" fill="#D29922" font-size="10" font-family="monospace">{{chartData.up.length ? chartData.up[chartData.up.length-1]+"ч" : ""}}</text><text v-if="chartData.up.length>1" x="4" y="16" fill="#555E6B" font-size="9" font-family="monospace">{{chartData.up.length>1 ? "max:"+Math.max.apply(null,chartData.up)+"ч" : ""}}</text><text v-if="chartData.labels.length" x="4" y="99" fill="#555E6B" font-size="9" font-family="monospace">{{chartData.labels[0]}}</text><text v-if="chartData.labels.length" x="316" y="99" text-anchor="end" fill="#555E6B" font-size="9" font-family="monospace">{{chartData.labels[chartData.labels.length-1]}}</text></svg></div>
          </div>
        </div>
        <div v-if="detailLoading" style="text-align:center;color:#8B949E;padding:20px">Загрузка...</div>
      </div>
    </div>

    <!-- MESSAGE MODAL -->
    <div v-if="msg" class="overlay" @click.self="msg=''">
      <div class="modal">
        <div class="mtitle">Сообщение</div>
        <p style="font-size:13px;color:#8B949E;line-height:1.6">{{ msg }}</p>
        <button @click="msg=''" class="btn-sm">Закрыть</button>
      </div>
    </div>

  <nav v-if="token" class="mob-nav">
    <button @click="tab='dashboard'" :class="['mob-tab',tab==='dashboard'&&'active']"><span>&#128241;</span>Роутеры</button>
    <button @click="switchMap" :class="['mob-tab',tab==='map'&&'active']"><span>&#128506;</span>Карта</button>
    <button @click="tab='users';loadUsers()" :class="['mob-tab',tab==='users'&&'active']"><span>&#128100;</span>Админы</button>
  </nav>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { Terminal } from '@xterm/xterm'
import { FitAddon } from '@xterm/addon-fit'
import '@xterm/xterm/css/xterm.css'

// Полифиллы для старых WebView
if(!Array.prototype.at){Array.prototype.at=function(i){return i<0?this[this.length+i]:this[i]}}
if(!Element.prototype.replaceChildren){Element.prototype.replaceChildren=function(...n){while(this.firstChild)this.removeChild(this.firstChild);this.append(...n)}}
const BASE=import.meta.env.VITE_SERVER_URL||'http://localhost:9000'
const token=ref(localStorage.getItem('owm_token')||'')
const tab=ref('dashboard')
const agents=ref([])
const metrics=ref({})
const cfgs=ref({})
const serverOk=ref(false)
const lUser=ref('admin'),lPass=ref(''),lErr=ref('')
const termAgent=ref(null),setsAgent=ref(null),sf=ref({}),msg=ref('')
const detailAgent=ref(null),detailPeriod=ref('1h'),detailLoading=ref(false),chartKey=ref(0)
const periods=[{v:'1h',l:'1 час'},{v:'6h',l:'6 часов'},{v:'24h',l:'24 часа'}]
const addOpen=ref(false),addLoading=ref(false),addStatus=ref(''),addOk=ref(false)
const add=ref({agent_id:'',display_name:'',description:'',host:'',ssh_pass:'',address:'',lat:null,lng:null})
const userList=ref([])
const newUser=ref({username:'',password:'',confirm:''})
const userMsg=ref(''),userOk=ref(false)
const mapSelected=ref(null)
const svgPoints=(d)=>{if(!d||d.length<2)return "";const mn=Math.min.apply(null,d),mx=Math.max.apply(null,d),rng=(mx-mn)||1;return d.map(function(v,i){var x=(4+(i/(d.length-1))*312).toFixed(1),y=(86-((v-mn)/rng)*80).toFixed(1);return x+","+y}).join(" ")};const svgArea=(d)=>{if(!d||d.length<2)return "";const mn=Math.min.apply(null,d),mx=Math.max.apply(null,d),rng=(mx-mn)||1;const pts=d.map(function(v,i){var x=(4+(i/(d.length-1))*312).toFixed(1),y=(86-((v-mn)/rng)*80).toFixed(1);return x+","+y});return pts[0].split(",")[0]+",86 "+pts.join(" ")+" "+pts[pts.length-1].split(",")[0]+",86"}
const chartData=ref({labels:[],load:[],mem:[],up:[]})
const sidebarOpen=ref(true)
const currentUser=ref(localStorage.getItem('owm_user')||'')
let term=null,termBuf='',leafMap=null,timer=null

const api=(p,o={})=>fetch(BASE+p,{...o,headers:{'Authorization':'Bearer '+token.value,'Content-Type':'application/json',...(o.headers||{})}})
const fMem=kb=>kb>1048576?(kb/1048576).toFixed(1)+'GB':(kb/1024).toFixed(0)+'MB'
const fTraffic=b=>b>1073741824?(b/1073741824).toFixed(1)+'GB':b>1048576?(b/1048576).toFixed(1)+'MB':b>1024?(b/1024).toFixed(0)+'KB':'0'
const fUp=s=>{const d=Math.floor(s/86400),h=Math.floor((s%86400)/3600);return d?d+'д '+h+'ч':h+'ч'}

function openAdd(){addOpen.value=true;addStatus.value='';add.value={agent_id:'',display_name:'',description:'',host:'',ssh_pass:'',address:'',lat:null,lng:null}}
async function doAdd(){
  if(!add.value.host||!add.value.ssh_pass||!add.value.agent_id){addStatus.value='Заполни ID, IP и пароль';addOk.value=false;return}
  addLoading.value=true;addStatus.value='Подключаемся по SSH...'
  const body={...add.value,port:22,lat:add.value.lat||null,lng:add.value.lng||null}
  const r=await api('/bootstrap',{method:'POST',body:JSON.stringify(body)})
  const d=await r.json()
  addLoading.value=false
  if(d.success){addOk.value=true;addStatus.value='✓ '+d.data.message;loadAll()}
  else{addOk.value=false;addStatus.value='✗ '+d.message}
}

async function doLogin(){
  lErr.value=''
  const r=await fetch(BASE+'/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:lUser.value,password:lPass.value})})
  if(r.ok){const d=await r.json();token.value=d.access_token;localStorage.setItem('owm_token',d.access_token);currentUser.value=lUser.value;localStorage.setItem('owm_user',lUser.value);loadAll();loadUsers();if(!timer)timer=setInterval(loadAll,10000)}
  else lErr.value='Неверный логин или пароль'
}
function logout(){token.value='';localStorage.removeItem('owm_token');localStorage.removeItem('owm_user');agents.value=[];clearInterval(timer);timer=null}

let loadingAll=false
async function loadAll(){
  if(loadingAll)return;loadingAll=true
  try{
    const r=await api('/agents');serverOk.value=r.ok;if(!r.ok)return
    agents.value=(await r.json()).data||[]
    for(const a of agents.value)if(a.online)loadMetric(a.agent_id)
    loadCfgs()
  }catch{serverOk.value=false}finally{loadingAll=false}
}
async function loadMetric(id){
  const r=await api('/metrics/'+id+'?limit=60');if(!r.ok)return
  const rows=(await r.json()).data||[]
  if(rows.length)metrics.value[id]=rows[rows.length-1]
}
async function loadCfgs(){
  const r=await api('/configs');if(!r.ok)return
  const map={};for(const c of((await r.json()).data||[]))map[c.agent_id]=c
  cfgs.value=map;if(leafMap)updateMarkers()
}

async function loadUsers(){
  const r=await api('/users');if(r.ok)userList.value=(await r.json()).data||[]
}
async function saveUser(){
  if(!newUser.value.username||!newUser.value.password){userMsg.value='Заполни оба поля';userOk.value=false;return}
  const r=await api('/users',{method:'POST',body:JSON.stringify(newUser.value)})
  userOk.value=r.ok;userMsg.value=r.ok?'✓ Сохранено':'✗ Ошибка'
  if(r.ok){newUser.value={username:'',password:'',confirm:''};loadUsers()}
}
async function deleteUser(u){
  if(!window.confirm('Удалить '+u+'?'))return
  await api('/users/'+u,{method:'DELETE'});loadUsers()
}

async function openSSH(agent){
  if(term){term.dispose();term=null}
  termAgent.value=agent;termBuf=''
  await nextTick()
  const fitAddon=new FitAddon()
  term=new Terminal({cursorBlink:true,fontSize:13,fontFamily:'JetBrains Mono,monospace',
    theme:{background:'#0D1117',foreground:'#E6EDF3',cursor:'#00D4FF'}})
  term.loadAddon(fitAddon)
  term.open(document.getElementById('terminal'))
  fitAddon.fit()
  term.writeln('\x1b[1;36mOpenWRT NetCtrl\x1b[0m')
  term.writeln('\x1b[90mExec-terminal. Набери команду и нажми Enter.\x1b[0m\r\n')
  term.write('\x1b[32m'+agent.agent_id+'\x1b[0m:~# ')
  const _drawer=document.querySelector('.drawer')
  const _adjustDrawer=()=>{
    if(!_drawer)return
    const vph=window.visualViewport?window.visualViewport.height:window.innerHeight
    const maxH=Math.max(120,vph-50)
    _drawer.style.height=maxH+'px'
    try{fitAddon.fit();setTimeout(()=>{term.scrollToBottom()},50)}catch(e){}
  }
  _adjustDrawer()
  if(window.visualViewport)window.visualViewport.addEventListener('resize',_adjustDrawer)
  window.addEventListener('resize',_adjustDrawer)
  term._vpCleanup=()=>{
    if(window.visualViewport)window.visualViewport.removeEventListener('resize',_adjustDrawer)
    window.removeEventListener('resize',_adjustDrawer)
    if(_drawer)_drawer.style.height=''
  }
  term.onData(async data=>{
    const code=data.charCodeAt(0)
    if(data==='\r'){
      term.write('\r\n');const cmd=termBuf.trim();termBuf=''
      if(cmd){
        term.write('\x1b[90m...\x1b[0m')
        const out=await runCmd(agent.agent_id,cmd)
        term.write('\r   \r')
        if(out){term.write(out.replace(/\n/g,'\r\n'));setTimeout(()=>term.scrollToBottom(),80);setTimeout(()=>term.scrollToBottom(),300)}
      }
      term.write('\x1b[32m'+agent.agent_id+'\x1b[0m:~# ')
    }else if(data==='\x7f'){if(termBuf.length){termBuf=termBuf.slice(0,-1);term.write('\b \b')}}
    else if(code>=32){termBuf+=data;term.write(data)}
  })
}
async function runCmd(agentId,cmd){
  const r=await api('/exec/'+agentId,{method:'POST',body:JSON.stringify({command:cmd})})
  const reqId=(await r.json()).data?.request_id;if(!reqId)return 'Ошибка\n'
  for(let i=0;i<30;i++){
    await new Promise(res=>setTimeout(res,300))
    const h=await api('/history/'+agentId)
    const e=((await h.json()).data||[]).find(x=>x.request_id===reqId)
    if(e?.status==='done')return(e.stdout||'')+(e.stderr?'\x1b[31m'+e.stderr+'\x1b[0m':'')
    if(e?.status==='error')return'\x1b[31mОшибка\x1b[0m\n'
  }
  return'\x1b[33mТаймаут\x1b[0m\n'
}
function closeSSH(){if(term){if(term._vpCleanup)term._vpCleanup();term.dispose();term=null};termAgent.value=null}

async function openLuCI(agent){
  const c=cfgs.value[agent.agent_id]
  if(!c?.luci_url||!c?.ssh_pass){openSets(agent);msg.value='Укажи LuCI URL и SSH пароль в настройках';return}
  const r=await api('/luci-login/'+agent.agent_id,{method:'POST'})
  const d=await r.json()
  if(d.data?.url)window.open(d.data.url,'_blank')
  else msg.value=d.message||'Не удалось подключиться к LuCI'
}

function openSets(agent){
  setsAgent.value=agent
  const c=cfgs.value[agent.agent_id]||{}
  sf.value={display_name:c.display_name||'',description:c.description||'',local_ip:c.local_ip||'',luci_url:c.luci_url||'',ssh_pass:c.ssh_pass||'',address:c.address||'',lat:c.lat||null,lng:c.lng||null}
}
async function geocode(addr){
  const r=await fetch('https://nominatim.openstreetmap.org/search?format=json&q='+encodeURIComponent(addr))
  const d=await r.json()
  if(d&&d.length)return{lat:parseFloat(d[0].lat),lng:parseFloat(d[0].lon)}
  return null
}
async function geocodeAdd(){
  const res=await geocode(add.value.address)
  if(res){add.value.lat=res.lat;add.value.lng=res.lng}
  else alert('Адрес не найден')
}
async function geocodeSf(){
  const res=await geocode(sf.value.address)
  if(res){sf.value.lat=res.lat;sf.value.lng=res.lng}
  else alert('Адрес не найден')
}
async function deleteRouter(agent){
  if(!window.confirm('Удалить роутер '+agent.agent_id+'?'))return
  const r=await api('/config/'+agent.agent_id,{method:'DELETE'})
  if(r.status===401){logout();return}
  const d=await r.json()
  if(!d.success){msg.value=d.message||'Ошибка удаления';return}
  delete metrics.value[agent.agent_id]
  delete cfgs.value[agent.agent_id]
  agents.value=agents.value.filter(a=>a.agent_id!==agent.agent_id)
}
async function openDetail(agent){
  detailAgent.value=null;await nextTick()
  chartKey.value++;detailAgent.value=agent;detailPeriod.value='1h'
  await nextTick();await loadCharts(agent.agent_id,'1h')
}
async function setPeriod(p){
  detailPeriod.value=p
  if(detailAgent.value)await loadCharts(detailAgent.value.agent_id,p)
}
async function loadCharts(id,period){
  detailLoading.value=true
  const limitMap={'1h':360,'6h':2160,'24h':8640}
  const r=await api('/metrics/'+id+'?limit='+(limitMap[period]||360))
  if(!r.ok){detailLoading.value=false;return}
  const rows=(await r.json()).data||[]
  detailLoading.value=false;if(!rows.length)return
  const step=Math.max(1,Math.floor(rows.length/120))
  const sampled=rows.filter((_,i)=>i%step===0)
  const labels=sampled.map(r=>{const d=new Date(r.timestamp*1000);return d.getHours().toString().padStart(2,'0')+':'+d.getMinutes().toString().padStart(2,'0')})
  const cfg=(label,color,data)=>({type:'line',data:{labels,datasets:[{label,data,borderColor:color,backgroundColor:color+'22',borderWidth:2,pointRadius:0,fill:true,tension:0.3}]},options:{responsive:true,maintainAspectRatio:false,animation:false,plugins:{legend:{display:false}},scales:{x:{ticks:{color:'#8B949E',maxTicksLimit:8,font:{size:10}},grid:{color:'#21262D'}},y:{ticks:{color:'#8B949E',font:{size:10}},grid:{color:'#21262D'}}}}})
  const mkChart=(id,label,color,data)=>{const el=document.getElementById(id);if(!el)return;const ex=window.Chart?.getChart?.(el);if(ex)ex.destroy();return new window.Chart(el,cfg(label,color,data))}
  chartData.value={
    labels,
    load:sampled.map(r=>r.load1),
    mem:sampled.map(r=>Math.round(r.mem_free/1024)),
    up:sampled.map(r=>Math.round(r.uptime/3600))
  }
}
async function saveSets(){
  const id=setsAgent.value.agent_id
  await api('/config/'+id,{method:'POST',body:JSON.stringify({agent_id:id,display_name:sf.value.display_name||null,description:sf.value.description||null,local_ip:sf.value.local_ip||null,luci_url:sf.value.luci_url||null,ssh_pass:sf.value.ssh_pass||null,address:sf.value.address||null,lat:sf.value.lat||null,lng:sf.value.lng||null})})
  setsAgent.value=null;await loadCfgs();updateMarkers()
}
async function switchMap(){tab.value="map";await nextTick();const _ml=document.querySelector(".map-layout");if(_ml){_ml.style.height=(window.innerHeight-104)+"px";}setTimeout(()=>{initMap();setTimeout(()=>{leafMap?.invalidateSize()},200);setTimeout(()=>{leafMap?.invalidateSize()},700);setTimeout(()=>{leafMap?.invalidateSize()},1500)},150)}
function initMap(){
  if(!window.L){setTimeout(initMap,500);return}
  const mapEl=document.getElementById('map');if(mapEl){const h=window.innerHeight-110;mapEl.style.minHeight=h+'px';mapEl.style.height=h+'px'}
  if(leafMap){const _c=leafMap.getContainer();if(_c&&_c.offsetHeight<10){leafMap.remove();leafMap=null;window._mk={};}else{leafMap.invalidateSize();return}}
  leafMap=window.L.map('map',{zoomControl:false}).setView([55.75,37.62],5)
  window.L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{attribution:'© OpenStreetMap'}).addTo(leafMap)
  window.L.control.zoom({position:'topright'}).addTo(leafMap)
  leafMap.on('click',async e=>{
    if(!mapSelected.value)return
    const{lat,lng}=e.latlng;const id=mapSelected.value;const cfg=cfgs.value[id]||{}
    await api('/config/'+id,{method:'POST',body:JSON.stringify({agent_id:id,display_name:cfg.display_name||null,description:cfg.description||null,local_ip:cfg.local_ip||null,luci_url:cfg.luci_url||null,ssh_pass:cfg.ssh_pass||null,address:cfg.address||null,lat,lng})})
    mapSelected.value=null;await loadCfgs();updateMarkers()
  })
  updateMarkers()
}
function selectMapRouter(agent){
  mapSelected.value=agent.agent_id
  const c=cfgs.value[agent.agent_id]
  if(c?.lat&&c?.lng&&leafMap)leafMap.setView([c.lat,c.lng],12)
}
function updateMarkers(){
  if(!leafMap)return;if(!window._mk)window._mk={}
  const agentMap={};for(const a of agents.value)agentMap[a.agent_id]=a
  for(const id of Object.keys(cfgs.value)){
    const c=cfgs.value[id];if(!c?.lat||!c?.lng)continue
    const a=agentMap[id]||{agent_id:id,online:false}
    const col=a.online?'#3FB950':'#F85149'
    const icon=window.L.divIcon({html:'<div style="width:14px;height:14px;border-radius:50%;background:'+col+';border:2px solid #fff;box-shadow:0 0 8px '+col+'"></div>',iconSize:[14,14],className:''})
    if(window._mk[id])window._mk[id].remove()
    const lm=metrics.value[id]
    const pop='<b>'+(c.display_name||id)+'</b><br>'+(a.online?'&#128994; Online':'&#128308; Offline')+(lm?'<br>Load: '+lm.load1.toFixed(2):'')
    window._mk[id]=window.L.marker([c.lat,c.lng],{icon}).bindPopup(pop).addTo(leafMap)
  }
}


onMounted(()=>{if(token.value){loadAll();loadUsers()};timer=setInterval(loadAll,10000)})
onUnmounted(()=>clearInterval(timer))
</script>

<style>
*,*::before,*::after{box-sizing:border-box;margin:0;padding:0}
body{background:#0D1117;color:#E6EDF3;font-family:'JetBrains Mono',monospace,sans-serif;font-size:14px}
.app{min-height:100vh;display:flex;flex-direction:column}
.hdr{display:flex;align-items:center;gap:12px;padding:0 1.25rem;height:50px;background:#161B22;border-bottom:1px solid #30363D;flex-shrink:0}
.logo{font-size:14px;font-weight:700;color:#00D4FF;letter-spacing:.06em;white-space:nowrap}
.tabs{display:flex;gap:3px;margin-left:8px}
.tab{background:none;border:none;color:#8B949E;padding:5px 14px;border-radius:6px;cursor:pointer;font-size:13px;font-family:inherit}
.tab:hover{color:#E6EDF3;background:#1C2128}.tab.active{color:#00D4FF;background:#1C2128}
.hdr-r{display:flex;align-items:center;gap:10px;margin-left:auto}
.srv{font-size:12px}.srv.ok{color:#3FB950}.srv.err{color:#F85149}
.btn-sm{background:#1C2128;border:1px solid #30363D;color:#8B949E;padding:5px 12px;border-radius:6px;cursor:pointer;font-size:12px;font-family:inherit}
.btn-sm:hover{border-color:#00D4FF;color:#00D4FF}
.btn-primary{background:#00D4FF;border:none;border-radius:6px;color:#0D1117;padding:8px 20px;font-size:13px;font-weight:700;cursor:pointer;font-family:inherit}
.btn-primary:hover{background:#33DCFF}
.btn-act{background:#1C2128;border:1px solid #30363D;color:#8B949E;padding:4px 10px;border-radius:5px;cursor:pointer;font-size:12px;font-family:inherit}
.btn-act:hover:not([disabled]){border-color:#00D4FF;color:#00D4FF}.btn-act[disabled]{opacity:.4;cursor:not-allowed}
.btn-x{background:none;border:none;color:#8B949E;font-size:18px;cursor:pointer;line-height:1}
.field{background:#1C2128;border:1px solid #30363D;border-radius:6px;color:#E6EDF3;padding:7px 12px;font-size:13px;font-family:inherit;width:100%}
.field::placeholder{color:#8B949E}.field:focus{outline:none;border-color:#00D4FF}
.txt-err{font-size:12px;color:#F85149}
.main{flex:1;padding:1.25rem}
.toolbar{display:flex;align-items:center;justify-content:space-between;margin-bottom:1rem}
.sec{font-size:11px;color:#8B949E;text-transform:uppercase;letter-spacing:.08em}
.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(230px,1fr));gap:10px}
.card{background:#161B22;border:1px solid #30363D;border-radius:10px;padding:.85rem;display:flex;flex-direction:column;gap:7px}
.card-on{border-left:3px solid #3FB950}.card-off{border-left:3px solid #F85149;opacity:.65}
.card-hdr{display:flex;align-items:center;gap:7px}
.dot{width:8px;height:8px;border-radius:50%;flex-shrink:0}
.dot-g{background:#3FB950;animation:pu 2s infinite}.dot-r{background:#F85149}
@keyframes pu{0%,100%{opacity:1}50%{opacity:.3}}
.cname{font-size:13px;font-weight:600;flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.badge{font-size:10px;padding:2px 6px;border-radius:3px;flex-shrink:0}
.badge-on{background:rgba(63,185,80,.15);color:#3FB950}.badge-off{background:rgba(248,81,73,.15);color:#F85149}
.card-desc{font-size:11px;color:#8B949E;margin-top:-4px}
.card-addr{font-size:11px;color:#8B949E}
.mblock{display:flex;flex-direction:column;gap:3px}
.mrow{display:flex;justify-content:space-between;font-size:12px}
.mrow span:first-child{color:#8B949E}
.card-btns{display:flex;gap:6px}
.map-layout{display:flex;gap:10px;height:calc(100vh - 90px)}
.map-sidebar{width:220px;flex-shrink:0;background:#161B22;border:1px solid #30363D;border-radius:10px;overflow-y:auto;display:flex;flex-direction:column;gap:2px;padding:8px}
.map-sidebar-title{font-size:11px;color:#8B949E;text-transform:uppercase;letter-spacing:.08em;padding:4px 6px;margin-bottom:4px}
.map-router-item{display:flex;align-items:flex-start;gap:8px;padding:8px;border-radius:6px;cursor:pointer;border:1px solid transparent}
.map-router-item:hover{background:#1C2128;border-color:#30363D}
.map-router-selected{background:#1C2128;border-color:#00D4FF !important}
.map-hint{margin-top:8px;padding:8px;background:rgba(0,212,255,.08);border:1px solid #00D4FF;border-radius:6px;font-size:11px;color:#00D4FF;text-align:center}
#map{flex:1;border-radius:10px;overflow:hidden}
.drawer{position:fixed;bottom:54px;left:0;right:0;height:300px;background:#161B22;border-top:1px solid #30363D;display:flex;flex-direction:column;z-index:50}
.drawer-hdr{display:flex;align-items:center;justify-content:space-between;padding:8px 14px;border-bottom:1px solid #30363D;font-size:13px;font-weight:600}
#terminal{flex:1;padding:6px;overflow:hidden}
.overlay{position:fixed;top:0;left:0;right:0;bottom:0;background:rgba(0,0,0,.78);display:flex;align-items:flex-start;justify-content:center;z-index:300;padding:8px 0 70px}
.modal{background:#161B22;border:1px solid #30363D;border-radius:12px;padding:1.5rem;width:400px;display:flex;flex-direction:column;gap:10px}
.mtitle{font-size:14px;font-weight:700;color:#00D4FF}
.login-wrap{flex:1;display:flex;align-items:center;justify-content:center}
.login-card{background:#161B22;border:1px solid #30363D;border-radius:12px;padding:2rem;width:290px;display:flex;flex-direction:column;gap:11px}
.login-title{font-size:17px;font-weight:700;color:#00D4FF;text-align:center;margin-bottom:6px}

.map-toggle{display:none;position:absolute;z-index:1001;top:10px;left:10px;background:#161B22;border:1px solid #00D4FF;color:#00D4FF;padding:8px 14px;border-radius:6px;cursor:pointer;font-size:13px;font-family:inherit;white-space:nowrap}
@media(max-width:700px){
  .map-toggle{display:block}
  .map-layout{position:relative;flex-direction:column;height:calc(100vh - 104px);overflow:hidden}
  .main{padding:0}
  .map-sidebar{position:absolute;top:0;left:0;z-index:1000;height:100%;width:200px;border-radius:0;padding-top:50px;background:rgba(22,27,34,0.82);backdrop-filter:blur(4px)}
  #map{position:absolute;top:0;left:0;width:100%;height:100%}
}

@media(max-width:700px){
  .tabs{display:none}
  .logo{font-size:13px}
  .mob-nav{display:flex}
  .main{padding-bottom:60px}
}
@media(min-width:701px){
  .mob-nav{display:none}
}
.mob-nav{position:fixed;bottom:0;left:0;right:0;background:#161B22;border-top:1px solid #30363D;z-index:200;justify-content:space-around;align-items:center;height:54px}
.mob-tab{background:none;border:none;color:#8B949E;font-size:10px;font-family:inherit;cursor:pointer;display:flex;flex-direction:column;align-items:center;gap:2px;padding:6px 12px}
.mob-tab.active{color:#00D4FF}
.mob-tab span{font-size:18px}
</style>
