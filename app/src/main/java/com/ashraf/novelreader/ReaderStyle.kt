package com.ashraf.novelreader

object ReaderStyle {
    fun apply(night: Boolean, fontSize: Int): String {
        val bg = if (night) "#101114" else "#f7f7f7"
        val fg = if (night) "#e7e5e2" else "#252525"
        return """
(function(){
  const fs=$fontSize;
  const bg='$bg', fg='$fg';
  let s=document.getElementById('novelreaderx-reader-style');
  if(!s){s=document.createElement('style');s.id='novelreaderx-reader-style';document.head.appendChild(s);}

  // Do not style every .content/article on the site: those can contain
  // navigation controls. Scope reader typography to our translated element.
  s.textContent=
    'html,body{background:'+bg+' !important;color:'+fg+' !important;}' +
    'body{margin:0 !important;}' +
    '[data-novelreaderx-translated="1"]{' +
      'background:'+bg+' !important;color:'+fg+' !important;' +
      'max-width:720px !important;width:100% !important;' +
      'margin-left:auto !important;margin-right:auto !important;' +
      'padding-left:18px !important;padding-right:18px !important;' +
      'font-family:"Noto Serif Bengali","Noto Serif",Georgia,serif !important;' +
      'font-size:'+fs+'px !important;line-height:1.9 !important;letter-spacing:.05px !important;' +
    '}' +
    '[data-novelreaderx-translated="1"] p{' +
      'font-family:"Noto Serif Bengali","Noto Serif",Georgia,serif !important;' +
      'font-size:'+fs+'px !important;line-height:1.9 !important;' +
      'color:'+fg+' !important;margin:0 0 1.05em 0 !important;text-align:left !important;' +
    '}' +
    '[data-novelreaderx-translated="1"] p.nr-title{' +
      'font-size:24px !important;line-height:1.45 !important;font-weight:700 !important;' +
      'margin:4px 0 1.25em 0 !important;' +
    '}' +
    'a{color:'+(night? '#9ecbff':'#245ea8')+';}';

  const el=window.__nrContentElement||window.__nrTranslatedElement;
  if(el){
    el.style.setProperty('background',bg,'important');
    el.style.setProperty('color',fg,'important');
    el.style.setProperty('max-width','720px','important');
    el.style.setProperty('width','100%','important');
    el.style.setProperty('margin-left','auto','important');
    el.style.setProperty('margin-right','auto','important');
    el.style.setProperty('padding-left','18px','important');
    el.style.setProperty('padding-right','18px','important');
    el.style.setProperty('font-family','"Noto Serif Bengali","Noto Serif",Georgia,serif','important');
    el.style.setProperty('font-size',fs+'px','important');
    el.style.setProperty('line-height','1.9','important');
    el.style.setProperty('text-align','left','important');
  }
  return 'styled';
})()
""".trimIndent()
    }
}
