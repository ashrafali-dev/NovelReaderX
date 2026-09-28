# NovelReader X

A fresh Android split-WebView novel reader.

## Core design
- Novel WebView on top, AI WebView below.
- Draggable split divider.
- ChatGPT, Gemini, DeepSeek, Claude and Grok provider tabs.
- Persistent WebView cookies and DOM storage.
- Saved editable translation prompt with `{{CHAPTER}}` placeholder.
- Instant extraction and automatic send.
- Automatic response detection and insertion into the novel page.
- Every chapter/request carries a session boundary so stale async responses are ignored.
- WebNovel uses fast live-DOM extraction and catalog-based adjacent chapter lookup when a direct next/previous URL is unavailable.

## Build
Pushes to `main` run the GitHub Actions build workflow. The workflow produces a debug APK artifact.

## License
This new project contains original code. Open-source projects were used as architectural references; no NovelStudio source is required for the new app.
