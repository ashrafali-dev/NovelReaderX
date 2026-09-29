package com.ashraf.novelreader

object ReaderStyle {
    fun apply(night: Boolean, fontSize: Int): String = """
(function(){
  // Translation is now written into the site's original chapter elements.
  // Never inject font-size, font-family, line-height, padding or margins.
  // This removes only NovelReaderX's old translation CSS; site CSS remains untouched.
  const s=document.getElementById('novelreaderx-reader-style');
  if(s)s.remove();
  document.querySelectorAll('[data-nr-translation="1"]').forEach(e=>{
    try{e.removeAttribute('data-nr-translation')}catch(_){}
  });
  return 'site-style';
})()
""".trimIndent()
}