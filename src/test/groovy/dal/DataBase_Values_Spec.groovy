package dal

import dal.api.DataBase
import dal.impl.SQLiteDataBase
import dal.values.ClassRoom
import dal.values.FullName
import dal.values.Person
import dal.values.School
import groovy.transform.CompileDynamic
import spock.lang.Narrative
import spock.lang.Specification
import spock.lang.Title
import sprouts.Tuple

@Title("Topsoil Values")
@Narrative('''
       
       Topsoil is aware that place oriented programming is a large 
       source of cognitive load for developers.
       In traditional data bases, every table is a mutable place
       whose ownership is shared by all the code that interacts with it.
       This is a source of bugs and complexity.
       
       The solution to this problem is to use value objects
       instead of regular mutable objects as data base rows.
       Topsoil supports value objects in the form of records.
       
       In this feature specification we will demonstrate how to 
       create a data base with a value based model.
       Here the model and values we are going to use:
       ```java
            public interface School extends Model<School>
            {
                interface Name extends Var<String> {}
                interface Class extends Var<ClassRoom> {}
                interface Director extends Var<Person> {}
            
                Name name();
                Director director();
            
                Class classRoom1();
                Class classRoom2();
                Class classRoom3();
            }
       ```
       And here the values:
       ```java
            public record ClassRoom(
                String name,
                int grade,
                Person teacher,
                Tuple<Person> students
            ) {
                public ClassRoom {
                    if (name == null || teacher == null || students == null) {
                        throw new IllegalArgumentException("Name, teacher, and students must not be null");
                    }
                }
            }
            
            public record Person(
                FullName name,
                int age
            ) {
                public Person {
                    if (name == null) {
                        throw new IllegalArgumentException("Name must not be null");
                    }
                }
            }
            
            public record FullName(
                String firstName,
                String lastName
            ) {
                public FullName {
                    if (firstName == null || lastName == null) {
                        throw new IllegalArgumentException("First name and last name must not be null");
                    }
                }
            }
       ```
       
''')
@CompileDynamic
class DataBase_Values_Spec extends Specification
{
    def TEST_DB_LOCATION = "test_data/"
    def TEST_DB_FILE = TEST_DB_LOCATION + "my.db"

    def setup() {
        def db = DataBase.at(TEST_DB_FILE)
        db.dropAllTables()
        db.close()
    }

    def 'Topsoil can create tables for a `School` of students.'()
    {
        given : 'We create a database instance for testing, the database will be opened in a test folder.'
            def db = DataBase.at(TEST_DB_FILE)
            db.dropAllTables()
        when : 'We create a single table for the school, all other value tables will be created automatically.'
            db.createTablesFor(School, ClassRoom, FullName, Person)
        then :
            noExceptionThrown()
        and : 'The database now contains the expected tables:'
            db.sqlCodeOfTable(School) == "CREATE TABLE dal_values_School_table (" +
                        "id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                        "name TEXT NOT NULL, " +
                        "fk_classRoom1_id INTEGER REFERENCES dal_values_ClassRoom_table(id), " +
                        "fk_classRoom2_id INTEGER REFERENCES dal_values_ClassRoom_table(id), " +
                        "fk_classRoom3_id INTEGER REFERENCES dal_values_ClassRoom_table(id), " +
                        "fk_director_id INTEGER REFERENCES dal_values_Person_table(id)" +
                    ")"
            db.listOfAllTableNames().contains("dal_values_School__students_list_table")
            db.sqlCodeOfTable(ClassRoom) == "CREATE TABLE dal_values_ClassRoom_table (" +
                        "id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                        "hash_code INTEGER NOT NULL, " +
                        "usages INTEGER NOT NULL, " +
                        "grade INT NOT NULL, " +
                        "name TEXT NOT NULL, " +
                        "fk_teacher_id INTEGER REFERENCES dal_values_Person_table(id)" +
                    ")"
            db.sqlCodeOfTable(FullName) == "CREATE TABLE dal_values_FullName_table (" +
                        "id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                        "hash_code INTEGER NOT NULL, " +
                        "usages INTEGER NOT NULL, " +
                        "firstName TEXT NOT NULL, " +
                        "lastName TEXT NOT NULL" +
                    ")"
            db.sqlCodeOfTable(Person) == "CREATE TABLE dal_values_Person_table (" +
                        "id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                        "hash_code INTEGER NOT NULL, " +
                        "usages INTEGER NOT NULL, " +
                        "age INT NOT NULL, " +
                        "fk_name_id INTEGER REFERENCES dal_values_FullName_table(id)" +
                    ")"
    }


    def 'We can create a `School` of students and store it in the database.'()
    {
        reportInfo """
            This is the canonical example of how to populate a `School` model
            with deeply nested value records.

            Notice how a single `set(...)` call on a model property is enough to
            persist a whole tree of values. The ORM walks the record components
            of the value, stores each nested value in its own table, and links
            them through foreign keys (or, in the case of `Tuple<Person>`,
            through an intermediate table).

            The `toString()` of a model proxy is the most direct way to inspect
            the state of a model and its nested values. The string representation
            below is what living documentation looks like: every nested value
            appears verbatim, including the contents of tuples.
        """
        given : 'We create a database instance for testing, the database will be opened in a test folder.'
            def db = DataBase.at(TEST_DB_FILE)
            db.dropAllTables()
        when : 'We create a single table for the school, all other value tables will be created automatically.'
            db.createTablesFor(School, ClassRoom, FullName, Person)
        then :
            noExceptionThrown()

        when : 'We now create some values for the school.'
            var person1 = new Person(new FullName("Thomas", "Eicher"), 30)
            var person2 = new Person(new FullName("Mellanie", "Fuchs"), 28)
            var person3 = new Person(new FullName("Gerhard", "Schmidt"), 45)
            var person4 = new Person(new FullName("Karina", "Müller"), 53)
            var classRoom1 = new ClassRoom("Math", 10, person2, Tuple.of(person2, person3))
            var classRoom2 = new ClassRoom("Science", 11, person2, Tuple.of(person1, person3))
            var classRoom3 = new ClassRoom("History", 12, person3, Tuple.of(person2))
        and : 'We create the school model instance itself:'
            var school = db.create(School)
        and : 'We populate the school:'
            school.director().set(person4)
            school.students().set(Tuple.of(person1, person2, person3))
            school.classRoom1().set(classRoom1)
            school.classRoom2().set(classRoom2)
            school.classRoom3().set(classRoom3)
        then :
            school.toString() == "School[" +
                    "id=1, " +
                    "name=\"\", " +
                    "classRoom1=ClassRoom[" +
                        "name=Math, grade=10, " +
                        "teacher=Person[name=FullName[firstName=Mellanie, lastName=Fuchs], age=28], " +
                        "students=Tuple<Person>[" +
                            "Person[name=FullName[firstName=Mellanie, lastName=Fuchs], age=28], " +
                            "Person[name=FullName[firstName=Gerhard, lastName=Schmidt], age=45]" +
                        "]" +
                    "], " +
                    "classRoom2=ClassRoom[" +
                        "name=Science, grade=11, " +
                        "teacher=Person[name=FullName[firstName=Mellanie, lastName=Fuchs], age=28], " +
                        "students=Tuple<Person>[" +
                            "Person[name=FullName[firstName=Thomas, lastName=Eicher], age=30], " +
                            "Person[name=FullName[firstName=Gerhard, lastName=Schmidt], age=45]" +
                        "]" +
                    "], " +
                    "classRoom3=ClassRoom[" +
                        "name=History, grade=12, " +
                        "teacher=Person[name=FullName[firstName=Gerhard, lastName=Schmidt], age=45], " +
                        "students=Tuple<Person>[" +
                            "Person[name=FullName[firstName=Mellanie, lastName=Fuchs], age=28]" +
                        "]" +
                    "], " +
                    "director=Person[name=FullName[firstName=Karina, lastName=Müller], age=53], " +
                    "students=Tuple<Person>[" +
                        "Person[name=FullName[firstName=Thomas, lastName=Eicher], age=30], " +
                        "Person[name=FullName[firstName=Mellanie, lastName=Fuchs], age=28], " +
                        "Person[name=FullName[firstName=Gerhard, lastName=Schmidt], age=45]" +
                    "]" +
                "]"
    }


    def 'A value stored on a model can be read back unchanged through the property getter.'()
    {
        reportInfo """
            Values in Topsoil are immutable records. After we `set(...)` a value
            on a model property, calling `get()` on that same property must
            return a record that is `equals()` to what we stored.

            This is the basic round-trip guarantee: the ORM is a transparent
            persistence layer. A property getter is not an opaque handle to a
            row; it returns the same value back, fully reconstructed.
        """
        given : 'A fresh database with the value tables created.'
            def db = DataBase.at(TEST_DB_FILE)
            db.dropAllTables()
            db.createTablesFor(School, ClassRoom, FullName, Person)
        and : 'A nested value tree, and a school that holds it.'
            var teacher = new Person(new FullName("Ada", "Lovelace"), 36)
            var student = new Person(new FullName("Alan", "Turing"), 20)
            var classRoom = new ClassRoom("Computing", 9, teacher, Tuple.of(student))
            var school = db.create(School)
        when : 'We assign the nested value to the school.'
            school.classRoom1().set(classRoom)
        then : 'Reading the property back gives us a value that is equal to the original.'
            school.classRoom1().get() == classRoom
        and : 'Equality is structural, all the way down to the leaves.'
            school.classRoom1().get().teacher() == teacher
            school.classRoom1().get().teacher().name() == new FullName("Ada", "Lovelace")
            school.classRoom1().get().students() == Tuple.of(student)
    }


    def 'A model loaded fresh from the database via `select(..)` carries the same value contents.'()
    {
        reportInfo """
            A model proxy obtained from `db.create(...)` and a model proxy
            obtained later from `db.select(SomeModel.class, id)` are two
            separate handles to the same underlying database record.

            Reading a value field through either handle must yield the same
            value contents — that's how we know the values are persisted in the
            database itself, and not held in some in-memory cache that lives
            with the original proxy.
        """
        given : 'A database with a populated school.'
            def db = DataBase.at(TEST_DB_FILE)
            db.dropAllTables()
            db.createTablesFor(School, ClassRoom, FullName, Person)
            var director = new Person(new FullName("Marie", "Curie"), 50)
            var teacher = new Person(new FullName("Niels", "Bohr"), 42)
            var firstSchool = db.create(School)
            firstSchool.director().set(director)
            firstSchool.classRoom1().set(new ClassRoom("Physics", 12, teacher, Tuple.of(director)))
            long schoolId = firstSchool.id().get()
        when : 'We select the same school again through a brand-new proxy.'
            var reloaded = db.select(School, schoolId)
        then : 'The director value comes back fully reconstructed.'
            reloaded.director().get() == director
        and : 'The class room value comes back, including its nested tuple of students.'
            reloaded.classRoom1().get() == new ClassRoom("Physics", 12, teacher, Tuple.of(director))
        and : 'Selecting all schools yields the same model.'
            db.selectAll(School).size() == 1
            db.selectAll(School).get(0).director().get() == director
    }


    def 'Replacing a value on a model field updates only that field, leaving siblings intact.'()
    {
        reportInfo """
            Each value-typed property is its own foreign key column. Updating
            one value field must not touch the others.

            We use this test to demonstrate that the ORM handles isolated
            updates correctly, and that subsequent reads of the unmodified
            sibling fields still return the original values.
        """
        given : 'A school with three populated class rooms.'
            def db = DataBase.at(TEST_DB_FILE)
            db.dropAllTables()
            db.createTablesFor(School, ClassRoom, FullName, Person)
            var alice = new Person(new FullName("Alice", "A"), 30)
            var bob   = new Person(new FullName("Bob",   "B"), 31)
            var carol = new Person(new FullName("Carol", "C"), 32)
            var room1 = new ClassRoom("Math",    1, alice, Tuple.of(bob))
            var room2 = new ClassRoom("Science", 2, bob,   Tuple.of(carol))
            var room3 = new ClassRoom("History", 3, carol, Tuple.of(alice))
            var school = db.create(School)
            school.classRoom1().set(room1)
            school.classRoom2().set(room2)
            school.classRoom3().set(room3)
        when : 'We swap classRoom2 for an entirely different value.'
            var newRoom2 = new ClassRoom("Music", 4, alice, Tuple.of(bob, carol))
            school.classRoom2().set(newRoom2)
        then : 'Only classRoom2 changes, the other rooms remain exactly as before.'
            school.classRoom1().get() == room1
            school.classRoom2().get() == newRoom2
            school.classRoom3().get() == room3
        and : 'Reading from a freshly selected proxy confirms the change is persistent.'
            var reloaded = db.select(School, school.id().get())
            reloaded.classRoom1().get() == room1
            reloaded.classRoom2().get() == newRoom2
            reloaded.classRoom3().get() == room3
    }


    def 'Deeply nested updates are achieved by replacing the value with a modified copy.'()
    {
        reportInfo """
            Values are immutable, so a "nested update" is really just the act of
            constructing a new value with one component swapped out, and then
            assigning that new value to the model property.

            This is a deliberate property of the design: there is no
            `school.director().name().firstName().set(...)` because such a
            mutation would violate the immutability contract of the value
            record. Instead, you read, transform, and write back.
        """
        given : 'A school with a director.'
            def db = DataBase.at(TEST_DB_FILE)
            db.dropAllTables()
            db.createTablesFor(School, ClassRoom, FullName, Person)
            var school = db.create(School)
            school.director().set(new Person(new FullName("Hedy", "Lamarr"), 40))
        when : 'We "rename" the director by replacing the whole value.'
            var oldDirector = school.director().get()
            var newDirector = new Person(new FullName("Hedwig", oldDirector.name().lastName()), oldDirector.age() + 1)
            school.director().set(newDirector)
        then : 'The change is observable end-to-end.'
            school.director().get() == new Person(new FullName("Hedwig", "Lamarr"), 41)
        and : 'And it is fully persisted: a fresh proxy sees the same value.'
            db.select(School, school.id().get()).director().get() == new Person(new FullName("Hedwig", "Lamarr"), 41)
    }


    def 'Two equal value records are de-duplicated to a single row in the value table.'()
    {
        reportInfo """
            Values are deduplicated by content. Storing the same `FullName`
            twice (whether through different `Person`s, different `ClassRoom`s,
            or anywhere else in the value graph) results in only a single row
            in the `dal_values_FullName_table`.

            This is what makes value semantics safe to use freely: the storage
            cost is paid per unique value, not per reference.
        """
        given : 'A database with the value tables for a school.'
            def db = DataBase.at(TEST_DB_FILE)
            db.dropAllTables()
            db.createTablesFor(School, ClassRoom, FullName, Person)
        when : 'We create a school whose director and classRoom teacher are the *same* person.'
            var charlie = new Person(new FullName("Charlie", "Chaplin"), 88)
            var school = db.create(School)
            school.director().set(charlie)
            school.classRoom1().set(new ClassRoom("Acting", 10, charlie, Tuple.of(charlie)))
        then : 'There is exactly one row for Charlie in the Person table.'
            db.query("SELECT id FROM dal_values_Person_table").get("id").size() == 1
        and : 'And exactly one row for his FullName.'
            db.query("SELECT id FROM dal_values_FullName_table").get("id").size() == 1
        and : 'The usage counter on the Person row reflects every reference (director + classRoom teacher + classRoom student).'
            db.query("SELECT usages FROM dal_values_Person_table").get("usages") == ["3"]
    }


    def 'Each new value reference bumps the usage counter on the value row.'()
    {
        reportInfo """
            Internally, every value table carries a `usages` column that tracks
            how many model fields point at the row. This is the bookkeeping
            that makes safe deduplication possible.

            This test pins the contract: every `set(...)` of a value increments
            the counter for that value (and for every nested value that the
            value transitively references).
        """
        given : 'A fresh database.'
            def db = DataBase.at(TEST_DB_FILE)
            db.dropAllTables()
            db.createTablesFor(School, ClassRoom, FullName, Person)
        and : 'A single shared person.'
            var shared = new Person(new FullName("Shared", "Soul"), 42)
        and : 'Three independent schools.'
            var schoolA = db.create(School)
            var schoolB = db.create(School)
            var schoolC = db.create(School)
        when : 'They all elect the same director.'
            schoolA.director().set(shared)
            schoolB.director().set(shared)
            schoolC.director().set(shared)
        then : 'There is still only one row for that person.'
            db.query("SELECT id FROM dal_values_Person_table").get("id").size() == 1
        and : 'And its usage counter is exactly 3.'
            db.query("SELECT usages FROM dal_values_Person_table").get("usages") == ["3"]
    }


    def 'Replacing a `Tuple<Person>` field rewrites the intermediate table contents.'()
    {
        reportInfo """
            A `Tuple<Person>` field is not stored inline; it lives in a
            dedicated intermediate table that maps the school id to a positional
            list of person ids.

            When we replace the tuple wholesale, the intermediate table must be
            cleared and repopulated. This test demonstrates that the new tuple
            is what we read back, and that there are no leftover rows from the
            old tuple.
        """
        given : 'A school with three students.'
            def db = DataBase.at(TEST_DB_FILE)
            db.dropAllTables()
            db.createTablesFor(School, ClassRoom, FullName, Person)
            var p1 = new Person(new FullName("One", "One"), 1)
            var p2 = new Person(new FullName("Two", "Two"), 2)
            var p3 = new Person(new FullName("Three", "Three"), 3)
            var school = db.create(School)
            school.students().set(Tuple.of(p1, p2, p3))
        expect : 'The students property reflects the three persons in the order we set them.'
            school.students().get() == Tuple.of(p1, p2, p3)

        when : 'We replace the entire tuple with a smaller, reordered one.'
            school.students().set(Tuple.of(p3, p1))
        then : 'Reading the tuple back gives us the new contents in the new order.'
            school.students().get() == Tuple.of(p3, p1)
        and : 'A freshly selected proxy sees the same new tuple, confirming persistence.'
            db.select(School, school.id().get()).students().get() == Tuple.of(p3, p1)

        when : 'We then clear the tuple altogether.'
            school.students().set(Tuple.of(Person))
        then : 'The property reads as an empty tuple.'
            school.students().get().size() == 0
        and : 'A freshly selected proxy also sees an empty tuple.'
            db.select(School, school.id().get()).students().get().size() == 0
    }


    def 'A model with a primitive `name` field can be queried by name and value contents survive the round-trip.'()
    {
        reportInfo """
            Topsoil queries operate on the model row, which only sees primitive
            columns and foreign keys. We can `where(...)` on the primitive
            `name` field of `School` and still expect the value-typed fields
            (director, class rooms, students) to be intact when we read them
            from the result.
        """
        given : 'A database with two schools that have different names.'
            def db = DataBase.at(TEST_DB_FILE)
            db.dropAllTables()
            db.createTablesFor(School, ClassRoom, FullName, Person)

            var fooDirector = new Person(new FullName("Foo", "Director"), 50)
            var barDirector = new Person(new FullName("Bar", "Director"), 60)

            var foo = db.create(School)
            foo.name().set("Foo High")
            foo.director().set(fooDirector)

            var bar = db.create(School)
            bar.name().set("Bar High")
            bar.director().set(barDirector)
        when : 'We query for the school named "Foo High".'
            var hits = db.select(School).where(School.Name).is("Foo High").asList()
        then : 'We get exactly one school back.'
            hits.size() == 1
        and : 'It is the right one, and its value field is intact.'
            hits.get(0).name().get() == "Foo High"
            hits.get(0).director().get() == fooDirector
        and : 'The other school is unaffected.'
            db.select(School).where(School.Name).is("Bar High").asList().get(0).director().get() == barDirector
    }


    def 'Equal nested values across different model fields share storage and stay equal after reload.'()
    {
        reportInfo """
            This test combines two ideas: deep equality and structural sharing.

            The same `FullName("Lin", "Manuel")` is used inside two different
            `Person`s, which themselves end up in two different fields of the
            school. After persistence, all of these references should resolve
            to the same `FullName` row, and reading them back should yield
            structurally equal values.
        """
        given : 'A database with the value tables.'
            def db = DataBase.at(TEST_DB_FILE)
            db.dropAllTables()
            db.createTablesFor(School, ClassRoom, FullName, Person)
        and : 'We, to verify the sharing, we access the data base API as the underlying SQL data base:'
            var asSqlDb = db as SQLiteDataBase
        and : 'Two distinct persons that happen to share a `FullName` value.'
            var sharedName = new FullName("Lin", "Manuel")
            var youngLin = new Person(sharedName, 25)
            var olderLin = new Person(sharedName, 45)
        when : 'We assign them to different fields of the same school.'
            var school = db.create(School)
            school.director().set(olderLin)
            school.students().set(Tuple.of(youngLin, olderLin))
        then : 'There are two Person rows (different ages), but only one FullName row.'
            asSqlDb.query("SELECT id FROM dal_values_Person_table").get("id").size() == 2
            asSqlDb.query("SELECT id FROM dal_values_FullName_table").get("id").size() == 1
        and : 'Reloading the school yields values that are structurally equal to the originals.'
            var reloaded = db.select(School, school.id().get())
            reloaded.director().get() == olderLin
            reloaded.students().get() == Tuple.of(youngLin, olderLin)
        and : 'And the nested name component compares equal across both reloaded persons.'
            reloaded.students().get().get(0).name() == reloaded.students().get().get(1).name()
            reloaded.students().get().get(0).name() == sharedName
    }


    def 'Replacing a unique value on a model field deletes the old value row from the value table.'()
    {
        reportInfo """
            Structural sharing means we cannot just blindly delete the old value
            when overwriting a property. We have to decrement its usage counter
            first; only when no one else is referencing the old value can the
            row safely be removed.

            This test pins the simplest case: the old value was unique to this
            field (usage = 1). After overwriting it, the row is gone — we have
            no leak — and the nested `FullName` row is gone too, because
            cleanup recurses through value components.
        """
        given : 'A school whose director is a unique person.'
            def db = DataBase.at(TEST_DB_FILE)
            db.dropAllTables()
            db.createTablesFor(School, ClassRoom, FullName, Person)
            var asSqlDb = db as SQLiteDataBase
            var school = db.create(School)
            school.director().set(new Person(new FullName("Solo", "Director"), 50))
        expect : 'There is exactly one Person and one FullName row.'
            asSqlDb.query("SELECT id FROM dal_values_Person_table").get("id").size() == 1
            asSqlDb.query("SELECT id FROM dal_values_FullName_table").get("id").size() == 1

        when : 'We assign a different director, with no shared inner FullName.'
            school.director().set(new Person(new FullName("New", "Director"), 41))
        then : 'The old Person row is gone, replaced by exactly one new row.'
            asSqlDb.query("SELECT firstName FROM dal_values_FullName_table").get("firstName") == ["New"]
            asSqlDb.query("SELECT id FROM dal_values_Person_table").get("id").size() == 1
        and : 'The old FullName row is gone too — recursive cleanup released it.'
            asSqlDb.query("SELECT id FROM dal_values_FullName_table").get("id").size() == 1
    }


    def 'Replacing a value field decrements the old usage but keeps the row alive when others still reference it.'()
    {
        reportInfo """
            When the old value is shared with someone else, the cleanup logic
            must *not* delete the row. It must only decrement the counter, so
            the remaining reference still resolves correctly.
        """
        given : 'Two schools whose director is the same person.'
            def db = DataBase.at(TEST_DB_FILE)
            db.dropAllTables()
            db.createTablesFor(School, ClassRoom, FullName, Person)
            var asSqlDb = db as SQLiteDataBase
            var shared = new Person(new FullName("Shared", "Boss"), 60)
            var schoolA = db.create(School)
            var schoolB = db.create(School)
            schoolA.director().set(shared)
            schoolB.director().set(shared)
        expect : 'The shared person has usage 2.'
            asSqlDb.query("SELECT usages FROM dal_values_Person_table").get("usages") == ["2"]

        when : 'School A swaps to a different director.'
            schoolA.director().set(new Person(new FullName("Local", "Boss"), 30))
        then : 'The shared row is still there, with usage 1 — held only by school B.'
            asSqlDb.query("SELECT usages FROM dal_values_Person_table " +
                          "WHERE id = (SELECT fk_director_id FROM dal_values_School_table WHERE id = " + schoolB.id().get() + ")")
                  .get("usages") == ["1"]
        and : 'School B can still read its director correctly.'
            schoolB.director().get() == shared
    }


    def 'Deleting a model with a unique value field removes the value row and all of its nested rows.'()
    {
        reportInfo """
            Deleting a model isn't just a `DELETE FROM models WHERE id = ?` —
            it must release every value reference held by the model, otherwise
            the value tables grow forever.

            Here we verify the strongest case: a school that owns its director
            uniquely. Once the school is deleted, the Person row *and* its
            nested FullName row must be gone.
        """
        given : 'A database with one school holding a unique director.'
            def db = DataBase.at(TEST_DB_FILE)
            db.dropAllTables()
            db.createTablesFor(School, ClassRoom, FullName, Person)
            var asSqlDb = db as SQLiteDataBase
            var school = db.create(School)
            school.director().set(new Person(new FullName("Bye", "Bye"), 99))
        expect : 'Both rows exist before the deletion.'
            asSqlDb.query("SELECT id FROM dal_values_Person_table").get("id").size() == 1
            asSqlDb.query("SELECT id FROM dal_values_FullName_table").get("id").size() == 1

        when : 'We delete the school.'
            db.delete(school)
        then : 'Both the Person row and the FullName row are cleaned up.'
            (asSqlDb.query("SELECT id FROM dal_values_Person_table").get("id") ?: []).size() == 0
            (asSqlDb.query("SELECT id FROM dal_values_FullName_table").get("id") ?: []).size() == 0
        and : 'And of course the School row is gone, too.'
            (asSqlDb.query("SELECT id FROM dal_values_School_table").get("id") ?: []).size() == 0
    }


    def 'Deleting one of two schools that share a value keeps the value alive for the survivor.'()
    {
        reportInfo """
            The dual of the previous test: when two schools share a director,
            deleting one of them must not nuke the shared director row. We
            decrement the usage counter and leave the row in place, ready to
            serve the still-living reference.
        """
        given : 'Two schools that share their director.'
            def db = DataBase.at(TEST_DB_FILE)
            db.dropAllTables()
            db.createTablesFor(School, ClassRoom, FullName, Person)
            var asSqlDb = db as SQLiteDataBase
            var shared = new Person(new FullName("Twin", "Director"), 55)
            var schoolA = db.create(School)
            var schoolB = db.create(School)
            schoolA.director().set(shared)
            schoolB.director().set(shared)
        expect : 'Initially the shared person has usage 2.'
            asSqlDb.query("SELECT usages FROM dal_values_Person_table").get("usages") == ["2"]

        when : 'We delete school A.'
            db.delete(schoolA)
        then : 'The Person and FullName rows are still there, with usage 1.'
            asSqlDb.query("SELECT usages FROM dal_values_Person_table").get("usages") == ["1"]
            asSqlDb.query("SELECT id FROM dal_values_FullName_table").get("id").size() == 1
        and : 'School B can still resolve its director.'
            schoolB.director().get() == shared

        when : 'We delete school B as well.'
            db.delete(schoolB)
        then : 'Now the Person and FullName rows are finally gone.'
            (asSqlDb.query("SELECT id FROM dal_values_Person_table").get("id") ?: []).size() == 0
            (asSqlDb.query("SELECT id FROM dal_values_FullName_table").get("id") ?: []).size() == 0
    }


    def 'A value that appears multiple times in the same tuple is reference-counted correctly.'()
    {
        reportInfo """
            A value can appear more than once at the same position-list. The
            ORM stores it as a single row with one usage increment per
            occurrence, and on cleanup it must decrement exactly that many
            times — not once, and not "until the row is gone".

            This is the edge case that exposes off-by-one bugs in reference
            counting most easily.
        """
        given : 'A school whose `students` tuple contains the same person three times.'
            def db = DataBase.at(TEST_DB_FILE)
            db.dropAllTables()
            db.createTablesFor(School, ClassRoom, FullName, Person)
            var asSqlDb = db as SQLiteDataBase
            var clone = new Person(new FullName("Clone", "Trooper"), 22)
            var school = db.create(School)
            school.students().set(Tuple.of(clone, clone, clone))
        expect : 'There is one Person row, with usage 3.'
            asSqlDb.query("SELECT id FROM dal_values_Person_table").get("id").size() == 1
            asSqlDb.query("SELECT usages FROM dal_values_Person_table").get("usages") == ["3"]
        and : 'And three rows in the intermediate table, all pointing to the same Person.'
            asSqlDb.query("SELECT * FROM dal_values_School__students_list_table")
                  .get("fk_dal_values_Person_table_id").size() == 3

        when : 'We delete the school.'
            db.delete(school)
        then : 'The intermediate table is empty for this school.'
            (asSqlDb.query("SELECT * FROM dal_values_School__students_list_table")
                   .get("fk_dal_values_Person_table_id") ?: []).size() == 0
        and : 'The Person row is fully released — three references, three decrements, gone.'
            (asSqlDb.query("SELECT id FROM dal_values_Person_table").get("id") ?: []).size() == 0
        and : 'And the nested FullName row is also gone.'
            (asSqlDb.query("SELECT id FROM dal_values_FullName_table").get("id") ?: []).size() == 0
    }


    def 'A tuple with mixed duplicates and unique items has independent counters per row.'()
    {
        reportInfo """
            Different values within the same tuple are tracked independently.
            Here we put `Tuple.of(a, a, b)` into a school and verify that `a`
            ends up with usage 2 while `b` ends up with usage 1.

            This is also the canonical setup for verifying that the cleanup
            logic doesn't accidentally release everything in one go.
        """
        given : 'A school with a mixed tuple.'
            def db = DataBase.at(TEST_DB_FILE)
            db.dropAllTables()
            db.createTablesFor(School, ClassRoom, FullName, Person)
            var asSqlDb = db as SQLiteDataBase
            var a = new Person(new FullName("A", "Twin"), 10)
            var b = new Person(new FullName("B", "Solo"), 11)
            var school = db.create(School)
            school.students().set(Tuple.of(a, a, b))
        expect : 'Two Person rows in the table.'
            asSqlDb.query("SELECT id FROM dal_values_Person_table").get("id").size() == 2
        and : 'Their usages match `a → 2` and `b → 1`, regardless of insertion order.'
            (asSqlDb.query("SELECT usages FROM dal_values_Person_table").get("usages").collect { it as int }.sort()) == [1, 2]

        when : 'We delete the school.'
            db.delete(school)
        then : 'Both Person rows and both FullName rows are cleaned up.'
            (asSqlDb.query("SELECT id FROM dal_values_Person_table").get("id") ?: []).size() == 0
            (asSqlDb.query("SELECT id FROM dal_values_FullName_table").get("id") ?: []).size() == 0
        and : 'And the intermediate table is empty.'
            (asSqlDb.query("SELECT id FROM dal_values_School__students_list_table").get("id") ?: []).size() == 0
    }


    def 'Replacing a tuple decrements old items independently of the new ones.'()
    {
        reportInfo """
            Replacing a `Tuple<Person>` field is, semantically, "release every
            old item, store every new item". The two operations have to be
            independent: the old items get their counters decreased (and rows
            possibly deleted), the new items get their counters increased.

            This test threads the needle by using overlapping content between
            the old and new tuple — `a` survives, `b` goes away, `c` arrives.
        """
        given : 'A school with `Tuple.of(a, b)` as its students.'
            def db = DataBase.at(TEST_DB_FILE)
            db.dropAllTables()
            db.createTablesFor(School, ClassRoom, FullName, Person)
            var asSqlDb = db as SQLiteDataBase
            var a = new Person(new FullName("A", "Stays"), 10)
            var b = new Person(new FullName("B", "Leaves"), 20)
            var c = new Person(new FullName("C", "Arrives"), 30)
            var school = db.create(School)
            school.students().set(Tuple.of(a, b))

        when : 'We replace the tuple with `Tuple.of(a, c)`.'
            school.students().set(Tuple.of(a, c))
        then : 'B is gone (its row was unique), but A and C are present.'
            asSqlDb.query("SELECT firstName FROM dal_values_FullName_table ORDER BY firstName").get("firstName") == ["A", "C"]
            asSqlDb.query("SELECT id FROM dal_values_Person_table").get("id").size() == 2
        and : 'Both surviving Person rows have usage 1 — each is now referenced exactly once.'
            asSqlDb.query("SELECT usages FROM dal_values_Person_table").get("usages") == ["1", "1"]
        and : 'And the school reads back the new tuple.'
            school.students().get() == Tuple.of(a, c)
    }


    def 'Deleting a school that holds a deeply nested ClassRoom recursively cleans up every contained value.'()
    {
        reportInfo """
            The killer test for value cleanup. A `ClassRoom` is itself a value
            that holds a `Person` teacher and a `Tuple<Person>` of students,
            and each `Person` holds a `FullName`. When the school is deleted,
            the cascade has to travel all the way down: ClassRoom → Person →
            FullName, plus every entry in the students intermediate table on
            the ClassRoom side.

            If the cleanup misses any layer, the corresponding table will be
            left with orphan rows — easy to detect, easy to forget without a
            test like this one.
        """
        given : 'A school with a single fully-loaded class room.'
            def db = DataBase.at(TEST_DB_FILE)
            db.dropAllTables()
            db.createTablesFor(School, ClassRoom, FullName, Person)
            var asSqlDb = db as SQLiteDataBase
            var teacher = new Person(new FullName("The", "Teacher"), 40)
            var s1 = new Person(new FullName("First", "Pupil"), 12)
            var s2 = new Person(new FullName("Second", "Pupil"), 13)
            var school = db.create(School)
            school.classRoom1().set(new ClassRoom("Topology", 11, teacher, Tuple.of(s1, s2)))
        expect : 'Everything is in place: 1 ClassRoom, 3 Persons, 3 FullNames.'
            asSqlDb.query("SELECT id FROM dal_values_ClassRoom_table").get("id").size() == 1
            asSqlDb.query("SELECT id FROM dal_values_Person_table").get("id").size() == 3
            asSqlDb.query("SELECT id FROM dal_values_FullName_table").get("id").size() == 3

        when : 'We delete the school.'
            db.delete(school)
        then : 'Every value table is empty afterwards — no orphans anywhere.'
            (asSqlDb.query("SELECT id FROM dal_values_ClassRoom_table").get("id") ?: []).size() == 0
            (asSqlDb.query("SELECT id FROM dal_values_Person_table").get("id") ?: []).size() == 0
            (asSqlDb.query("SELECT id FROM dal_values_FullName_table").get("id") ?: []).size() == 0
        and : 'And the intermediate tables involved (ClassRoom students, School students) are empty too.'
            (asSqlDb.query("SELECT id FROM dal_values_School__students_list_table").get("id") ?: []).size() == 0
            (asSqlDb.query("SELECT id FROM dal_values_ClassRoom__students_list_table").get("id") ?: []).size() == 0
    }


    def 'When the same ClassRoom is shared across schools, deleting one school keeps it alive for the other.'()
    {
        reportInfo """
            Even composite values like `ClassRoom` are deduplicated and shared.
            If two schools assign the *same* class room to their `classRoom1`
            field, the underlying row is held jointly. Deleting one school
            must therefore not delete the class room — only its inner
            usage counter should drop.

            We then delete the second school and watch the entire cascade
            finally tear everything down.
        """
        given : 'Two schools that share their first class room.'
            def db = DataBase.at(TEST_DB_FILE)
            db.dropAllTables()
            db.createTablesFor(School, ClassRoom, FullName, Person)
            var asSqlDb = db as SQLiteDataBase
            var teacher = new Person(new FullName("Common", "Teacher"), 50)
            var pupil   = new Person(new FullName("Common", "Pupil"), 14)
            var room    = new ClassRoom("Shared", 9, teacher, Tuple.of(pupil))
            var schoolA = db.create(School)
            var schoolB = db.create(School)
            schoolA.classRoom1().set(room)
            schoolB.classRoom1().set(room)
        expect : 'A single ClassRoom row exists, with usage 2.'
            asSqlDb.query("SELECT usages FROM dal_values_ClassRoom_table").get("usages") == ["2"]
        and : 'A single teacher and pupil — each used once by the ClassRoom.'
            asSqlDb.query("SELECT id FROM dal_values_Person_table").get("id").size() == 2
            asSqlDb.query("SELECT usages FROM dal_values_Person_table").get("usages") == ["1", "1"]

        when : 'We delete only school A.'
            db.delete(schoolA)
        then : 'The ClassRoom row survives, with usage 1.'
            asSqlDb.query("SELECT usages FROM dal_values_ClassRoom_table").get("usages") == ["1"]
        and : 'School B still resolves it.'
            schoolB.classRoom1().get() == room

        when : 'We delete school B too.'
            db.delete(schoolB)
        then : 'The cascade reaches all the way down: every value row is gone.'
            (asSqlDb.query("SELECT id FROM dal_values_ClassRoom_table").get("id") ?: []).size() == 0
            (asSqlDb.query("SELECT id FROM dal_values_Person_table").get("id") ?: []).size() == 0
            (asSqlDb.query("SELECT id FROM dal_values_FullName_table").get("id") ?: []).size() == 0
    }


    def 'Many create-and-delete cycles do not leak a single value row.'()
    {
        reportInfo """
            The strongest possible smoke test against memory leaks: in a tight
            loop, create a school, populate it with a fresh value tree, and
            then delete it. After enough iterations a leak would have made
            the value tables grow unboundedly.

            We assert at the end that *every* value table is empty — there is
            no row left behind by any cycle.
        """
        given : 'A fresh database.'
            def db = DataBase.at(TEST_DB_FILE)
            db.dropAllTables()
            db.createTablesFor(School, ClassRoom, FullName, Person)
            var asSqlDb = db as SQLiteDataBase

        when : 'We run 25 create-populate-delete cycles, each with unique values.'
            (1..25).each { i ->
                var t = new Person(new FullName("T${i}", "T"), 30 + i)
                var s = new Person(new FullName("S${i}", "S"), 10 + i)
                var school = db.create(School)
                school.director().set(t)
                school.students().set(Tuple.of(s, s))
                school.classRoom1().set(new ClassRoom("Room${i}", i, t, Tuple.of(s)))
                db.delete(school)
            }
        then : 'No School row remains.'
            (asSqlDb.query("SELECT id FROM dal_values_School_table").get("id") ?: []).size() == 0
        and : 'And no value-table row remains, anywhere.'
            (asSqlDb.query("SELECT id FROM dal_values_ClassRoom_table").get("id") ?: []).size() == 0
            (asSqlDb.query("SELECT id FROM dal_values_Person_table").get("id") ?: []).size() == 0
            (asSqlDb.query("SELECT id FROM dal_values_FullName_table").get("id") ?: []).size() == 0
        and : 'And no intermediate-table row either.'
            (asSqlDb.query("SELECT id FROM dal_values_School__students_list_table").get("id") ?: []).size() == 0
            (asSqlDb.query("SELECT id FROM dal_values_ClassRoom__students_list_table").get("id") ?: []).size() == 0
    }

}
