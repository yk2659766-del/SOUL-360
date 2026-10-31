package com.soul360.design.ai

/** Design style presets for AI render. Swatches are ARGB colours for the style card. */
data class RenderStyle(
    val id: String,
    val title: String,
    val subtitle: String,
    val prompt: String,
    val swatch: List<Long>,
    val allowsRestyle: Boolean = true,
)

object RenderStyles {
    val all = listOf(
        RenderStyle("keep", "Just make it real", "Your design, photoreal",
            "Keep the existing style, colours and decor of the room. Only make the whole scene look like a real photograph.",
            listOf(0xFFF5EFE6, 0xFFA39383), allowsRestyle = false),
        RenderStyle("modern", "Modern Minimal", "Clean lines · calm neutrals",
            "Modern minimalist interior: clean lines, uncluttered surfaces, warm white and greige palette, matte black accents, hidden storage, sculptural lighting.",
            listOf(0xFFF2F0EB, 0xFF2B2B2B)),
        RenderStyle("japandi", "Japandi", "Warm wood · zen calm",
            "Japandi interior: light oak and walnut wood, linen and bouclé textures, low profile forms, earthy beige and sage tones, paper lantern lighting, a few ceramics and plants.",
            listOf(0xFFE8DCCB, 0xFF7A8B6F)),
        RenderStyle("luxury", "Luxury Contemporary", "Marble · brass · velvet",
            "Luxury contemporary interior: marble surfaces, brushed brass details, velvet and leather, layered statement lighting, rich cream and taupe palette with deep accents, high-end hotel feel.",
            listOf(0xFFEDE4D3, 0xFFB08D57)),
        RenderStyle("scandi", "Scandinavian", "Bright · airy · cosy",
            "Scandinavian interior: bright and airy, white walls, pale wood floors, soft wool textiles, simple functional furniture, cosy hygge accents and green plants.",
            listOf(0xFFFAFAF7, 0xFFC9B79C)),
        RenderStyle("midcentury", "Mid-Century", "Teak · olive · mustard",
            "Mid-century modern interior: teak and walnut furniture with tapered legs, olive, mustard and burnt orange accents, globe pendant lights, graphic rug.",
            listOf(0xFFD9A441, 0xFF6B4F2A)),
        RenderStyle("arabic", "Modern Arabic", "Arches · plaster · warmth",
            "Modern Arabic/Middle-Eastern interior: soft arches, lime-plaster walls, warm sand and terracotta tones, carved wood and brass lanterns, majlis-style low seating accents, elegant and serene.",
            listOf(0xFFE9D8C0, 0xFFB5653A)),
        RenderStyle("industrial", "Industrial Loft", "Concrete · steel · leather",
            "Industrial loft interior: exposed concrete and brick, black steel frames, aged leather, reclaimed wood, Edison-style pendant lights, moody contrast.",
            listOf(0xFF8E8A85, 0xFF3A3533)),
    )

    val lighting = listOf(
        "Natural daylight" to "bright natural daylight from the windows, soft and even",
        "Golden hour" to "warm golden-hour sunlight with long soft shadows",
        "Evening cosy" to "evening ambience with warm lamps and indirect lighting turned on",
        "Studio bright" to "bright, crisp editorial lighting like an interior magazine shoot",
    )

    fun buildPrompt(style: RenderStyle, lightingDesc: String, restyleRoom: Boolean, notes: String, furnitureCount: Int, materials: List<String> = emptyList()): String = buildString {
        append("You are an expert interior designer and architectural visualiser. ")
        append("Image 1 is a photo of a room in which furniture cut-outs have been roughly placed by the user. ")
        if (furnitureCount > 0) append("Images 2 to ${furnitureCount + 1} show the exact furniture pieces for reference. ")
        if (materials.isNotEmpty()) {
            val first = furnitureCount + 2
            append("Images $first to ${first + materials.size - 1} are material samples (finishes) the designer chose. ")
        }
        append("Create ONE photorealistic, high-end interior photograph of this room.\n")
        append("Rules:\n")
        append("- Keep the same camera angle, framing and perspective as image 1.\n")
        if (restyleRoom && style.allowsRestyle) {
            append("- Keep the room's architecture (walls, windows, doors, ceiling, floor layout) but you may refresh wall colour, flooring finish, rugs, curtains, decor, plants and light fixtures to match the style.\n")
        } else {
            append("- Keep the room's architecture, walls, windows, floor and existing elements exactly as they are.\n")
        }
        append("- Keep every placed furniture item in the same position and similar size. Preserve each item's design, shape, colour and material")
        if (furnitureCount > 0) append(" as shown in the reference images")
        append(". Correct its perspective and scale so it stands naturally on the floor, aligned with the room.\n")
        materials.forEachIndexed { i, m ->
            append("- Apply material image ${furnitureCount + 2 + i} realistically to the $m, matching its colour, pattern and texture at real-world scale, following the room's perspective, with natural lighting and reflections.\n")
        }
        append("- Add realistic contact shadows, reflections and light that match the scene. Remove any cut-out edges or halos.\n")
        append("- Style: ${style.prompt}\n")
        append("- Lighting: $lightingDesc.\n")
        if (notes.isNotBlank()) append("- Extra wishes from the designer: ${notes.trim()}\n")
        append("- No text, logos, watermarks or people. Sharp, clean, magazine-quality result.")
    }
}
