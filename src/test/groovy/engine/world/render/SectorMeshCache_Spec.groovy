package engine.world.render

import app.engine.primitives.BoundsF64
import app.engine.primitives.VecF64
import app.engine.world.Material
import app.engine.world.Side
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
        return WorldSector.empty(bounds).withChildren(new WorldTreeNode(kids))
    }

    def "An empty block has no faces."()
    {
        expect:
            new SectorMeshCache().meshOf(block { int x, int y, int z -> Material.AIR }).faceCount() == 0
    }

    def "A fully solid block greedy-meshes its outer shell into one quad per face."()
    {
        when:
            var mesh = new SectorMeshCache().meshOf(block { int x, int y, int z -> Material.ROCK })
        then: 'Each of the 6 faces is one merged 8x8 rectangle: 6 quads...'
            mesh.faceCount() == 6
        and: '...vastly fewer than the 512*6 = 3072 a naive per-voxel renderer would draw.'
            mesh.faceCount() < 512 * 6
    }

    def "A multi-level chunk culls faces on the boundaries between its sub-blocks."()
    {
        when: 'A 64-unit chunk whose 8x8x8 cells are each a solid 8x8x8 rock block (two tree levels).'
            var bounds = cube(0, 64)
            var cells = bounds.subdivide(WorldTreeNode.RESOLUTION)
            WorldSector[] blocks = new WorldSector[WorldTreeNode.SECTOR_COUNT]
            for ( int i = 0; i < WorldTreeNode.SECTOR_COUNT; i++ )
                blocks[i] = solidBlock(cells.get(i))
            var chunk = WorldSector.empty(bounds).withChildren(new WorldTreeNode(blocks))
            var mesh = new SectorMeshCache().meshOf(chunk)
        then: 'Meshed as one grid, the whole chunk is a solid cube: 6 shell faces, not 6 per sub-block.'
            mesh.faceCount() == 6
    }

    def "A coarser resolution meshes from branch sectors and collapses detail (level of detail)."()
    {
        given: 'An aggregated 64-unit chunk: an 8x8x8 checkerboard of solid rock sub-blocks and air.'
            var bounds = cube(0, 64)
            var cells = bounds.subdivide(WorldTreeNode.RESOLUTION)
            WorldSector[] blocks = new WorldSector[WorldTreeNode.SECTOR_COUNT]
            for ( int z = 0; z < 8; z++ )
                for ( int y = 0; y < 8; y++ )
                    for ( int x = 0; x < 8; x++ ) {
                        int i = WorldTreeNode.indexOf(x, y, z)
                        blocks[i] = ((x + y + z) % 2 == 0) ? solidBlock(cells.get(i)) : WorldSector.empty(cells.get(i))
                    }
            var chunk = WorldSector.empty(bounds).withChildren(new WorldTreeNode(blocks)).aggregated()
            var cache = new SectorMeshCache()
        expect: 'At the sub-block resolution the checkerboard exposes many faces...'
            cache.meshOf(chunk, 8).faceCount() > 6
        and: '...but coarsened to a single voxel - from the chunk\'s own aggregated ether, not its leaves - it is at most a 6-face box, far fewer.'
            cache.meshOf(chunk, 1).faceCount() <= 6
            cache.meshOf(chunk, 1).faceCount() < cache.meshOf(chunk, 8).faceCount()
    }

    /** A solid 8x8x8 block of rock leaves over the given bounds. */
    private static WorldSector solidBlock( BoundsF64 bounds ) {
        var cells = bounds.subdivide(WorldTreeNode.RESOLUTION)
        WorldSector[] leaves = new WorldSector[WorldTreeNode.SECTOR_COUNT]
        for ( int i = 0; i < WorldTreeNode.SECTOR_COUNT; i++ )
            leaves[i] = WorldSector.leaf(cells.get(i), WorldSectorEtherData.of(Material.ROCK))
        return WorldSector.empty(bounds).withChildren(new WorldTreeNode(leaves))
    }

    def "A column of stacked voxels greedy-merges each side into a single strip, regardless of height."()
    {
        when: 'A single 1x(height)x1 column of rock along Y at the corner.'
            var mesh = new SectorMeshCache().meshOf(block { int x, int y, int z ->
                (x == 0 && z == 0 && y < height) ? Material.ROCK : Material.AIR
            })
        then: 'The 4 sides each merge into one strip + a cap at each end = 6, however tall the column.'
            mesh.faceCount() == 6
        where:
            height << [1, 2, 3, 5, 8]
    }

    def "Two adjacent opaque voxels hide the shared face and merge their coplanar faces."()
    {
        when: 'Two rock voxels side by side along X.'
            var mesh = new SectorMeshCache().meshOf(block { int x, int y, int z ->
                (y == 0 && z == 0 && x < 2) ? Material.ROCK : Material.AIR
            })
        then: 'The two X end-caps stay separate; the 2x1 faces on Y and Z each merge into one => 6.'
            mesh.faceCount() == 6
    }

    def "Greedy meshing merges within an appearance but not across appearances."()
    {
        when: 'A solid floor (y==0): grass on one half (x<4), sand on the other.'
            var mesh = new SectorMeshCache().meshOf(block { int x, int y, int z ->
                y == 0 ? (x < 4 ? Material.GRASS : Material.SAND) : Material.AIR
            })
        then: 'The top (+Y) merges into exactly two quads (one per material region), not one and not 64.'
            mesh.quads().count { Quad q -> q.normal().y() > 0.5 } == 2
    }

    def "Every emitted face is a visible, non-invisible surface."()
    {
        given:
            var mesh = new SectorMeshCache().meshOf(block { int x, int y, int z -> y < 4 ? Material.ROCK : Material.AIR })
        expect:
            mesh.quads().every { Quad q -> !q.profile().isInvisible() }
            mesh.faceCount() > 0
    }

    def "A coarse leaf whose ether recesses a face meshes as a box shrunk to fit its content."()
    {
        given: 'A solid leaf over 0..8 whose top face is recessed by half (POS_Y inset 0.5), in the ether.'
            var ether = WorldSectorEtherData.of(Material.ROCK)
            ether = ether.withSide(Side.POS_Y, ether.sideOf(Side.POS_Y).withInset(0.5))
            var leaf = WorldSector.leaf(cube(0, 8), ether)
        when: 'It is meshed as a single coarse box (res-1).'
            var mesh = new SectorMeshCache().meshOf(leaf, 1)
            var ys = mesh.quads().collectMany { [it.c0().y(), it.c1().y(), it.c2().y(), it.c3().y()] }
            var xs = mesh.quads().collectMany { [it.c0().x(), it.c1().x(), it.c2().x(), it.c3().x()] }
        then: 'It is still a 6-face box...'
            mesh.faceCount() == 6
        and: '...but its top has dropped to the content surface (max y = 4), not the full cube (8)...'
            ys.max() == 4.0
            ys.min() == 0.0
        and: '...while the un-inset axes keep the full 0..8 extent.'
            xs.min() == 0.0
            xs.max() == 8.0
    }

    def "A leaf with no insets meshes as the full unshrunk box."()
    {
        given:
            var leaf = WorldSector.leaf(cube(0, 8), WorldSectorEtherData.of(Material.ROCK))
        when:
            var ys = new SectorMeshCache().meshOf(leaf, 1).quads().collectMany { [it.c0().y(), it.c1().y(), it.c2().y(), it.c3().y()] }
        then: 'No recession: the box spans the full 0..8.'
            ys.min() == 0.0
            ys.max() == 8.0
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