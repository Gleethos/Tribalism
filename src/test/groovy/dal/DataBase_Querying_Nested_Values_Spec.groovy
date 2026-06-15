package dal

import dal.api.DataBase
import dal.models.OrgModel
import dal.models.ProductModel
import dal.values.GeoPoint
import dal.values.Org
import dal.values.Place
import dal.values.Product
import groovy.transform.CompileDynamic
import spock.lang.Narrative
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Title

@Title("Querying nested Values with inline selector lambdas")
@Narrative('''

       The `where(Model::valueField).is(..)` API can query a model by a value field, but it forces
       the model author to hand-write a "zoom" delegation method for every nested field they want
       to query. That is friction.

       This specification covers the friendlier alternative: special `where`/`and`/`or` overloads
       that accept the nesting **inline**, as a root value selector followed by one or more plain
       field-accessor lambdas (method references or lambda chains). No methods need to be added to
       the model at all.

       The model under test holds a single nested value tree:
       ```java
            public interface OrgModel extends Model<OrgModel> { Var<Org> org(); }

            public record Org(String title, Place place, int rank) implements Value {}
            public record Place(String name, GeoPoint location)     implements Value {}
            public record GeoPoint(int lat, int lon)                implements Value {}
       ```
       So an `OrgModel` is `org -> {title, place -> {name, location -> {lat, lon}}, rank}`, three
       value levels deep, and we query straight into it:
       ```java
            db.select(OrgModel.class).where(OrgModel::org, Org::title).is("Globex")
            db.select(OrgModel.class).where(OrgModel::org, Org::place, Place::name).is("HQ")
            db.select(OrgModel.class).where(OrgModel::org, o -> o.place().location().lat()).is(34)
       ```

       Resolution is done by *executing* the accessor lambdas against a probe value (a sentinel tree
       with a unique marker in every field) and matching the returned marker back to its field path —
       which is robust to however the lambda was compiled. Misuse (anything other than a pure chain
       of value-field accessors) is detected and reported.

''')
@Subject([OrgModel])
@CompileDynamic
class DataBase_Querying_Nested_Values_Spec extends Specification
{
    def TEST_DB_LOCATION = "test_data/"
    def TEST_DB_FILE = TEST_DB_LOCATION + "my.db"

    def setup() {
        def db = DataBase.at(TEST_DB_FILE)
        db.dropAllTables()
        db.close()
    }

    /** Opens a fresh database and stocks three orgs across two places and three ranks. */
    private DataBase orgRegistry() {
        def db = DataBase.at(TEST_DB_FILE)
        db.dropAllTables()
        db.createTablesFor(OrgModel, Org, Place, GeoPoint)

        db.create(OrgModel).org().set(new Org("Acme",    new Place("HQ",  new GeoPoint(40, -73)),  1))
        db.create(OrgModel).org().set(new Org("Globex",  new Place("Lab", new GeoPoint(34, -118)), 2))
        db.create(OrgModel).org().set(new Org("Initech", new Place("HQ",  new GeoPoint(40, -73)),  3))
        return db
    }

    private static Set<String> titles(List<OrgModel> orgs) {
        return orgs.collect { it.org().get().title() } as Set
    }

    def 'A single method-reference reaches a field one value deep.'()
    {
        reportInfo """
            `where(OrgModel::org, Org::title)` selects the model's `org` value, then the `title`
            field inside it — no zoom method on the model required.
        """
        given : 'A registry of orgs.'
            def db = orgRegistry()

        expect : 'We can match a one-deep string field.'
            db.select(OrgModel).where(OrgModel::org, Org::title).is("Globex")
              .expectOne().org().get().rank() == 2
        and : 'And a one-deep numeric field with a range operator.'
            titles(db.select(OrgModel).where(OrgModel::org, Org::rank).greaterThan(1).asList()) == ["Globex", "Initech"] as Set
        and : 'A value nobody has matches nothing.'
            db.select(OrgModel).where(OrgModel::org, Org::title).is("Nope").notExists()

        cleanup:
            db.close()
    }

    def 'A chain of method references reaches fields two and three values deep.'()
    {
        reportInfo """
            Each extra method reference descends one more value:
            `where(OrgModel::org, Org::place, Place::name)` is two deep, and adding `GeoPoint::lat`
            makes it three deep. They become nested `IN (SELECT id ...)` sub-queries.
        """
        given : 'A registry of orgs.'
            def db = orgRegistry()

        expect : 'Two levels deep: filter by the place name.'
            titles(db.select(OrgModel).where(OrgModel::org, Org::place, Place::name).is("HQ").asList()) == ["Acme", "Initech"] as Set
        and : 'Three levels deep: filter by the latitude of the place.'
            titles(db.select(OrgModel).where(OrgModel::org, Org::place, Place::location, GeoPoint::lat).is(34).asList()) == ["Globex"] as Set
        and : 'Three levels deep with a numeric range operator.'
            db.select(OrgModel).where(OrgModel::org, Org::place, Place::location, GeoPoint::lon).lessThan(-100).count() == 1

        cleanup:
            db.close()
    }

    def 'A single lambda can express an arbitrarily deep accessor chain.'()
    {
        reportInfo """
            Instead of one method reference per level, a single lambda may navigate the whole chain:
            `o -> o.place().location().lat()`. It is resolved exactly the same way.
        """
        given : 'A registry of orgs.'
            def db = orgRegistry()

        expect : 'A two-deep lambda chain.'
            titles(db.select(OrgModel).where(OrgModel::org, { o -> o.place().name() }).is("Lab").asList()) == ["Globex"] as Set
        and : 'A three-deep lambda chain into a numeric leaf.'
            db.select(OrgModel).where(OrgModel::org, { o -> o.place().location().lon() }).is(-118)
              .expectOne().org().get().title() == "Globex"
        and : 'Mixing a method-reference root step with a deeper lambda also works.'
            titles(db.select(OrgModel).where(OrgModel::org, Org::place, { p -> p.location().lat() }).is(40).asList()) == ["Acme", "Initech"] as Set

        cleanup:
            db.close()
    }

    def 'Nested selectors compose with `and` and `or` across arbitrary depths.'()
    {
        reportInfo """
            `and`/`or` have the same nested overloads as `where`, so conditions at different depths
            combine freely.
        """
        given : 'A registry of orgs.'
            def db = orgRegistry()

        expect : 'AND across a two-deep and a one-deep field.'
            db.select(OrgModel)
              .where(OrgModel::org, Org::place, Place::name).is("HQ")
              .and(OrgModel::org, Org::rank).greaterThan(1)
              .expectOne().org().get().title() == "Initech"
        and : 'OR across two one-deep fields.'
            titles(db.select(OrgModel)
                     .where(OrgModel::org, Org::title).is("Acme")
                     .or(OrgModel::org, Org::rank).is(2)
                     .asList()) == ["Acme", "Globex"] as Set
        and : 'A deep AND-combination that nothing satisfies.'
            db.select(OrgModel)
              .where(OrgModel::org, Org::place, Place::name).is("Lab")
              .and(OrgModel::org, Org::rank).is(1)
              .notExists()

        cleanup:
            db.close()
    }

    def 'Nested selectors support the full operator vocabulary.'()
    {
        given : 'A registry of orgs.'
            def db = orgRegistry()

        expect : '`like` on a nested string.'
            titles(db.select(OrgModel).where(OrgModel::org, Org::title).like("Glo%").asList()) == ["Globex"] as Set
        and : '`in` on a nested string.'
            db.select(OrgModel).where(OrgModel::org, Org::title).in("Acme", "Initech").count() == 2
        and : '`isNot` on a nested numeric.'
            titles(db.select(OrgModel).where(OrgModel::org, Org::rank).isNot(2).asList()) == ["Acme", "Initech"] as Set
        and : '`greaterThanOrEqual` / `lessThanOrEqual` bracket a nested numeric range.'
            db.select(OrgModel)
              .where(OrgModel::org, Org::rank).greaterThanOrEqual(2)
              .and(OrgModel::org, Org::rank).lessThanOrEqual(2)
              .expectOne().org().get().title() == "Globex"

        cleanup:
            db.close()
    }

    def 'Nested selectors can drive ascending and descending ordering, at any depth.'()
    {
        reportInfo """
            `orderAscendingBy`/`orderDescendingBy` have the same inline nested overloads. The sort key
            is rendered as a correlated scalar sub-query that drills down to the leaf column, so we can
            order by a field buried several values deep — including via a deep lambda chain.
        """
        given : 'A registry of orgs.'
            def db = orgRegistry()
        and : 'A where-clause that keeps every org (so we are really just testing ordering).'
            def all = { db.select(OrgModel).where(OrgModel::org, Org::rank).greaterThanOrEqual(0) }

        expect : 'Ascending by a one-deep numeric field.'
            all().orderAscendingBy(OrgModel::org, Org::rank).asList()
               .collect { it.org().get().title() } == ["Acme", "Globex", "Initech"]
        and : 'Descending by the same field reverses the order.'
            all().orderDescendingBy(OrgModel::org, Org::rank).asList()
               .collect { it.org().get().title() } == ["Initech", "Globex", "Acme"]
        and : 'Ascending by a three-deep numeric field puts the smallest latitude first (Globex=34).'
            all().orderAscendingBy(OrgModel::org, Org::place, Place::location, GeoPoint::lat).asList()
               .collect { it.org().get().title() }.first() == "Globex"
        and : 'Ascending by a two-deep string field via a deep lambda sorts the place names.'
            all().orderAscendingBy(OrgModel::org, { o -> o.place().name() }).asList()
               .collect { it.org().get().place().name() } == ["HQ", "HQ", "Lab"]

        cleanup:
            db.close()
    }

    def 'A misused ordering selector is rejected just like a where selector.'()
    {
        given : 'A registry of orgs.'
            def db = orgRegistry()

        when : 'We try to order by a computed lambda.'
            db.select(OrgModel).where(OrgModel::org, Org::rank).greaterThanOrEqual(0)
              .orderAscendingBy(OrgModel::org, { o -> o.rank() * 2 }).asList()
        then :
            var e = thrown(IllegalArgumentException)
            e.message.contains("pure chain of value-field accessors")

        cleanup:
            db.close()
    }

    // ------------------------------------------------------------------------------------------
    // Misuse: anything that is not a pure chain of value-field accessors must be reported clearly.
    // ------------------------------------------------------------------------------------------

    def 'A navigation lambda that computes a value is rejected.'()
    {
        reportInfo """
            The probe-execution approach catches misuse: a lambda that does arithmetic (or anything
            other than plain field access) returns a value that is not a known field marker, so the
            query API refuses it instead of silently producing a wrong query.
        """
        given : 'A registry of orgs.'
            def db = orgRegistry()

        when : 'We pass a lambda that computes rather than navigates.'
            db.select(OrgModel).where(OrgModel::org, { o -> o.rank() + 1 }).is(2)
        then : 'It is rejected with a helpful message.'
            var e = thrown(IllegalArgumentException)
            e.message.contains("pure chain of value-field accessors")

        cleanup:
            db.close()
    }

    def 'A navigation lambda that returns a constant is rejected.'()
    {
        given : 'A registry of orgs.'
            def db = orgRegistry()

        when : 'We pass a lambda that ignores the value and returns a constant.'
            db.select(OrgModel).where(OrgModel::org, { o -> "constant" }).is("constant")
        then :
            thrown(IllegalArgumentException)

        cleanup:
            db.close()
    }

    def 'Navigating only to a nested Value (not a primitive leaf) is rejected.'()
    {
        reportInfo """
            A query needs a primitive column to compare against. Stopping the navigation at a value
            that is itself a `Value` (here `Org::place`) is a usage error.
        """
        given : 'A registry of orgs.'
            def db = orgRegistry()

        when : 'We stop the navigation at the Place value instead of a primitive field of it.'
            db.select(OrgModel).where(OrgModel::org, Org::place).is(null)
        then :
            var e = thrown(IllegalArgumentException)
            e.message.contains("not a primitive")

        cleanup:
            db.close()
    }

    def 'A root selector that is not a single Value-typed property is rejected.'()
    {
        reportInfo """
            The first argument must select a single `Value`-typed model property. A derived/zoom
            property (a default method) is not allowed as the root of an inline nested selector.
        """
        given : 'A database with the ProductModel value-backed table.'
            def db = DataBase.at(TEST_DB_FILE)
            db.dropAllTables()
            db.createTablesFor(ProductModel, Product)

        when : 'We use a derived zoom getter as the root.'
            db.select(ProductModel).where(ProductModel::name, { s -> s.trim() }).is("x")
        then :
            var e = thrown(IllegalArgumentException)
            e.message.contains("Value-typed")

        cleanup:
            db.close()
    }
}
