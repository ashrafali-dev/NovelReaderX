package com.ashraf.novelreader

import android.content.Context
import java.text.Normalizer
import java.util.Locale

data class GlossaryEntry(
    val source: String,
    val variants: List<String>,
    val translation: String
)

class GlossaryStore(context: Context) {
    private val prefs=context.getSharedPreferences("novel_reader_glossary",Context.MODE_PRIVATE)

    fun raw(): String = prefs.getString("glossary",DEFAULT) ?: DEFAULT
    fun save(value:String){ prefs.edit().putString("glossary",value).apply(); rebuild() }
    fun count(): Int = parse(raw()).size

    @Volatile private var cachedMatcher: Matcher?=null

    init { rebuild() }

    fun match(text:String): List<GlossaryEntry> = cachedMatcher?.find(text).orEmpty()

    private fun rebuild(){
        cachedMatcher=Matcher(parse(raw()))
    }

    private fun parse(raw:String): List<GlossaryEntry>{
        val out=ArrayList<GlossaryEntry>()
        val seen=HashSet<String>()

        raw.lineSequence().forEach{line->
            val s=line.trim()
            if(s.isBlank() || s.startsWith("#")) return@forEach

            val arrow=s.indexOf("=>")
            if(arrow<=0 || arrow>=s.length-2) return@forEach

            val translation=s.substring(arrow+2).trim()
            if(translation.isBlank()) return@forEach

            val variants=s.substring(0,arrow)
                .split("|")
                .map{it.trim()}
                .filter{it.isNotBlank()}
                .distinctBy{normalize(it)}

            if(variants.isEmpty()) return@forEach

            val key=variants.joinToString("\u0001"){normalize(it)}
            if(seen.add(key)){
                out.add(GlossaryEntry(variants.first(),variants,translation))
            }
        }
        return out
    }

    private class Matcher(private val entries:List<GlossaryEntry>){
        private data class Hit(val start:Int,val end:Int,val entry:GlossaryEntry)

        private class Node{
            val next=HashMap<Char,Node>()
            var entry:GlossaryEntry?=null
        }

        private val root=Node()

        init{
            entries.forEach{entry->
                entry.variants.forEach{variant->
                    var n=root
                    normalize(variant).forEach{c->n=n.next.getOrPut(c){Node()}}
                    if(n.entry==null)n.entry=entry
                }
            }
        }

        fun find(text:String):List<GlossaryEntry>{
            if(text.isBlank() || entries.isEmpty())return emptyList()

            val s=normalize(text)
            val hits=ArrayList<Hit>()

            for(i in s.indices){
                var n=root
                var j=i
                var best:Hit?=null

                while(j<s.length){
                    n=n.next[s[j]] ?: break
                    j++

                    val e=n.entry
                    if(e!=null && boundary(s,i,j)){
                        best=Hit(i,j,e)
                    }
                }

                if(best!=null)hits.add(best)
            }

            val selected=ArrayList<Hit>()
            var lastEnd=-1

            hits.sortedWith(
                compareBy<Hit>{it.start}.thenByDescending{it.end-it.start}
            ).forEach{hit->
                if(hit.start>=lastEnd){
                    selected.add(hit)
                    lastEnd=hit.end
                }
            }

            val seen=HashSet<GlossaryEntry>()
            return selected.map{it.entry}.filter{seen.add(it)}
        }

        private fun boundary(s:String,start:Int,end:Int):Boolean{
            val term=s.substring(start,end)
            val latinEdge=
                term.firstOrNull()?.isAsciiWord()==true ||
                term.lastOrNull()?.isAsciiWord()==true

            if(!latinEdge)return true

            val before=if(start>0)s[start-1] else null
            val after=if(end<s.length)s[end] else null

            return before?.isAsciiWord()!=true && after?.isAsciiWord()!=true
        }

        private fun Char.isAsciiWord()=
            this in 'a'..'z' || this in '0'..'9' || this=='_'
    }

    companion object{
        private const val DEFAULT=""

        private fun normalize(s:String):String=
            Normalizer.normalize(s,Normalizer.Form.NFKC).lowercase(Locale.ROOT)
    }
}
