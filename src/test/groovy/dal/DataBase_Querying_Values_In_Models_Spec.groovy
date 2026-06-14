package dal

import dal.api.DataBase
import dal.models.AccountModel
import dal.models.ProductModel
import dal.values.Address
import dal.values.Product
import dal.values.User
import groovy.transform.CompileDynamic
import spock.lang.Narrative
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Title

import java.time.LocalDateTime

@Title("Querying Values stored in Models")
@Narrative('''

       A Topsoil `Model` does not have to expose its fields as a flat list of
       primitive properties. Instead it may hold a single immutable **value**
       record and then expose lens-like *zoom* properties into that value.
       This is the most powerful way to use Topsoil, because it lets us combine
       the convenience of value-oriented programming (immutability, structural
       equality, no place-oriented bugs) with the queryability of a relational
       database.

       The model under test here is the following:
       ```java
            public interface ProductModel extends Model<ProductModel> {
                Var<Product> state();
                default Var<String> name()              { return state().zoomTo(Product::name,         Product::withName); }
                default Var<String> description()       { return state().zoomTo(Product::description,  Product::withDescription); }
                default Var<LocalDateTime> createDate() { return state().zoomTo(Product::creationDate, Product::withCreationDate); }
                default Var<Double> price()             { return state().zoomTo(Product::price,        Product::withPrice); }
            }
       ```
       ...and it wraps the following value record:
       ```java
            public record Product(
                String name,
                String description,
                LocalDateTime creationDate,
                double price
            ) implements Value { ... }
       ```

       Notice that `name()`, `description()`, `createDate()` and `price()` are
       not stored as their own columns; they are *windows* into the single
       `Product` value held by `state()`. The whole point of this specification
       is to pin down the expectation that we can nevertheless query a model
       **through these zoom lenses**, exactly as if they were ordinary columns:
       ```java
            db.select(ProductModel.class)
              .where(ProductModel::name).is("bicycle")
              .exists()
       ```
       The query builder should transparently resolve a zoom property back to
       the underlying value field and translate the comparison into SQL against
       the value table.

       This is a *behaviour specification first*: it describes how querying
       value-backed models is supposed to work. If the implementation does not
       yet support this, the failing assertions are a to-do list, not a defect
       in the spec.

''')
@Subject([ProductModel, Product])
@CompileDynamic
class DataBase_Querying_Values_In_Models_Spec extends Specification
{
    def TEST_DB_LOCATION = "test_data/"
    def TEST_DB_FILE = TEST_DB_LOCATION + "my.db"

    /**
     *  A fixed clock-ish set of timestamps so that ordering and range
     *  queries over `createDate` are deterministic and easy to read.
     */
    static final LocalDateTime DAY1 = LocalDateTime.of(2024, 1, 1, 9, 0, 0)
    static final LocalDateTime DAY2 = LocalDateTime.of(2024, 1, 2, 9, 0, 0)
    static final LocalDateTime DAY3 = LocalDateTime.of(2024, 1, 3, 9, 0, 0)
    static final LocalDateTime DAY4 = LocalDateTime.of(2024, 1, 4, 9, 0, 0)

    def setup() {
        def db = DataBase.at(TEST_DB_FILE)
        db.dropAllTables()
        db.close()
    }

    /**
     *  Opens a fresh database, (re)creates the tables for the value-backed
     *  product model and stocks a little shop with four products. Returns the
     *  ready-to-query database. We keep the products well-spread over price and
     *  creation date so every comparison operator has something to bite on.
     */
    private DataBase stockedShop() {
        def db = DataBase.at(TEST_DB_FILE)
        db.dropAllTables()
        db.createTablesFor(ProductModel, Product)

        db.create(ProductModel).state().set(new Product("bicycle",  "A sturdy city bicycle.",      DAY1,  299.0))
        db.create(ProductModel).state().set(new Product("car",      "A small electric car.",       DAY2, 19999.0))
        db.create(ProductModel).state().set(new Product("scooter",  "A foldable kick scooter.",    DAY3,    89.0))
        db.create(ProductModel).state().set(new Product("skateboard","A maple wood skateboard.",   DAY4,    59.0))
        return db
    }

    def 'The canonical existence check: we can ask whether a product with a given name exists.'()
    {
        reportInfo """
            This is the headline scenario. A zoom property (`ProductModel::name`)
            reaches into the wrapped `Product` value, and yet we can use it in a
            `where(...)` clause as the field to test, just like a flat column.

            `exists()` returns `true` as soon as at least one row matches and
            `notExists()` is its exact negation.
        """
        given : 'A shop stocked with a handful of products.'
            def db = stockedShop()

        expect : 'A product that we stocked is reported as existing.'
            db.select(ProductModel).where(ProductModel::name).is("bicycle").exists()
        and : 'A product that we never stocked is reported as not existing.'
            db.select(ProductModel).where(ProductModel::name).is("helicopter").notExists()
        and : '`exists()` and `notExists()` are always exact opposites of each other.'
            db.select(ProductModel).where(ProductModel::name).is("car").exists()
            !db.select(ProductModel).where(ProductModel::name).is("car").notExists()

        cleanup:
            db.close()
    }

    def 'We can select a value-backed model by an exact match on a zoomed string field.'()
    {
        reportInfo """
            The `is(..)` comparison performs an exact equality test. Selecting by
            the zoomed `name` field must return the model whose underlying
            `Product` value carries that name, fully reconstructed.
        """
        given : 'A stocked shop.'
            def db = stockedShop()

        when : 'We select the product named "scooter".'
            var hits = db.select(ProductModel).where(ProductModel::name).is("scooter").asList()
        then : 'Exactly one model comes back.'
            hits.size() == 1
        and : 'Its wrapped value is intact, all the way down to price and date.'
            hits[0].state().get() == new Product("scooter", "A foldable kick scooter.", DAY3, 89.0)
        and : 'And the individual zoom lenses read the expected components.'
            hits[0].name().get() == "scooter"
            hits[0].price().get() == 89.0
            hits[0].createDate().get() == DAY3

        cleanup:
            db.close()
    }

    def 'The `isNot` comparison excludes a single value and returns everything else.'()
    {
        given : 'A stocked shop.'
            def db = stockedShop()

        when : 'We select every product that is not the car.'
            var names = db.select(ProductModel)
                            .where(ProductModel::name).isNot("car")
                            .asList()
                            .collect { it.name().get() } as Set
        then : 'We get the three non-car products.'
            names == ["bicycle", "scooter", "skateboard"] as Set

        cleanup:
            db.close()
    }

    def 'We can use `like` and `notLike` with wildcards on a zoomed string field.'()
    {
        reportInfo """
            `like(..)` matches a SQL pattern where `%` stands for any sequence of
            characters. Because `description` is a zoom into the value, the
            pattern is matched against the value table column behind it.
        """
        given : 'A stocked shop.'
            def db = stockedShop()

        when : 'We look for products whose description mentions being electric.'
            var electric = db.select(ProductModel)
                                .where(ProductModel::description).like("%electric%")
                                .asList()
        then : 'Only the car matches.'
            electric.size() == 1
            electric[0].name().get() == "car"

        when : 'We look for products whose name starts with an "s".'
            var sProducts = db.select(ProductModel)
                                .where(ProductModel::name).like("s%")
                                .asList()
                                .collect { it.name().get() } as Set
        then : 'The scooter and the skateboard match.'
            sProducts == ["scooter", "skateboard"] as Set

        when : 'We invert the previous pattern with `notLike`.'
            var notS = db.select(ProductModel)
                            .where(ProductModel::name).notLike("s%")
                            .asList()
                            .collect { it.name().get() } as Set
        then : 'We get exactly the products that do not start with "s".'
            notS == ["bicycle", "car"] as Set

        cleanup:
            db.close()
    }

    def 'Numeric comparisons work on a zoomed `double` price field.'()
    {
        reportInfo """
            The numeric operators (`greaterThan`, `greaterThanOrEqual`,
            `lessThan`, `lessThanOrEqual`) must work against the zoomed `price`
            field just as they would against a flat numeric column.
        """
        given : 'A stocked shop.'
            def db = stockedShop()

        expect : 'Products strictly cheaper than 100 are the scooter and skateboard.'
            db.select(ProductModel).where(ProductModel::price).lessThan(100.0)
              .asList().collect { it.name().get() } as Set == ["scooter", "skateboard"] as Set
        and : 'Products at least 299 are the bicycle and the car.'
            db.select(ProductModel).where(ProductModel::price).greaterThanOrEqual(299.0)
              .asList().collect { it.name().get() } as Set == ["bicycle", "car"] as Set
        and : 'Exactly one product costs more than 1000.'
            db.select(ProductModel).where(ProductModel::price).greaterThan(1000.0).count() == 1
        and : 'And no product is cheaper than 10.'
            db.select(ProductModel).where(ProductModel::price).lessThan(10.0).notExists()

        cleanup:
            db.close()
    }

    def 'We can combine zoomed fields with `and` to express a price range.'()
    {
        reportInfo """
            A `Junction` lets us chain comparisons. Here we express a
            "between" range over the zoomed price by combining a lower and an
            upper bound with `and`, mixing two different zoom lenses is allowed
            in the same query, too.
        """
        given : 'A stocked shop.'
            def db = stockedShop()

        when : 'We select products priced between 50 and 300 (inclusive).'
            var midRange = db.select(ProductModel)
                                .where(ProductModel::price).greaterThanOrEqual(50.0)
                                .and(ProductModel::price).lessThanOrEqual(300.0)
                                .asList()
                                .collect { it.name().get() } as Set
        then : 'The bicycle, scooter and skateboard fall in that range, but not the car.'
            midRange == ["bicycle", "scooter", "skateboard"] as Set

        when : 'We combine a name pattern with a price bound.'
            var cheapS = db.select(ProductModel)
                            .where(ProductModel::name).like("s%")
                            .and(ProductModel::price).lessThan(70.0)
                            .asList()
        then : 'Only the skateboard satisfies both conditions.'
            cheapS.size() == 1
            cheapS[0].name().get() == "skateboard"

        cleanup:
            db.close()
    }

    def 'We can combine zoomed fields with `or` to widen a query.'()
    {
        given : 'A stocked shop.'
            def db = stockedShop()

        when : 'We select products that are either named "car" or cost less than 90.'
            var result = db.select(ProductModel)
                            .where(ProductModel::name).is("car")
                            .or(ProductModel::price).lessThan(90.0)
                            .asList()
                            .collect { it.name().get() } as Set
        then : 'We get the car (by name) plus the scooter and skateboard (by price).'
            result == ["car", "scooter", "skateboard"] as Set

        cleanup:
            db.close()
    }

    def 'The `in` and `notIn` operators match a zoomed field against a set of candidates.'()
    {
        reportInfo """
            `in(..)` matches when the zoomed field equals any of the supplied
            candidates; `notIn(..)` is its complement.
        """
        given : 'A stocked shop.'
            def db = stockedShop()

        expect : 'Selecting by a set of names returns exactly the matching products.'
            db.select(ProductModel).where(ProductModel::name).in("bicycle", "scooter")
              .asList().collect { it.name().get() } as Set == ["bicycle", "scooter"] as Set
        and : 'The complement excludes that very set.'
            db.select(ProductModel).where(ProductModel::name).notIn("bicycle", "scooter")
              .asList().collect { it.name().get() } as Set == ["car", "skateboard"] as Set
        and : 'A candidate list with no overlap yields nothing.'
            db.select(ProductModel).where(ProductModel::name).in("truck", "boat").notExists()

        cleanup:
            db.close()
    }

    def 'We can order the result ascending and descending by a zoomed numeric field.'()
    {
        reportInfo """
            `orderAscendingBy` and `orderDescendingBy` accept a numeric zoom
            selector. Sorting must happen on the underlying value column, so the
            returned models come back in price order.
        """
        given : 'A stocked shop.'
            def db = stockedShop()

        when : 'We order every product by ascending price.'
            var cheapestFirst = db.select(ProductModel)
                                    .where(ProductModel::price).greaterThanOrEqual(0.0)
                                    .orderAscendingBy(ProductModel::price)
                                    .asList()
                                    .collect { it.name().get() }
        then : 'They come back from the cheapest to the most expensive.'
            cheapestFirst == ["skateboard", "scooter", "bicycle", "car"]

        when : 'We order every product by descending price instead.'
            var dearestFirst = db.select(ProductModel)
                                    .where(ProductModel::price).greaterThanOrEqual(0.0)
                                    .orderDescendingBy(ProductModel::price)
                                    .asList()
                                    .collect { it.name().get() }
        then : 'They come back from the most expensive to the cheapest.'
            dearestFirst == ["car", "bicycle", "scooter", "skateboard"]

        cleanup:
            db.close()
    }

    def 'We can query a value-backed model by a zoomed `LocalDateTime` field.'()
    {
        reportInfo """
            Not every value field is a string or a number. The `creationDate`
            field is a `LocalDateTime`, and we expect range comparisons over it
            to work through the zoom lens just the same.
        """
        given : 'A stocked shop.'
            def db = stockedShop()

        expect : 'We can match an exact creation date.'
            db.select(ProductModel).where(ProductModel::createDate).is(DAY1)
              .asList().collect { it.name().get() } == ["bicycle"]
        and : 'We can ask for everything created on or after the third day.'
            db.select(ProductModel).where(ProductModel::createDate).greaterThanOrEqual(DAY3)
              .asList().collect { it.name().get() } as Set == ["scooter", "skateboard"] as Set
        and : 'And everything created strictly before the second day.'
            db.select(ProductModel).where(ProductModel::createDate).lessThan(DAY2)
              .asList().collect { it.name().get() } == ["bicycle"]

        cleanup:
            db.close()
    }

    def 'The result-shaping methods `count`, `first`, `last`, `asSet` and `limit` all work on value-backed queries.'()
    {
        reportInfo """
            The terminal operations of the `Query` interface are independent of
            whether the queried fields are flat columns or zoom lenses. Here we
            exercise the most common ones against a value-backed model.
        """
        given : 'A stocked shop.'
            def db = stockedShop()

        expect : 'There are four products in total.'
            db.select(ProductModel).where(ProductModel::price).greaterThanOrEqual(0.0).count() == 4
        and : 'Ordered by price, the first is the skateboard and the last is the car.'
            db.select(ProductModel).where(ProductModel::price).greaterThanOrEqual(0.0)
              .orderAscendingBy(ProductModel::price).first().get().name().get() == "skateboard"
            db.select(ProductModel).where(ProductModel::price).greaterThanOrEqual(0.0)
              .orderAscendingBy(ProductModel::price).last().get().name().get() == "car"
        and : '`asSet` returns all distinct matches.'
            db.select(ProductModel).where(ProductModel::price).lessThan(100.0).asSet().size() == 2
        and : '`limit` truncates an ordered result to the requested size.'
            db.select(ProductModel).where(ProductModel::price).greaterThanOrEqual(0.0)
              .orderAscendingBy(ProductModel::price).limit(2).collect { it.name().get() } == ["skateboard", "scooter"]

        cleanup:
            db.close()
    }

    def 'The `expectOne` and `expectOneOrNone` helpers enforce result cardinality.'()
    {
        reportInfo """
            `expectOne()` is the right tool when exactly one row must match, and
            `expectOneOrNone()` for the at-most-one case. Both throw if the query
            yields more rows than expected, which protects callers from silently
            picking an arbitrary row.
        """
        given : 'A stocked shop.'
            def db = stockedShop()

        expect : 'A unique-name query returns the single expected model.'
            db.select(ProductModel).where(ProductModel::name).is("bicycle")
              .expectOne().name().get() == "bicycle"
        and : 'A no-match query under `expectOneOrNone` yields an empty Optional.'
            !db.select(ProductModel).where(ProductModel::name).is("nope").expectOneOrNone().isPresent()

        when : 'We demand exactly one result from a query that matches several.'
            db.select(ProductModel).where(ProductModel::price).greaterThanOrEqual(0.0).expectOne()
        then : 'It refuses to guess and throws instead.'
            thrown(IllegalStateException)

        when : 'We demand at-most-one from a query that matches several.'
            db.select(ProductModel).where(ProductModel::price).greaterThanOrEqual(0.0).expectOneOrNone()
        then : 'It throws as well.'
            thrown(IllegalStateException)

        cleanup:
            db.close()
    }

    /**
     *  Opens a fresh database and stocks a few accounts whose state is a {@link User} value that
     *  itself nests an {@link Address} value. This is the fixture for the multi-level zoom scenarios.
     */
    private DataBase accountBook() {
        def db = DataBase.at(TEST_DB_FILE)
        db.dropAllTables()
        db.createTablesFor(AccountModel, User, Address)

        db.create(AccountModel).user().set(new User("alice", new Address("1 Main St",  "10001", "New York")))
        db.create(AccountModel).user().set(new User("bob",   new Address("2 Oak Ave",  "90001", "Los Angeles")))
        db.create(AccountModel).user().set(new User("carol", new Address("3 Pine Rd",  "10001", "Yonkers")))
        return db
    }

    def 'A two-level zoom lens queries a Value nested inside another Value.'()
    {
        reportInfo """
            This is the headline recursive case. `AccountModel::postalCode` is a zoom lens that
            reaches *two* values deep:
            ```java
                default Var<String> postalCode() {
                    return user().zoomTo(User::address,    User::withAddress)
                                 .zoomTo(Address::postalCode, Address::withPostalCode);
                }
            ```
            The query API resolves the whole chain `user -> address -> postalCode` and translates
            it into nested `IN (SELECT id ...)` sub-queries against the `User` and `Address` value
            tables, so we can filter accounts by a field buried two values down.
        """
        given : 'An account book with three users in two postal codes.'
            def db = accountBook()

        when : 'We select every account in postal code "10001".'
            var names = db.select(AccountModel)
                            .where(AccountModel::postalCode).is("10001")
                            .asList()
                            .collect { it.username().get() } as Set
        then : 'Alice and Carol match; Bob (90001) does not.'
            names == ["alice", "carol"] as Set

        and : 'A single-level zoom into the same User value still works alongside the deep one.'
            db.select(AccountModel).where(AccountModel::username).is("bob")
              .expectOne().postalCode().get() == "90001"
        and : 'And a postal code nobody lives in matches nothing.'
            db.select(AccountModel).where(AccountModel::postalCode).is("00000").notExists()

        cleanup:
            db.close()
    }

    def 'Multi-level zoom lenses compose with `and` across different depths.'()
    {
        reportInfo """
            Two zoom lenses of different depths can be combined in one query: here a two-level
            `postalCode` and a two-level `city`, plus a one-level `username`. Each becomes its own
            nested sub-query, all `AND`-ed together against the same account row.
        """
        given : 'An account book.'
            def db = accountBook()

        expect : 'Filtering by postal code AND city pins down the single matching account.'
            db.select(AccountModel)
              .where(AccountModel::postalCode).is("10001")
              .and(AccountModel::city).is("Yonkers")
              .expectOne().username().get() == "carol"
        and : 'Combining a deep zoom with a shallow one works too.'
            db.select(AccountModel)
              .where(AccountModel::city).like("Los%")
              .and(AccountModel::username).is("bob")
              .count() == 1
        and : 'A contradictory combination matches nothing.'
            db.select(AccountModel)
              .where(AccountModel::postalCode).is("10001")
              .and(AccountModel::city).is("Los Angeles")
              .notExists()

        cleanup:
            db.close()
    }

    def 'A query can drive a bulk delete of value-backed models, and the value rows are released.'()
    {
        reportInfo """
            `db.delete(Query)` runs the query and deletes every matching model.
            Because each `ProductModel` owns a `Product` value, deleting the
            models must also clean up the now-orphaned rows in the value table.
        """
        given : 'A stocked shop.'
            def db = stockedShop()

        expect : 'We start with four products.'
            db.selectAll(ProductModel).size() == 4

        when : 'We delete every product cheaper than 100.'
            db.delete(db.select(ProductModel).where(ProductModel::price).lessThan(100.0))
        then : 'Only the bicycle and the car remain.'
            db.selectAll(ProductModel).collect { it.name().get() } as Set == ["bicycle", "car"] as Set
        and : 'And the deleted products can no longer be found.'
            db.select(ProductModel).where(ProductModel::name).is("scooter").notExists()
            db.select(ProductModel).where(ProductModel::name).is("skateboard").notExists()

        cleanup:
            db.close()
    }
}
