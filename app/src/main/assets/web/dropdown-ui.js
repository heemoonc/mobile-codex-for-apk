/* Keep native select values/events as bindings; render a toggleable in-app list. */
(()=>{'use strict';
 const records=new WeakMap();let active=null,serial=0;
 function close(focus=false){if(!active)return false;const r=active;active=null;r.list.hidden=true;r.button.setAttribute('aria-expanded','false');if(focus&&r.button.isConnected)r.button.focus();return true;}
 function sync(r){const s=r.source,o=s.options[s.selectedIndex],text=o?.textContent||'';if(r.text.textContent!==text)r.text.textContent=text;r.button.disabled=s.disabled;r.wrap.hidden=s.hidden;
  const name=s.getAttribute('aria-label')||[...(s.labels||[])].map(l=>[...l.childNodes].filter(n=>n.nodeType===3).map(n=>n.textContent).join('').trim()).filter(Boolean).join(' ');
  if(r.button.getAttribute('aria-label')!==(name||text))r.button.setAttribute('aria-label',name||text);
  if(s.disabled||s.hidden||!s.isConnected){if(active===r)close();}
 }
 function choose(r,index){const o=r.source.options[index];if(!o||o.disabled||o.parentElement?.disabled)return;r.source.selectedIndex=index;sync(r);close(true);r.source.dispatchEvent(new Event('input',{bubbles:true}));r.source.dispatchEvent(new Event('change',{bubbles:true}));}
 function enabled(r){return [...r.list.querySelectorAll('button:not(:disabled)')];}
 function open(r,keyboard=false){sync(r);if(r.button.disabled||r.wrap.hidden)return;close();r.list.replaceChildren();
  [...r.source.options].forEach((o,index)=>{if(o.hidden)return;const b=document.createElement('button');b.type='button';b.setAttribute('role','option');b.dataset.value=o.value;b.textContent=o.textContent;b.disabled=o.disabled||!!o.parentElement?.disabled;b.setAttribute('aria-selected',String(index===r.source.selectedIndex));b.addEventListener('click',()=>choose(r,index));r.list.append(b);});
  if(!r.list.children.length)return;active=r;r.list.hidden=false;r.button.setAttribute('aria-expanded','true');r.wrap.classList.remove('dropdown-above');const box=r.wrap.getBoundingClientRect(),room=(window.visualViewport?.height||innerHeight)-box.bottom;if(room<160&&box.top>room)r.wrap.classList.add('dropdown-above');
  if(keyboard)(r.list.querySelector('[aria-selected=true]:not(:disabled)')||enabled(r)[0])?.focus();
 }
 function mount(s){if(records.has(s)||s.hidden||s.multiple||s.size>1)return;
  const wrap=document.createElement('span');wrap.className='dropdown'+(s.classList.contains('text-input')?' dropdown-field':'');const button=document.createElement('button'),text=document.createElement('span'),arrow=document.createElement('span'),list=document.createElement('span');
  button.type='button';button.className='dropdown-trigger';button.setAttribute('role','combobox');button.setAttribute('aria-haspopup','listbox');button.setAttribute('aria-expanded','false');arrow.className='dropdown-arrow';arrow.setAttribute('aria-hidden','true');list.className='dropdown-list';list.id='dropdown-list-'+(++serial);list.setAttribute('role','listbox');list.hidden=true;button.setAttribute('aria-controls',list.id);button.append(text,arrow);
  s.before(wrap);wrap.append(s,button,list);s.classList.add('dropdown-source');s.tabIndex=-1;s.setAttribute('aria-hidden','true');const r={source:s,wrap,button,text,list};records.set(s,r);
  button.addEventListener('click',e=>{e.preventDefault();active===r?close():open(r);});
  wrap.addEventListener('keydown',e=>{if(e.key==='Escape'&&active===r){e.preventDefault();e.stopPropagation();close(true);return;}if(e.key==='Tab'){close();return;}if(e.key==='ArrowDown'||e.key==='ArrowUp'){e.preventDefault();e.stopPropagation();if(active!==r){open(r,true);return;}const items=enabled(r),i=items.indexOf(document.activeElement),next=items[(i+(e.key==='ArrowDown'?1:-1)+items.length)%items.length];next?.focus();}else if(active===r&&(e.key==='Home'||e.key==='End')){e.preventDefault();const items=enabled(r);(e.key==='Home'?items[0]:items.at(-1))?.focus();}else if(active===r&&(e.key==='Enter'||e.key===' ')&&document.activeElement.getAttribute('role')==='option'){e.preventDefault();document.activeElement.click();}});
  s.addEventListener('change',()=>sync(r));s.addEventListener('invalid',e=>{e.preventDefault();button.focus();});s.addEventListener('click',e=>e.preventDefault());sync(r);
 }
 function scan(node){if(node.nodeType!==1)return;if(node.matches('select'))mount(node);node.querySelectorAll('select').forEach(mount);}
 for(const key of ['value','selectedIndex']){const property=Object.getOwnPropertyDescriptor(HTMLSelectElement.prototype,key);Object.defineProperty(HTMLSelectElement.prototype,key,{...property,set(v){property.set.call(this,v);const r=records.get(this);if(r)sync(r);}});}
 scan(document.documentElement);
 new MutationObserver(changes=>{for(const c of changes){const s=c.target.nodeType===1?(c.target.matches('select')?c.target:c.target.closest('select')):c.target.parentElement?.closest('select');if(s&&records.has(s)){const r=records.get(s);sync(r);if(active===r&&c.type==='childList')close();}for(const n of c.addedNodes||[])scan(n);}if(active&&(!active.wrap.isConnected||active.source.closest('dialog:not([open])')))close();}).observe(document.body,{subtree:true,childList:true,characterData:true,attributes:true,attributeFilter:['disabled','hidden','selected','label','aria-label','open']});
 document.addEventListener('pointerdown',e=>{if(active&&!active.wrap.contains(e.target)&&![...(active.source.labels||[])].some(l=>l.contains(e.target)))close();},true);
 document.addEventListener('click',e=>{if(e.target.closest('.dropdown'))return;const label=e.target.closest('label'),s=label?.querySelector('select'),r=s&&records.get(s);if(r){e.preventDefault();active===r?close():open(r);}else if(active)close();});
 document.addEventListener('cancel',e=>{if(close(true)){e.preventDefault();e.stopImmediatePropagation();}},true);
 document.addEventListener('reset',()=>queueMicrotask(()=>document.querySelectorAll('select').forEach(s=>{const r=records.get(s);if(r)sync(r);})));
 window.DropdownUI={close};
})();
