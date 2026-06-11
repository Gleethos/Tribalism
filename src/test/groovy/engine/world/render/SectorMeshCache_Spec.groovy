package engine.world.render

import app.engine.primitives.BoundsF64
import app.engine.primitives.VecF64
import app.engine.world.Material
import app.engine.world.Side
import app.engine.world.VolumePatch
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

    def "Intermediate power-of-two resolutions mesh by downsampling and keep a flat surface where it is."()
    {
        given: 'A half-full block: solid rock below y==4, air above.'
            var halfFull = block { int x, int y, int z -> y < 4 ? Material.ROCK : Material.AIR }
            var cache = new SectorMeshCache()
        expect: 'The in-between resolutions (impossible on the old 1/8/64 ladder) mesh fine...'
            [2, 4, 8].every { int res -> cache.meshOf(halfFull, res).faceCount() == 6 }
        and: '...and at every step the floor surface stays exactly at y == 4 (downsampling shifts nothing).'
            [2, 4, 8].every { int res ->
                cache.meshOf(halfFull, res).quads().collectMany {
                    [it.c0().y(), it.c1().y(), it.c2().y(), it.c3().y()]
                }.max() == 4.0d
            }
    }

    def "Downsampling merges 2x2x2 blocks by majority: a checkerboard coarsens to solid, not to air."()
    {
        given: 'An 8x8x8 checkerboard of rock and air (every 2x2x2 block holds exactly 4 solid cells).'
            var checkerboard = block { int x, int y, int z -> ((x + y + z) % 2 == 0) ? Material.ROCK : Material.AIR }
            var cache = new SectorMeshCache()
        expect: 'At full resolution the checkerboard exposes a sea of faces...'
            cache.meshOf(checkerboard, 8).faceCount() > 6
        and: '...halved once, every merged cell is majority-solid, so it collapses to one solid box.'
            cache.meshOf(checkerboard, 4).faceCount() == 6
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

    def "A childless leaf with a volume patch meshes with real shape at coarse resolutions."()
    {
        given: 'A leaf over 0..8 whose patch is a terrace: ground 2 cells high on the left (x<4), 6 on the right.'
            var leaf = WorldSector.leaf(cube(0, 8), WorldSectorEtherData.of(Material.ROCK),
                                        patchOf { int x, int y, int z -> y < (x < 4 ? 2 : 6) ? Material.ROCK : Material.AIR })
            var cache = new SectorMeshCache()
        when:
            var mesh = cache.meshOf(leaf, 8)
        then: 'Two terrace tops at the two ground heights - not one flat box top - plus the step wall between them.'
            topHeights(mesh) == [2.0d, 6.0d]
            mesh.faceCount() > 6
        and: 'Downsampled to res 4 (cell-aligned terraces) the shape survives.'
            topHeights(cache.meshOf(leaf, 4)) == [2.0d, 6.0d]
        and: 'Without a patch, the same childless leaf can only ever be a single box, whatever is asked for.'
            new SectorMeshCache().meshOf(WorldSector.leaf(cube(0, 8), WorldSectorEtherData.of(Material.ROCK)), 8).faceCount() == 6
    }

    def "A floating structure in a volume patch meshes with an exposed underside - the far field is volumetric, not a heightmap."()
    {
        given: 'A patch whose only content is a slab floating at y 5..6, nothing beneath it.'
            var leaf = WorldSector.leaf(cube(0, 8), WorldSectorEtherData.of(Material.ROCK),
                                        patchOf { int x, int y, int z -> y == 5 ? Material.ROCK : Material.AIR })
        when:
            var mesh = new SectorMeshCache().meshOf(leaf, 8)
        then: 'The slab has a drawn underside at y == 5 (a heightmap could never represent this)...'
            mesh.quads().any { Quad q -> q.normal().y() < -0.5 && [q.c0().y(), q.c1().y(), q.c2().y(), q.c3().y()].every { it == 5.0d } }
        and: '...and its top at y == 6.'
            topHeights(mesh) == [6.0d]
        and: 'Floating content advertises no solid base, so it will occlude nothing behind it.'
            leaf.volumePatch().solidBaseFraction() == 0.0d
    }

    /** A volume patch whose cells come from a (x,y,z)->Material function. */
    private static VolumePatch patchOf( Closure<Material> materialAt ) {
        Material[] cells = new Material[VolumePatch.CELL_COUNT]
        for ( int z = 0; z < VolumePatch.RESOLUTION; z++ )
            for ( int y = 0; y < VolumePatch.RESOLUTION; y++ )
                for ( int x = 0; x < VolumePatch.RESOLUTION; x++ )
                    cells[WorldTreeNode.indexOf(x, y, z)] = materialAt(x, y, z) as Material
        return VolumePatch.of(cells)
    }

    /** The distinct heights of all upward (+Y) faces of the mesh, ascending. */
    private static List<Double> topHeights( mesh ) {
        return mesh.quads().findAll { Quad q -> q.normal().y() > 0.5 }
                   .collectMany { Quad q -> [q.c0().y(), q.c1().y(), q.c2().y(), q.c3().y()] }
                   .unique().sort()
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

    def "Per-cell insets apply at res>1: a recessed half drops its top, and a step wall bridges the gap (no hole)."()
    {
        given: 'A single ground layer (y==0). Left half (x<4) flush; right half (x>=4) recessed by half on top.'
            var ground = blockOf { int x, int y, int z ->
                if ( y != 0 ) return WorldSectorEtherData.empty()
                var rock = WorldSectorEtherData.of(Material.ROCK)
                return x < 4 ? rock : rock.withSide(Side.POS_Y, rock.sideOf(Side.POS_Y).withInset(0.5))
            }
        when: 'Meshed at full (sub-block) resolution.'
            var mesh = new SectorMeshCache().meshOf(ground, 8)
            var tops = mesh.quads().findAll { it.normal().y() > 0.5 }
        then: 'The flush half keeps its top at the cell boundary (y=1)...'
            tops.any { ysOf(it).max() == 1.0 }
        and: '...the recessed half drops its top to the content surface (y=0.5)...'
            tops.any { ysOf(it).max() == 0.5 }
        and: '...and an X-facing step wall reaches up to y=1 at the x=4 seam, so the height step is not a hole.'
            mesh.quads().any { q ->
                Math.abs(q.normal().x()) > 0.5 && xsOf(q).every { Math.abs(it - 4.0) < 1e-9 } && ysOf(q).max() == 1.0
            }
    }

    def "A volume patch's baked top insets recede far terraces to the continuous surface (no cell quantization)."()
    {
        given: 'A patch of 4-cell-high ground whose columns are all recessed by a quarter cell on top.'
            Material[] cells = new Material[VolumePatch.CELL_COUNT]
            for ( int z = 0; z < 8; z++ )
                for ( int y = 0; y < 8; y++ )
                    for ( int x = 0; x < 8; x++ )
                        cells[WorldTreeNode.indexOf(x, y, z)] = y < 4 ? Material.ROCK : Material.AIR
            double[] topInsets = new double[64]
            Arrays.fill(topInsets, 0.25d) // a multiple of the mesher's 1/16 geometric quantization.
            var leaf = WorldSector.leaf(cube(0, 8), WorldSectorEtherData.of(Material.ROCK), VolumePatch.of(cells, topInsets))
        when:
            var mesh = new SectorMeshCache().meshOf(leaf, 8)
            var tops = topHeights(mesh)
        then: 'One merged terrace top, a quarter cell below the cell boundary: y = 4 - 0.25 = 3.75.'
            tops.size() == 1
            Math.abs(tops[0] - 3.75d) < 1e-9
        and: 'The advertised occluding base never reaches above that drawn surface.'
            leaf.volumePatch().solidBaseFraction() <= 3.75d / 8 + 1e-9
    }

    def "Recession is quantized for merging: nearly-equal insets share one quad, instead of one quad per cell."()
    {
        given: 'A ground layer whose every cell recedes by a slightly DIFFERENT amount within one 1/16 bucket.'
            var ground = blockOf { int x, int y, int z ->
                if ( y != 0 ) return WorldSectorEtherData.empty()
                var rock = WorldSectorEtherData.of(Material.ROCK)
                // 0.5 .. 0.5567: all in the same 1/16 bucket (8/16), as continuous terrain insets would be.
                return rock.withSide(Side.POS_Y, rock.sideOf(Side.POS_Y).withInset(0.5 + (x + z * 8) * 0.0009))
            }
        when:
            var mesh = new SectorMeshCache().meshOf(ground, 8)
        then: 'The 64 almost-equal tops merge into ONE quad at the floored bucket depth (y = 0.5)...'
            mesh.quads().count { Quad q -> q.normal().y() > 0.5 } == 1
            topHeights(mesh) == [0.5d]
        and: '...instead of the 64 per-cell tops raw continuous insets would force.'
            mesh.faceCount() < 64
    }

    def "Crossing opposing insets collapse a face to nothing instead of crashing (regression: the frozen horizon spots)."()
    {
        given: 'A ground layer of narrow-ridge cells: NEG_X and POS_X insets that CROSS (0.6 + 0.6 > 1).'
            var ground = blockOf { int x, int y, int z ->
                if ( y != 0 ) return WorldSectorEtherData.empty()
                var rock = WorldSectorEtherData.of(Material.ROCK)
                rock = rock.withSide(Side.NEG_X, rock.sideOf(Side.NEG_X).withInset(0.6))
                return rock.withSide(Side.POS_X, rock.sideOf(Side.POS_X).withInset(0.6))
            }
        when: 'Meshed at full resolution. (This used to build an inverted box and throw - which the GL render loop swallowed per frame, silently freezing the picture whenever such a sector was on screen.)'
            var mesh = new SectorMeshCache().meshOf(ground, 8)
        then: 'No throw; the zero-width content simply emits no face on the collapsed axes...'
            notThrown(IllegalArgumentException)
        and: '...leaving only the X-facing end caps (the faces whose extents did not collapse).'
            mesh.faceCount() > 0
            mesh.quads().every { Quad q -> Math.abs(q.normal().x()) > 0.5 }
    }

    /** An (un-aggregated) 8x8x8 block whose leaves come from a (x,y,z)->WorldSectorEtherData function. */
    private static WorldSector blockOf( Closure<WorldSectorEtherData> etherAt ) {
        var bounds = cube(0, 8)
        var cells = bounds.subdivide(WorldTreeNode.RESOLUTION)
        WorldSector[] kids = new WorldSector[WorldTreeNode.SECTOR_COUNT]
        for ( int z = 0; z < 8; z++ )
            for ( int y = 0; y < 8; y++ )
                for ( int x = 0; x < 8; x++ ) {
                    int i = WorldTreeNode.indexOf(x, y, z)
                    kids[i] = WorldSector.leaf(cells.get(i), etherAt(x, y, z) as WorldSectorEtherData)
                }
        return WorldSector.empty(bounds).withChildren(new WorldTreeNode(kids))
    }

    private static List<Double> ysOf( Quad q ) { [q.c0().y(), q.c1().y(), q.c2().y(), q.c3().y()] }
    private static List<Double> xsOf( Quad q ) { [q.c0().x(), q.c1().x(), q.c2().x(), q.c3().x()] }

    def "The mesh is memoized: the same immutable sector returns the identical mesh."()
    {
        given:
            var cache = new SectorMeshCache()
            var solid = block { int x, int y, int z -> Material.ROCK }
        expect: 'Same instance back (cached), not a rebuild.'
            cache.meshOf(solid).is(cache.meshOf(solid))
    }
}