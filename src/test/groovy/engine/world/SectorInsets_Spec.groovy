package engine.world

import app.engine.primitives.BoundsF64
import app.engine.primitives.VecF64
import app.engine.world.Material
import app.engine.world.MaterialId
import app.engine.world.Side
import app.engine.world.TextureProfile
import app.engine.world.WorldSector
import app.engine.world.WorldSectorEtherData
import app.engine.world.WorldTreeNode
import sprouts.Tuple
import spock.lang.Narrative
import spock.lang.Specification
import spock.lang.Title

@Title("Sector insets & aggregation coherence - derived bottom-up from sub-sectors")
@Narrative('''

    A branching sector summarizes its sub-sectors: its per-side appearance is the
    average of the matching face of its boundary children, and its per-side insets
    are found by an inward-moving algorithm that peels off fully-transparent child
    layers. A leaf, having no sub-sectors, has no insets. These data-driven specs
    assert that coherence directly.

''')
class SectorInsets_Spec extends Specification
{
    private static BoundsF64 cube( double from, double to ) {
        return BoundsF64.of(VecF64.of(from, from, from), VecF64.of(to, to, to))
    }

    /** An aggregated 8x8x8 branch over {@code bounds} whose leaves come from a (x,y,z)->Material function. */
    private static WorldSector branch( BoundsF64 bounds, Closure<Material> materialAt ) {
        var cells = bounds.subdivide(WorldTreeNode.RESOLUTION)
        WorldSector[] kids = new WorldSector[WorldTreeNode.SECTOR_COUNT]
        for ( int z = 0; z < WorldTreeNode.RESOLUTION; z++ )
            for ( int y = 0; y < WorldTreeNode.RESOLUTION; y++ )
                for ( int x = 0; x < WorldTreeNode.RESOLUTION; x++ ) {
                    int i = WorldTreeNode.indexOf(x, y, z)
                    kids[i] = WorldSector.leaf(cells.get(i), WorldSectorEtherData.of(materialAt(x, y, z) as Material))
                }
        var node = new WorldTreeNode(Tuple.of(WorldSector, kids))
        return WorldSector.empty(bounds).withChildren(node).aggregated()
    }

    private static WorldSector branch( Closure<Material> materialAt ) {
        return branch(cube(0, 8), materialAt)
    }

    // ---- Leaf base case ---------------------------------------------------------

    def "A leaf has no sub-sectors, so all of its insets are zero."()
    {
        expect:
            WorldSector.leaf(cube(0, 8), WorldSectorEtherData.of(Material.ROCK)).insets().isNone()
            WorldSector.empty(cube(0, 8)).insets().isNone()
    }

    // ---- The inward-moving layer algorithm --------------------------------------

    def "The top inset counts the empty layers above the solid content."()
    {
        when: 'The bottom (8 - airLayers) layers are rock, the rest air.'
            var sector = branch { int x, int y, int z -> y < (8 - airLayers) ? Material.ROCK : Material.AIR }
        then: 'The top face is recessed by exactly the empty layers; the bottom never is (until all-air).'
            Math.abs(sector.insets().forSide(Side.POS_Y) - top) < 1e-12
            Math.abs(sector.insets().forSide(Side.NEG_Y) - bottom) < 1e-12
        where:
            airLayers || top   | bottom
            0         || 0.0   | 0.0
            1         || 0.125 | 0.0
            2         || 0.25  | 0.0
            4         || 0.5   | 0.0
            7         || 0.875 | 0.0
            8         || 1.0   | 1.0
    }

    def "A fully solid branch is flush on every face; a fully empty branch is fully inset on every face."()
    {
        given:
            var solid = branch { int x, int y, int z -> Material.ROCK }
            var empty = branch { int x, int y, int z -> Material.AIR }
        expect:
            Side.values().every { solid.insets().forSide(it) == 0.0 }
            Side.values().every { Math.abs(empty.insets().forSide(it) - 1.0) < 1e-12 }
    }

    def "A half-full sector is inset only on the empty side."()
    {
        given: 'The bottom half is rock, the top half air.'
            var sector = branch { int x, int y, int z -> y < 4 ? Material.ROCK : Material.AIR }
        expect:
            Math.abs(sector.insets().forSide(side) - expected) < 1e-12
        where:
            side       || expected
            Side.POS_Y || 0.5
            Side.NEG_Y || 0.0
            Side.NEG_X || 0.0
            Side.POS_X || 0.0
            Side.NEG_Z || 0.0
            Side.POS_Z || 0.0
    }

    def "The inset refines below child granularity using the children's own insets."()
    {
        given: 'A root whose only content is a bottom layer of half-full sub-branches.'
            var rootBounds = cube(0, 64)
            var cells = rootBounds.subdivide(WorldTreeNode.RESOLUTION)
            WorldSector[] kids = new WorldSector[WorldTreeNode.SECTOR_COUNT]
            for ( int z = 0; z < 8; z++ )
                for ( int y = 0; y < 8; y++ )
                    for ( int x = 0; x < 8; x++ ) {
                        int i = WorldTreeNode.indexOf(x, y, z)
                        kids[i] = (y == 0)
                                ? branch(cells.get(i), { int cx, int cy, int cz -> cy < 4 ? Material.ROCK : Material.AIR })
                                : WorldSector.leaf(cells.get(i), WorldSectorEtherData.empty())
                    }
            var root = WorldSector.empty(rootBounds)
                                  .withChildren(new WorldTreeNode(Tuple.of(WorldSector, kids)))
                                  .aggregated()
        expect: '7 empty top layers + half of the 8th (the sub-branches are half-empty) = 7.5/8.'
            Math.abs(root.insets().forSide(Side.POS_Y) - 7.5 / 8.0) < 1e-12
        and: 'The bottom is flush (the sub-branches are solid at their base).'
            root.insets().forSide(Side.NEG_Y) == 0.0
    }

    // ---- Aggregation coherence (appearance derived from sub-sectors) ------------

    def "Each side's appearance is exactly the average of that face of its boundary sub-sectors."()
    {
        given: 'A non-uniform branch (rock below, air above) so faces genuinely differ.'
            var sector = branch { int x, int y, int z -> y < 4 ? Material.ROCK : Material.AIR }
        expect: 'For every side, the aggregated face equals the average of the boundary children faces.'
            Side.values().every { side ->
                var faces = WorldTreeNode.boundaryCells(side).collect {
                    sector.children().sector(it).ether().sideOf(side)
                }
                sector.ether().sideOf(side) == TextureProfile.average(faces)
            }
    }

    def "The whole-cube material is Specific when children agree and Diverse when they differ."()
    {
        expect:
            branch { int x, int y, int z -> Material.ROCK }.ether().material() == Material.ROCK.materialId()
            branch { int x, int y, int z -> y < 4 ? Material.ROCK : Material.AIR }.ether().material() == MaterialId.diverse()
    }

    // ---- Solid-opacity (occluder detection), also derived from sub-sectors ------

    def "A branch is a solid occluder only when every voxel inside is fully opaque."()
    {
        expect: 'A wholly opaque block is solid...'
            branch { int x, int y, int z -> Material.ROCK }.isSolidOpaque()
        and: '...but any air, or any non-opaque material, anywhere disqualifies it.'
            !branch { int x, int y, int z -> Material.AIR }.isSolidOpaque()
            !branch { int x, int y, int z -> y < 4 ? Material.ROCK : Material.AIR }.isSolidOpaque()
            !branch { int x, int y, int z -> Material.WATER }.isSolidOpaque()
    }

    def "A leaf is a solid occluder iff its material is fully opaque."()
    {
        expect:
            WorldSector.leaf(cube(0, 8), WorldSectorEtherData.of(material)).isSolidOpaque() == solid
        where:
            material       || solid
            Material.ROCK   || true
            Material.SOIL   || true
            Material.METAL  || true
            Material.AIR    || false
            Material.WATER  || false   // opacity 0.65
            Material.ICE    || false   // opacity 0.5
            Material.LEAVES || false   // opacity 0.8
    }
}