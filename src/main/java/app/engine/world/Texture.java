package app.engine.world;

/**
 *  A single <i>visual appearance quality</i> of a sector's surface &mdash; a hint
 *  about how it should look, not about what it is "made of".
 *  <p>
 *  Unlike the old material-percentage model, these qualities are <b>independent</b>
 *  and do <b>not</b> sum to one: a surface can be a "grainy liquid" with both
 *  {@link #GRAINY} and {@link #LIQUID} at full strength. Each quality is carried in
 *  a {@link TextureProfile} as an intensity in {@code [0, 1]}; a quality absent from
 *  the profile is simply {@code 0}. A profile where <i>every</i> quality is {@code 0}
 *  is the null object &mdash; empty space (air), which is invisible.
 *  <p>
 *  The long-term intent is for these to feed a <b>procedural noise shader</b>: each
 *  quality biases the noise and lighting model (its frequency, anisotropy, specular
 *  response, emission, displacement, …) so that a material's look emerges
 *  procedurally rather than from a fixed texture image. The first-draft Graphics2D
 *  renderer uses a crude subset of them (a tint per hue-bearing quality, plus
 *  {@link #OPACITY} for visibility) as a stand-in for that shader.
 */
public enum Texture {
    /** How solid / non-see-through the surface is. {@code 0} = empty air, {@code 1} = fully opaque. Drives visibility (and, later, alpha). */
    OPACITY,
    /** Mirror-like specular sheen (still water, polished stone). */
    REFLECTIVE,
    /** Metalness: conductive, tinted specular highlights (iron, gold). */
    METALLIC,
    /** Self-illumination / glow, independent of scene lighting (lava, crystals, embers). */
    EMISSIVE,
    /** Micro-surface roughness; high = matte and light-scattering, low = smooth. */
    ROUGH,
    /** Fine granular speckle (sand, soil, gravel). */
    GRAINY,
    /** Loose fine powder (snow, dust, ash). */
    POWDERY,
    /** Faceted, gem-like angular structure (quartz, ice crystals). */
    CRYSTALLINE,
    /** Flowing fluid body (water, lava). */
    LIQUID,
    /** Surface moisture / glistening film (mud, wet rock). */
    WET,
    /** Glowing molten flow, hot and viscous (lava). */
    MOLTEN,
    /** Directional fibres / grain running through the surface (wood, planks). */
    FIBROUS,
    /** Many fine upright strands (grass, fur, roots). */
    HAIRY,
    /** Soft patchy organic overgrowth (moss, lichen). */
    MOSSY,
    /** Clustered foliage / leaves (canopy, bushes). */
    LEAFY,
    /** Sharp protrusions / jagged points (crystals, broken rock, ice shards). */
    SPIKY,
    /** Cracked, fractured planes and fissures (shattered stone, dried mud). */
    SHATTERED,
    /** Pitted with holes / cavities (pumice, sponge, coral). */
    POROUS,
    /** Stratified horizontal bands (sedimentary rock, sandstone). */
    LAYERED,
    /** Threaded veins running through the body (marble, ore, roots). */
    VEINED
}