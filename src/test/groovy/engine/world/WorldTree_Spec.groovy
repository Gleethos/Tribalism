package engine.world

import app.engine.primitives.BoundsF64
import app.engine.primitives.VecF64
import app.engine.world.Material
import app.engine.world.WorldSection
import app.engine.world.WorldSectionEtherData
import app.engine.world.WorldTreeEntityId
import app.engine.world.WorldTreeNode
import spock.lang.Narrative
import spock.lang.Specification
import spock.lang.Title

@Title("The world tree - recursion, fall-down and level of detail")
@Narrative('''

    The world tree is built from sections, each of which may branch into a node
    of exactly 512 (8x8x8) finer sections. Two behaviours make the structure
    interesting: entities "fall down" to the deepest section that still fully
    contains them, and a parent section can summarize a whole sub-tree by
    averaging its children's material (level of detail).

''')
class WorldTree_Spec extends Specification
{
    private static BoundsF64 cube( double from, double to ) {
        return BoundsF64.of(VecF64.of(from, from, from), VecF64.of(to, to, to))
    }

    def "A uniform node holds exactly 512 sections laid out as an 8x8x8 cube."()
    {
        when:
            var node = WorldTreeNode.uniform(cube(0, 8), WorldSectionEtherData.empty())
        then:
            node.sections().size() == 512
            WorldTreeNode.SECTION_COUNT == 512
            WorldTreeNode.RESOLUTION == 8
        and: 'The linear index follows x + y*8 + z*64.'
            WorldTreeNode.indexOf(1, 0, 0) == 1
            WorldTreeNode.indexOf(0, 1, 0) == 8
            WorldTreeNode.indexOf(0, 0, 1) == 64
        and: 'Each leaf occupies its own unit cell.'
            node.section(0, 0, 0).bounds() == cube(0, 1)
            node.section(1, 1, 1).bounds() == BoundsF64.of(VecF64.of(1, 1, 1), VecF64.of(2, 2, 2))
    }

    def "A fresh section is a leaf, and subdividing gives it 512 children."()
    {
        given:
            var section = WorldSection.empty(cube(0, 8))
        expect:
            section.isLeaf()
        when:
            var split = section.subdivide()
        then:
            !split.isLeaf()
            split.children().sections().size() == 512
        and: 'Subdividing again is a no-op once children exist.'
            split.subdivide() === split || split.subdivide().children().sections().size() == 512
    }

    def "A small entity falls down to the deepest section that still contains it."()
    {
        given: 'A root spanning 0..64, so two levels of 8x8x8 reach unit cells.'
            var root = WorldSection.empty(cube(0, 64))
            var entity = WorldTreeEntityId.of(42L, BoundsF64.of(VecF64.of(1.2, 1.2, 1.2), VecF64.of(1.8, 1.8, 1.8)))
        when:
            var withEntity = root.insert(entity, 2)
        then: 'It is not left at the root...'
            withEntity.entities().isEmpty()
        and: '...nor at the first level...'
            var level1 = withEntity.children().section(0, 0, 0)
            level1.entities().isEmpty()
        and: '...but rests in the level-2 cell that tightly encloses it.'
            var level2 = level1.children().section(1, 1, 1)
            level2.entities().contains(entity)
            level2.bounds().contains(entity.bounds())
    }

    def "A large entity spanning several cells comes to rest at the root."()
    {
        given:
            var root = WorldSection.empty(cube(0, 64))
            var big = WorldTreeEntityId.of(7L, BoundsF64.of(VecF64.of(0, 0, 0), VecF64.of(40, 40, 40)))
        when:
            var withEntity = root.insert(big, 4)
        then: 'It straddles cell boundaries, so it stays here and the root is not split.'
            withEntity.entities().contains(big)
            withEntity.isLeaf()
    }

    def "Level-of-detail aggregation averages a section's children into itself."()
    {
        given: 'A subdivided section where a single child is solid rock.'
            var section = WorldSection.empty(cube(0, 8)).subdivide()
            var rockChild = section.children().section(0).withEther(WorldSectionEtherData.of(Material.ROCK))
            var withRock = section.withChildren(section.children().withSection(0, rockChild))
        when:
            var aggregated = withRock.aggregated()
        then: 'The parent voxel becomes 1/512 rock, summarizing its sub-tree.'
            Math.abs(aggregated.ether().fractionOf(Material.ROCK) - 1.0 / 512.0) < 1e-12
    }

    def "Aggregating a uniformly subdivided section preserves its material exactly."()
    {
        given: 'Splitting a solid rock voxel must keep it solid rock.'
            var rock = WorldSection.leaf(cube(0, 8), WorldSectionEtherData.of(Material.ROCK))
        when:
            var aggregated = rock.subdivide().aggregated()
        then:
            Math.abs(aggregated.ether().fractionOf(Material.ROCK) - 1.0) < 1e-12
    }

    def "Aggregation recurses through multiple levels."()
    {
        given: 'Two levels deep, with one unit cell set to rock.'
            var root = WorldSection.empty(cube(0, 64)).subdivide()
            var level1 = root.children().section(0).subdivide()
            var rockLeaf = level1.children().section(0).withEther(WorldSectionEtherData.of(Material.ROCK))
            var level1WithRock = level1.withChildren(level1.children().withSection(0, rockLeaf))
            var rootWithLevels = root.withChildren(root.children().withSection(0, level1WithRock))
        when:
            var aggregated = rootWithLevels.aggregated()
        then: 'The single rock cell is 1 of 512*512 leaves under the root.'
            Math.abs(aggregated.ether().fractionOf(Material.ROCK) - 1.0 / (512.0 * 512.0)) < 1e-18
    }
}