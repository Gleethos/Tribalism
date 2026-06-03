package engine.world

import app.engine.primitives.BoundsF64
import app.engine.primitives.CameraF64
import app.engine.primitives.VecF64
import app.engine.world.Entity
import app.engine.world.WorldSector
import app.engine.world.WorldSectorEtherData
import app.engine.world.Material
import spock.lang.Narrative
import spock.lang.Specification
import spock.lang.Title

@Title("Entity - the world's actors as a sum type")
@Narrative('''

    Every entity carries a WorldTreeEntityId (a long id plus bounds) and adds its
    own payload. A camera entity wraps a viewpoint; a voxel entity is itself a
    little world described by a nested WorldSector.

''')
class Entity_Spec extends Specification
{
    def "A camera entity exposes its id and a bounds around the camera position."()
    {
        given:
            var camera = new CameraF64(VecF64.of(5, 6, 7), VecF64.of(0, 0, 0), VecF64.of(0, 1, 0),
                                       Math.toRadians(60), 1.5, 0.1, 100)
        when:
            var entity = Entity.CameraEntity.of(1L, camera)
        then:
            entity.id() == 1L
            entity.camera() == camera
            entity.bounds().contains(camera.position())
    }

    def "A voxel entity takes its bounds from its nested sector."()
    {
        given:
            var bounds = BoundsF64.of(VecF64.of(0, 0, 0), VecF64.of(2, 2, 2))
            var sector = WorldSector.leaf(bounds, WorldSectorEtherData.of(Material.ROCK))
        when:
            var entity = Entity.VoxelEntity.of(9L, sector)
        then:
            entity.id() == 9L
            entity.bounds() == bounds
            entity.sector() == sector
    }

    def "Entities can be matched exhaustively as a sum type."()
    {
        given:
            Entity entity = Entity.VoxelEntity.of(3L, WorldSector.empty(BoundsF64.cube(VecF64.zero(), 2)))
        when:
            var kind = switch (entity) {
                case Entity.CameraEntity -> "camera"
                case Entity.VoxelEntity  -> "voxel"
                default                  -> "unknown"
            }
        then:
            kind == "voxel"
    }
}