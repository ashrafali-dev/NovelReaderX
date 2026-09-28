package com.ashraf.novelreader

import android.content.Context

class PromptStore(context: Context) {
    private val prefs = context.getSharedPreferences("novel_reader", Context.MODE_PRIVATE)
    fun get(): String = prefs.getString("prompt", DEFAULT) ?: DEFAULT
    fun save(value: String) { prefs.edit().putString("prompt", value).apply() }

    companion object {
        const val DEFAULT = """
You are a professional literary translator.
Translate only the chapter text below into natural, fluent Bengali.
Preserve names, terminology, tone, dialogue, paragraph structure, emotions, and pacing.
Do not add explanations or comments. Return only the Bengali translation.

[CHAPTER]
{{CHAPTER}}
""".trimIndent()
    }
}