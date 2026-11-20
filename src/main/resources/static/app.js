const $ = (id) => document.getElementById(id);
function openLogin(){ $('loginModal').classList.remove('hidden'); $('loginError').textContent=''; }
function closeLogin(){ $('loginModal').classList.add('hidden'); }
function toast(message){ const el=$('toast'); el.textContent=message; el.classList.add('show'); setTimeout(()=>el.classList.remove('show'),2800); }
async function api(path, data, method='POST'){
  const response=await fetch('/api'+path,{method,headers:{'Content-Type':'application/json'},...(data?{body:JSON.stringify(data)}:{})});
  const payload=await response.json().catch(()=>({}));
  if(!response.ok) throw new Error(payload.message || payload.error || 'Something went wrong.');
  return payload;
}
$('loginForm').addEventListener('submit',async e=>{
  e.preventDefault();
  try{await api('/login',{username:$('username').value.trim(),password:$('password').value});closeLogin();document.querySelector('main').classList.add('hidden');document.querySelector('footer').classList.add('hidden');$('dashboard').classList.remove('hidden');await loadAccount();window.scrollTo(0,0);toast('Welcome to your demo dashboard.');}
  catch(err){$('loginError').textContent=err.message;}
});
async function loadAccount(){
  const data=await api('/account',null,'GET');
  $('balance').textContent=money(data.balance);
  $('transactionRows').innerHTML=data.transactions.map(t=>`<tr><td>${escapeHtml(t.description)}</td><td>${escapeHtml(t.date)}</td><td class="${t.type==='CREDIT'?'credit':'debit'}">${t.type}</td><td class="right ${t.type==='CREDIT'?'credit':'debit'}">${t.type==='CREDIT'?'+':'−'} ${money(t.amount)}</td></tr>`).join('') || '<tr><td colspan="4">No transactions yet.</td></tr>';
}
function money(value){return new Intl.NumberFormat('en-IN',{style:'currency',currency:'INR'}).format(value);}
function escapeHtml(value){return String(value).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));}
async function actionForm(formId,path,values){
  $(formId).addEventListener('submit',async e=>{e.preventDefault();try{await api(path,values());await loadAccount();$(formId).reset();toast('Demo transaction completed.');}catch(err){toast(err.message);}});
}
actionForm('depositForm','/deposit',()=>({amount:$('depositAmount').value}));
actionForm('withdrawForm','/withdraw',()=>({amount:$('withdrawAmount').value}));
actionForm('transferForm','/transfer',()=>({amount:$('transferAmount').value,recipient:$('transferRecipient').value}));
async function logout(){try{await api('/logout',{});}catch(e){}$('dashboard').classList.add('hidden');document.querySelector('main').classList.remove('hidden');document.querySelector('footer').classList.remove('hidden');toast('You have signed out.');}
window.addEventListener('keydown',e=>{if(e.key==='Escape')closeLogin();});
