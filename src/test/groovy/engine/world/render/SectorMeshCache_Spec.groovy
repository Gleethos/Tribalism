package engine.world.render

import app.engine.primitives.BoundsF64
import app.engine.primitives.VecF64
import app.engine.world.Material
import app.engine.world.WorldSector
import app.engine.world.WorldSectorEtherData
import app.engine.world.WorldTreeNode
import app.engine.world.render.Quad
import app.engine.world.render.SectorMeshCache
import sprouts.Tuple
import spock.lang.Narrative
import spock.lang.Specification
import spock.lang.Title

@Title("SectorMeshCache - occlusion-culled voxel meshes, memoized per sector")
@Narrative('''

    Meshing an 8x8x8 block of voxels drops every face buried between two opaque
    voxels, leaving only the visible surface. Because a WorldSector is an immutable
    value it is the cache key, so the same block is meshed once and reused.

''')
class SectorMeshCache_Spec extends Specification
{
    private static BoundsF64 cube( double from, double to ) {
        return BoundsF64.of(VecF64.of(from, from, from), VecF64.of(to, to, to))
    }

    /** An (un-aggregated) 8x8x8 block whose leaves come from a (x,y,z)->Material function. */
    private static WorldSector block( Closure<Material> materialAt ) {
        var bounds = cube(0, 8)
        var cells = bounds.subdivide(WorldTreeNode.RESOLUTION)
        WorldSector[] kids = new WorldSector[WorldTreeNode.SECTOR_COUNT]
        for ( int z = 0; z < 8; z++ )
            for ( int y = 0; y < 8; y++ )
                for ( int x = 0; x < 8; x++ ) {
                    int i = WorldTreeNode.indexOf(x, y, z)
                    kids[i] = WorldSector.leaf(cells.get(i), WorldSectorEtherData.of(materialAt(x, y, z) as Material))
                }
        return WorldSector.empty(bounds).withChildren(new WorldTreeNode(Tuple.of(WorldSector, kids)))
    }

    def "An empty block has no faces."()
    {
        expect:
            new SectorMeshCache().meshOf(block { int x, int y, int z -> Material.AIR }).faceCount() == 0
    }

    def "A fully solid block keeps only its outer shell - every interior face is culled."()
    {
        when:
            var mesh = new SectorMeshCache().meshOf(block { int x, int y, int z -> Material.ROCK })
        then: 'Only the 6 outer 8x8 faces survive (6*64 = 384)...'
            mesh.faceCount() == 6 * 64
        and: '...far fewer than the 512*6 = 3072 a naive per-voxel renderer would draw.'
            mesh.faceCount() < 512 * 6
    }

    def "A column of stacked voxels culls the shared faces between consecutive blocks."()
    {
        when: 'A single 1x(height)x1 column of rock along Y at the corner.'
            var mesh = new SectorMeshCache().meshOf(block { int x, int y, int z ->
                (x == 0 && z == 0 && y < height) ? Material.ROCK : Material.AIR
            })
        then: 'Four side faces per voxel + one cap at each end = 4*height + 2 (internal interfaces culled).'
            mesh.faceCount() == 4 * height + 2
        where:
            height << [1, 2, 3, 5, 8]
    }

    def "Two adjacent opaque voxels hide the single face between them."()
    {
        when: 'Two rock voxels side by side along X.'
            var mesh = new SectorMeshCache().meshOf(block { int x, int y, int z ->
                (y == 0 && z == 0 && x < 2) ? Material.ROCK : Material.AIR
            })
        then: '2 voxels * 6 faces - 2 (the shared interface, one from each side) = 10.'
            mesh.faceCount() == 10
    }

    def "Every emitted face is a visible, non-invisible surface."()
    {
        given:
            var mesh = new SectorMeshCache().meshOf(block { int x, int y, int z -> y < 4 ? Material.ROCK : Material.AIR })
        expect:
            mesh.quads().every { Quad q -> !q.profile().isInvisible() }
            mesh.faceCount() > 0
    }

    def "The mesh is memoized: the same immutable sector returns the identical mesh."()
    {
        given:
            var cache = new SectorMeshCache()
            var solid = block { int x, int y, int z -> Material.ROCK }
        expect: 'Same instance back (cached), not a rebuild.'
            cache.meshOf(solid).is(cache.meshOf(solid))
    }
}