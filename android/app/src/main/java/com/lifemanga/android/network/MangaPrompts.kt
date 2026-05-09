package com.lifemanga.android.network

import com.lifemanga.android.data.BubbleMode
import com.lifemanga.android.data.MangaStyle

object MangaPrompts {

    /**
     * Build a rich, Wan2.5-optimised positive prompt for a manga panel or page.
     *
     * @param style          The manga art style.
     * @param bubbleMode     Speech bubble language / presence directive.
     * @param userPrompt     Free-form scene description from the user.
     * @param isColor        Whether to render in full colour or black-and-white.
     * @param panelCount     Number of panels on the page (1 = single panel).
     * @param previousPageDesc Short description of the previous page for continuity (optional).
     * @param characterDescs   Per-character appearance descriptions for consistency (optional).
     */
    fun buildMangaPrompt(
        style: MangaStyle,
        bubbleMode: BubbleMode,
        userPrompt: String,
        isColor: Boolean,
        panelCount: Int = 1,
        previousPageDesc: String = "",
        characterDescs: List<String> = emptyList(),
    ): String = buildString {

        // 1. Style foundation from the existing MangaStyle prompt system
        append(style.effectivePrompt(isColor))

        // 2. Layout directive
        append("\n\nLAYOUT DIRECTIVE:\n")
        when (panelCount) {
            1 -> append("Single full-page manga panel. Dramatic composition, clear focal point.")
            4 -> append(
                "Four-panel (2×2 grid) manga page layout. " +
                    "Each panel clearly separated by clean black panel borders with consistent gutter width. " +
                    "Use varied panel sizes for visual rhythm; allow one panel to be slightly larger for emphasis.",
            )
            6 -> append(
                "Six-panel manga page layout. " +
                    "Arrange panels in a dynamic but readable grid — consider a 3×2 or 2-3-1 split. " +
                    "Keep gutter lines clean and consistent. Each panel should advance the story beat.",
            )
            8 -> append(
                "Eight-panel manga page layout. " +
                    "Dense grid composition (4×2 or 3-3-2). Panels should be compact but readable; " +
                    "reserve the largest panel for the emotional climax of the page.",
            )
            else -> append(
                "$panelCount-panel manga page layout. " +
                    "Balance panel sizes for readability. " +
                    "Use clean black panel borders with consistent gutters.",
            )
        }

        // 3. Speech bubble directive
        append("\n\nSPEECH BUBBLE DIRECTIVE:\n")
        append(bubbleMode.directive)

        // 4. Character consistency anchors
        if (characterDescs.isNotEmpty()) {
            append("\n\nCHARACTER REFERENCE (maintain consistency across all panels):\n")
            characterDescs.forEachIndexed { idx, desc ->
                append("Character ${idx + 1}: $desc\n")
            }
        }

        // 5. Story continuity
        if (previousPageDesc.isNotBlank()) {
            append("\n\nCONTINUITY FROM PREVIOUS PAGE:\n")
            append(previousPageDesc.trim())
            append(
                "\nMaintain visual and narrative continuity — same characters, setting, and mood " +
                    "unless the scene explicitly changes.",
            )
        }

        // 6. User scene description
        if (userPrompt.isNotBlank()) {
            append("\n\nSCENE DESCRIPTION:\n")
            append(userPrompt.trim())
        }

        // 7. Wan2.5 quality boosters
        append(
            "\n\nQUALITY BOOSTERS: " +
                "masterpiece, best quality, highly detailed manga illustration, " +
                "professional comic art, sharp linework, clear panel composition, " +
                "print-ready manga page.",
        )
    }

    /**
     * Build a negative prompt to suppress common artefacts for the given style.
     */
    fun buildNegativePrompt(style: MangaStyle, isColor: Boolean): String = buildString {
        // Universal negative terms
        append(
            "low quality, blurry, jpeg artefacts, watermark, signature, username, " +
                "deformed anatomy, extra limbs, missing fingers, fused fingers, " +
                "bad proportions, disfigured, ugly face, worst quality, " +
                "duplicate panels, overlapping panels, illegible text, " +
                "photorealistic, 3D render, CGI, realistic photography, ",
        )

        if (!isColor) {
            append("color, colorful, colored lineart, ")
        } else {
            append("monochrome, grayscale, desaturated, ")
        }

        // Style-specific suppressors
        when (style) {
            MangaStyle.CHIBI_4KOMA -> append("realistic proportions, detailed background, heavy shading, ")
            MangaStyle.HORROR_JUNJI_ITO -> append(
                "bright colors, cute style, chibi, blood, gore, violence, nudity, ",
            )
            MangaStyle.RETRO_GEKIGA -> append("modern style, anime eyes, chibi, digital art look, ")
            MangaStyle.SCIFI_MECHA -> append("fantasy elements, magic, medieval, organic shapes, ")
            else -> append("sketchy lines, rough pencil, unfinished artwork, ")
        }

        append("nsfw, explicit content, nudity, sexual content")
    }

    /**
     * Build a prompt for generating a character reference / model sheet.
     *
     * @param name     Character name.
     * @param bio      Short bio or personality traits.
     * @param artStyle Short art style descriptor (e.g. "modern shonen manga").
     */
    fun buildCharacterSheetPrompt(
        name: String,
        bio: String,
        artStyle: String,
    ): String = buildString {
        append("Character reference sheet for \"$name\". ")
        if (bio.isNotBlank()) append("Personality and background: ${bio.trim()}. ")
        append(
            "Show the character from multiple angles: front view, three-quarter view, and side profile. " +
                "Include close-up of face and hair. Clean $artStyle style linework. " +
                "Consistent character design across all views. " +
                "White background with simple label annotations. " +
                "Professional manga character design sheet. " +
                "masterpiece, best quality, character model sheet, turnaround reference.",
        )
    }

    /**
     * Build the system prompt sent to Qwen for structured story / script generation.
     * The model is instructed to return strict JSON only.
     *
     * @param panelCount Number of panels to script.
     */
    fun buildStorySystemPrompt(panelCount: Int): String = buildString {
        append(
            "You are a professional manga story writer and scriptwriter. " +
                "Your task is to create a compelling $panelCount-panel manga script based on the user's request.\n\n",
        )
        append("Return ONLY valid JSON — no markdown, no explanation, no code fences. ")
        append("The JSON must conform exactly to this schema:\n\n")
        append(
            """
{
  "title": "Short manga title (max 10 words)",
  "synopsis": "One-sentence synopsis of the whole page",
  "panels": [
    {
      "description": "Visual description of what is drawn in this panel. Be specific about characters, setting, expressions, camera angle, and composition. This will be fed directly to an image generation model.",
      "dialogue": "Spoken dialogue for speech bubbles (empty string if none)",
      "narration": "Caption box narration text (empty string if none)"
    }
  ]
}
""".trimIndent(),
        )
        append(
            "\n\nRules:\n" +
                "- The panels array must have exactly $panelCount entries.\n" +
                "- Each panel description must be vivid, self-contained, and manga-appropriate.\n" +
                "- Dialogue should be concise — no more than 10 words per bubble.\n" +
                "- Narration should be punchy — no more than 15 words per caption.\n" +
                "- Keep content family-friendly unless the user explicitly requests otherwise.\n" +
                "- Output ONLY the JSON object. Do not include any text before or after it.",
        )
    }
}
