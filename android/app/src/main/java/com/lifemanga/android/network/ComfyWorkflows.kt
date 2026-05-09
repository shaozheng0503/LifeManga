package com.lifemanga.android.network

object ComfyWorkflows {

    // ---------------------------------------------------------------------------
    // Public workflow builders
    // ---------------------------------------------------------------------------

    /**
     * Text-to-manga workflow using Wan2.5 T2I.
     *
     * Graph:
     *   "1" WanTextToImageApi  →  "2" SaveImage
     */
    fun textToManga(
        prompt: String,
        negPrompt: String = "",
        width: Int = 896,
        height: Int = 1152,
        seed: Long = -1L,
    ): Map<String, Any> {
        val resolvedSeed = resolveSeed(seed)
        return mapOf(
            "1" to mapOf(
                "class_type" to "WanTextToImageApi",
                "inputs" to mapOf(
                    "model" to "wan2.5-t2i-preview",
                    "prompt" to prompt,
                    "negative_prompt" to negPrompt,
                    "width" to width,
                    "height" to height,
                    "seed" to resolvedSeed,
                    "watermark" to false,
                ),
            ),
            "2" to mapOf(
                "class_type" to "SaveImage",
                "inputs" to mapOf(
                    "filename_prefix" to "manga",
                    "images" to listOf("1", 0),
                ),
            ),
        )
    }

    /**
     * Image-to-manga workflow using a single uploaded reference image.
     *
     * Graph:
     *   "1" LoadImage  →  "2" WanImageToImageApi  →  "3" SaveImage
     */
    fun imageToManga(
        uploadedFilename: String,
        prompt: String,
        negPrompt: String = "",
        seed: Long = -1L,
    ): Map<String, Any> {
        val resolvedSeed = resolveSeed(seed)
        return mapOf(
            "1" to mapOf(
                "class_type" to "LoadImage",
                "inputs" to mapOf(
                    "image" to uploadedFilename,
                    "upload" to false,
                ),
            ),
            "2" to mapOf(
                "class_type" to "WanImageToImageApi",
                "inputs" to mapOf(
                    "model" to "wan2.5-i2i-preview",
                    "image" to listOf("1", 0),
                    "prompt" to prompt,
                    "negative_prompt" to negPrompt,
                    "seed" to resolvedSeed,
                    "watermark" to false,
                ),
            ),
            "3" to mapOf(
                "class_type" to "SaveImage",
                "inputs" to mapOf(
                    "filename_prefix" to "manga",
                    "images" to listOf("2", 0),
                ),
            ),
        )
    }

    /**
     * Multi-image-to-manga workflow (max 2 images) via ImageBatch.
     *
     * Graph:
     *   "1" LoadImage ─┐
     *                  ├→ "3" ImageBatch  →  "4" WanImageToImageApi  →  "5" SaveImage
     *   "2" LoadImage ─┘
     */
    fun multiImageToManga(
        uploadedFilenames: List<String>,
        prompt: String,
        negPrompt: String = "",
        seed: Long = -1L,
    ): Map<String, Any> {
        require(uploadedFilenames.isNotEmpty()) { "At least one filename required" }
        val resolvedSeed = resolveSeed(seed)

        return if (uploadedFilenames.size == 1) {
            // Fall back to single-image path if only one image given
            imageToManga(uploadedFilenames[0], prompt, negPrompt, resolvedSeed)
        } else {
            mapOf(
                "1" to mapOf(
                    "class_type" to "LoadImage",
                    "inputs" to mapOf(
                        "image" to uploadedFilenames[0],
                        "upload" to false,
                    ),
                ),
                "2" to mapOf(
                    "class_type" to "LoadImage",
                    "inputs" to mapOf(
                        "image" to uploadedFilenames[1],
                        "upload" to false,
                    ),
                ),
                "3" to mapOf(
                    "class_type" to "ImageBatch",
                    "inputs" to mapOf(
                        "image1" to listOf("1", 0),
                        "image2" to listOf("2", 0),
                    ),
                ),
                "4" to mapOf(
                    "class_type" to "WanImageToImageApi",
                    "inputs" to mapOf(
                        "model" to "wan2.5-i2i-preview",
                        "image" to listOf("3", 0),
                        "prompt" to prompt,
                        "negative_prompt" to negPrompt,
                        "seed" to resolvedSeed,
                        "watermark" to false,
                    ),
                ),
                "5" to mapOf(
                    "class_type" to "SaveImage",
                    "inputs" to mapOf(
                        "filename_prefix" to "manga",
                        "images" to listOf("4", 0),
                    ),
                ),
            )
        }
    }

    // ---------------------------------------------------------------------------
    // Internal helpers
    // ---------------------------------------------------------------------------

    private fun resolveSeed(seed: Long): Long =
        if (seed < 0L) (0L..2_147_483_647L).random() else seed
}
