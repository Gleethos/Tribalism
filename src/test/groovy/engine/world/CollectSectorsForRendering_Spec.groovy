package engine.world

import app.engine.primitives.BoundsF64
import app.engine.primitives.CameraF64
import app.engine.primitives.VecF64
import app.engine.world.Material
import app.engine.world.ScreenId
import app.engine.world.SectorDrawCollector
import app.engine.world.Side
import app.engine.world.VolumePatch
import app.engine.world.World
import app.engine.world.WorldSector
import app.engine.world.WorldSectorEtherData
import app.engine.world.WorldTreeNode
import app.engine.world.gen.WorldGenerator
import sprouts.Tuple
import spock.lang.Narrative
import spock.lang.Specification
import spock.lang.Title

@Title("World.collectSectorsForRendering - visibility without a renderer")
@Narrative('''

    All of the per-frame visibility logic - frustum culling, occlusion culling and
    the level-of-detail decision - lives on the World, not on any renderer. The world
    is asked to collect for a screen id (which resolves to its bound camera and size),
    so we assert those behaviours directly by collecting the sectors it considers
    visible, with no drawing surface and no renderer in sight.

''')
class CollectSectorsForRendering_Spec extends Specification
{
    private static final ScreenId SCREEN = ScreenId.of(1L)

    /** Gives the world a camera (id 1) and a screen (SCREEN) bound to it, so it can be collected. */
    private static World onScreen( World world, CameraF64 camera, int w, int h ) {
        return world.createCamera(1L, camera)
                    .createScreen(SCREEN, w, h)
                    .bindScreenToCamera(SCREEN, 1L)
    }

    /** Collects every render unit the world hands out, with the level of detail chosen for it. */
    private static final class Capture implements SectorDrawCollector {
        final List<WorldSector> sectors = []
        final List<Integer> resolutions = []
        @Override void collect( WorldSector sector, app.engine.world.ViewInfo view, int meshResolution ) {
            sectors << sector
            resolutions << meshResolution
        }
    }

    def "A camera looking at the world collects sectors; one looking away collects none (frustum culling)."()
    {
        given: 'A small generated world.'
            var world = World.of(WorldGenerator.withSeed(1337L).generate(BoundsF64.cube(VecF64.zero(), 128), 2))
            var facing  = new CameraF64(VecF64.of(96, 40, 96), VecF64.zero(),         VecF64.of(0, 1, 0), Math.toRadians(60), 1.0, 0.5, 2000)
            var away    = new CameraF64(VecF64.of(96, 40, 96), VecF64.of(300, 40, 300), VecF64.of(0, 1, 0), Math.toRadians(60), 1.0, 0.5, 2000)
            var seen = new Capture()
            var none = new Capture()
        when:
            var seenStats = onScreen(world, facing, 240, 160).collectSectorsForRendering(SCREEN, 64.0, seen)
            var noneStats = onScreen(world, away,   240, 160).collectSectorsForRendering(SCREEN, 64.0, none)
        then: 'Facing the world yields visible sectors...'
            seenStats.sectorsCollected() > 0
            seen.sectors.size() == seenStats.sectorsCollected()
        and: '...while everything is behind the camera when looking away, so nothing is collected.'
            noneStats.sectorsCollected() == 0
            none.sectors.isEmpty()
    }

    def "Solid geometry in front occludes whole sub-trees behind it."()
    {
        given: 'A near and a far solid ROCK block on the same line of sight, air elsewhere.'
            var bounds = BoundsF64.cube(VecF64.zero(), 128)
            var cells = bounds.subdivide(WorldTreeNode.RESOLUTION)
            WorldSector[] kids = new WorldSector[WorldTreeNode.SECTOR_COUNT]
            for ( int i = 0; i < WorldTreeNode.SECTOR_COUNT; i++ )
                kids[i] = WorldSector.leaf(cells.get(i), WorldSectorEtherData.empty())
            int near = WorldTreeNode.indexOf(0, 4, 4)
            int far  = WorldTreeNode.indexOf(7, 4, 4)
            // Solid branches (not leaves) so the root recurses and occlusion can act between them.
            kids[near] = WorldSector.leaf(cells.get(near), WorldSectorEtherData.of(Material.ROCK)).subdivide().aggregated()
            kids[far]  = WorldSector.leaf(cells.get(far),  WorldSectorEtherData.of(Material.ROCK)).subdivide().aggregated()
            var world = World.of(WorldSector.empty(bounds)
                                            .withChildren(new WorldTreeNode(kids)))
        and: 'A camera up close, looking straight through the near block at the far one.'
            var facing = new CameraF64(VecF64.of(-100, 8, 8), VecF64.of(0, 8, 8), VecF64.of(0, 1, 0), Math.toRadians(60), 1.0, 0.5, 2000)
            var away   = new CameraF64(VecF64.of(-100, 8, 8), VecF64.of(-200, 8, 8), VecF64.of(0, 1, 0), Math.toRadians(60), 1.0, 0.5, 2000)
        when:
            var facingStats = onScreen(world, facing, 200, 200).collectSectorsForRendering(SCREEN, 64.0, new Capture())
        then: 'At least one sector behind the near block is occlusion-culled.'
            facingStats.occlusionCulledSectors() > 0
        when:
            var awayStats = onScreen(world, away, 200, 200).collectSectorsForRendering(SCREEN, 64.0, new Capture())
        then: 'Facing away, frustum culling removes everything before occlusion even applies.'
            awayStats.sectorsCollected() == 0
            awayStats.occlusionCulledSectors() == 0
    }

    def "An inset solid box only occludes what its drawn (shrunk) box covers, not its full bounds (regression: phantom sky-band culling)."()
    {
        given: 'A near solid box and a far solid box on the same line of sight, air elsewhere.'
            var bounds = BoundsF64.cube(VecF64.zero(), 128)
            var cells = bounds.subdivide(WorldTreeNode.RESOLUTION)
            int near = WorldTreeNode.indexOf(0, 4, 4)
            int far  = WorldTreeNode.indexOf(7, 4, 4)
            var rock = WorldSectorEtherData.of(Material.ROCK)
            // The near box's top recedes halfway down (a surface box: fully opaque faces, but the
            // renderer draws it inset-shrunk), so the far box pokes out visibly above its DRAWN top.
            var recessedRock = rock.withSide(Side.POS_Y, rock.sideOf(Side.POS_Y).withInset(0.5d))
        and: 'A camera at the drawn surface height, looking straight along the row of cells.'
            var camera = new CameraF64(VecF64.of(-100, 8, 8), VecF64.of(0, 8, 8), VecF64.of(0, 1, 0), Math.toRadians(60), 1.0, 0.5, 2000)
        when: 'The near box is recessed: the far box must survive, its upper half is visible above the drawn top.'
            var seen = new Capture()
            var stats = onScreen(twoBoxWorld(bounds, cells, near, recessedRock, far, rock), camera, 200, 200)
                    .collectSectorsForRendering(SCREEN, 16.0, seen)
        then: 'The far box is collected - the band above the near box\'s drawn top is not phantom coverage.'
            seen.sectors.any { it.bounds() == cells.get(far) }
            stats.occlusionCulledSectors() == 0
        when: 'Control: the same scene with a flush (un-inset) near box really does hide the far box.'
            var blocked = new Capture()
            var blockedStats = onScreen(twoBoxWorld(bounds, cells, near, rock, far, rock), camera, 200, 200)
                    .collectSectorsForRendering(SCREEN, 16.0, blocked)
        then:
            !blocked.sectors.any { it.bounds() == cells.get(far) }
            blockedStats.occlusionCulledSectors() > 0
    }

    def "A solid leaf with a volume patch is drawn with shape at its LoD resolution and occludes only its solid base."()
    {
        given: 'A near solid-opaque leaf whose patch is ground filling its bottom half, and a far solid box behind it.'
            var bounds = BoundsF64.cube(VecF64.zero(), 128)
            var cells = bounds.subdivide(WorldTreeNode.RESOLUTION)
            int near = WorldTreeNode.indexOf(0, 4, 4)
            int far  = WorldTreeNode.indexOf(7, 4, 4)
            var rock = WorldSectorEtherData.of(Material.ROCK)
            var camera = new CameraF64(VecF64.of(-100, 8, 8), VecF64.of(0, 8, 8), VecF64.of(0, 1, 0), Math.toRadians(60), 1.0, 0.5, 2000)
            var groundLeaf = WorldSector.leaf(cells.get(near), rock, patchOf { int x, int y, int z -> y < 4 ? Material.ROCK : Material.AIR })
        when:
            var seen = new Capture()
            var stats = onScreen(twoSectorWorld(bounds, cells, near, groundLeaf, far, WorldSector.leaf(cells.get(far), rock)), camera, 200, 200)
                    .collectSectorsForRendering(SCREEN, 16.0, seen)
        then: 'The patched leaf is collected at its patch resolution (8), no longer forced to a single res-1 box.'
            seen.resolutions[seen.sectors.findIndexOf { it.bounds() == cells.get(near) }] == 8
        and: 'The far box pokes out above the ground and survives: only the solid base slab occludes.'
            seen.sectors.any { it.bounds() == cells.get(far) }
            stats.occlusionCulledSectors() == 0
        when: 'Control: the same scene with the patch solid to its top really does hide the far box.'
            var fullLeaf = WorldSector.leaf(cells.get(near), rock, patchOf { int x, int y, int z -> Material.ROCK })
            var blocked = new Capture()
            var blockedStats = onScreen(twoSectorWorld(bounds, cells, near, fullLeaf, far, WorldSector.leaf(cells.get(far), rock)), camera, 200, 200)
                    .collectSectorsForRendering(SCREEN, 16.0, blocked)
        then:
            !blocked.sectors.any { it.bounds() == cells.get(far) }
            blockedStats.occlusionCulledSectors() > 0
    }

    def "A larger chunk size yields fewer, bigger render units (the batching knob)."()
    {
        given: 'A non-solid branch (rock below, air above) and a camera looking straight at it.'
            var bounds = BoundsF64.cube(VecF64.zero(), 128)
            var root = branch(bounds) { int x, int y, int z -> y < 4 ? Material.ROCK : Material.AIR }
            var world = World.of(root)
            var camera = new CameraF64(VecF64.of(0, 0, 140), VecF64.zero(), VecF64.of(0, 1, 0), Math.toRadians(60), 1.0, 0.5, 4000)
            var coarse = new Capture()
            var fine = new Capture()
        when: 'A chunk size of the whole world meshes it as one unit; a small one splits it up.'
            onScreen(world, camera, 600, 600).collectSectorsForRendering(SCREEN, 128.0, coarse)
            onScreen(world, camera, 600, 600).collectSectorsForRendering(SCREEN, 16.0, fine)
        then: 'The coarse pass hands over a single big chunk; the fine pass many small ones.'
            coarse.sectors.size() == 1
            fine.sectors.size() > coarse.sectors.size()
    }

    def "A sector is meshed coarsely when far and finely when near (level of detail by distance)."()
    {
        given: 'A 128-unit branch (rock below, air above), with a chunk size of the whole world so it is always one unit.'
            var bounds = BoundsF64.cube(VecF64.zero(), 128)
            var world = World.of(branch(bounds) { int x, int y, int z -> y < 4 ? Material.ROCK : Material.AIR })
            var near = new CameraF64(VecF64.of(0, 0, 200),  VecF64.zero(), VecF64.of(0, 1, 0), Math.toRadians(60), 1.0, 0.5, 6000)
            var far  = new CameraF64(VecF64.of(0, 0, 3000), VecF64.zero(), VecF64.of(0, 1, 0), Math.toRadians(60), 1.0, 0.5, 6000)
            var nearUnits = new Capture()
            var farUnits  = new Capture()
        when:
            onScreen(world, near, 600, 600).collectSectorsForRendering(SCREEN, 128.0, nearUnits)
            onScreen(world, far,  600, 600).collectSectorsForRendering(SCREEN, 128.0, farUnits)
        then: 'Both hand over the single root unit...'
            nearUnits.sectors.size() == 1
            farUnits.sectors.size() == 1
        and: '...but the distant one is meshed at a coarser (smaller) grid resolution.'
            farUnits.resolutions[0] < nearUnits.resolutions[0]
    }

    def "The level-of-detail ladder steps by powers of two, not jumps of eight."()
    {
        given: 'A 128-unit branch (rock below, air above), chunk size the whole world so it is always one unit.'
            var bounds = BoundsF64.cube(VecF64.zero(), 128)
            var world = World.of(branch(bounds) { int x, int y, int z -> y < 4 ? Material.ROCK : Material.AIR })
            var camera = new CameraF64(VecF64.of(0, 0, 64 + distance), VecF64.zero(), VecF64.of(0, 1, 0), Math.toRadians(60), 1.0, 0.5, 8000)
            var units = new Capture()
        when:
            onScreen(world, camera, 600, 600).collectSectorsForRendering(SCREEN, 128.0, units)
        then: 'The single unit is meshed at an in-between resolution the old powers-of-8 ladder could not express.'
            units.sectors.size() == 1
            units.resolutions[0] == expectedResolution
        where: 'detailCells = 40 * 64 / distance, rounded to the nearest power of two.'
            distance | expectedResolution
            160.0    | 16
            640.0    | 4
            1280.0   | 2
    }

    def "A camera inside a chunk meshes it at the finest detail, not the coarsest (regression: detailCells +Infinity)."()
    {
        given: 'A 128-unit branch (rock below, air above), chunk size the whole world so it is always one unit.'
            var bounds = BoundsF64.cube(VecF64.zero(), 128)
            var world = World.of(branch(bounds) { int x, int y, int z -> y < 4 ? Material.ROCK : Material.AIR })
            // One camera sits INSIDE the chunk (distance 0 -> detailCells == +Infinity); one is just outside it.
            var inside      = new CameraF64(VecF64.of(0, 32, 0), VecF64.of(0, 0, 0),  VecF64.of(0, 1, 0), Math.toRadians(60), 1.0, 0.5, 6000)
            var nearOutside = new CameraF64(VecF64.of(0, 0, 70), VecF64.zero(),       VecF64.of(0, 1, 0), Math.toRadians(60), 1.0, 0.5, 6000)
            var insideUnits = new Capture()
            var nearUnits   = new Capture()
        when:
            onScreen(world, inside,      600, 600).collectSectorsForRendering(SCREEN, 128.0, insideUnits)
            onScreen(world, nearOutside, 600, 600).collectSectorsForRendering(SCREEN, 128.0, nearUnits)
        then: 'Each hands over the single root unit.'
            insideUnits.sectors.size() == 1
            nearUnits.sectors.size() == 1
        and: 'Being inside meshes at the FINEST resolution, the same as standing right next to it...'
            insideUnits.resolutions[0] == nearUnits.resolutions[0]
        and: '...and emphatically not the res-1 single box the +Infinity overflow used to collapse it to.'
            insideUnits.resolutions[0] > 1
    }

    /** A world of air leaves with two solid boxes at the given child indices (the occlusion duel rig). */
    private static World twoBoxWorld( BoundsF64 bounds, Tuple<BoundsF64> cells, int near, WorldSectorEtherData nearEther, int far, WorldSectorEtherData farEther ) {
        return twoSectorWorld(bounds, cells, near, WorldSector.leaf(cells.get(near), nearEther), far, WorldSector.leaf(cells.get(far), farEther))
    }

    /** A world of air leaves with two arbitrary sectors at the given child indices. */
    private static World twoSectorWorld( BoundsF64 bounds, Tuple<BoundsF64> cells, int near, WorldSector nearSector, int far, WorldSector farSector ) {
        WorldSector[] kids = new WorldSector[WorldTreeNode.SECTOR_COUNT]
        for ( int i = 0; i < WorldTreeNode.SECTOR_COUNT; i++ )
            kids[i] = WorldSector.leaf(cells.get(i), WorldSectorEtherData.empty())
        kids[near] = nearSector
        kids[far]  = farSector
        return World.of(WorldSector.empty(bounds).withChildren(new WorldTreeNode(kids)))
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
        return WorldSector.empty(bounds).withChildren(new WorldTreeNode(kids)).aggregated()
    }
}