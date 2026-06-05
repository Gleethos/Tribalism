package engine.world

import app.engine.primitives.BoundsF64
import app.engine.primitives.CameraF64
import app.engine.primitives.VecF64
import app.engine.world.Material
import app.engine.world.SectorDrawCollector
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
    the level-of-detail decision - lives on the World, not on any renderer. That lets
    us assert those behaviours directly by collecting the sectors the world considers
    visible from a camera, with no drawing surface and no renderer in sight.

''')
class CollectSectorsForRendering_Spec extends Specification
{
    /** Collects every sector the world hands out, remembering each sector's wantsDetail flag. */
    private static final class Capture implements SectorDrawCollector {
        final List<WorldSector> sectors = []
        final Map<WorldSector, Boolean> wantsDetail = [:]
        @Override void collect( WorldSector sector, boolean detail, app.engine.world.ViewInfo view ) {
            sectors << sector
            wantsDetail[sector] = detail
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
            var seenStats = world.collectSectorsForRendering(facing, 240, 160, 28.0, seen)
            var noneStats = world.collectSectorsForRendering(away,   240, 160, 28.0, none)
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
                                            .withChildren(new WorldTreeNode(Tuple.of(WorldSector, kids))))
        and: 'A camera up close, looking straight through the near block at the far one.'
            var facing = new CameraF64(VecF64.of(-100, 8, 8), VecF64.of(0, 8, 8), VecF64.of(0, 1, 0), Math.toRadians(60), 1.0, 0.5, 2000)
            var away   = new CameraF64(VecF64.of(-100, 8, 8), VecF64.of(-200, 8, 8), VecF64.of(0, 1, 0), Math.toRadians(60), 1.0, 0.5, 2000)
        when:
            var facingStats = world.collectSectorsForRendering(facing, 200, 200, 28.0, new Capture())
        then: 'At least one sector behind the near block is occlusion-culled.'
            facingStats.occlusionCulledSectors() > 0
        when:
            var awayStats = world.collectSectorsForRendering(away, 200, 200, 28.0, new Capture())
        then: 'Facing away, frustum culling removes everything before occlusion even applies.'
            awayStats.sectorsCollected() == 0
            awayStats.occlusionCulledSectors() == 0
    }

    def "The level-of-detail flag follows on-screen size: detail up close, coarse from afar."()
    {
        given: 'A non-solid branch (rock below, air above) so it is refined rather than treated as a solid occluder.'
            var bounds = BoundsF64.cube(VecF64.zero(), 128)
            var root = branch(bounds) { int x, int y, int z -> y < 4 ? Material.ROCK : Material.AIR }
            var world = World.of(root)
            var near = new CameraF64(VecF64.of(0, 0, 140),  VecF64.zero(), VecF64.of(0, 1, 0), Math.toRadians(60), 1.0, 0.5, 4000)
            var far  = new CameraF64(VecF64.of(0, 0, 3500), VecF64.zero(), VecF64.of(0, 1, 0), Math.toRadians(60), 1.0, 0.5, 8000)
            var up = new Capture()
            var off = new Capture()
        when:
            world.collectSectorsForRendering(near, 600, 600, 28.0, up)
            world.collectSectorsForRendering(far,  600, 600, 28.0, off)
        then: 'Up close the root wants detail; from far away it is collected as one coarse box.'
            up.wantsDetail[root] == true
            off.wantsDetail[root] == false
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
        return WorldSector.empty(bounds).withChildren(new WorldTreeNode(Tuple.of(WorldSector, kids))).aggregated()
    }
}