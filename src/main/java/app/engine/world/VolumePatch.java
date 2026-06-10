package app.engine.world;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 *  A baked, coarse <b>volumetric</b> level-of-detail summary of one sector's content: a
 *  {@value WorldTreeNode#RESOLUTION}&sup3; grid of {@link Material}s ({@link Material#AIR}
 *  = empty), sampled top-down by the generator when a childless coarse leaf is created.
 *  <p>
 *  This is what lets the <b>far field</b> have shape. A childless leaf used to be drawable
 *  only as a single (inset-shrunk) box — the whole landscape inside a 512-unit sector
 *  reduced to one slab. With a patch the renderer can mesh the same leaf at resolutions
 *  2&ndash;{@value WorldTreeNode#RESOLUTION} through its ordinary grid pipeline, showing
 *  ridges, cliffs and per-cell materials kilometres away without keeping (or ever
 *  building) the leaf's {@value WorldTreeNode#SECTOR_COUNT} child sectors.
 *  <p>
 *  Deliberately <b>not</b> a heightmap: the grid is volumetric so distant content of any
 *  shape — overhangs, arches, floating structures, future buildings — is representable.
 *  The engine stays content-agnostic; only the generator knows the current world happens
 *  to be a height field. The one surface-flavoured derivative is
 *  {@link #solidBaseFraction()}, precomputed for the occlusion culler: the fraction of the
 *  sector's height up to which <i>every</i> column is fully opaque from the bottom — a box
 *  that is certainly drawn and may safely block what is behind it (0 for floating content,
 *  which then simply occludes nothing).
 *  <p>
 *  An immutable value (it participates in {@link WorldSector#equals}, and thereby in the
 *  renderer's value-keyed mesh caches). Cells are stored as palette indices
 *  ({@code byte} per cell + the few distinct materials), so a typical patch is well under
 *  a kilobyte — bounded far-field memory is the whole point.
 */
public final class VolumePatch
{
    /** The grid edge of a patch — one tree level, matching {@link WorldTreeNode#RESOLUTION}. */
    public static final int RESOLUTION = WorldTreeNode.RESOLUTION;
    /** The number of cells in a patch ({@code RESOLUTION}&sup3;). */
    public static final int CELL_COUNT = WorldTreeNode.SECTOR_COUNT;

    private final Material[] _palette;
    /** Palette index per cell, in {@link WorldTreeNode#indexOf} linear order. Never exposed. */
    private final byte[] _cells;
    private final double _solidBaseFraction;
    private int _hash;

    private VolumePatch( Material[] palette, byte[] cells, double solidBaseFraction ) {
        _palette = palette;
        _cells = cells;
        _solidBaseFraction = solidBaseFraction;
    }

    /**
     *  @param cells The material of each grid cell ({@link Material#AIR} for empty space),
     *               {@link WorldTreeNode#indexOf indexed} {@code x + y*RESOLUTION + z*RESOLUTION²};
     *               the array is copied (palette-compressed), not retained.
     *  @return A patch over the given cell materials.
     */
    public static VolumePatch of( Material[] cells ) {
        if ( cells.length != CELL_COUNT )
            throw new IllegalArgumentException("A volume patch needs " + CELL_COUNT + " cells, but got " + cells.length + ".");
        List<Material> palette = new ArrayList<>();
        byte[] indexed = new byte[CELL_COUNT];
        for ( int i = 0; i < CELL_COUNT; i++ ) {
            Material material = Objects.requireNonNull(cells[i]);
            int index = palette.indexOf(material);
            if ( index < 0 ) {
                if ( palette.size() > Byte.MAX_VALUE )
                    throw new IllegalArgumentException("A volume patch supports at most " + (Byte.MAX_VALUE + 1) + " distinct materials.");
                index = palette.size();
                palette.add(material);
            }
            indexed[i] = (byte) index;
        }
        return new VolumePatch(palette.toArray(new Material[0]), indexed, solidBaseFractionOf(cells));
    }

    /** @return The material of the cell at grid coordinate ({@code x}, {@code y}, {@code z}). */
    public Material material( int x, int y, int z ) {
        return _palette[_cells[WorldTreeNode.indexOf(x, y, z)]];
    }

    /**
     *  @return The fraction of the sector's height, measured up from its bottom, to which every
     *          column of the grid is contiguously {@link TextureProfile fully opaque}. Geometry
     *          drawn from this patch always covers that slab, so the occlusion culler may mark
     *          (only) its silhouette. {@code 0} when any column's base is open (e.g. floating
     *          content), in which case the patch occludes nothing.
     */
    public double solidBaseFraction() {
        return _solidBaseFraction;
    }

    /** @return The shortest per-column run of contiguously fully-opaque cells up from the grid bottom, as a fraction. */
    private static double solidBaseFractionOf( Material[] cells ) {
        int shortestRun = RESOLUTION;
        for ( int z = 0; z < RESOLUTION && shortestRun > 0; z++ )
            for ( int x = 0; x < RESOLUTION && shortestRun > 0; x++ ) {
                int run = 0;
                while ( run < RESOLUTION && isFullyOpaque(cells[WorldTreeNode.indexOf(x, run, z)]) )
                    run++;
                shortestRun = Math.min(shortestRun, run);
            }
        return shortestRun / (double) RESOLUTION;
    }

    private static boolean isFullyOpaque( Material material ) {
        return material.texture().intensityOf(Texture.OPACITY) >= 1.0;
    }

    @Override
    public boolean equals( Object obj ) {
        if ( this == obj ) return true;
        if ( !(obj instanceof VolumePatch other) ) return false;
        return Arrays.equals(_cells, other._cells) && Arrays.equals(_palette, other._palette);
    }

    @Override
    public int hashCode() {
        int h = _hash;
        if ( h == 0 ) {
            h = 31 * Arrays.hashCode(_cells) + Arrays.hashCode(_palette);
            _hash = h;
        }
        return h;
    }

    @Override
    public String toString() {
        return "VolumePatch[materials=" + _palette.length + ", solidBaseFraction=" + _solidBaseFraction + ']';
    }
}