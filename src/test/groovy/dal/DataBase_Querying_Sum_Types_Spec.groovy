package dal

import dal.api.DataBase
import dal.impl.SQLiteDataBase
import dal.models.CanvasModel
import dal.models.DrawingModel
import dal.values.Circle
import dal.values.Rectangle
import dal.values.Shape
import dal.values.Triangle
import groovy.transform.CompileDynamic
import spock.lang.Narrative
import spock.lang.Subject
import spock.lang.Title

import spock.lang.Specification

@Title("Sum types (sealed value hierarchies) in Topsoil")
@Narrative('''

       Besides plain record values, Topsoil supports **sum types**: a sealed interface that
       `extends Value`, whose permitted subtypes are the record "tips". This lets a single field hold
       any one of a closed set of shapes, the way an algebraic data type would.

       We model a tiny vector-drawing app:
       ```java
            public sealed interface Shape extends Value permits Circle, Rectangle, Triangle {}
            public record Circle(double radius)                 implements Shape {}
            public record Rectangle(double width, double height) implements Shape {}
            public record Triangle(double base, double height)   implements Shape {}

            public interface DrawingModel extends Model<DrawingModel> {
                Var<String> label();
                Var<Shape>  shape();   // <- a polymorphic sum-typed field
            }
       ```

       Storage is polymorphic but encapsulated: the sealed type gets a single **union table** with a
       `type` discriminator column plus one nullable foreign key per record tip. A `Var<Shape>` field
       is therefore just an ordinary foreign key to that union table — values are deduplicated and
       reference-counted exactly like any other value.

       Querying uses `isOfType(SubType.class)` to filter/narrow by the concrete type, and the usual
       `is(..)`/`isNot(..)` to match a whole polymorphic value. Misuse is reported with clear errors.

''')
@Subject([Shape, DrawingModel])
@CompileDynamic
class DataBase_Querying_Sum_Types_Spec extends Specification
{
    def TEST_DB_FILE = "test_data/my.db"

    def setup() {
        def db = DataBase.at(TEST_DB_FILE); db.dropAllTables(); db.close()
    }

    private DataBase gallery() {
        def db = DataBase.at(TEST_DB_FILE)
        db.dropAllTables()
        db.createTablesFor(DrawingModel, Shape, Circle, Rectangle, Triangle)

        db.create(DrawingModel).with { it.label().set("sun");   it.shape().set(new Circle(5.0));         it }
        db.create(DrawingModel).with { it.label().set("moon");  it.shape().set(new Circle(2.0));         it }
        db.create(DrawingModel).with { it.label().set("door");  it.shape().set(new Rectangle(2.0, 3.0)); it }
        db.create(DrawingModel).with { it.label().set("roof");  it.shape().set(new Triangle(4.0, 6.0));  it }
        return db
    }

    private static Set<String> labels(List<DrawingModel> ds) { ds.collect { it.label().get() } as Set }

    def 'A sealed value type is stored in a discriminated union table.'()
    {
        reportInfo """
            The sealed `Shape` interface gets its own table carrying a `type` discriminator and one
            nullable foreign key per record tip. Each concrete tip keeps its own value table.
        """
        given : 'A database with the shape hierarchy registered.'
            def db = DataBase.at(TEST_DB_FILE)
            db.dropAllTables()
            db.createTablesFor(DrawingModel, Shape, Circle, Rectangle, Triangle)

        expect : 'The union table exists with a discriminator and a FK column per subtype.'
            def shapeSql = db.sqlCodeOfTable(Shape)
            shapeSql.contains("type TEXT NOT NULL")
            shapeSql.contains("fk_Circle_id INTEGER REFERENCES dal_values_Circle_table(id)")
            shapeSql.contains("fk_Rectangle_id INTEGER REFERENCES dal_values_Rectangle_table(id)")
            shapeSql.contains("fk_Triangle_id INTEGER REFERENCES dal_values_Triangle_table(id)")
        and : 'And each record tip has its own value table.'
            db.listOfAllTableNames().containsAll([
                "dal_values_Shape_table", "dal_values_Circle_table",
                "dal_values_Rectangle_table", "dal_values_Triangle_table"
            ])

        cleanup:
            db.close()
    }

    def 'Each subtype round-trips through a polymorphic field as its concrete type.'()
    {
        given : 'A gallery of drawings.'
            def db = gallery()

        expect : 'Every drawing reads its shape back as the exact concrete subtype it was stored as.'
            db.select(DrawingModel).where(DrawingModel::label).is("sun").expectOne().shape().get() == new Circle(5.0)
            db.select(DrawingModel).where(DrawingModel::label).is("door").expectOne().shape().get() == new Rectangle(2.0, 3.0)
            db.select(DrawingModel).where(DrawingModel::label).is("roof").expectOne().shape().get() == new Triangle(4.0, 6.0)
        and : 'The runtime type is the concrete record, not the sealed interface.'
            db.select(DrawingModel).where(DrawingModel::label).is("sun").expectOne().shape().get() instanceof Circle

        cleanup:
            db.close()
    }

    def 'isOfType filters a query down to one concrete subtype.'()
    {
        reportInfo """
            `where(DrawingModel::shape).isOfType(Circle.class)` keeps only the drawings whose shape is
            a circle. The result is also a terminal query, so the type filter can stand on its own.
        """
        given : 'A gallery of drawings.'
            def db = gallery()

        expect : 'Only the two circles match.'
            labels(db.select(DrawingModel).where(DrawingModel::shape).isOfType(Circle.class).asList()) == ["sun", "moon"] as Set
        and : 'Exactly one rectangle and one triangle exist.'
            db.select(DrawingModel).where(DrawingModel::shape).isOfType(Rectangle.class).count() == 1
            db.select(DrawingModel).where(DrawingModel::shape).isOfType(Triangle.class).exists()

        cleanup:
            db.close()
    }

    def 'isOfType narrows the type so a following `is`/`isNot` matches the whole value.'()
    {
        reportInfo """
            After narrowing to `Circle`, `is(new Circle(5.0))` matches drawings whose shape is exactly
            that circle. Because the narrowed `is(..)` returns a normal junction, it can be combined
            further with `and`/`or`.
        """
        given : 'A gallery of drawings.'
            def db = gallery()

        expect : 'Narrowed whole-value match finds the one circle of radius 5.'
            db.select(DrawingModel).where(DrawingModel::shape).isOfType(Circle.class).is(new Circle(5.0))
              .expectOne().label().get() == "sun"
        and : 'Narrowed `isNot` keeps the other circle.'
            labels(db.select(DrawingModel).where(DrawingModel::shape).isOfType(Circle.class).isNot(new Circle(5.0)).asList()) == ["moon"] as Set
        and : 'A circle nobody drew matches nothing.'
            db.select(DrawingModel).where(DrawingModel::shape).isOfType(Circle.class).is(new Circle(99.0)).notExists()

        cleanup:
            db.close()
    }

    def 'A whole polymorphic value can be matched directly with `is`/`isNot`.'()
    {
        reportInfo """
            Without narrowing, `where(DrawingModel::shape).is(value)` matches the polymorphic field
            against a concrete value of any subtype (by resolving its union row).
        """
        given : 'A gallery of drawings.'
            def db = gallery()

        expect : 'Direct whole-value match across subtypes.'
            db.select(DrawingModel).where(DrawingModel::shape).is(new Rectangle(2.0, 3.0)).expectOne().label().get() == "door"
            db.select(DrawingModel).where(DrawingModel::shape).is(new Triangle(4.0, 6.0)).expectOne().label().get() == "roof"
        and : '`isNot` excludes a specific value (across all subtypes).'
            labels(db.select(DrawingModel).where(DrawingModel::shape).isNot(new Circle(5.0)).asList()) == ["moon", "door", "roof"] as Set

        cleanup:
            db.close()
    }

    def 'A type filter composes with other conditions when placed last.'()
    {
        reportInfo """
            `isOfType` is a `Compare` step, so it can be the final condition after `and(..)`:
            "the drawing labelled 'door' whose shape is a Rectangle".
        """
        given : 'A gallery of drawings.'
            def db = gallery()

        expect : 'Combine a plain condition with a trailing type filter.'
            db.select(DrawingModel)
              .where(DrawingModel::label).is("door")
              .and(DrawingModel::shape).isOfType(Rectangle.class)
              .exists()
        and : 'A contradictory combination matches nothing.'
            db.select(DrawingModel)
              .where(DrawingModel::label).is("door")
              .and(DrawingModel::shape).isOfType(Circle.class)
              .notExists()

        cleanup:
            db.close()
    }

    def 'Equal sum values are deduplicated and reference-counted; cleanup releases them.'()
    {
        reportInfo """
            Two drawings that share the same shape share a single union row (and a single tip row).
            Deleting the last user releases both.
        """
        given : 'Two drawings sharing the exact same circle, plus one rectangle.'
            def db = DataBase.at(TEST_DB_FILE)
            db.dropAllTables()
            db.createTablesFor(DrawingModel, Shape, Circle, Rectangle, Triangle)
            def asSql = db as SQLiteDataBase
            db.create(DrawingModel).with { it.label().set("a"); it.shape().set(new Circle(5.0));         it }
            db.create(DrawingModel).with { it.label().set("b"); it.shape().set(new Circle(5.0));         it }
            db.create(DrawingModel).with { it.label().set("c"); it.shape().set(new Rectangle(2.0, 3.0)); it }

        expect : 'The two equal circles share one Circle row and one union row, with usage 2.'
            asSql.query("SELECT id FROM dal_values_Circle_table").get("id").size() == 1
            asSql.query("SELECT usages FROM dal_values_Shape_table WHERE " +
                        "type = 'dal.values.Circle'").get("usages") == ["2"]

        when : 'We delete both circle drawings.'
            db.delete(db.select(DrawingModel).where(DrawingModel::shape).isOfType(Circle.class))
        then : 'The shared Circle row and its union row are gone, the rectangle remains.'
            (asSql.query("SELECT id FROM dal_values_Circle_table").get("id") ?: []).size() == 0
            (asSql.query("SELECT id FROM dal_values_Shape_table WHERE type = 'dal.values.Circle'").get("id") ?: []).size() == 0
            asSql.query("SELECT id FROM dal_values_Rectangle_table").get("id").size() == 1

        cleanup:
            db.close()
    }

    def 'A list (Vars) of polymorphic shapes round-trips in order.'()
    {
        reportInfo """
            Sum types work inside collections too: a `Vars<Shape>` palette stores a mixed list of
            polymorphic shapes, each resolved back to its concrete subtype in order.
        """
        given : 'A canvas with a mixed palette of shapes.'
            def db = DataBase.at(TEST_DB_FILE)
            db.dropAllTables()
            db.createTablesFor(CanvasModel, Shape, Circle, Rectangle, Triangle)
            def canvas = db.create(CanvasModel)
            canvas.title().set("scene")
            canvas.shapes().set(sprouts.Tuple.of(Shape, new Circle(1.0), new Rectangle(2.0, 2.0), new Triangle(3.0, 3.0), new Circle(1.0)))

        when : 'We reload the canvas from a fresh proxy.'
            def reloaded = db.select(CanvasModel, canvas.id().get())
        then : 'The mixed, ordered list of concrete shapes comes back intact (duplicates included).'
            reloaded.shapes().get() == sprouts.Tuple.of(Shape, new Circle(1.0), new Rectangle(2.0, 2.0), new Triangle(3.0, 3.0), new Circle(1.0))

        cleanup:
            db.close()
    }

    // ------------------------------------------------------------------------------------------
    // Misuse: descriptive, helpful errors.
    // ------------------------------------------------------------------------------------------

    def 'Narrowing to a type that is not a permitted subtype is rejected.'()
    {
        given : 'A gallery of drawings.'
            def db = gallery()

        when : 'We narrow to a class that is not one of the sealed permits.'
            db.select(DrawingModel).where(DrawingModel::shape).isOfType(String.class).asList()
        then :
            var e = thrown(IllegalArgumentException)
            e.message.contains("not a permitted subtype")

        cleanup:
            db.close()
    }

    def 'isOfType on a non-sum field is rejected.'()
    {
        given : 'A gallery of drawings.'
            def db = gallery()

        when : 'We call isOfType on the plain (non-sum) label field.'
            db.select(DrawingModel).where(DrawingModel::label).isOfType(Circle.class).asList()
        then :
            var e = thrown(IllegalArgumentException)
            e.message.contains("not a sum-type")

        cleanup:
            db.close()
    }

    def 'Using an unsupported operator after isOfType is rejected.'()
    {
        reportInfo """
            After narrowing, only whole-value `is`/`isNot` (or a terminal) make sense; ordering or
            range operators on a narrowed value are reported as misuse.
        """
        given : 'A gallery of drawings.'
            def db = gallery()

        when : 'We try a range operator after narrowing.'
            db.select(DrawingModel).where(DrawingModel::shape).isOfType(Circle.class).greaterThan(new Circle(1.0))
        then :
            var e = thrown(IllegalArgumentException)
            e.message.contains("isOfType")

        cleanup:
            db.close()
    }
}
