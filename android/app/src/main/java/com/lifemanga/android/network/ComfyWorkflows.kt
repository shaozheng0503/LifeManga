package com.lifemanga.android.network

/**
 * Workflows for the z_image local model deployment.
 *
 * Pipeline:
 *   UNETLoader (z_image_bf16) → ModelSamplingAuraFlow (shift=3.0)
 *   CLIPLoader (qwen_3_4b_fp8_mixed, lumina2) → CLIPTextEncode ×2
 *   EmptySD3LatentImage → KSampler (res_multistep, simple, 25 steps, cfg 4.0)
 *   VAEDecode → SaveImage
 */
object ComfyWorkflows {

    private const val UNET_NAME = "z_image_bf16.safetensors"
    private const val CLIP_NAME = "qwen_3_4b_fp8_mixed.safetensors"
    private const val VAE_NAME = "ae.safetensors"

    fun textToManga(
        prompt: String,
        negPrompt: String = "",
        width: Int = 896,
        height: Int = 1152,
        seed: Long = -1L,
    ): Map<String, Any> {
        val s = resolveSeed(seed)
        return mapOf(
            "1" to node("UNETLoader", "unet_name" to UNET_NAME, "weight_dtype" to "default"),
            "2" to node("CLIPLoader", "clip_name" to CLIP_NAME, "type" to "lumina2"),
            "3" to node("VAELoader", "vae_name" to VAE_NAME),
            "4" to node("ModelSamplingAuraFlow", "model" to ref("1"), "shift" to 3.0),
            "5" to node("CLIPTextEncode", "clip" to ref("2"), "text" to prompt),
            "6" to node("CLIPTextEncode", "clip" to ref("2"), "text" to negPrompt),
            "7" to node("EmptySD3LatentImage", "width" to width, "height" to height, "batch_size" to 1),
            "8" to node("KSampler",
                "model" to ref("4"), "positive" to ref("5"), "negative" to ref("6"),
                "latent_image" to ref("7"),
                "seed" to s, "steps" to 25, "cfg" to 4.0,
                "sampler_name" to "res_multistep", "scheduler" to "simple", "denoise" to 1.0,
            ),
            "9" to node("VAEDecode", "samples" to ref("8"), "vae" to ref("3")),
            "10" to node("SaveImage", "filename_prefix" to "manga", "images" to ref("9")),
        )
    }

    fun imageToManga(
        uploadedFilename: String,
        prompt: String,
        negPrompt: String = "",
        seed: Long = -1L,
        denoise: Double = 0.75,
    ): Map<String, Any> {
        val s = resolveSeed(seed)
        return mapOf(
            "1" to node("UNETLoader", "unet_name" to UNET_NAME, "weight_dtype" to "default"),
            "2" to node("CLIPLoader", "clip_name" to CLIP_NAME, "type" to "lumina2"),
            "3" to node("VAELoader", "vae_name" to VAE_NAME),
            "4" to node("ModelSamplingAuraFlow", "model" to ref("1"), "shift" to 3.0),
            "5" to node("CLIPTextEncode", "clip" to ref("2"), "text" to prompt),
            "6" to node("CLIPTextEncode", "clip" to ref("2"), "text" to negPrompt),
            "7" to node("LoadImage", "image" to uploadedFilename, "upload" to false),
            "8" to node("VAEEncode", "pixels" to ref("7"), "vae" to ref("3")),
            "9" to node("KSampler",
                "model" to ref("4"), "positive" to ref("5"), "negative" to ref("6"),
                "latent_image" to ref("8"),
                "seed" to s, "steps" to 25, "cfg" to 4.0,
                "sampler_name" to "res_multistep", "scheduler" to "simple", "denoise" to denoise,
            ),
            "10" to node("VAEDecode", "samples" to ref("9"), "vae" to ref("3")),
            "11" to node("SaveImage", "filename_prefix" to "manga", "images" to ref("10")),
        )
    }

    fun multiImageToManga(
        uploadedFilenames: List<String>,
        prompt: String,
        negPrompt: String = "",
        seed: Long = -1L,
    ): Map<String, Any> {
        require(uploadedFilenames.isNotEmpty()) { "At least one filename required" }
        // Use first image for i2i; append note about multiple refs in prompt
        val enrichedPrompt = if (uploadedFilenames.size > 1)
            "$prompt, multi-reference character consistency"
        else prompt
        return imageToManga(uploadedFilenames[0], enrichedPrompt, negPrompt, seed)
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private fun ref(nodeId: String): List<Any> = listOf(nodeId, 0)

    private fun node(classType: String, vararg inputs: Pair<String, Any>): Map<String, Any> =
        mapOf("class_type" to classType, "inputs" to mapOf(*inputs))

    private fun resolveSeed(seed: Long): Long =
        if (seed < 0L) (0L..2_147_483_647L).random() else seed
}
