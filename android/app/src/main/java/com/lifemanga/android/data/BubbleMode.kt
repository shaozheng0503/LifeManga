package com.lifemanga.android.data

enum class BubbleMode(
    val key: String,
    val displayName: String,
    val directive: String,
) {
    NONE(
        key = "none",
        displayName = "不画气泡",
        directive = "Do not draw any speech bubbles or text balloons. Tell the story purely through visual composition, body language, and facial expressions.",
    ),
    EMPTY(
        key = "empty",
        displayName = "空白气泡",
        directive = "Draw clean, perfectly oval speech bubble shapes with crisp single-line borders, but leave them completely empty inside. No text, letters, or symbols.",
    ),
    CHINESE(
        key = "chinese",
        displayName = "中文气泡",
        directive = "Draw speech bubbles with concise Simplified Chinese (简体中文) text inside, no more than 8 characters per bubble, in clean handwritten manga lettering. Make sure the Chinese characters are accurate and readable.",
    ),
    JAPANESE(
        key = "japanese",
        displayName = "日文气泡",
        directive = "Draw speech bubbles with concise Japanese text inside (mostly hiragana / katakana, occasional simple kanji), no more than 8 characters per bubble, in clean handwritten manga lettering.",
    ),
    ENGLISH(
        key = "english",
        displayName = "英文气泡",
        directive = "Draw speech bubbles with concise English text inside, no more than 12 characters per bubble, in clean uppercase comic-book lettering.",
    );

    companion object {
        fun fromKey(key: String?): BubbleMode? = entries.firstOrNull { it.key == key }
    }
}
