package com.ashraf.novelreader

import org.json.JSONObject

object WebNovelAdapter {
    fun isWebNovel(url: String) = SiteProfiles.forUrl(url).navigationMode == NavigationMode.WEBNOVEL_CATALOG

    fun extractScript(): String = """
(function(){
 const clean=s=>(s||'').replace(/\u00a0/g,' ').replace(/[ \t]+/g,' ').replace(/\n[ \t]+/g,'\n').replace(/\n{3,}/g,'\n\n').trim();
 const BAD='script,style,noscript,iframe,nav,header,footer,aside,form,button,svg,img,video,audio,canvas,[aria-hidden="true"],[hidden],.ads,.ad,[class*="advert" i],[id*="advert" i],[class*="comment" i],[id*="comment" i],[class*="sidebar" i],[id*="sidebar" i],[class*="toolbar" i],[id*="toolbar" i],[class*="cookie" i],[id*="cookie" i],[class*="popup" i],[id*="popup" i],.j_catalog_list,.j_readTool';
 const BLOCK='p,blockquote,pre,li';
 const semantic=/(^|[-_ ])(?:chapter|content|reader|reading|novel|story|prose|article|entry|post|text|body|main)([-_ ]|$)/i;
 const chapterName=/(chapter|chap|episode|ep|part|volume|prologue|epilogue)/i;
 const uiWord=/(comment|reply|share|follow|login|sign.?in|register|subscribe|advert|cookie|menu|sidebar|toolbar|navigation|download app|read offline|table of contents)/i;

 const visible=e=>{
   if(!e||e===document.body||e===document.documentElement)return false;
   const s=getComputedStyle(e);
   if(s.display==='none'||s.visibility==='hidden'||s.visibility==='collapse'||s.opacity==='0')return false;
   const r=e.getBoundingClientRect?.();
   return !!r&&r.width>20&&r.height>20;
 };
 const textOf=e=>{
   if(!e)return '';
   const c=e.cloneNode(true);
   try{c.querySelectorAll(BAD).forEach(x=>x.remove())}catch(_){}
   c.querySelectorAll('br').forEach(x=>x.replaceWith('\n'));
   return clean(c.textContent||'');
 };
 const ownText=e=>{
   let out='';
   for(const n of e?.childNodes||[]){
     if(n.nodeType===Node.TEXT_NODE)out+=' '+(n.nodeValue||'');
     else if(n.nodeType===Node.ELEMENT_NODE && n.tagName==='BR')out+='\n';
   }
   return clean(out);
 };
 const blockStats=e=>{
   const blocks=[...e.querySelectorAll(BLOCK)].filter(x=>visible(x));
   let useful=0,total=0;
   for(const b of blocks){
     const t=clean(b.textContent||'');
     if(t.length>=20 && t.length<=12000){useful++;total+=t.length}
   }
   return {count:useful,total};
 };
 const metaOf=e=>((e.id||'')+' '+(typeof e.className==='string'?e.className:'')+' '+(e.getAttribute?.('role')||'')).toLowerCase();

 // The important part: find the LOWEST visible element that actually owns the
 // chapter text. Ancestor wrappers are rejected when a descendant owns most
 // of the same text. This prevents comments/recommendations/page shells from
 // winning merely because they contain more characters.
 const raw=[...document.querySelectorAll('article,main,[role="main"],section,div')];
 const DIRECT='[data-chapter-body],[data-chapter-content],[class*="chapter-body" i],[class*="chapter-content" i],[class*="novel-content" i],[class*="reading-content" i],[class*="reader-content" i]';
 const directRoots=[...document.querySelectorAll(DIRECT)].filter(e=>visible(e));
 const candidates=[];
 for(const e of raw){
   if(!visible(e))continue;
   const t=textOf(e),len=t.length;
   if(len<180||len>120000)continue;
   const bs=blockStats(e),meta=metaOf(e);
   const isDirect=directRoots.includes(e);
   // A chapter root normally owns multiple paragraph-like blocks. A single
   // long paragraph is not enough unless the site explicitly identifies the
   // element as chapter/content/reader text.
   if(bs.count<2 && !isDirect && !/(chapter|content|reader|reading|prose|article|story)/i.test(meta))continue;
   const own=ownText(e).length;
   let score=0;
   score += Math.log2(len+1)*250;
   score += Math.min(bs.total,30000)*0.18;
   score += Math.min(bs.count,80)*650;
   score += Math.min(own,1500)*0.4;
   if(e.matches?.('article,main,[role="main"]'))score+=700;
   if(isDirect)score+=12000;
   if(semantic.test(meta))score+=2200;
   if(/chapter|prose|reader|reading/.test(meta))score+=2200;
   if(uiWord.test(meta))score-=5000;
   const r=e.getBoundingClientRect();
   if(r.width>120&&r.height>150)score+=300;

   // If a visible descendant contains most of this element's text, this is
   // almost certainly just a wrapper. Penalize the wrapper heavily.
   let nestedMax=0,nestedEl=null;
   for(const d of e.querySelectorAll('article,main,[role="main"],section,div')){
     if(!visible(d)||d===e)continue;
     const dl=textOf(d).length;
     if(dl>nestedMax){nestedMax=dl;nestedEl=d}
   }
   if(nestedMax>0 && nestedMax/len>=0.72){
     // A directly identified chapter/content root is authoritative; its
     // descendant may be a single paragraph or formatting container.
     if(!isDirect){
       score-=9000;
       if(nestedMax/len>=0.9)score-=7000;
     }
   }

   // A chapter root normally has several paragraph-like blocks and little
   // navigation/link text.
   let linkLen=0;
   try{e.querySelectorAll('a,button,[role="button"]').forEach(x=>linkLen+=(x.innerText||x.textContent||'').trim().length)}catch(_){}
   const linkRatio=linkLen/Math.max(1,len);
   if(linkRatio>.25)score-=Math.min(10000,linkRatio*14000);
   if(bs.count>=2)score+=2500;
   if(bs.count>=3)score+=2200;
   if(bs.count>=8)score+=1800;
   if(bs.count===1)score-=7000;
   candidates.push({e,t,len,score,blocks:bs.count,nestedMax});
 }

 // Prefer an explicitly identified chapter/content root first. This
 // prevents a generic paragraph from winning when the page already exposes
 // the real chapter container.
 candidates.sort((a,b)=>b.score-a.score);
 let best=candidates.find(x=>directRoots.includes(x.e))||candidates[0]||null;
 if(!best){
   // Last resort: use the largest text-bearing block, but keep its containing
   // element as the chapter root when possible so we do not throw away sibling
   // paragraphs.
   let largest=null;
   for(const e of document.querySelectorAll(BLOCK)){
     if(!visible(e))continue;
     const t=textOf(e);
     if(t.length>=180 && (!largest || t.length>largest.t.length))largest={e,t};
   }
   if(largest){
     let root=largest.e.parentElement;
     while(root && root!==document.body && textOf(root).length<largest.t.length*1.15){
       root=root.parentElement;
     }
     const rt=root&&root!==document.body?textOf(root):largest.t;
     best={e:root&&root!==document.body?root:largest.e,t:rt,len:rt.length,score:0,blocks:blockStats(root&&root!==document.body?root:largest.e).count};
   }
 }
 if(!best||best.len<120)return JSON.stringify({ok:false});

 // Final guard: if the chosen node is itself a wrapper, descend to the
 // strongest visible child that still contains most of the chapter.
 let changed=true;
 while(changed){
   changed=false;
   let childBest=null, childScore=-Infinity;
   for(const d of best.e.querySelectorAll('article,main,[role="main"],section,div')){
     if(!visible(d))continue;
     const dt=textOf(d),ratio=dt.length/Math.max(1,best.len);
     if(ratio<0.72||dt.length<180)continue;
     const bs=blockStats(d),m=metaOf(d);
     // Never descend into a lone paragraph-like child; that was the bug that
     // could reduce a full chapter to its first long paragraph.
     if(bs.count<2)continue;
     let s=ratio*4000+bs.count*700+(semantic.test(m)?1800:0)+(chapterName.test(m)?2200:0);
     if(uiWord.test(m))s-=5000;
     if(s>childScore){childScore=s;childBest={e:d,t:dt,len:dt.length,score:s,blocks:bs.count}}
   }
   if(childBest && childBest.score>5000){best=childBest;changed=true}
 }

 let body=textOf(best.e);
 let title='';
 const titleCandidates=[...document.querySelectorAll('h1,h2,[class*="title" i],[id*="title" i]')];
 let titleScore=-1;
 for(const e of titleCandidates){
   if(!visible(e))continue;
   const t=clean(e.textContent);
   if(!t||t.length>180||uiWord.test(t))continue;
   let s=0;
   if(/^chapter\s*[-#:.]?\s*\d+/i.test(t)||/^episode\s*[-#:.]?\s*\d+/i.test(t)||/^ch\.?\s*\d+/i.test(t)||/^part\s*[-#:.]?\s*\d+/i.test(t))s+=10;
   if(chapterName.test(t))s+=3;
   if(e.tagName==='H1')s+=5; else if(e.tagName==='H2')s+=3;
   if(t.length<100)s+=2;
   if(s>titleScore){titleScore=s;title=t}
 }
 if(!title)title=clean(document.title||'');
 const titleNorm=clean(title).toLowerCase();
 body=body.split(/\n+/).map(x=>x.trim()).filter(x=>{
   const n=clean(x).toLowerCase();
   if(!n)return false;
   if(titleNorm&&n===titleNorm)return false;
   if(uiWord.test(n)&&n.length<180)return false;
   return true;
 }).join('\n\n').trim();
 if(title&&body.toLowerCase().startsWith(title.toLowerCase()))body=body.slice(title.length).trim();
 if(body.length<120)return JSON.stringify({ok:false});

 const num=(title.match(/(?:chapter|chap|ch|episode|ep|part)\.?\s*[-#:.]?\s*(\d+(?:\.\d+)?)/i)||title.match(/第\s*(\d+)\s*[章话話節回]/)||[])[1]||'';

 // Build a stable selector that points to the exact chosen content root.
 const selectorFor=e=>{
   if(!e)return '';
   if(e.id&&/^[A-Za-z_][A-Za-z0-9_-]*$/.test(e.id))return '#'+CSS.escape(e.id);
   const parts=[];
   let x=e;
   while(x&&x!==document.body&&parts.length<6){
     let p=x.tagName.toLowerCase();
     const cls=[...x.classList].filter(v=>/^[A-Za-z_][A-Za-z0-9_-]*$/.test(v)).slice(0,2);
     if(cls.length)p+='.'+cls.map(v=>CSS.escape(v)).join('.');
     const parent=x.parentElement;
     if(parent){
       const same=[...parent.children].filter(y=>y.tagName===x.tagName);
       if(same.length>1)p+=':nth-of-type('+(same.indexOf(x)+1)+')';
     }
     parts.unshift(p);x=parent;
   }
   return parts.join(' > ');
 };
 window.__nrContentElement=best.e;
 window.__nrContentSelector=selectorFor(best.e);
 window.__nrContentSignature={url:location.href,textHash:body.length+':'+body.slice(0,160),element:best.e};

 const links=[...document.querySelectorAll('a[href],button,[role="button"]')];
 function pick(next){
   const re=next?/^(?:next|next chapter|continue|continue reading|newer)\b|next chapter|continue reading|›|»|→/i:/^(?:prev|previous|previous chapter|older)\b|previous chapter|‹|«|←/i;
   let target=null,bestScore=-1;
   for(const e of links){
     if(!visible(e))continue;
     const meta=[e.innerText,e.getAttribute('aria-label'),e.getAttribute('title'),e.id,typeof e.className==='string'?e.className:''].join(' ');
     if(!re.test(meta))continue;
     let s=0;
     if(/chapter/i.test(meta))s+=5;
     if(e.tagName==='A'&&e.href)s+=3;
     if(e.closest('nav'))s+=1;
     if(s>bestScore){bestScore=s;target=e}
   }
   return target?.href||null;
 }
 const chapterText=title?(title+'\n\n'+body):body;
 return JSON.stringify({ok:true,url:location.href,title,num,text:chapterText,next:pick(true),prev:pick(false)});
})()
""".trimIndent()
    fun buildChapter(raw: String): Chapter? = runCatching {
        val o=JSONObject(raw)
        if(!o.optBoolean("ok"))return null
        val url=o.optString("url");val title=o.optString("title")
        Chapter(id="${url}#${o.optString("num")}",url=url,title=title,number=o.optString("num"),
            text=o.optString("text"),nextUrl=o.optString("next").takeIf{it.isNotBlank()&&it!="null"},
            prevUrl=o.optString("prev").takeIf{it.isNotBlank()&&it!="null"})
    }.getOrNull()
    fun catalogUrl(chapterUrl:String):String?=runCatching{
        val u=android.net.Uri.parse(chapterUrl);val seg=u.pathSegments
        val i=seg.indexOfFirst{it.equals("book",true)}
        if(i>=0&&i+1<seg.size)"${u.scheme}://${u.host}/book/${seg[i+1]}/catalog" else null
    }.getOrNull()
    fun navigateFromCatalogScript(dir:String,title:String,currentUrl:String):String{
        val t=JSONObject.quote(title);val u=JSONObject.quote(currentUrl);val d=JSONObject.quote(dir)
        return """
(async function(){
 const dir=$d,title=$t;
 const norm=s=>(s||'').replace(/\s+/g,' ').trim().toLowerCase();
 const strip=s=>norm(s).replace(/^\s*\d+\s*[-.:)]?\s*/,'');
 const clean=u=>{try{return new URL(u,location.href).pathname.replace(/\/+$/,'')}catch(e){return String(u||'').split('?')[0].split('#')[0].replace(/\/+$/,'')}};
 const book=(location.pathname.match(/^\/book\/[^/]+/i)||[])[0];if(!book)return 'none';
 const html=await(await fetch(location.origin+book+'/catalog',{credentials:'include',cache:'no-store'})).text();
 const doc=new DOMParser().parseFromString(html,'text/html');
 const links=[...doc.querySelectorAll('.j_catalog_list .volume-item li a[href],.j_catalog_list a[href],a[href]')].map(a=>({p:clean(a.href),t:strip(a.getAttribute('title')||a.getAttribute('aria-label')||a.textContent)})).filter(x=>x.t&&x.p.startsWith(book+'/')&&!/\/catalog$/i.test(x.p));
 if(!links.length)return 'none';
 const cur=strip(title),currentPath=new URL(currentUrl,location.href).pathname.replace(/\/+$/,'');
 let idx=links.findIndex(x=>new URL(x.p,location.href).pathname.replace(/\/+$/,'')===currentPath);
 if(idx<0)idx=links.findIndex(x=>x.t===cur);
 if(idx<0){let best=-1,bs=0;const words=cur.split(/\s+/).filter(x=>x.length>=3);links.forEach((x,i)=>{let s=words.reduce((n,w)=>n+(x.t.includes(w)?(w.length>=5?3:1):0),0);if(s>bs){bs=s;best=i}});if(bs>=3)idx=best;}
 if(idx<0)return 'no-current';const ni=dir==='next'?idx+1:idx-1;if(ni<0||ni>=links.length)return 'edge';
 return new URL(links[ni].p,location.origin).href;
})().catch(e=>'error:'+e)
""".trimIndent()
    }
}