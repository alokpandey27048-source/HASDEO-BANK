const $ = (id) => document.getElementById(id);
let publicMaintenance = false;
let publicCustomerSignupEnabled = true;
let publicCustomerLoginEnabled = true;
let sessionPingTimer = null;
let presenceRefreshTimer = null;
let publicStatusTimer = null;
let isAdminSession = false;
let lastAnnouncementStamp = null;
let balanceRefreshTimer = null;
let balanceRefreshBusy = false;
let latestAccountData = null;
let latestTransactions = [];
let currentAccountRole = 'CUSTOMER';
let ownerAnalyticsLoaded = false;

function openLogin(){openCustomerLogin();}
function openCustomerLogin(){showAuthForm('customer');$('loginModal').classList.remove('hidden');}
function openSignup(){showAuthForm('signup');$('loginModal').classList.remove('hidden');}
function openAdminLogin(){showAuthForm('admin');$('loginModal').classList.remove('hidden');}
function showAuthForm(mode){const forms={customer:'customerLoginForm',signup:'signupForm',admin:'loginForm'};if((mode==='signup'&&!publicCustomerSignupEnabled)||(mode==='customer'&&!publicCustomerLoginEnabled)){Object.values(forms).forEach(id=>$(id).classList.add('hidden'));$('loginModal').classList.remove('hidden');$('authTitle').innerHTML=mode==='signup'?'Signups are <em>paused.</em>':'Customer login is <em>paused.</em>';$('authDescription').textContent='This customer access option is temporarily disabled by the website owner.';$('loginError').textContent=mode==='signup'?'New customer signups are temporarily closed.':'Customer login is temporarily disabled.';return;}Object.values(forms).forEach(id=>$(id).classList.add('hidden'));$(forms[mode]).classList.remove('hidden');$('loginError').textContent='';$('authTitle').innerHTML=mode==='signup'?'Create your <em>account.</em>':mode==='admin'?'Admin <em>sign in.</em>':'Customer <em>sign in.</em>';$('authDescription').textContent=mode==='signup'?'Create a free educational demo account.':mode==='admin'?'Separate secure access for the website administrator.':'Sign in to your educational demo account.';}
function closeLogin(){ $('loginModal').classList.add('hidden'); }
function toast(message){ const el=$('toast'); el.textContent=message; el.classList.add('show'); setTimeout(()=>el.classList.remove('show'),2800); }
function escapeHtml(value){return String(value??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));}
function money(value){
  const n=Number(value)||0;
  const exact=new Intl.NumberFormat('en-IN',{style:'currency',currency:'INR',minimumFractionDigits:2,maximumFractionDigits:2}).format(n);
  const crore=10000000, lakh=100000;
  if(Math.abs(n)>=crore) return `${exact} (${new Intl.NumberFormat('en-IN',{maximumFractionDigits:2}).format(n/crore)} Cr)`;
  if(Math.abs(n)>=lakh) return `${exact} (${new Intl.NumberFormat('en-IN',{maximumFractionDigits:2}).format(n/lakh)} Lakh)`;
  return exact;
}

async function api(path,data,method='POST'){
  const response=await fetch('/api'+path,{method,credentials:'same-origin',cache:'no-store',headers:{'Content-Type':'application/json'},...(data!==undefined&&data!==null?{body:JSON.stringify(data)}:{})});
  const payload=await response.json().catch(()=>({}));
  if(!response.ok) throw new Error(payload.message||payload.error||'Something went wrong.');
  return payload;
}

function formatTransactionDate(transaction){
  // API sends an ISO-8601 timestamp with an explicit offset; display it in India time only.
  if(transaction.timestamp){
    const parsed=new Date(transaction.timestamp);
    if(!Number.isNaN(parsed.getTime())) return new Intl.DateTimeFormat('en-IN',{
      timeZone:'Asia/Kolkata',day:'2-digit',month:'short',year:'numeric',hour:'2-digit',minute:'2-digit',hour12:true
    }).format(parsed);
  }
  return transaction.date||'—';
}

function syncPageVisibility(){
  const dashboardOpen=!$('dashboard').classList.contains('hidden');
  // Keep the authenticated dashboard independent from the landing page's document flow.
  document.body.classList.toggle('dashboard-active',dashboardOpen);
  const maintenanceVisible=publicMaintenance&&!isAdminSession&&!dashboardOpen;
  $('maintenanceScreen').classList.toggle('hidden',!maintenanceVisible);
  document.querySelector('.topbar').classList.toggle('hidden',dashboardOpen||maintenanceVisible);
  document.querySelector('main').classList.toggle('hidden',dashboardOpen||maintenanceVisible);
  document.querySelector('footer').classList.toggle('hidden',dashboardOpen||maintenanceVisible);
}

async function checkPublicStatus(){
  try{
    const status=await api('/public/status',null,'GET');
    publicMaintenance=Boolean(status.maintenance);
    publicCustomerSignupEnabled=status.customerSignupEnabled!==false;
    publicCustomerLoginEnabled=status.customerLoginEnabled!==false;
    document.querySelector('.nav-signup')?.classList.toggle('hidden',!publicCustomerSignupEnabled);
    document.querySelector('.nav-login')?.classList.toggle('hidden',!publicCustomerLoginEnabled);
    $('customerLoginSwitch')?.classList.toggle('hidden',!publicCustomerLoginEnabled);
    $('customerSignupSwitch')?.classList.toggle('hidden',!publicCustomerSignupEnabled);
    $('maintenanceMessage').textContent=status.message||'The website is temporarily unavailable.';
    if(status.siteTitle) document.title=status.siteTitle+' | Banking, made premium';
    $('announcementBar').textContent=status.announcement||'';
    $('announcementBar').classList.toggle('hidden',!status.announcement);
    const stamp=status.announcementUpdatedAt||'';
    if(stamp){
      const stored=localStorage.getItem('hasdeoAnnouncementSeenAt')||'';
      if(stamp!==stored && status.announcement) toast('New announcement: '+status.announcement);
      if(stamp!==stored) localStorage.setItem('hasdeoAnnouncementSeenAt',stamp);
    }
    syncPageVisibility();
  }catch(error){console.warn('Could not load public website status:',error.message);}
}

function renderDashboardServices(){
 const nav=$('dashboardServiceNav'),grid=$('dashboardServiceGrid');if(!nav||!grid)return;
 const core=[{id:'overview',icon:'↗',title:'Account Overview'},{id:'deposit',icon:'＋',title:'Deposit Funds'},{id:'withdraw',icon:'−',title:'Withdraw Funds'},{id:'transfer',icon:'⇄',title:'Transfer Demo Funds'}];
 const all=[...core,...serviceDefinitions];
 nav.innerHTML=all.map(x=>`<button type="button" class="side-link" data-open-service="${x.id}">${x.icon} &nbsp; ${escapeHtml(x.title)}</button>`).join('');
 grid.innerHTML=all.map(x=>`<button type="button" class="dashboard-service-card" data-open-service="${x.id}"><span class="service-icon">${x.icon}</span><strong>${escapeHtml(x.title)}</strong><small>${escapeHtml(x.desc||'Open your dedicated service dashboard')}</small></button>`).join('');
 [nav,grid].forEach(root=>root.querySelectorAll('[data-open-service]').forEach(btn=>btn.addEventListener('click',()=>openServiceWorkspace(btn.dataset.openService))));
}
async function enterDashboard(role){
  const isAdmin=role==='ADMIN';isAdminSession=isAdmin;
  closeLogin();
  $('dashboard').classList.remove('hidden');$('ownerPanel').classList.add('hidden');
  window.scrollTo({top:0,left:0,behavior:'instant'});
  $('dashboard').scrollTop=0;
  syncPageVisibility();
  renderDashboardServices();document.querySelectorAll('.owner-nav').forEach(el=>el.classList.toggle('hidden',!isAdmin));
  $('customerAccountCard').classList.remove('hidden');
  $('dashboard').dataset.role=role;
  document.querySelector('.admin-pill').innerHTML='<span></span> '+(isAdmin?'Admin demo':'Customer demo');
  document.querySelector('.side .brand small').textContent=isAdmin?'ADMIN PORTAL':'CUSTOMER PORTAL';
  await loadAccount();if(isAdmin)await loadOwnerOverview();
  const savedLanguage=localStorage.getItem('hasdeoLanguage');if(savedLanguage&&$('languageSelect')){$('languageSelect').value=savedLanguage;$('languageSelect').dispatchEvent(new Event('change'));}
  if(sessionPingTimer)clearInterval(sessionPingTimer);api('/session/ping',{}).catch(()=>{});sessionPingTimer=setInterval(()=>api('/session/ping',{}).catch(()=>{}),20000);
  if(balanceRefreshTimer)clearInterval(balanceRefreshTimer);
  balanceRefreshTimer=setInterval(async()=>{
    if(balanceRefreshBusy || $('dashboard').classList.contains('hidden')) return;
    balanceRefreshBusy=true;
    try { await loadAccount(); if(isAdmin && !$('ownerPanel').classList.contains('hidden')) await loadOwnerOverview(); }
    catch(error) { console.warn('Automatic account refresh skipped:',error.message); }
    finally { balanceRefreshBusy=false; }
  },4000);
  // Re-assert the dashboard's own scroll position after async account/owner data loads.
  window.scrollTo({top:0,left:0,behavior:'instant'});
  $('dashboard').scrollTop=0;
  toast(isAdmin?'Welcome to the admin dashboard.':'Welcome to your demo account.');
}
$('loginForm').addEventListener('submit',async e=>{e.preventDefault();try{await api('/login',{username:$('username').value.trim(),password:$('password').value});await enterDashboard('ADMIN');}catch(err){$('loginError').textContent=err.message;}});
$('customerLoginForm').addEventListener('submit',async e=>{e.preventDefault();try{await api('/customer-login',{username:$('customerLoginUsername').value.trim(),password:$('customerLoginPassword').value});await enterDashboard('CUSTOMER');}catch(err){$('loginError').textContent=err.message;}});
$('signupForm').addEventListener('submit',async e=>{e.preventDefault();if($('signupPassword').value!==$('signupConfirm').value){$('loginError').textContent='Passwords do not match.';return;}try{const result=await api('/signup',{name:$('signupName').value.trim(),username:$('signupUsername').value.trim(),email:$('signupEmail').value.trim(),cardTier:$('signupCardTier').value,password:$('signupPassword').value});$('signupForm').reset();if(result.requiresLogin){$('loginError').textContent=result.message||'Account created, but customer login is currently disabled. Please contact the website owner.';toast('Demo account created; login is currently OFF.');return;}await enterDashboard('CUSTOMER');toast('Your demo account has been created.');}catch(err){$('loginError').textContent=err.message;}});

async function loadAccount(){
  const data=await api('/account',null,'GET');
  latestAccountData=data; latestTransactions=data.transactions||[]; currentAccountRole=data.role||'CUSTOMER';
  $('balance').textContent=money(data.balance);
  $('detailName').textContent=data.name||'—';$('detailCardName').textContent=(data.name||'CUSTOMER').toUpperCase();$('detailType').textContent=data.accountType||'Savings';applyCardTier(data.cardTier||'Classic');$('detailStatus').textContent=data.status||'Active';$('accountNumberSummary').textContent='•••• '+String(data.cardNumber||'').slice(-4);$('accountTypeSummary').textContent=data.accountType||'Savings';$('accountStatusSummary').textContent=data.status||'Active';
  const card=String(data.cardNumber||'').replace(/(.{4})/g,'$1 ').trim();$('detailCardNumber').textContent=card||'—';$('detailCardCopy').textContent=card||'—';$('detailTransferCode').textContent=data.transferCode||'—';$('detailCodeCopy').textContent=data.transferCode||'—';
  const customer=data.role==='CUSTOMER';$('openCardUpgrade').classList.toggle('hidden',!customer);$('accountSectionEyebrow').textContent=customer?'YOUR ACCOUNT DETAILS':'ADMIN ACCOUNT DETAILS';$('accountSectionTitle').innerHTML=customer?'My <em>Account</em>':'Admin <em>Account</em>';$('customerAccountCard').classList.remove('hidden');document.querySelectorAll('.owner-nav').forEach(el=>el.classList.toggle('hidden',customer));$('transferForm').closest('.action-card').classList.remove('hidden');
  renderTransactions(); renderNotifications(); renderQrCode(); updateSavingsGoal();
  $('passwordChangeForm').classList.toggle('hidden',!customer);
}
function renderTransactions(){
  const query=($('transactionSearch')?.value||'').trim().toLowerCase();const type=$('transactionType')?.value||'ALL';
  const rows=latestTransactions.filter(t=>(type==='ALL'||t.type===type)&&(!query||String(t.description).toLowerCase().includes(query)||String(t.id).toLowerCase().includes(query)||String(t.amount).includes(query)));
  $('transactionRows').innerHTML=rows.map(t=>`<tr><td>${escapeHtml(t.description)}</td><td>${escapeHtml(formatTransactionDate(t))}</td><td class="${t.type==='CREDIT'?'credit':'debit'}">${escapeHtml(t.type)}</td><td class="right ${t.type==='CREDIT'?'credit':'debit'}">${t.type==='CREDIT'?'+':'−'} ${money(t.amount)}</td></tr>`).join('')||'<tr><td colspan="4">No matching transactions.</td></tr>';
}
function renderNotifications(){
  const rows=latestTransactions.slice(0,8);$('notificationCount').textContent=rows.length;
  $('notificationList').innerHTML=rows.map(t=>`<div class="notification-item"><b>${escapeHtml(t.type==='CREDIT'?'Money added':'Money deducted')}</b><span>${escapeHtml(t.description)} · ${money(t.amount)}</span><small>${escapeHtml(formatTransactionDate(t))}</small></div>`).join('')||'No alerts yet.';
}
function renderQrCode(){
  const box=$('accountQr');if(!box||!latestAccountData)return;box.innerHTML='';const token=String(latestAccountData.qrToken||'');
  if(!token){$('qrCodeLabel').textContent='Your secure demo profile QR is being prepared. Refresh in a moment.';return;}
  const profileUrl=window.location.origin+'/card.html?token='+encodeURIComponent(token);$('qrCodeLabel').textContent='Scan to view your public demo card profile · '+String(latestAccountData.cardTier||'Classic')+' Card';
  if(window.QRCode)new QRCode(box,{text:profileUrl,width:128,height:128,colorDark:'#102b4b',colorLight:'#ffffff',correctLevel:QRCode.CorrectLevel.M});else box.textContent='QR library unavailable';
}
const cardTierThemes={Classic:'tier-classic',Silver:'tier-silver',Gold:'tier-gold',Platinum:'tier-platinum',Royal:'tier-royal'};
const cardTierEmblems={Classic:'◈',Silver:'❄',Gold:'♛',Platinum:'✦',Royal:'🦁'};
const cardTierCaptions={Classic:'SIGNATURE COLLECTION',Silver:'SILVER EDITION',Gold:'GOLD SIGNATURE',Platinum:'PLATINUM RESERVE',Royal:'ROYAL HERITAGE'};
function applyCardTier(tier){const safe=Object.prototype.hasOwnProperty.call(cardTierThemes,tier)?tier:'Classic';document.querySelectorAll('#customerAccountCard .account-card-visual, #serviceWorkspace .account-card-visual').forEach(el=>{el.classList.remove(...Object.values(cardTierThemes));el.classList.add(cardTierThemes[safe]);const emblem=el.querySelector('.card-art-emblem');if(emblem){emblem.className='card-art-emblem emblem-'+safe.toLowerCase();emblem.textContent=cardTierEmblems[safe];}const caption=el.querySelector('.card-art-caption');if(caption)caption.textContent=cardTierCaptions[safe];});const label=$('currentCardTier');if(label)label.textContent=safe+' Card';const cardLabel=$('detailCardTier');if(cardLabel)cardLabel.textContent=safe.toUpperCase()+' DEMO CARD';const mainEmblem=$('accountCardEmblem');if(mainEmblem){mainEmblem.className='card-art-emblem emblem-'+safe.toLowerCase();mainEmblem.textContent=cardTierEmblems[safe];}const mainCaption=$('accountCardCaption');if(mainCaption)mainCaption.textContent=cardTierCaptions[safe];if(latestAccountData)latestAccountData.cardTier=safe;}
const cardTierDescriptions={Classic:'Midnight navy, silver lines and a clean signature crest',Silver:'Ice-silver metal, pearl highlights and a crystal emblem',Gold:'Champagne gold, dark bronze details and a royal crown',Platinum:'Graphite black, platinum edges and a luminous star',Royal:'Imperial purple, antique gold ornaments and a crowned lion crest'};
function openCardUpgrade(){const modal=$('cardUpgradeModal'),choices=$('cardTierChoices');if(!modal||!choices)return;const current=latestAccountData?.cardTier||'Classic';choices.innerHTML=Object.keys(cardTierThemes).map(tier=>`<button type="button" class="card-tier-choice ${cardTierThemes[tier]} ${tier===current?'selected':''}" data-card-tier="${tier}"><span class="tier-preview"><b>HASDEO <small>BANK</small></b><span class="tier-preview-emblem">${cardTierEmblems[tier]}</span><small>${cardTierCaptions[tier]}</small><i>•••• &nbsp; •••• &nbsp; •••• &nbsp; ${escapeHtml(String(latestAccountData?.cardNumber||'0000').slice(-4))}</i></span><strong>${tier} Card</strong><small>${cardTierDescriptions[tier]}</small>${tier===current?'<em>Current card</em>':''}</button>`).join('');$('cardUpgradeFeedback').textContent='';modal.classList.remove('hidden');}
async function chooseCardTier(tier){const feedback=$('cardUpgradeFeedback');try{const result=await api('/customer/card-tier',{cardTier:tier});latestAccountData.cardTier=result.cardTier;applyCardTier(result.cardTier);$('cardUpgradeModal').classList.add('hidden');toast(result.message||'Demo card style updated.');}catch(error){feedback.textContent=error.message;}}
$('openCardUpgrade')?.addEventListener('click',openCardUpgrade);$('closeCardUpgrade')?.addEventListener('click',()=>$('cardUpgradeModal').classList.add('hidden'));$('cardUpgradeModal')?.addEventListener('click',e=>{if(e.target===$('cardUpgradeModal'))$('cardUpgradeModal').classList.add('hidden');});$('cardTierChoices')?.addEventListener('click',e=>{const choice=e.target.closest('[data-card-tier]');if(choice)chooseCardTier(choice.dataset.cardTier);});
function updateSavingsGoal(){
  const goal=JSON.parse(localStorage.getItem('hasdeoDemoGoal')||'null');const bar=$('goalProgressBar');if(!bar)return;
  if(!goal){$('goalProgressText').textContent='No savings goal set.';bar.style.width='0%';return;}
  const balance=Number(latestAccountData?.balance||0),pct=Math.min(100,Math.round(balance/Math.max(1,Number(goal.amount))*100));
  $('goalName').value=goal.name;$('goalAmount').value=goal.amount;bar.style.width=pct+'%';$('goalProgressText').textContent=`${goal.name}: ${money(balance)} / ${money(goal.amount)} (${pct}%)`;
}
$('transactionSearch').addEventListener('input',renderTransactions);$('transactionType').addEventListener('change',renderTransactions);
$('downloadStatement').addEventListener('click',()=>{
  const rows=[['Transaction ID','Description','Date & Time (IST)','Type','Amount'],...latestTransactions.map(t=>[t.id,t.description,formatTransactionDate(t),t.type,t.amount])];
  const csv=rows.map(row=>row.map(v=>'"'+String(v??'').replace(/"/g,'""')+'"').join(',')).join('\r\n');
  const blob=new Blob(['\ufeff'+csv],{type:'text/csv;charset=utf-8;'});const url=URL.createObjectURL(blob);const a=document.createElement('a');a.href=url;a.download='HASDEO-BANK-demo-statement.csv';a.click();URL.revokeObjectURL(url);
});
$('printStatement').addEventListener('click',()=>{
  const w=window.open('','_blank','width=900,height=700');if(!w){toast('Allow pop-ups to print your statement.');return;}
  const rows=latestTransactions.map(t=>`<tr><td>${escapeHtml(t.id)}</td><td>${escapeHtml(t.description)}</td><td>${escapeHtml(formatTransactionDate(t))}</td><td>${escapeHtml(t.type)}</td><td>${money(t.amount)}</td></tr>`).join('');
  w.document.write(`<!doctype html><html><head><title>HASDEO BANK Demo Statement</title><style>body{font:14px Arial;padding:28px;color:#102b4b}h1{font-family:Georgia}table{border-collapse:collapse;width:100%;margin-top:24px}td,th{border-bottom:1px solid #ddd;padding:10px;text-align:left;font-size:12px}.note{color:#777;font-size:11px}</style></head><body><h1>HASDEO BANK</h1><h2>Demo account statement</h2><p>${escapeHtml(latestAccountData?.name||'Demo account')} · ${escapeHtml(latestAccountData?.accountType||'')}</p><p>Available demo balance: ${money(latestAccountData?.balance||0)}</p><table><thead><tr><th>ID</th><th>Description</th><th>Date &amp; time (IST)</th><th>Type</th><th>Amount</th></tr></thead><tbody>${rows}</tbody></table><p class="note">Educational demo only. Not a real bank statement or proof of funds.</p><script>window.onload=()=>window.print()<\/script></body></html>`);w.document.close();
});
$('goalForm').addEventListener('submit',e=>{e.preventDefault();const amount=Number($('goalAmount').value);if(!Number.isFinite(amount)||amount<=0){toast('Enter a valid goal amount.');return;}localStorage.setItem('hasdeoDemoGoal',JSON.stringify({name:$('goalName').value.trim(),amount}));updateSavingsGoal();toast('Savings goal saved in this browser.');});
$('passwordChangeForm').addEventListener('submit',async e=>{e.preventDefault();try{const r=await api('/profile/password',{currentPassword:$('currentDemoPassword').value,newPassword:$('newDemoPassword').value});$('passwordFeedback').textContent=r.message;$('passwordChangeForm').reset();toast(r.message);}catch(err){$('passwordFeedback').textContent=err.message;}});
$('feedbackForm').addEventListener('submit',async e=>{e.preventDefault();try{const r=await api('/feedback',{rating:$('feedbackRating').value,message:$('feedbackMessage').value});$('feedbackResult').textContent=r.message;$('feedbackMessage').value='';toast(r.message);}catch(err){$('feedbackResult').textContent=err.message;}});
$('themeToggle').addEventListener('click',()=>{document.body.classList.toggle('theme-dark');const dark=document.body.classList.contains('theme-dark');localStorage.setItem('hasdeoTheme',dark?'dark':'light');$('themeToggle').textContent=dark?'☀ Light mode':'☾ Dark mode';});
if(localStorage.getItem('hasdeoTheme')==='dark'){document.body.classList.add('theme-dark');$('themeToggle').textContent='☀ Light mode';}
$('notificationToggle').addEventListener('click',()=>$('notificationPanel').classList.toggle('hidden'));$('closeNotifications').addEventListener('click',()=>$('notificationPanel').classList.add('hidden'));
const translations={hi:{'Your financial overview':'आपका वित्तीय अवलोकन','Recent transactions':'हाल के लेन-देन','Set a savings goal':'बचत लक्ष्य तय करें','Account settings':'खाता सेटिंग्स'},hinglish:{'Your financial overview':'Aapka financial overview','Recent transactions':'Recent transactions','Set a savings goal':'Savings goal set karein','Account settings':'Account settings'},pa:{'Your financial overview':'ਤੁਹਾਡਾ ਵਿੱਤੀ ਵੇਰਵਾ','Recent transactions':'ਹਾਲੀਆ ਲੈਣ-ਦੇਣ','Set a savings goal':'ਬਚਤ ਦਾ ਟੀਚਾ ਬਣਾਓ','Account settings':'ਖਾਤਾ ਸੈਟਿੰਗਾਂ'}};
$('languageSelect').addEventListener('change',()=>{const lang=$('languageSelect').value;localStorage.setItem('hasdeoLanguage',lang);const map=translations[lang]||{};const h1=document.querySelector('.dash-top h1');if(h1)h1.textContent=map['Your financial overview']||'Good to see you.';const tx=document.querySelector('#transactions h2');if(tx)tx.innerHTML=(map['Recent transactions']||'Recent')+' <em></em>';const goals=document.querySelector('#goalForm')?.closest('.feature-card')?.querySelector('h3');if(goals)goals.textContent=map['Set a savings goal']||'Set a savings goal';const profile=document.querySelector('#passwordChangeForm')?.closest('.feature-card')?.querySelector('h3');if(profile)profile.textContent=map['Account settings']||'Account settings';});
async function copyAccountDetail(id){const value=$(id).textContent.replace(/\s/g,'');try{await navigator.clipboard.writeText(value);toast('Copied to clipboard.');}catch(e){toast('Copy is unavailable in this browser.');}}

async function actionForm(formId,path,values){
  $(formId).addEventListener('submit',async e=>{e.preventDefault();try{await api(path,values());await loadAccount();await loadOwnerOverview();$(formId).reset();toast('Demo transaction completed.');}catch(err){toast(err.message);}});
}
actionForm('depositForm','/deposit',()=>({amount:$('depositAmount').value}));
actionForm('withdrawForm','/withdraw',()=>({amount:$('withdrawAmount').value}));
let recipientLookupTimer=null;let confirmedRecipientCode='';
$('transferRecipient').addEventListener('input',()=>{clearTimeout(recipientLookupTimer);const code=$('transferRecipient').value.trim();confirmedRecipientCode='';$('recipientPreview').textContent='Looking up customer…';if(!/^\d{4}$/.test(code)){$('recipientPreview').textContent='Enter the 4-digit transfer code.';return;}recipientLookupTimer=setTimeout(async()=>{try{const recipient=await api('/recipient/'+code,null,'GET');confirmedRecipientCode=code;$('recipientPreview').textContent='Recipient: '+recipient.name+' · '+(recipient.role==='ADMIN'?'Admin demo code ':'Customer demo code ')+recipient.transferCode;}catch(error){$('recipientPreview').textContent=error.message;}},300);});
$('transferForm').addEventListener('submit',async e=>{e.preventDefault();const code=$('transferRecipient').value.trim();const button=$('transferForm').querySelector('button[type=submit],button:not([type])');if(code!==confirmedRecipientCode){toast('Please enter and verify a valid recipient code first.');return;}if(button?.disabled)return;if(button){button.disabled=true;button.textContent='Processing…';}try{const result=await api('/transfer',{amount:$('transferAmount').value,recipientCode:code});await loadAccount();$('transferForm').reset();confirmedRecipientCode='';$('recipientPreview').textContent='Enter a 4-digit demo code.';toast('Demo transfer sent to '+result.recipientName+'.');}catch(error){toast(error.message);}finally{if(button){button.disabled=false;button.textContent='Make demo transfer →';}}});

function showDashboardOverview(){
  $('ownerPanel').classList.add('hidden');
  document.querySelectorAll('.dash-main > :not(#ownerPanel)').forEach(el=>el.classList.remove('hidden'));
}
async function showOwnerPanel(){
  document.querySelectorAll('.dash-main > :not(#ownerPanel)').forEach(el=>el.classList.add('hidden'));
  $('ownerPanel').classList.remove('hidden');
  await loadOwnerPanel();
  $('ownerPanel').scrollIntoView({behavior:'smooth',block:'start'});
}

async function loadOwnerOverview(){
  try{
    const data=await api('/owner/overview',null,'GET');
    $('ownerBalance').textContent=money(data.balance);$('ownerCustomers').textContent=data.customers;
    $('ownerTransactions').textContent=data.transactions;$('ownerSessions').textContent=data.activeSessions;if(!ownerAnalyticsLoaded) await renderOwnerAnalytics();const adminCard=$('adminCardNumber');if(adminCard)adminCard.textContent='0000 0000 0000 2704';const adminCode=$('adminTransferCode');if(adminCode)adminCode.textContent='2704';
  }catch(error){console.warn(error.message);}
}
async function renderOwnerAnalytics(){
  const target=$('ownerAnalytics');if(!target)return;try{const rows=await api('/owner/transactions',null,'GET');const now=new Date();const months=[];for(let i=5;i>=0;i--){const d=new Date(now.getFullYear(),now.getMonth()-i,1);months.push({key:d.getFullYear()+'-'+String(d.getMonth()+1).padStart(2,'0'),label:d.toLocaleDateString('en-IN',{month:'short'}),credits:0,debits:0});}
    rows.forEach(t=>{const d=new Date(t.timestamp||'');if(Number.isNaN(d.getTime()))return;const key=d.getFullYear()+'-'+String(d.getMonth()+1).padStart(2,'0');const m=months.find(x=>x.key===key);if(m){if(t.type==='CREDIT')m.credits+=Number(t.amount)||0;else m.debits+=Number(t.amount)||0;}});
    const max=Math.max(1,...months.flatMap(m=>[m.credits,m.debits]));target.innerHTML=months.map(m=>`<div class="analytics-month"><b>${m.label}</b><div class="analytics-track"><span class="analytics-credit" style="width:${Math.round(m.credits/max*100)}%"></span></div><div class="analytics-track"><span class="analytics-debit" style="width:${Math.round(m.debits/max*100)}%"></span></div><small>In ${money(m.credits)} · Out ${money(m.debits)}</small></div>`).join('');ownerAnalyticsLoaded=true;
  }catch(e){target.textContent='Analytics are temporarily unavailable.';}
}
$('downloadMonthlyReport').addEventListener('click',async()=>{try{const rows=await api('/owner/transactions',null,'GET');const now=new Date();const selected=rows.filter(t=>{const d=new Date(t.timestamp||'');return !Number.isNaN(d.getTime())&&d.getMonth()===now.getMonth()&&d.getFullYear()===now.getFullYear();});const data=[['ID','Description','Date & Time (IST)','Type','Amount'],...selected.map(t=>[t.id,t.description,formatTransactionDate(t),t.type,t.amount])];const csv=data.map(r=>r.map(v=>'"'+String(v??'').replace(/"/g,'""')+'"').join(',')).join('\r\n');const url=URL.createObjectURL(new Blob(['\ufeff'+csv],{type:'text/csv;charset=utf-8'}));const a=document.createElement('a');a.href=url;a.download='HASDEO-BANK-admin-monthly-report.csv';a.click();URL.revokeObjectURL(url);toast('Monthly demo report downloaded.');}catch(e){toast(e.message);}});
async function loadOwnerPanel(){
  await loadOwnerOverview();
  const active=document.querySelector('.owner-tab.active')?.dataset.ownerTab||'overview';
  await loadOwnerTab(active);
}
async function loadOwnerTab(tab){
  if(presenceRefreshTimer){clearInterval(presenceRefreshTimer);presenceRefreshTimer=null;}
  document.querySelectorAll('.owner-tab').forEach(b=>b.classList.toggle('active',b.dataset.ownerTab===tab));
  document.querySelectorAll('[data-owner-section]').forEach(s=>s.classList.toggle('hidden',s.dataset.ownerSection!==tab));
  try{
    if(tab==='customers') await loadCustomers();
    if(tab==='transactions') await loadOwnerTransactions();
    if(tab==='maintenance') await loadMaintenance();
    if(tab==='sessions') await loadSessions();
    if(tab==='presence'){await loadLivePresence();await loadPresenceHistory();presenceRefreshTimer=setInterval(()=>{loadLivePresence().catch(()=>{});loadPresenceHistory().catch(()=>{});},15000);}
    if(tab==='activity') await loadActivity();
    if(tab==='settings') await loadSettings();
    if(tab==='backup') await loadBackupStatus();
    if(tab==='support') await loadSupportTickets();
    if(tab==='ai') await loadAiBrain();
  }catch(error){toast(error.message);}
}
document.querySelectorAll('.owner-tab').forEach(button=>button.addEventListener('click',()=>loadOwnerTab(button.dataset.ownerTab)));

async function loadCustomers(){
  const rows=await api('/owner/customers',null,'GET');
  $('customerRows').innerHTML=rows.map(c=>`<tr><td>${escapeHtml(c.customerCode)}</td><td>${escapeHtml(c.name)}</td><td>${escapeHtml(c.username||'—')}</td><td>${escapeHtml(c.cardNumber||'—')}</td><td>${escapeHtml(c.transferCode||'—')}</td><td>${money(c.balance)}</td><td>${escapeHtml(c.accountType)}</td><td>${escapeHtml(c.createdAt)}</td><td><span class="owner-status ${c.status==='ACTIVE'?'':'inactive'}">${escapeHtml(c.status)}</span></td><td><button class="owner-action" data-customer-action="toggle" data-id="${c.id}" data-status="${c.status}">${c.status==='ACTIVE'?'Deactivate':'Activate'}</button><button class="owner-action danger" data-customer-action="delete" data-id="${c.id}">Delete</button></td></tr>`).join('')||'<tr><td colspan="10">No demo customers added.</td></tr>';
}
$('customerForm').addEventListener('submit',async e=>{
  e.preventDefault();try{await api('/owner/customers',{name:$('customerName').value,username:$('customerUsername').value,password:$('customerPassword').value,contact:$('customerContact').value,email:$('customerEmail').value,accountType:$('customerType').value});$('customerForm').reset();await loadCustomers();await loadOwnerOverview();toast('Demo customer added.');}catch(error){toast(error.message);}
});
$('customerRows').addEventListener('click',async e=>{
  const button=e.target.closest('[data-customer-action]');if(!button)return;
  const id=button.dataset.id;
  try{
    if(button.dataset.customerAction==='delete'){
      if(!window.confirm('Delete this demo customer record?'))return;
      await api('/owner/customers/'+id,{},'DELETE');
    }else{
      const status=button.dataset.status==='ACTIVE'?'INACTIVE':'ACTIVE';
      await api('/owner/customers/'+id,{status},'PUT');
    }
    await loadCustomers();await loadOwnerOverview();toast('Customer record updated.');
  }catch(error){toast(error.message);}
});


async function loadSupportTickets(){
  const rows=await api('/owner/support',null,'GET');
  $('supportTicketRows').innerHTML=rows.map(t=>`<tr><td>${escapeHtml(formatTransactionDate(t))}</td><td>${escapeHtml(t.name)}</td><td>${escapeHtml(t.email)}</td><td>${escapeHtml(t.category)}</td><td>${escapeHtml(t.message)}</td><td>${escapeHtml(t.status)}</td></tr>`).join('')||'<tr><td colspan="6">No support requests.</td></tr>';
}
$('supportForm').addEventListener('submit',async e=>{e.preventDefault();try{const result=await api('/support',{name:$('supportName').value.trim(),email:$('supportEmail').value.trim(),category:$('supportCategory').value,message:$('supportMessage').value.trim()});$('supportForm').reset();$('supportFeedback').textContent='Request received. Ticket #'+result.ticketId+'. '+(result.emailSent?'Email sent to the site owner.':'Ticket saved; owner email is not configured or could not be sent.');toast('Support request submitted.');}catch(error){$('supportFeedback').textContent=error.message;}});

const supportAiHistory=[];let pendingAiMessage='';let aiConversationLanguage='en';let aiLanguageAsked=false;
function detectCustomerLanguage(text){if(/[\u0A00-\u0A7F]/.test(text))return 'Punjabi';if(/[\u0900-\u097F]/.test(text))return 'Hindi';if(/\b(kya|kaise|hai|ho|mujhe|mera|aap|kr|kar|bta|batao|kyu|nahi|haan)\b/i.test(text))return 'Hinglish';return 'English';}
function isAffirmative(text){return /^(yes|yeah|y|haan|ha|ji|sure|okay|ok|bilkul|ਹਾਂ)(\s|[.!]*)$/i.test(text.trim());}
function isNegative(text){return /^(no|nope|nahi|nah|na|ਨਹੀਂ)(\s|[.!]*)$/i.test(text.trim());}
function addAiMessage(role,text){
  const line=document.createElement('div');line.className='support-ai-message '+(role==='user'?'user':'assistant');
  const label=document.createElement('b');label.textContent=role==='user'?'You':'HASDEO Help AI';
  const content=document.createElement('p');content.textContent=text;
  line.append(label,content);$('supportAiMessages').appendChild(line);$('supportAiMessages').scrollTop=$('supportAiMessages').scrollHeight;
}
$('supportAiForm').addEventListener('submit',async e=>{
  e.preventDefault();const input=$('supportAiQuestion');const message=input.value.trim();if(!message)return;
  if(pendingAiMessage){if(isAffirmative(message)){aiConversationLanguage=detectCustomerLanguage(pendingAiMessage);const original=pendingAiMessage;pendingAiMessage='';input.value='';addAiMessage('user',message);supportAiHistory.push({role:'user',content:'Please continue in '+aiConversationLanguage+'.'});input.value=original;input.form.requestSubmit();return;}if(isNegative(message)){pendingAiMessage='';aiConversationLanguage='en';aiLanguageAsked=true;addAiMessage('user',message);addAiMessage('assistant','Of course. I will continue in English. Please send your question again.');input.value='';return;}addAiMessage('assistant','Please reply Yes to continue in your language, or No to continue in English.');input.value='';return;}
  if(!aiLanguageAsked){const detected=detectCustomerLanguage(message);if(detected!=='English'){pendingAiMessage=message;aiLanguageAsked=true;addAiMessage('user',message);addAiMessage('assistant',`I noticed you're using ${detected}. Would you like me to continue our conversation in ${detected}? Please reply Yes or No.`);input.value='';return;}aiLanguageAsked=true;}
  const history=supportAiHistory.slice(-8);addAiMessage('user',message);supportAiHistory.push({role:'user',content:message});input.value='';
  const button=$('supportAiSend');button.disabled=true;button.textContent='Thinking…';
  try{
    const aiMessage=aiConversationLanguage==='en'?message:`[Customer selected ${aiConversationLanguage}. Reply in ${aiConversationLanguage}.]\n${message}`;
    const result=await api('/support/ai',{message:aiMessage,history,name:$('supportAiName').value.trim(),email:$('supportAiEmail').value.trim()});
    addAiMessage('assistant',result.answer);supportAiHistory.push({role:'assistant',content:result.answer});
    if(result.escalated&&!result.emailSent) addAiMessage('assistant','Your ticket is saved in the admin Support panel. Owner email notifications need Gmail SMTP configuration.');
  }catch(error){console.error('HASDEO Help AI request failed:',error);addAiMessage('assistant','I’m temporarily unable to connect to the AI service. Please try again shortly, or use the Contact Support form below so the website team can help.');}
  finally{button.disabled=false;button.textContent='Send';input.focus();}
});


async function loadOwnerTransactions(){
  const rows=await api('/owner/transactions',null,'GET');
  $('ownerTransactionRows').innerHTML=rows.map(t=>`<tr><td>${escapeHtml(t.id)}</td><td>${escapeHtml(t.description)}</td><td>${escapeHtml(formatTransactionDate(t))}</td><td>${escapeHtml(t.type)}</td><td class="right ${t.type==='CREDIT'?'credit':'debit'}">${t.type==='CREDIT'?'+':'−'} ${money(t.amount)}</td></tr>`).join('')||'<tr><td colspan="5">No transactions.</td></tr>';
}
async function loadMaintenance(){
  const data=await api('/owner/settings',null,'GET');
  $('maintenanceToggle').checked=Boolean(data.maintenance);$('maintenanceText').value=data.maintenanceMessage||'';$('maintenanceState').textContent=data.maintenance?'MAINTENANCE ON · Customers blocked':'MAINTENANCE OFF · Website available';
}
$('saveMaintenance').addEventListener('click',async()=>{
  try{const result=await api('/owner/maintenance',{enabled:$('maintenanceToggle').checked,message:$('maintenanceText').value});await checkPublicStatus();$('maintenanceState').textContent=result.maintenance?'MAINTENANCE ON · Customers blocked':'MAINTENANCE OFF · Website available';toast(result.maintenance?'Maintenance mode enabled. Customer access is blocked.':'Maintenance mode disabled. Website is available.');}
  catch(error){toast(error.message);}
});
async function loadSessions(){
  const rows=await api('/owner/sessions',null,'GET');
  $('sessionRows').innerHTML=rows.map(s=>`<tr><td>${escapeHtml(s.username)}</td><td>${escapeHtml(s.role||'ADMIN')}</td><td>${escapeHtml(s.loginAt)}</td><td>${escapeHtml(s.lastSeenAt)}</td><td>${escapeHtml(s.logoutAt)}</td><td><span class="owner-status ${s.status==='ACTIVE'?'':s.status.toLowerCase()}">${escapeHtml(s.status)}</span></td></tr>`).join('')||'<tr><td colspan="6">No login history.</td></tr>';
}
async function loadLivePresence(){
  const rows=await api('/owner/presence',null,'GET');
  $('presenceRows').innerHTML=rows.map(c=>`<tr><td>${escapeHtml(c.name)}</td><td>${escapeHtml(c.username||'—')}</td><td><span class="owner-status ${c.status==='ONLINE'?'':'inactive'}">${escapeHtml(c.status)}</span></td><td>${escapeHtml(c.lastSeenAt)}</td><td>${escapeHtml(c.loginAt)}</td></tr>`).join('')||'<tr><td colspan="5">No customer accounts yet.</td></tr>';
  $('presenceUpdatedAt').textContent='Live refresh: '+new Date().toLocaleTimeString('en-IN',{timeZone:'Asia/Kolkata'});
}
async function loadPresenceHistory(){
  const rows=await api('/owner/presence/history',null,'GET');
  $('presenceHistoryRows').innerHTML=rows.map(e=>`<tr><td>${escapeHtml(e.timestamp)}</td><td>${escapeHtml(e.username)}</td><td>${escapeHtml(e.event)}</td><td>${escapeHtml(e.details)}</td></tr>`).join('')||'<tr><td colspan="4">No presence events recorded yet.</td></tr>';
}
async function loadActivity(){
  const rows=await api('/owner/activity',null,'GET');
  $('activityRows').innerHTML=rows.map(a=>`<tr><td>${escapeHtml(formatTransactionDate(a))}</td><td>${escapeHtml(a.actor)}</td><td>${escapeHtml(a.action)}</td><td>${escapeHtml(a.details)}</td></tr>`).join('')||'<tr><td colspan="4">No activity recorded.</td></tr>';
}
async function loadSettings(){
  const data=await api('/owner/settings',null,'GET');$('siteTitle').value=data.siteTitle||'';
  $('customerSignupEnabled').checked=data.customerSignupEnabled!==false; $('customerLoginEnabled').checked=data.customerLoginEnabled!==false;
  $('signupAccessFeedback').textContent='Signup: '+(data.customerSignupEnabled===false?'OFF':'ON');
  $('loginAccessFeedback').textContent='Customer login: '+(data.customerLoginEnabled===false?'OFF':'ON');
  $('customerAccessFeedback').textContent='Both settings are stored separately on the server.';
  await Promise.all([loadMailStatus(),loadServerTimeStatus()]);
}
async function loadMailStatus(){
  const data=await api('/owner/mail-status',null,'GET');
  $('mailStatus').textContent=data.configured?'Configured · '+data.sender+' → '+data.recipient:data.message;
  $('mailStatus').classList.toggle('success',Boolean(data.configured));
  $('testMailButton').disabled=!data.configured;
}
async function loadServerTimeStatus(){
  const data=await api('/owner/time-status',null,'GET');
  $('serverTimeStatus').textContent='Server IST time: '+new Intl.DateTimeFormat('en-IN',{timeZone:'Asia/Kolkata',dateStyle:'medium',timeStyle:'medium'}).format(new Date(data.ist));
}
$('testMailButton').addEventListener('click',async()=>{
  const button=$('testMailButton');button.disabled=true;button.textContent='Sending…';
  try{const result=await api('/owner/mail-test',{});toast(result.message||'Test email sent.');}
  catch(error){toast(error.message);}
  finally{button.disabled=false;button.textContent='Send test email';}
});
$('settingsForm').addEventListener('submit',async e=>{
  e.preventDefault();try{await api('/owner/settings',{siteTitle:$('siteTitle').value});await checkPublicStatus();toast('Website settings saved.');}catch(error){toast(error.message);}
});
$('sendAnnouncement').addEventListener('click',async()=>{
  const button=$('sendAnnouncement'),message=$('announcementMessage').value.trim();
  if(!message){toast('Type an announcement first.');return;}
  button.disabled=true;button.textContent='Sending…';
  try{const result=await api('/owner/announcement/send',{message});$('announcementMessage').value='';await checkPublicStatus();$('announcementFeedback').textContent='Sent to all customer sessions · '+new Date(result.updatedAt).toLocaleString('en-IN',{timeZone:'Asia/Kolkata'});toast('Announcement sent to all customers.');}
  catch(error){$('announcementFeedback').textContent=error.message;toast(error.message);}
  finally{button.disabled=false;button.textContent='Send to all customers';}
});
async function saveOneCustomerAccessSetting(kind){
  const isSignup=kind==='signup';
  const checkbox=$(isSignup?'customerSignupEnabled':'customerLoginEnabled');
  const button=$(isSignup?'saveCustomerSignup':'saveCustomerLogin');
  const feedback=$(isSignup?'signupAccessFeedback':'loginAccessFeedback');
  const field=isSignup?'customerSignupEnabled':'customerLoginEnabled';
  const label=isSignup?'Signup':'Customer login';
  button.disabled=true;button.textContent='Saving…';
  try{
    const result=await api('/owner/access-control',{[field]:checkbox.checked});
    await checkPublicStatus();
    feedback.textContent=label+': '+(result[field]?'ON':'OFF')+' · saved';
    $('customerAccessFeedback').textContent=label+' setting saved independently. Other setting was not changed.';
    toast(label+' setting saved: '+(result[field]?'ON':'OFF'));
  }catch(error){feedback.textContent='Save failed: '+error.message;toast(error.message);}
  finally{button.disabled=false;button.textContent=isSignup?'Save signup setting':'Save login setting';}
}
$('saveCustomerSignup').addEventListener('click',()=>saveOneCustomerAccessSetting('signup'));
$('saveCustomerLogin').addEventListener('click',()=>saveOneCustomerAccessSetting('login'));
async function loadAiBrain(){
  const data=await api('/owner/settings',null,'GET');
  $('aiCreatorName').value=data.aiCreatorName||'Alok';
  $('aiCustomKnowledge').value=data.aiCustomKnowledge||'';
  $('aiBrainFeedback').textContent='Your saved AI guidance is active for new support questions.';
}
$('saveAiBrain').addEventListener('click',async()=>{
  const button=$('saveAiBrain');button.disabled=true;button.textContent='Saving…';
  try{
    await api('/owner/settings',{aiCreatorName:$('aiCreatorName').value.trim(),aiCustomKnowledge:$('aiCustomKnowledge').value});
    $('aiBrainFeedback').textContent='Saved. New AI conversations will use these instructions.';
    toast('AI knowledge saved.');
  }catch(error){$('aiBrainFeedback').textContent=error.message;}
  finally{button.disabled=false;button.textContent='Save AI knowledge';}
});
async function loadBackupStatus(){
  const data=await api('/owner/backup-status',null,'GET');
  const status=data.status||'UNKNOWN';
  $('backupStatus').textContent=data.configured?'Google Drive configured · '+status:'Google Drive not configured';
  $('backupStatus').classList.toggle('success',status==='SUCCESS');
  $('backupStatus').classList.toggle('failed',status==='FAILED'||status==='NOT_CONFIGURED');
  $('backupMessage').textContent=data.message||'Backup status unavailable.';
  $('backupLastRun').textContent=data.lastBackup||'Not available';
  $('backupFileName').textContent=data.fileName||'—';
  $('backupRetention').textContent=data.retentionCount||14;
  $('runBackupNow').disabled=Boolean(data.running);
  $('runBackupNow').textContent=data.running?'Backup running…':'Run backup now';
  $('backupRows').innerHTML=(data.history||[]).map(r=>`<tr><td>${escapeHtml(r.startedAt)}</td><td>${escapeHtml(r.finishedAt)}</td><td><span class="owner-status ${r.status==='SUCCESS'?'':'inactive'}">${escapeHtml(r.status)}</span></td><td>${escapeHtml(r.fileName)}</td><td>${escapeHtml(r.message)}</td></tr>`).join('')||'<tr><td colspan="5">No backup runs recorded.</td></tr>';
}
$('runBackupNow').addEventListener('click',async()=>{
  const button=$('runBackupNow');button.disabled=true;button.textContent='Backup running…';
  try{const result=await api('/owner/backup-run',{});toast(result.message||'Backup started.');await loadBackupStatus();if(result.status==='RUNNING') pollBackupStatus();}
  catch(error){button.disabled=false;button.textContent='Run backup now';toast(error.message);}
});

async function pollBackupStatus(){
  let attempts=0;
  const timer=setInterval(async()=>{
    attempts++;
    try{
      const data=await api('/owner/backup-status',null,'GET');
      await loadBackupStatus();
      if(!data.running || attempts>=120){clearInterval(timer);if(data.status==='SUCCESS')toast('Backup uploaded and verified.');else if(!data.running)toast(data.message||'Backup process finished.');}
    }catch(error){clearInterval(timer);toast(error.message);}
  },3000);
}

async function logout(){
  isAdminSession=false;
  if(presenceRefreshTimer){clearInterval(presenceRefreshTimer);presenceRefreshTimer=null;}
  if(sessionPingTimer){clearInterval(sessionPingTimer);sessionPingTimer=null;}
  if(balanceRefreshTimer){clearInterval(balanceRefreshTimer);balanceRefreshTimer=null;}
  try{await api('/logout',{});}catch(e){}
  $('dashboard').classList.add('hidden');$('ownerPanel').classList.add('hidden');$('dashboard').dataset.role='';$('transferForm').closest('.action-card').classList.remove('hidden');document.querySelectorAll('.owner-nav').forEach(el=>el.classList.remove('hidden'));
  document.querySelectorAll('.dash-main > :not(#ownerPanel)').forEach(el=>el.classList.remove('hidden'));
  syncPageVisibility();
  await checkPublicStatus();
  toast('You have signed out.');
}
async function restoreSession(){
  try{
    const state=await api('/session',null,'GET');
    if(state.authenticated) await enterDashboard(state.role);
  }catch(error){console.warn('Could not restore the previous session:',error.message);}
}
window.addEventListener('keydown',e=>{if(e.key==='Escape')closeLogin();});
(async()=>{await checkPublicStatus();await restoreSession();await checkPublicStatus();publicStatusTimer=setInterval(checkPublicStatus,15000);})();

// Individual service workspaces: each tile opens its own focused dashboard while the original site stays intact.
const serviceDefinitions=[
 {id:'analytics',icon:'▥',kicker:'05 / INSIGHTS',title:'Spending Analytics',desc:'A visual summary of your demo credits and debits.'},
 {id:'schedule',icon:'◷',kicker:'06 / PLANNING',title:'Scheduled Transfers',desc:'Prepare a fictional transfer reminder for a future date.'},
 {id:'cards',icon:'▣',kicker:'07 / CARDS',title:'Card Management',desc:'Manage the appearance and demo status of your virtual card.'},
 {id:'statements',icon:'▤',kicker:'08 / DOCUMENTS',title:'Monthly E-Statements',desc:'Review and export your available demo transaction history.'},
 {id:'goals',icon:'◎',kicker:'09 / SAVINGS',title:'Savings Goals',desc:'Set a target and track progress using your demo balance.'},
 {id:'alerts',icon:'♧',kicker:'10 / NOTIFICATIONS',title:'Notification Centre',desc:'Review recent account activity and demo alerts.'},
 {id:'risk',icon:'◇',kicker:'11 / SECURITY',title:'Security Alerts',desc:'Understand account safety signals and session guidance.'},
 {id:'referral',icon:'↗',kicker:'12 / COMMUNITY',title:'Referral Demo',desc:'Share a fictional invite code for this educational project.'},
 {id:'receipts',icon:'▧',kicker:'13 / RECORDS',title:'Transaction Receipts',desc:'Open a printable receipt for a completed demo transaction.'},
 {id:'devices',icon:'⌘',kicker:'14 / ACCESS',title:'Login Device History',desc:'See a local browser session summary and safe sign-in tips.'},
 {id:'status',icon:'◉',kicker:'15 / SERVICE STATUS',title:'Website Status',desc:'Check the availability of the demo website components.'},
 {id:'knowledge',icon:'✦',kicker:'16 / ASSISTANCE',title:'AI Knowledge Centre',desc:'Get guidance and visit the owner-managed AI knowledge settings.'}
];
function installServiceTiles(){
 const grid=document.querySelector('.service-grid');if(!grid||grid.dataset.extended==='yes')return;grid.dataset.extended='yes';
 serviceDefinitions.forEach(item=>{const card=document.createElement('article');card.tabIndex=0;card.setAttribute('role','button');card.setAttribute('aria-label','Open '+item.title);card.dataset.service=item.id;card.innerHTML=`<span>${item.icon}</span><small>${item.kicker}</small><h3>${item.title}</h3><p>${item.desc}</p>`;card.addEventListener('click',()=>openServiceWorkspace(item.id));card.addEventListener('keydown',e=>{if(e.key==='Enter'||e.key===' '){e.preventDefault();openServiceWorkspace(item.id);}});grid.appendChild(card);});
 grid.querySelectorAll('article').forEach((card,index)=>{if(!card.dataset.service){card.tabIndex=0;card.setAttribute('role','button');card.addEventListener('click',()=>openServiceWorkspace(['overview','deposit','withdraw','transfer'][index]||'overview'));card.addEventListener('keydown',e=>{if(e.key==='Enter'||e.key===' '){e.preventDefault();card.click();}});}});
}
function workspaceStats(){const tx=latestTransactions;const credits=tx.filter(t=>t.type==='CREDIT').reduce((a,t)=>a+(Number(t.amount)||0),0);const debits=tx.filter(t=>t.type==='DEBIT').reduce((a,t)=>a+(Number(t.amount)||0),0);return {balance:Number(latestAccountData?.balance)||0,credits,debits,count:tx.length};}
function workspacePanel(title,body){return `<section class="workspace-panel"><h3>${title}</h3>${body}</section>`;}
function openServiceWorkspace(id){
 const item=serviceDefinitions.find(x=>x.id===id)||{icon:'H',title:'Account service',desc:'Your HASDEO demo workspace.'};const box=$('serviceWorkspace');$('serviceWorkspaceTitle').textContent=item.title;$('serviceWorkspaceSubtitle').textContent=item.desc;$('serviceWorkspaceIcon').textContent=item.icon;
 const s=workspaceStats();let body='';
 if(id==='analytics'){
  const bars=latestTransactions.slice(0,8).reverse().map((t,i)=>`<span style="height:${Math.max(12,Math.min(100,(Number(t.amount)||0)/(Math.max(1,...latestTransactions.map(x=>Number(x.amount)||0)))*100))}%" data-label="${i+1}"></span>`).join('');
  body=`<div class="workspace-grid"><div class="workspace-stat"><small>AVAILABLE DEMO BALANCE</small><strong>${money(s.balance)}</strong></div><div class="workspace-stat"><small>TOTAL CREDITS IN LOADED HISTORY</small><strong>${money(s.credits)}</strong></div><div class="workspace-stat"><small>TOTAL DEBITS IN LOADED HISTORY</small><strong>${money(s.debits)}</strong></div></div>${workspacePanel('Recent activity chart',`<div class="workspace-chart">${bars||'<div class="workspace-empty">No transaction data is available yet.</div>'}</div><p>Bars represent the latest loaded demo transactions, not a complete financial statement.</p>`)}`;
 }else if(id==='schedule'){
  body=`${workspacePanel('Create a demo reminder',`<form id="scheduleDemoForm"><label>Recipient label</label><input id="scheduleRecipient" maxlength="60" placeholder="e.g. Monthly savings reminder" required><label>Amount (₹)</label><input id="scheduleAmount" type="number" min="0.01" max="1000000000" step="0.01" placeholder="0.00" required><label>Reminder date</label><input id="scheduleDate" type="date" min="${new Date().toISOString().slice(0,10)}" required><button class="gold-btn">Save reminder</button></form><div id="scheduleFeedback" class="workspace-empty">Reminders are saved in this browser only. They do not move funds or execute payments.</div>`)}`;
 }else if(id==='cards'){
  const frozen=localStorage.getItem('hasdeoDemoCardFrozen')==='yes';const tier=latestAccountData?.cardTier||'Classic';body=`<div class="workspace-panel"><div class="account-card-visual ${cardTierThemes[tier]||'tier-classic'}" style="max-width:440px;padding:25px;border-radius:14px;color:#fff"><div class="account-card-brand">HASDEO <b>BANK</b><span>${escapeHtml(tier.toUpperCase())} DEMO CARD</span></div><div class="card-art-emblem emblem-${String(tier).toLowerCase()}">${cardTierEmblems[tier]||'◈'}</div><div class="card-art-caption">${cardTierCaptions[tier]||'SIGNATURE COLLECTION'}</div><div class="account-card-number">•••• &nbsp; •••• &nbsp; •••• &nbsp; ${escapeHtml(String(latestAccountData?.cardNumber||'0000').slice(-4))}</div><div class="account-card-bottom"><div><small>STATUS</small><strong>${frozen?'FROZEN':'ACTIVE DEMO'}</strong></div><div><small>NETWORK</small><strong>DEMO ONLY</strong></div></div></div><p>This is a fictional ${escapeHtml(tier)} card design. No payment network is connected.</p><button class="gold-btn" id="workspaceUpgradeCard">Upgrade Card</button> <button class="gold-btn" id="toggleDemoCard">${frozen?'Unfreeze demo card':'Freeze demo card'}</button></div>`;
 }else if(id==='statements'||id==='receipts'){
  const rows=latestTransactions.slice(0,30).map(t=>`<div class="workspace-row"><span>${escapeHtml(t.description)}<br><small>${escapeHtml(formatTransactionDate(t))}</small></span><b>${t.type==='CREDIT'?'+':'−'} ${money(t.amount)}</b></div>`).join('')||'<div class="workspace-empty">No transactions available yet.</div>';
  body=`<div class="workspace-grid"><div class="workspace-stat"><small>RECORDS AVAILABLE</small><strong>${s.count}</strong></div><div class="workspace-stat"><small>ACCOUNT BALANCE</small><strong>${money(s.balance)}</strong></div><div class="workspace-stat"><small>EXPORT</small><strong>CSV / Print</strong></div></div>${workspacePanel(id==='receipts'?'Latest transaction receipts':'Statement history',rows+`<div class="workspace-actions"><button class="gold-btn" id="downloadWorkspaceStatement">Download CSV statement</button><button class="gold-btn" id="printWorkspaceStatement">Print / Save PDF</button></div>`)}`;
 }else if(id==='goals'){
  const goal=JSON.parse(localStorage.getItem('hasdeoDemoGoal')||'null');const pct=goal?Math.min(100,(s.balance/Math.max(1,Number(goal.target)))*100):0;body=`${workspacePanel('Your savings target',`<div class="workspace-grid"><div class="workspace-stat"><small>CURRENT DEMO BALANCE</small><strong>${money(s.balance)}</strong></div><div class="workspace-stat"><small>GOAL PROGRESS</small><strong>${goal?pct.toFixed(1)+'%':'—'}</strong></div></div><form id="workspaceGoalForm"><label>Goal name</label><input id="workspaceGoalName" maxlength="60" value="${escapeHtml(goal?.name||'My savings goal')}" required><label>Target amount (₹)</label><input id="workspaceGoalTarget" type="number" min="1" step="0.01" value="${goal?Number(goal.target):10000}" required><button class="gold-btn">Save savings goal</button></form><div class="goal-progress"><div style="width:${pct}%"></div></div><p>Progress is a visual tracker based on the demo account balance.</p>`)}`;
 }else if(id==='alerts'){
  const rows=latestTransactions.slice(0,12).map(t=>`<div class="workspace-row"><span>${escapeHtml(t.description)}<br><small>${escapeHtml(formatTransactionDate(t))}</small></span><b>${t.type==='CREDIT'?'+':'−'} ${money(t.amount)}</b></div>`).join('')||'<div class="workspace-empty">No new account alerts.</div>';body=workspacePanel('Recent account notifications',rows);
 }else if(id==='risk'){
  body=`<div class="workspace-grid"><div class="workspace-stat"><small>ACCOUNT STATUS</small><strong>${escapeHtml(latestAccountData?.status||'Sign in to view')}</strong></div><div class="workspace-stat"><small>DEMO LOGIN</small><strong>Session protected</strong></div><div class="workspace-stat"><small>SECURITY CHECK</small><strong>Guidance</strong></div></div>${workspacePanel('Security checklist','<ul><li>Use a unique password for this demo account.</li><li>Never share passwords, OTPs, PINs or card details.</li><li>Sign out on shared devices.</li><li>Report unexpected demo activity through Contact Support.</li></ul>')}`;
 }else if(id==='referral'){
  const code='HASDEO-'+String(latestAccountData?.transferCode||'DEMO');body=workspacePanel('Your fictional invite code',`<div class="workspace-stat"><small>DEMO REFERRAL CODE</small><strong>${escapeHtml(code)}</strong></div><p>Share this code only as a project demonstration. There are no cash rewards or real referrals.</p><button class="gold-btn" id="copyReferralCode">Copy demo code</button>`);
 }else if(id==='devices'){
  body=`<div class="workspace-grid"><div class="workspace-stat"><small>CURRENT BROWSER</small><strong>${escapeHtml(/Windows/i.test(navigator.userAgent)?'Windows browser':/Android/i.test(navigator.userAgent)?'Android browser':'Web browser')}</strong></div><div class="workspace-stat"><small>SESSION</small><strong>Current device</strong></div><div class="workspace-stat"><small>LAST CHECK</small><strong>${new Date().toLocaleString('en-IN')}</strong></div></div>${workspacePanel('Safe access','<p>This page can identify only the browser currently in use. It does not have access to a verified list of all your devices. Use Login History in the admin area for recorded demo sessions.</p>')}`;
 }else if(id==='status'){
  body=`<div class="workspace-grid"><div class="workspace-stat"><small>WEBSITE</small><strong><span class="workspace-badge">OPEN</span></strong></div><div class="workspace-stat"><small>ACCOUNT DATA</small><strong>${latestAccountData?'Loaded':'Sign-in required'}</strong></div><div class="workspace-stat"><small>AI SUPPORT</small><strong>API dependent</strong></div></div>${workspacePanel('Service information','<p>The public website is responding if you can see this page. Account and AI services may require sign-in or server configuration. This panel does not perform external uptime monitoring.</p><button class="gold-btn" id="refreshServiceStatus">Refresh page status</button>')}`;
 }else if(id==='knowledge'){
  body=workspacePanel('HASDEO Help AI','<p>Ask the website assistant about demo features, account steps, transfers and common errors. If it is not configured, the Contact Support form remains available.</p><div class="workspace-actions"><button class="gold-btn" id="openAiSupport">Open AI Support</button><button class="gold-btn" id="openAiKnowledge">Owner AI Knowledge Settings</button></div>');
 }else if(id==='overview'){
  body=`<div class="workspace-grid"><div class="workspace-stat"><small>AVAILABLE DEMO BALANCE</small><strong>${money(s.balance)}</strong></div><div class="workspace-stat"><small>ACCOUNT HOLDER</small><strong>${escapeHtml(latestAccountData?.name||'—')}</strong></div><div class="workspace-stat"><small>ACCOUNT STATUS</small><strong>${escapeHtml(latestAccountData?.status||'—')}</strong></div></div>${workspacePanel('Account overview','<p>Your demo account summary is shown above. Use the dashboard transaction history for recent activity.</p>')}`;
 }else if(['deposit','withdraw','transfer'].includes(id)){
  const target=id==='deposit'?'depositForm':id==='withdraw'?'withdrawForm':'transferForm';body=workspacePanel(item.title,`<p>Use the corresponding demo form in your dashboard.</p><button class="gold-btn" id="workspaceGoAction">Open ${item.title}</button>`);
 }
 $('serviceWorkspaceBody').innerHTML=body;box.classList.remove('hidden');document.body.style.overflow='hidden';
 const close=()=>{box.classList.add('hidden');document.body.style.overflow='';};$('closeServiceWorkspace').onclick=close;
 const schedule=$('scheduleDemoForm');if(schedule)schedule.onsubmit=e=>{e.preventDefault();const items=JSON.parse(localStorage.getItem('hasdeoDemoSchedules')||'[]');items.push({recipient:$('scheduleRecipient').value,amount:Number($('scheduleAmount').value),date:$('scheduleDate').value});localStorage.setItem('hasdeoDemoSchedules',JSON.stringify(items));$('scheduleFeedback').textContent='Demo reminder saved in this browser. No funds were moved.';schedule.reset();};
 const upgrade=$('workspaceUpgradeCard');if(upgrade)upgrade.onclick=()=>{$('serviceWorkspace').classList.add('hidden');document.body.style.overflow='';openCardUpgrade();};const toggle=$('toggleDemoCard');if(toggle)toggle.onclick=()=>{localStorage.setItem('hasdeoDemoCardFrozen',localStorage.getItem('hasdeoDemoCardFrozen')==='yes'?'no':'yes');openServiceWorkspace('cards');};
 const goalForm=$('workspaceGoalForm');if(goalForm)goalForm.onsubmit=e=>{e.preventDefault();localStorage.setItem('hasdeoDemoGoal',JSON.stringify({name:$('workspaceGoalName').value,target:Number($('workspaceGoalTarget').value)}));toast('Savings goal saved in this browser.');openServiceWorkspace('goals');};
 const csv=$('downloadWorkspaceStatement');if(csv)csv.onclick=()=>downloadDemoStatement();const print=$('printWorkspaceStatement');if(print)print.onclick=()=>window.print();
 const copy=$('copyReferralCode');if(copy)copy.onclick=()=>navigator.clipboard?.writeText('HASDEO-'+String(latestAccountData?.transferCode||'DEMO')).then(()=>toast('Demo code copied.')).catch(()=>toast('Copy unavailable.'));
 const refresh=$('refreshServiceStatus');if(refresh)refresh.onclick=()=>toast('Website interface is responding.');
 const ai=$('openAiSupport');if(ai)ai.onclick=()=>{close();document.querySelector('#support').scrollIntoView({behavior:'smooth'});};
 const aiOwner=$('openAiKnowledge');if(aiOwner)aiOwner.onclick=()=>{close();if($('dashboard').classList.contains('hidden')){openAdminLogin();toast('Admin sign-in is required.');}else if($('dashboard').dataset.role==='ADMIN'){showOwnerPanel().then(()=>loadOwnerTab('ai'));}else toast('Admin access is required.');};
 const sign=$('workspaceSignIn');if(sign)sign.onclick=()=>{close();openCustomerLogin();};const goAction=$('workspaceGoAction');if(goAction)goAction.onclick=()=>{close();const target=id==='deposit'?'depositAmount':id==='withdraw'?'withdrawAmount':'transferRecipient';$(target)?.scrollIntoView({behavior:'smooth',block:'center'});$(target)?.focus();};
}
function downloadDemoStatement(){const data=[['ID','Description','Date & Time (IST)','Type','Amount'],...latestTransactions.map(t=>[t.id,t.description,formatTransactionDate(t),t.type,t.amount])];const csv=data.map(r=>r.map(v=>'"'+String(v??'').replace(/"/g,'""')+'"').join(',')).join('\r\n');const url=URL.createObjectURL(new Blob(['\ufeff'+csv],{type:'text/csv;charset=utf-8'}));const a=document.createElement('a');a.href=url;a.download='HASDEO-BANK-demo-statement.csv';a.click();URL.revokeObjectURL(url);toast('Demo statement downloaded.');}
$('closeServiceWorkspace').addEventListener('click',()=>{$('serviceWorkspace').classList.add('hidden');document.body.style.overflow='';});
$('serviceWorkspace').addEventListener('click',e=>{if(e.target===$('serviceWorkspace')){$('serviceWorkspace').classList.add('hidden');document.body.style.overflow='';}});
window.addEventListener('keydown',e=>{if(e.key==='Escape'&&!$('serviceWorkspace').classList.contains('hidden')){$('serviceWorkspace').classList.add('hidden');document.body.style.overflow='';}});
installServiceTiles();
