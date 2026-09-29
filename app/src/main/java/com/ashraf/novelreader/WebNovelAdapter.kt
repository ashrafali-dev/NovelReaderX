package com.ashraf.novelreader

import org.json.JSONObject

object WebNovelAdapter {
    fun isWebNovel(url: String) = SiteProfiles.forUrl(url).navigationMode == NavigationMode.WEBNOVEL_CATALOG

    fun extractScript(): String = """
(function(){
 const clean=s=>(s||'').replace(/\\u00a0/g,' ').replace(/[ \\t]+/g,' ').replace(/\\n[ \\t]+/g,'\\n').replace(/\\n{3,}/g,'\\n\\n').trim();
 const BAD='script,style,noscript,iframe,nav,header,footer,aside,form,button,svg,img,video,audio,canvas,[aria-hidden="true"],[hidden],.ads,.ad,[class*="advert" i],[id*="advert" i],[class*="comment" i],[id*="comment" i],[class*="sidebar" i],[id*="sidebar" i],[class*="toolbar" i],[id*="toolbar" i],[class*="cookie" i],[id*="cookie" i],[class*="popup" i],[id*="popup" i],.j_catalog_list,.j_readTool';
 const semantic=/(^|[-_ ])(?:chapter|content|reader|reading|novel|story|prose|article|entry|post|text|body|main)([-_ ]|$)/i;
 const uiWord=/(comment|reply|share|follow|login|sign.?in|register|subscribe|advert|cookie|menu|sidebar|toolbar|navigation|download app|read offline)/i;
 const textOf=e=>{
   if(!e)return '';
   const c=e.cloneNode(true);
   try{c.querySelectorAll(BAD).forEach(x=>x.remove())}catch(_){}
   c.querySelectorAll('br').forEach(x=>x.replaceWith('\\n'));
   return clean(c.textContent||'');
 };
 const directBlocks=e=>e?e.querySelectorAll(':scope > p,:scope > div,:scope > section,:scope > article,:scope > blockquote,:scope > li').length:0;
 const paragraphCount=e=>e?e.querySelectorAll('p,blockquote').length:0;
 const linkTextLength=e=>{
   let n=0;
   try{e.querySelectorAll('a,button,[role="button"]').forEach(x=>n+=(x.innerText||x.textContent||'').trim().length)}catch(_){}
   return n;
 };
 const depth=e=>{let n=0;for(let x=e;x&&x!==document.body;x=x.parentElement)n++;return n};
 const scoreElement=e=>{
   if(!e||e===document.body||e===document.documentElement)return null;
   const t=textOf(e),len=t.length;
   if(len<120)return null;
   const pc=Math.min(paragraphCount(e),50);
   const db=Math.min(directBlocks(e),60);
   const links=linkTextLength(e);
   const ratio=links/Math.max(1,len);
   const meta=((e.id||'')+' '+(e.className&&typeof e.className==='string'?e.className:'')+' '+(e.getAttribute?.('role')||'')).toLowerCase();
   let score=Math.log2(len+1)*900+Math.min(len,12000)*0.45+pc*700+db*220;
   if(e.matches?.('article,main,[role="main"]'))score+=2500;
   if(semantic.test(meta))score+=5000;
   if(/chapter|prose|reader|reading/.test(meta))score+=3500;
   if(uiWord.test(meta))score-=5000;
   if(ratio>.35)score-=Math.min(9000,ratio*12000);
   if(len>30000)score-=Math.min(12000,(len-30000)*0.12);
   score-=Math.max(0,depth(e)-12)*120;
   const r=e.getBoundingClientRect?.();
   if(r&&r.width>20&&r.height>20)score+=500;
   return {e,t,len,score,pc,db};
 };
 const elements=[...document.querySelectorAll('article,main,[role="main"],section,div')];
 let best=null;
 for(const e of elements){
   const x=scoreElement(e);
   if(!x)continue;
   if(!best||x.score>best.score)best=x;
 }
 if(!best){
   for(const e of document.querySelectorAll('p,blockquote')){
     const x=scoreElement(e);
     if(x&&(!best||x.score>best.score))best=x;
   }
 }
 if(!best||best.len<120)return JSON.stringify({ok:false});
 let body=best.t;
 let title='';
 const titleCandidates=[...document.querySelectorAll('h1,h2,[class*="title" i],[id*="title" i]')];
 let titleScore=-1;
 for(const e of titleCandidates){
   const t=clean(e.textContent);
   if(!t||t.length>180||uiWord.test(t))continue;
   let s=0;
   if(/^chapter\\s*[-#:.]?\\s*\\d+/i.test(t)||/^episode\\s*[-#:.]?\\s*\\d+/i.test(t)||/^ch\\.?\\s*\\d+/i.test(t))s+=8;
   if(e.tagName==='H1')s+=5; else if(e.tagName==='H2')s+=3;
   if(t.length<100)s+=2;
   if(s>titleScore){titleScore=s;title=t}
 }
 if(!title)title=clean(document.title||'');
 const titleNorm=clean(title).toLowerCase();
 body=body.split(/\\n+/).map(x=>x.trim()).filter(x=>{
   const n=clean(x).toLowerCase();
   if(!n)return false;
   if(titleNorm&&n===titleNorm)return false;
   if(uiWord.test(n)&&n.length<180)return false;
   return true;
 }).join('\\n\\n').trim();
 if(title&&body.toLowerCase().startsWith(title.toLowerCase()))body=body.slice(title.length).trim();
 if(body.length<120)return JSON.stringify({ok:false});
 const num=(title.match(/(?:chapter|chap|ch|episode|ep)\\.?\\s*[-#:.]?\\s*(\\d+(?:\\.\\d+)?)/i)||title.match(/第\\s*(\\d+)\\s*[章话話節回]/)||[])[1]||'';
 const selectorFor=e=>{
   if(!e)return '';
   if(e.id&&/^[A-Za-z_][A-Za-z0-9_-]*$/.test(e.id))return '#'+e.id;
   const cls=[...e.classList].filter(x=>/^[A-Za-z_][A-Za-z0-9_-]*$/.test(x)).slice(0,3);
   return e.tagName.toLowerCase()+(cls.length?'.'+cls.join('.'):'' );
 };
 window.__nrContentElement=best.e;
 window.__nrContentSelector=selectorFor(best.e);
 const links=[...document.querySelectorAll('a[href],button,[role="button"]')];
 function pick(next){
   const re=next?/^(?:next|next chapter|continue|older|newer)\\b|next chapter|continue reading|›|»|→/i:/^(?:prev|previous|previous chapter|older|newer)\\b|previous chapter|‹|«|←/i;
   let target=null,bestScore=-1;
   for(const e of links){
     const meta=[e.innerText,e.getAttribute('aria-label'),e.getAttribute('title'),e.id,typeof e.className==='string'?e.className:''].join(' ');
     if(!re.test(meta))continue;
     const r=e.getBoundingClientRect();if(r.width<2||r.height<2)continue;
     let s=0;
     if(/chapter/i.test(meta))s+=5;
     if(e.tagName==='A'&&e.href)s+=3;
     if(e.closest('nav'))s+=1;
     if(s>bestScore){bestScore=s;target=e}
   }
   return target?.href||null;
 }
 const chapterText=title?(title+'\\n\\n'+body):body;
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