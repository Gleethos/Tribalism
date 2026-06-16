package maps

import app.engine.primitives.BoundsF64
import app.engine.primitives.VecF64
import app.maps.MapWorlds
import app.models.GameMapModel
import dal.api.DataBase
import groovy.transform.CompileDynamic
import spock.lang.Narrative
import spock.lang.Specification
import spock.lang.Title

@Title("GameMapModel to world-engine World bridge")
@Narrative('''

    A `GameMapModel` stores only the deterministic recipe for its terrain (a seed plus generation
    parameters). `MapWorlds` reconstructs the live engine `World` from that recipe. This pins
    the seam: the generator carries the map's parameters, a world can be built from a map, and
    terrain is a pure function of the seed (so two maps with the same seed generate identical
    terrain, and different seeds differ).

''')
@CompileDynamic
class MapWorlds_Spec extends Specification
{
    def TEST_DB_FILE = "test_data/mapworlds_spec.db"

    def setup() {
        // Pre-create the db file so Topsoil does not mkdirs() a non-existent ".db" path.
        def f = new File(TEST_DB_FILE)
        if ( f.isDirectory() ) f.deleteDir()
        f.parentFile?.mkdirs()
        if ( !f.exists() ) f.createNewFile()
        def db = DataBase.at(TEST_DB_FILE)
        db.dropAllTables()
        db.close()
    }

    private GameMapModel newMap( DataBase db, long seed, Double chunkSize = null, Double genDist = null ) {
        var map = db.create(GameMapModel)
        map.state().set(app.models.GameMap.empty())
        map.name().set("Test map")
        map.seed().set(seed)
        if ( chunkSize != null ) map.chunkSize().set(chunkSize)
        if ( genDist != null ) map.generationDistance().set(genDist)
        return map
    }

    def 'The generator reflects the parameters stored on the map.'() {
        given : 'A database with a GameMapModel carrying explicit generation parameters.'
            var db = DataBase.at(TEST_DB_FILE)
            db.dropAllTables()
            db.createTablesFor(GameMapModel, app.models.GameMap)
            var map = newMap(db, 1234L, 32.0d, 128.0d)
        when : 'We build the generator from the map.'
            var generator = MapWorlds.generatorOf(map)
        then : 'It carries the map chunk size and generation distance.'
            generator.chunkSize() == 32.0d
            generator.generationDistance() == 128.0d
        cleanup :
            db?.close()
    }

    def 'A world can be built from a map.'() {
        given : 'A database with a simple GameMapModel.'
            var db = DataBase.at(TEST_DB_FILE)
            db.dropAllTables()
            db.createTablesFor(GameMapModel, app.models.GameMap)
            var map = newMap(db, 42L)
        expect : 'Building a world from it yields a usable, non-null world.'
            MapWorlds.worldOf(map) != null
        cleanup :
            db?.close()
    }

    def 'Terrain is a pure function of the seed: same seed agrees, different seeds differ.'() {
        given : 'A database with three maps: two share a seed, one differs.'
            var db = DataBase.at(TEST_DB_FILE)
            db.dropAllTables()
            db.createTablesFor(GameMapModel, app.models.GameMap)
            var mapA  = newMap(db, 7L)
            var mapA2 = newMap(db, 7L)
            var mapB  = newMap(db, 8L)
        and : 'A region of the world to generate.'
            var region = BoundsF64.cube(VecF64.zero(), 64.0d)
        when : 'We generate that region from each map generator.'
            var sectorA  = MapWorlds.generatorOf(mapA).generate(region)
            var sectorA2 = MapWorlds.generatorOf(mapA2).generate(region)
            var sectorB  = MapWorlds.generatorOf(mapB).generate(region)
        then : 'Same seed produces structurally identical terrain (engine value semantics).'
            sectorA == sectorA2
        and : 'A different seed produces different terrain.'
            sectorA != sectorB
        cleanup :
            db?.close()
    }
}
