package engine.world

import app.engine.primitives.BoundsF64
import app.engine.primitives.VecF64
import app.engine.world.Material
import app.engine.world.MaterialId
import app.engine.world.Side
import app.engine.world.Texture
import app.engine.world.WorldSector
import app.engine.world.WorldSectorEtherData
import app.engine.world.WorldTreeEntityId
import app.engine.world.WorldTreeNode
import spock.lang.Narrative
import spock.lang.Specification
import spock.lang.Title

@Title("The world tree - recursion, fall-down and level of detail")
@Narrative('''

    The world tree is built from sectors, each of which may branch into a node
    of exactly 512 (8x8x8) finer sectors. Two behaviours make the structure
    interesting: entities "fall down" to the deepest sector that still fully
    contains them, and a parent sector can summarize a whole sub-tree by
    averaging its children's material (level of detail).

''')
class WorldTree_Spec extends Specification
{
    private static BoundsF64 cube( double from, double to ) {
        return BoundsF64.of(VecF64.of(from, from, from), VecF64.of(to, to, to))
    }

    def "A uniform node holds exactly 512 sectors laid out as an 8x8x8 cube."()
    {
        when:
            var node = WorldTreeNode.uniform(cube(0, 8), WorldSectorEtherData.empty())
        then:
            node.sectors().size() == 512
            WorldTreeNode.SECTOR_COUNT == 512
            WorldTreeNode.RESOLUTION == 8
        and: 'The linear index follows x + y*8 + z*64.'
            WorldTreeNode.indexOf(1, 0, 0) == 1
            WorldTreeNode.indexOf(0, 1, 0) == 8
            WorldTreeNode.indexOf(0, 0, 1) == 64
        and: 'Each leaf occupies its own unit cell.'
            node.sector(0, 0, 0).bounds() == cube(0, 1)
            node.sector(1, 1, 1).bounds() == BoundsF64.of(VecF64.of(1, 1, 1), VecF64.of(2, 2, 2))
    }

    def "A fresh sector is a leaf, and subdividing gives it 512 children."()
    {
        given:
            var sector = WorldSector.empty(cube(0, 8))
        expect:
            sector.isLeaf()
        when:
            var split = sector.subdivide()
        then:
            !split.isLeaf()
            split.children().sectors().size() == 512
        and: 'Subdividing again is a no-op once children exist.'
            split.subdivide() === split || split.subdivide().children().sectors().size() == 512
    }

    def "A small entity falls down to the deepest sector that still contains it."()
    {
        given: 'A root spanning 0..64, so two levels of 8x8x8 reach unit cells.'
            var root = WorldSector.empty(cube(0, 64))
            var entity = WorldTreeEntityId.of(42L, BoundsF64.of(VecF64.of(1.2, 1.2, 1.2), VecF64.of(1.8, 1.8, 1.8)))
        when:
            var withEntity = root.insert(entity, 2)
        then: 'It is not left at the root...'
            withEntity.entities().isEmpty()
        and: '...nor at the first level...'
            var level1 = withEntity.children().sector(0, 0, 0)
            level1.entities().isEmpty()
        and: '...but rests in the level-2 cell that tightly encloses it.'
            var level2 = level1.children().sector(1, 1, 1)
            level2.entities().contains(entity)
            level2.bounds().contains(entity.bounds())
    }

    def "A large entity spanning several cells comes to rest at the root."()
    {
        given:
            var root = WorldSector.empty(cube(0, 64))
            var big = WorldTreeEntityId.of(7L, BoundsF64.of(VecF64.of(0, 0, 0), VecF64.of(40, 40, 40)))
        when:
            var withEntity = root.insert(big, 4)
        then: 'It straddles cell boundaries, so it stays here and the root is not split.'
            withEntity.entities().contains(big)
            withEntity.isLeaf()
    }

    def "Per-side aggregation summarizes only the faces a child actually lies on."()
    {
        given: 'A subdivided sector where the single corner child (0,0,0) is solid rock.'
            var sector = WorldSector.empty(cube(0, 8)).subdivide()
            var rockChild = sector.children().sector(0).withEther(WorldSectorEtherData.of(Material.ROCK))
            var withRock = sector.withChildren(sector.children().withSector(0, rockChild))
        when:
            var aggregated = withRock.aggregated()
        then: 'Corner cell (0,0,0) sits on the three negative faces, so each is 1/64 opaque...'
            Math.abs(aggregated.ether().sideOf(Side.NEG_X).intensityOf(Texture.OPACITY) - 1.0 / 64.0) < 1e-12
            Math.abs(aggregated.ether().sideOf(Side.NEG_Y).intensityOf(Texture.OPACITY) - 1.0 / 64.0) < 1e-12
            Math.abs(aggregated.ether().sideOf(Side.NEG_Z).intensityOf(Texture.OPACITY) - 1.0 / 64.0) < 1e-12
        and: '...while the opposite faces never see it (the interior is hidden).'
            aggregated.ether().sideOf(Side.POS_X).intensityOf(Texture.OPACITY) == 0
            aggregated.ether().sideOf(Side.POS_Y).intensityOf(Texture.OPACITY) == 0
            aggregated.ether().sideOf(Side.POS_Z).intensityOf(Texture.OPACITY) == 0
        and: 'Mixing rock and air children makes the whole-cube material Diverse.'
            aggregated.ether().material().isDiverse()
    }

    def "Aggregating a uniformly subdivided sector preserves its appearance and material."()
    {
        given: 'Splitting a solid rock voxel must keep it solid rock all over.'
            var rock = WorldSector.leaf(cube(0, 8), WorldSectorEtherData.of(Material.ROCK))
        when:
            var aggregated = rock.subdivide().aggregated()
        then: 'Every face stays fully opaque...'
            Side.values().every {
                Math.abs(aggregated.ether().sideOf(it).intensityOf(Texture.OPACITY) - 1.0) < 1e-12
            }
        and: '...and the single material is preserved (all children agreed).'
            aggregated.ether().material() == Material.ROCK.materialId()
    }

    def "Per-side aggregation recurses through multiple levels, dividing by face area each time."()
    {
        given: 'Two levels deep, with the corner unit cell (0,0,0) set to rock.'
            var root = WorldSector.empty(cube(0, 64)).subdivide()
            var level1 = root.children().sector(0).subdivide()
            var rockLeaf = level1.children().sector(0).withEther(WorldSectorEtherData.of(Material.ROCK))
            var level1WithRock = level1.withChildren(level1.children().withSector(0, rockLeaf))
            var rootWithLevels = root.withChildren(root.children().withSector(0, level1WithRock))
        when:
            var aggregated = rootWithLevels.aggregated()
        then: 'Each level averages over a 64-cell face, so the corner rock is 1/(64*64) opaque on the -X face.'
            Math.abs(aggregated.ether().sideOf(Side.NEG_X).intensityOf(Texture.OPACITY) - 1.0 / (64.0 * 64.0)) < 1e-15
            aggregated.ether().sideOf(Side.POS_X).intensityOf(Texture.OPACITY) == 0
    }
}