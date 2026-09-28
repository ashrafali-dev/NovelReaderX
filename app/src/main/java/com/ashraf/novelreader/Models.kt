package com.ashraf.novelreader

data class Chapter(
    val id: String,
    val url: String,
    val title: String,
    val number: String,
    val text: String,
    val nextUrl: String?,
    val prevUrl: String?
)

enum class AiProvider(val label: String, val home: String, val host: String) {
    CHATGPT("ChatGPT", "https://chatgpt.com/", "chatgpt.com"),
    GEMINI("Gemini", "https://gemini.google.com/", "gemini.google.com"),
    DEEPSEEK("DeepSeek", "https://chat.deepseek.com/", "deepseek.com"),
    CLAUDE("Claude", "https://claude.ai/", "claude.ai"),
    GROK("Grok", "https://grok.com/", "grok.com")
}