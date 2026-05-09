package com.lifemanga.android.data

enum class CharacterArtStyle(
    val key: String,
    val displayName: String,
    val subtitle: String,
    val promptTag: String,
) {
    JP_ANIME(
        key = "jpAnime",
        displayName = "日漫",
        subtitle = "少年/少女漫画风",
        promptTag = "Japanese anime manga style, shonen manga, cel-shaded, crisp lineart",
    ),
    US_COMICS(
        key = "usComics",
        displayName = "美漫",
        subtitle = "Marvel/DC 风格",
        promptTag = "American comic book style, bold inking, superhero art, dynamic shading",
    ),
    KR_MANHWA(
        key = "krManhwa",
        displayName = "韩漫",
        subtitle = "全彩竖条漫风",
        promptTag = "Korean manhwa style, full color webtoon, soft shading, detailed backgrounds",
    ),
    KAWAII(
        key = "kawaii",
        displayName = "萌系",
        subtitle = "可爱 chibi 风",
        promptTag = "kawaii cute anime style, big eyes, pastel colors, soft shading",
    ),
    CHIBI(
        key = "chibi",
        displayName = "Q版",
        subtitle = "2-3头身迷你",
        promptTag = "chibi super deformed style, 2-3 head proportions, cute, round shapes",
    ),
    RENDER_3D(
        key = "render3D",
        displayName = "3D渲染",
        subtitle = "CG 写实风",
        promptTag = "3D render character art, CGI, realistic lighting, subsurface scattering, high detail",
    ),
    SEMI_REAL(
        key = "semiReal",
        displayName = "半写实",
        subtitle = "插画写实混合",
        promptTag = "semi-realistic illustration, painterly style, detailed anatomy, soft rendering",
    ),
    WATERCOLOR(
        key = "watercolor",
        displayName = "水彩",
        subtitle = "手绘水彩质感",
        promptTag = "watercolor illustration style, loose brushwork, wet-on-wet, paper texture, soft edges",
    ),
    PIXEL_ART(
        key = "pixelArt",
        displayName = "像素",
        subtitle = "复古像素游戏风",
        promptTag = "pixel art style, 16-bit retro game sprite, limited palette, sharp pixels",
    );

    companion object {
        fun fromKey(key: String) = entries.firstOrNull { it.key == key } ?: JP_ANIME
    }
}
