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
    /** Per column ({@code x + z*RESOLUTION}): how far the column's TOP solid cell is empty from its
     *  top, quantized to {@code [0, 255]} of the cell height. Refines the cell-quantized surface to
     *  the continuous one, so far terrain meshes at the same smooth height a res-1 box shrinks to. */
    private final byte[] _topInsets;
    private final double _solidBaseFraction;
    private int _hash;

    private VolumePatch( Material[] palette, byte[] cells, byte[] topInsets, double solidBaseFraction ) {
        _palette = palette;
        _cells = cells;
        _topInsets = topInsets;
        _solidBaseFraction = solidBaseFraction;
    }

    /** @return A patch over the given cell materials, with no sub-cell surface refinement (flush cells). */
    public static VolumePatch of( Material[] cells ) {
        return of(cells, new double[RESOLUTION * RESOLUTION]);
    }

    /**
     *  @param cells     The material of each grid cell ({@link Material#AIR} for empty space),
     *                   {@link WorldTreeNode#indexOf indexed} {@code x + y*RESOLUTION + z*RESOLUTION²};
     *                   the array is copied (palette-compressed), not retained.
     *  @param topInsets Per column ({@code x + z*RESOLUTION}), in {@code [0, 1]} of one cell height: how far
     *                   the column's top solid cell's content is recessed below that cell's top. The
     *                   sub-cell surface refinement that keeps a coarse terrace from quantizing to cells.
     *  @return A patch over the given cell materials.
     */
    public static VolumePatch of( Material[] cells, double[] topInsets ) {
        if ( cells.length != CELL_COUNT )
            throw new IllegalArgumentException("A volume patch needs " + CELL_COUNT + " cells, but got " + cells.length + ".");
        if ( topInsets.length != RESOLUTION * RESOLUTION )
            throw new IllegalArgumentException("A volume patch needs " + (RESOLUTION * RESOLUTION) + " column top insets, but got " + topInsets.length + ".");
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
        byte[] quantized = new byte[RESOLUTION * RESOLUTION];
        double[] applied = new double[RESOLUTION * RESOLUTION];
        for ( int i = 0; i < quantized.length; i++ ) {
            quantized[i] = (byte) Math.round(Math.max(0, Math.min(1, topInsets[i])) * 255);
            applied[i] = (quantized[i] & 0xFF) / 255.0; // EXACTLY what a renderer will recede by.
        }
        return new VolumePatch(palette.toArray(new Material[0]), indexed, quantized, solidBaseFractionOf(cells, applied));
    }

    /** @return The material of the cell at grid coordinate ({@code x}, {@code y}, {@code z}). */
    public Material material( int x, int y, int z ) {
        return _palette[_cells[WorldTreeNode.indexOf(x, y, z)]];
    }

    /**
     *  @return How far column ({@code x}, {@code z})'s top solid cell is empty from its top, in
     *          {@code [0, 1]} of one cell height &mdash; the sub-cell surface refinement a renderer
     *          applies as that cell's top inset.
     */
    public double topInset( int x, int z ) {
        return (_topInsets[x + z * RESOLUTION] & 0xFF) / 255.0;
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

    /**
     *  @return The shortest per-column run of contiguously fully-opaque cells up from the grid bottom,
     *          as a fraction of the grid height. Where a column's run ends at its recessed surface cell,
     *          that cell's {@code topInset} is subtracted &mdash; the drawn surface sits that far below
     *          the cell boundary, and the advertised solid base must never reach above what is drawn
     *          (the occlusion culler marks exactly this slab).
     */
    private static double solidBaseFractionOf( Material[] cells, double[] topInsets ) {
        double shortestRun = RESOLUTION;
        for ( int z = 0; z < RESOLUTION && shortestRun > 0; z++ )
            for ( int x = 0; x < RESOLUTION && shortestRun > 0; x++ ) {
                int run = 0;
                while ( run < RESOLUTION && isFullyOpaque(cells[WorldTreeNode.indexOf(x, run, z)]) )
                    run++;
                int topVisible = RESOLUTION - 1;
                while ( topVisible >= 0 && cells[WorldTreeNode.indexOf(x, topVisible, z)].texture().isInvisible() )
                    topVisible--;
                double effective = run;
                if ( run > 0 && topVisible == run - 1 )
                    effective -= topInsets[x + z * RESOLUTION]; // the run's top cell is the recessed surface.
                shortestRun = Math.min(shortestRun, effective);
            }
        return Math.max(0, shortestRun) / RESOLUTION;
    }

    private static boolean isFullyOpaque( Material material ) {
        return material.texture().intensityOf(Texture.OPACITY) >= 1.0;
    }

    @Override
    public boolean equals( Object obj ) {
        if ( this == obj ) return true;
        if ( !(obj instanceof VolumePatch other) ) return false;
        return Arrays.equals(_cells, other._cells)
            && Arrays.equals(_topInsets, other._topInsets)
            && Arrays.equals(_palette, other._palette);
    }

    @Override
    public int hashCode() {
        int h = _hash;
        if ( h == 0 ) {
            h = 31 * (31 * Arrays.hashCode(_cells) + Arrays.hashCode(_topInsets)) + Arrays.hashCode(_palette);
            _hash = h;
        }
        return h;
    }

    @Override
    public String toString() {
        return "VolumePatch[materials=" + _palette.length + ", solidBaseFraction=" + _solidBaseFraction + ']';
    }
}