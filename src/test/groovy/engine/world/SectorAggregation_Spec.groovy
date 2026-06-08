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
import spock.lang.Narrative
import spock.lang.Specification
import spock.lang.Title

@Title("Sector aggregation coherence - appearance & solidity derived bottom-up from sub-sectors")
@Narrative('''

    A branching sector summarizes its sub-sectors: its per-side appearance is the
    average of the matching face of its boundary children, its whole-cube material is
    the merge of theirs, and it is a solid occluder only when every voxel inside is
    opaque. (A sector's SHAPE - how far content is recessed per face - is no longer
    derived here; it lives in the ether's per-side insets, described top-down by the
    generator: see WorldSectorEtherData_Spec / WorldGenerator_Spec.) These data-driven
    specs assert that aggregation coherence directly.

''')
class SectorAggregation_Spec extends Specification
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
        var node = new WorldTreeNode(kids)
        return WorldSector.empty(bounds).withChildren(node).aggregated()
    }

    private static WorldSector branch( Closure<Material> materialAt ) {
        return branch(cube(0, 8), materialAt)
    }

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