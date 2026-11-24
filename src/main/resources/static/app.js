const $ = (id) => document.getElementById(id);
let publicMaintenance = false;
let sessionPingTimer = null;

function openLogin(){openCustomerLogin();}
function openCustomerLogin(){showAuthForm('customer');$('loginModal').classList.remove('hidden');}
function openSignup(){showAuthForm('signup');$('loginModal').classList.remove('hidden');}
function openAdminLogin(){showAuthForm('admin');$('loginModal').classList.remove('hidden');}
function showAuthForm(mode){const forms={customer:'customerLoginForm',signup:'signupForm',admin:'loginForm'};Object.values(forms).forEach(id=>$(id).classList.add('hidden'));$(forms[mode]).classList.remove('hidden');$('loginError').textContent='';$('authTitle').innerHTML=mode==='signup'?'Create your <em>account.</em>':mode==='admin'?'Admin <em>sign in.</em>':'Customer <em>sign in.</em>';$('authDescription').textContent=mode==='signup'?'Create a free educational demo account.':mode==='admin'?'Separate secure access for the website administrator.':'Sign in to your educational demo account.';}
function closeLogin(){ $('loginModal').classList.add('hidden'); }
function toast(message){ const el=$('toast'); el.textContent=message; el.classList.add('show'); setTimeout(()=>el.classList.remove('show'),2800); }
function escapeHtml(value){return String(value??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));}
function money(value){return new Intl.NumberFormat('en-IN',{style:'currency',currency:'INR'}).format(Number(value)||0);}

async function api(path,data,method='POST'){
  const response=await fetch('/api'+path,{method,credentials:'same-origin',headers:{'Content-Type':'application/json'},...(data!==undefined&&data!==null?{body:JSON.stringify(data)}:{})});
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

async function checkPublicStatus(){
  try{
    const status=await api('/public/status',null,'GET');
    publicMaintenance=Boolean(status.maintenance);
    $('maintenanceMessage').textContent=status.message||'The website is temporarily unavailable.';
    if(status.siteTitle) document.title=status.siteTitle+' | Banking, made premium';
    $('announcementBar').textContent=status.announcement||'';
    $('announcementBar').classList.toggle('hidden',!status.announcement);
    $('maintenanceScreen').classList.toggle('hidden',!publicMaintenance);
    document.querySelector('.topbar').classList.toggle('hidden',publicMaintenance);
    document.querySelector('main').classList.toggle('hidden',publicMaintenance);
    document.querySelector('footer').classList.toggle('hidden',publicMaintenance);
  }catch(error){console.warn('Could not load public website status:',error.message);}
}

async function enterDashboard(role){
  closeLogin();$('maintenanceScreen').classList.add('hidden');document.querySelector('main').classList.add('hidden');document.querySelector('footer').classList.add('hidden');
  $('dashboard').classList.remove('hidden');$('ownerPanel').classList.add('hidden');
  const isAdmin=role==='ADMIN';document.querySelectorAll('.owner-nav').forEach(el=>el.classList.toggle('hidden',!isAdmin));
  $('transferForm').closest('.action-card').classList.toggle('hidden',isAdmin);
  $('customerAccountCard').classList.remove('hidden');
  $('dashboard').dataset.role=role;
  document.querySelector('.admin-pill').innerHTML='<span></span> '+(isAdmin?'Admin demo':'Customer demo');
  document.querySelector('.side .brand small').textContent=isAdmin?'ADMIN PORTAL':'CUSTOMER PORTAL';
  await loadAccount();if(isAdmin)await loadOwnerOverview();
  if(sessionPingTimer)clearInterval(sessionPingTimer);sessionPingTimer=setInterval(()=>api('/session/ping',{}).catch(()=>{}),60000);
  window.scrollTo(0,0);toast(isAdmin?'Welcome to the admin dashboard.':'Welcome to your demo account.');
}
$('loginForm').addEventListener('submit',async e=>{e.preventDefault();try{await api('/login',{username:$('username').value.trim(),password:$('password').value});await enterDashboard('ADMIN');}catch(err){$('loginError').textContent=err.message;}});
$('customerLoginForm').addEventListener('submit',async e=>{e.preventDefault();try{await api('/customer-login',{username:$('customerLoginUsername').value.trim(),password:$('customerLoginPassword').value});await enterDashboard('CUSTOMER');}catch(err){$('loginError').textContent=err.message;}});
$('signupForm').addEventListener('submit',async e=>{e.preventDefault();if($('signupPassword').value!==$('signupConfirm').value){$('loginError').textContent='Passwords do not match.';return;}try{await api('/signup',{name:$('signupName').value.trim(),username:$('signupUsername').value.trim(),email:$('signupEmail').value.trim(),password:$('signupPassword').value});$('signupForm').reset();await enterDashboard('CUSTOMER');toast('Your demo account has been created.');}catch(err){$('loginError').textContent=err.message;}});

async function loadAccount(){
  const data=await api('/account',null,'GET');
  $('balance').textContent=money(data.balance);
  $('detailName').textContent=data.name||'—';$('detailCardName').textContent=(data.name||'CUSTOMER').toUpperCase();$('detailType').textContent=data.accountType||'Savings';$('detailStatus').textContent=data.status||'Active';$('accountNumberSummary').textContent='•••• '+String(data.cardNumber||'').slice(-4);$('accountTypeSummary').textContent=data.accountType||'Savings';$('accountStatusSummary').textContent=data.status||'Active';
  const card=String(data.cardNumber||'').replace(/(.{4})/g,'$1 ').trim();$('detailCardNumber').textContent=card||'—';$('detailCardCopy').textContent=card||'—';$('detailTransferCode').textContent=data.transferCode||'—';$('detailCodeCopy').textContent=data.transferCode||'—';
  const customer=data.role==='CUSTOMER';$('customerAccountCard').classList.toggle('hidden',!customer);document.querySelectorAll('.owner-nav').forEach(el=>el.classList.toggle('hidden',customer));$('transferForm').closest('.action-card').classList.toggle('hidden',!customer);
  $('transactionRows').innerHTML=data.transactions.map(t=>`<tr><td>${escapeHtml(t.description)}</td><td>${escapeHtml(formatTransactionDate(t))}</td><td class="${t.type==='CREDIT'?'credit':'debit'}">${escapeHtml(t.type)}</td><td class="right ${t.type==='CREDIT'?'credit':'debit'}">${t.type==='CREDIT'?'+':'−'} ${money(t.amount)}</td></tr>`).join('')||'<tr><td colspan="4">No transactions yet.</td></tr>';
}
async function copyAccountDetail(id){const value=$(id).textContent.replace(/\s/g,'');try{await navigator.clipboard.writeText(value);toast('Copied to clipboard.');}catch(e){toast('Copy is unavailable in this browser.');}}

async function actionForm(formId,path,values){
  $(formId).addEventListener('submit',async e=>{e.preventDefault();try{await api(path,values());await loadAccount();await loadOwnerOverview();$(formId).reset();toast('Demo transaction completed.');}catch(err){toast(err.message);}});
}
actionForm('depositForm','/deposit',()=>({amount:$('depositAmount').value}));
actionForm('withdrawForm','/withdraw',()=>({amount:$('withdrawAmount').value}));
let recipientLookupTimer=null;let confirmedRecipientCode='';
$('transferRecipient').addEventListener('input',()=>{clearTimeout(recipientLookupTimer);const code=$('transferRecipient').value.trim();confirmedRecipientCode='';$('recipientPreview').textContent='Looking up customer…';if(!/^\d{4}$/.test(code)){$('recipientPreview').textContent='Enter the 4-digit transfer code.';return;}recipientLookupTimer=setTimeout(async()=>{try{const recipient=await api('/recipient/'+code,null,'GET');confirmedRecipientCode=code;$('recipientPreview').textContent='Recipient: '+recipient.name+' · Code '+recipient.transferCode;}catch(error){$('recipientPreview').textContent=error.message;}},300);});
$('transferForm').addEventListener('submit',async e=>{e.preventDefault();const code=$('transferRecipient').value.trim();if(code!==confirmedRecipientCode){toast('Please enter and verify a valid recipient code first.');return;}try{const result=await api('/transfer',{amount:$('transferAmount').value,recipientCode:code});await loadAccount();$('transferForm').reset();confirmedRecipientCode='';$('recipientPreview').textContent='Enter a code to find the customer.';toast('Demo transfer sent to '+result.recipientName+'.');}catch(error){toast(error.message);}});

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
    $('ownerTransactions').textContent=data.transactions;$('ownerSessions').textContent=data.activeSessions;
  }catch(error){console.warn(error.message);}
}
async function loadOwnerPanel(){
  await loadOwnerOverview();
  const active=document.querySelector('.owner-tab.active')?.dataset.ownerTab||'overview';
  await loadOwnerTab(active);
}
async function loadOwnerTab(tab){
  document.querySelectorAll('.owner-tab').forEach(b=>b.classList.toggle('active',b.dataset.ownerTab===tab));
  document.querySelectorAll('[data-owner-section]').forEach(s=>s.classList.toggle('hidden',s.dataset.ownerSection!==tab));
  try{
    if(tab==='customers') await loadCustomers();
    if(tab==='transactions') await loadOwnerTransactions();
    if(tab==='maintenance') await loadMaintenance();
    if(tab==='sessions') await loadSessions();
    if(tab==='activity') await loadActivity();
    if(tab==='settings') await loadSettings();
    if(tab==='backup') await loadBackupStatus();
    if(tab==='support') await loadSupportTickets();
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
$('supportForm').addEventListener('submit',async e=>{e.preventDefault();try{const result=await api('/support',{name:$('supportName').value.trim(),email:$('supportEmail').value.trim(),category:$('supportCategory').value,message:$('supportMessage').value.trim()});$('supportForm').reset();$('supportFeedback').textContent='Request received. Ticket #'+result.ticketId+'.';toast('Support request submitted.');}catch(error){$('supportFeedback').textContent=error.message;}});

async function loadOwnerTransactions(){
  const rows=await api('/owner/transactions',null,'GET');
  $('ownerTransactionRows').innerHTML=rows.map(t=>`<tr><td>${escapeHtml(t.id)}</td><td>${escapeHtml(t.description)}</td><td>${escapeHtml(formatTransactionDate(t))}</td><td>${escapeHtml(t.type)}</td><td class="right ${t.type==='CREDIT'?'credit':'debit'}">${t.type==='CREDIT'?'+':'−'} ${money(t.amount)}</td></tr>`).join('')||'<tr><td colspan="5">No transactions.</td></tr>';
}
async function loadMaintenance(){
  const data=await api('/owner/settings',null,'GET');
  $('maintenanceToggle').checked=Boolean(data.maintenance);$('maintenanceText').value=data.maintenanceMessage||'';
}
$('saveMaintenance').addEventListener('click',async()=>{
  try{await api('/owner/maintenance',{enabled:$('maintenanceToggle').checked,message:$('maintenanceText').value});await checkPublicStatus();toast('Maintenance settings saved.');}
  catch(error){toast(error.message);}
});
async function loadSessions(){
  const rows=await api('/owner/sessions',null,'GET');
  $('sessionRows').innerHTML=rows.map(s=>`<tr><td>${escapeHtml(s.username)}</td><td>${escapeHtml(s.loginAt)}</td><td>${escapeHtml(s.lastSeenAt)}</td><td>${escapeHtml(s.logoutAt)}</td><td><span class="owner-status ${s.status==='ACTIVE'?'':s.status.toLowerCase()}">${escapeHtml(s.status)}</span></td></tr>`).join('')||'<tr><td colspan="5">No login history.</td></tr>';
}
async function loadActivity(){
  const rows=await api('/owner/activity',null,'GET');
  $('activityRows').innerHTML=rows.map(a=>`<tr><td>${escapeHtml(formatTransactionDate(a))}</td><td>${escapeHtml(a.actor)}</td><td>${escapeHtml(a.action)}</td><td>${escapeHtml(a.details)}</td></tr>`).join('')||'<tr><td colspan="4">No activity recorded.</td></tr>';
}
async function loadSettings(){
  const data=await api('/owner/settings',null,'GET');$('siteTitle').value=data.siteTitle||'';$('siteAnnouncement').value=data.announcement||'';
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
  e.preventDefault();try{await api('/owner/settings',{siteTitle:$('siteTitle').value,announcement:$('siteAnnouncement').value});await checkPublicStatus();toast('Website settings saved.');}catch(error){toast(error.message);}
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
  if(sessionPingTimer){clearInterval(sessionPingTimer);sessionPingTimer=null;}
  try{await api('/logout',{});}catch(e){}
  $('dashboard').classList.add('hidden');$('ownerPanel').classList.add('hidden');$('dashboard').dataset.role='';$('transferForm').closest('.action-card').classList.remove('hidden');document.querySelectorAll('.owner-nav').forEach(el=>el.classList.remove('hidden'));
  document.querySelectorAll('.dash-main > :not(#ownerPanel)').forEach(el=>el.classList.remove('hidden'));
  await checkPublicStatus();
  if(!publicMaintenance){document.querySelector('main').classList.remove('hidden');document.querySelector('footer').classList.remove('hidden');}
  toast('You have signed out.');
}
window.addEventListener('keydown',e=>{if(e.key==='Escape')closeLogin();});
checkPublicStatus();
