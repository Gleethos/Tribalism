package app.engine.world;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import static app.engine.world.Texture.*;

/**
 *  A concrete substance a sector can be made of: a stable integer {@link #id()},
 *  a human {@link #name()} and a default {@link TextureProfile appearance}.
 *  <p>
 *  This replaces the old fixed {@code Material} enum. Being a record backed by an
 *  integer id, the registry can grow to thousands of materials over time; the
 *  handful of constants below are a <b>starter set</b> the procedural generator
 *  draws from. A material contributes two distinct things to a sector:
 *  <ul>
 *      <li>its {@link #materialId()} &mdash; the gameplay identity (one per cube,
 *          see {@link WorldSectorEtherData}); and</li>
 *      <li>its {@link #texture()} &mdash; the visual appearance qualities that drive
 *          rendering (and, later, a procedural noise shader).</li>
 *  </ul>
 *  Note there is no colour here: hue is a rendering concern derived from the
 *  {@link Texture} qualities, keeping this data model free of any rendering types.
 *
 *  @param id      A stable, unique integer identity.
 *  @param name    A human-readable name.
 *  @param texture The default appearance of a surface of this material.
 */
public record Material(
    int id,
    String name,
    TextureProfile texture
) {
    private static final Map<Integer, Material> _REGISTRY = new LinkedHashMap<>();

    private static Material register( int id, String name, TextureProfile texture ) {
        Material material = new Material(id, name, texture);
        if ( _REGISTRY.putIfAbsent(id, material) != null )
            throw new IllegalStateException("Duplicate material id " + id + " for '" + name + "'.");
        return material;
    }

    // ---- Starter registry -------------------------------------------------------
    // Air is the null substance: an empty profile, hence invisible.

    public static final Material AIR     = register(0,  "Air",     TextureProfile.none());

    public static final Material ROCK    = register(1,  "Rock",
            profile(OPACITY, 1.0, ROUGH, 0.8, SHATTERED, 0.35, GRAINY, 0.2, LAYERED, 0.15));

    public static final Material SOIL    = register(2,  "Soil",
            profile(OPACITY, 1.0, GRAINY, 0.85, ROUGH, 0.4, POROUS, 0.2));

    public static final Material GRASS   = register(3,  "Grass",
            profile(OPACITY, 1.0, HAIRY, 0.75, MOSSY, 0.5, GRAINY, 0.2));

    public static final Material SAND    = register(4,  "Sand",
            profile(OPACITY, 1.0, GRAINY, 1.0, POWDERY, 0.4, ROUGH, 0.2));

    public static final Material WATER   = register(5,  "Water",
            profile(OPACITY, 0.65, LIQUID, 1.0, WET, 0.8, REFLECTIVE, 0.5));

    public static final Material WOOD    = register(6,  "Wood",
            profile(OPACITY, 1.0, FIBROUS, 0.9, ROUGH, 0.4, LAYERED, 0.3));

    public static final Material METAL   = register(7,  "Metal",
            profile(OPACITY, 1.0, METALLIC, 1.0, REFLECTIVE, 0.8, ROUGH, 0.15));

    public static final Material ICE     = register(8,  "Ice",
            profile(OPACITY, 0.5, REFLECTIVE, 0.7, CRYSTALLINE, 0.5, WET, 0.4));

    public static final Material LAVA    = register(9,  "Lava",
            profile(OPACITY, 1.0, MOLTEN, 1.0, EMISSIVE, 0.9, LIQUID, 0.7, ROUGH, 0.3));

    public static final Material SNOW    = register(10, "Snow",
            profile(OPACITY, 0.95, POWDERY, 1.0, GRAINY, 0.3, REFLECTIVE, 0.3));

    public static final Material MOSS    = register(11, "Moss",
            profile(OPACITY, 1.0, MOSSY, 1.0, HAIRY, 0.4, POROUS, 0.3));

    public static final Material CRYSTAL = register(12, "Crystal",
            profile(OPACITY, 0.6, CRYSTALLINE, 1.0, REFLECTIVE, 0.8, SPIKY, 0.5, EMISSIVE, 0.2));

    public static final Material CLAY    = register(13, "Clay",
            profile(OPACITY, 1.0, GRAINY, 0.4, ROUGH, 0.5, WET, 0.3));

    public static final Material BARK    = register(14, "Bark",
            profile(OPACITY, 1.0, FIBROUS, 0.7, ROUGH, 0.8, SHATTERED, 0.3));

    public static final Material LEAVES  = register(15, "Leaves",
            profile(OPACITY, 0.8, LEAFY, 1.0, HAIRY, 0.3, POROUS, 0.4));

    public static final Material MUD     = register(16, "Mud",
            profile(OPACITY, 1.0, WET, 0.9, GRAINY, 0.5, LIQUID, 0.2));

    public static final Material ORE     = register(17, "Ore",
            profile(OPACITY, 1.0, VEINED, 0.8, METALLIC, 0.6, ROUGH, 0.5, REFLECTIVE, 0.4));

    /** @return The {@link MaterialId.Specific} id of this material. */
    public MaterialId materialId() {
        return MaterialId.of(id);
    }

    /** @return The registered material with the given {@code id}, or {@link #AIR} if unknown. */
    public static Material byId( int id ) {
        return _REGISTRY.getOrDefault(id, AIR);
    }

    /** @return All registered starter materials, in registration order. */
    public static Collection<Material> all() {
        return java.util.Collections.unmodifiableCollection(_REGISTRY.values());
    }

    /** Builds a {@link TextureProfile} from alternating {@code (Texture, intensity)} pairs. */
    private static TextureProfile profile( Object... qualityThenIntensity ) {
        TextureProfile profile = TextureProfile.none();
        for ( int i = 0; i < qualityThenIntensity.length; i += 2 )
            profile = profile.with((Texture) qualityThenIntensity[i], (Double) qualityThenIntensity[i + 1]);
        return profile;
    }
}