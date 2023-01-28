package dal

import dal.api.DataBase
import dal.models.Address
import dal.models.Animal
import dal.models.Atom
import dal.models.Food
import dal.models.Furniture
import dal.models.Ingredient
import dal.models.InvalidModel
import dal.models.ModeWithDefaults
import dal.models.Person
import dal.models.Rabbit
import dal.models.Raccoon
import dal.models.Workplace
import spock.lang.Narrative
import spock.lang.Specification
import spock.lang.Title

@Title("The Tribalism Data Access Layer")
@Narrative('''
       
       This specification describes how the Data-Access-Layer, in short DAL, is supposed to behave.
       More specifically this DAL is designed around the usage of MVVM properties and the
       usage of the `Entity`and `Field` annotations.
       The Data-Access-Layer is supposed to automatically convert 
       models (or even view models) into database tables, table entries
       and then back into models.
       
''')
class DataBase_Spec extends Specification
{
    def TEST_DB_LOCATION = "test_data/"
    def TEST_DB_FILE = TEST_DB_LOCATION + "my.db"

    def setup() {
        def db = DataBase.at(TEST_DB_FILE)
        db.dropAllTables()
        db.close()
    }

    def 'We can query "Foods" using their properties.'()
    {
        reportInfo """
            Here we use the following model:
            ```
                public interface Food extends Model<Food>
                {
                    Var<String> name();
                    Var<Double> calories();
                    Var<Double> fat();
                    Var<Double> carbs();
                    Var<Double> protein();
                    Vars<Ingredient> ingredients();
                }
            ```
            As you can see, the model has a property called "ingredients" which is a list of "Ingredient" models
            which is why we also need to create a table for the "Ingredient" model if we want to use the food model.
            In this feature you can see how to create a database and add some foods to it to
            then we query the database for foods with a certain amount of carbs and protein.
        """
        given : 'We create a database instance for testing, the database will be opened in a test folder.'
            def db = DataBase.at(TEST_DB_FILE)
            db.dropAllTables()
        and : 'We create 2 test tables'
            db.createTablesFor(Food, Ingredient)
        and : 'We create some foods'
            var food1 = db.create(Food)
            food1.name().set("Chana Masala")
            food1.carbs().set(50.0)
            food1.protein().set(25.0)
            var food2 = db.create(Food)
            food2.name().set("Mochi")
            food2.carbs().set(100.0)
            food2.protein().set(10.0)
            var food3 = db.create(Food)
            food3.name().set("Saitan Steak")
            food3.carbs().set(5.0)
            food3.protein().set(70.0)
        when : 'We select all foods as strings...'
            var foods = db.selectAll(Food).asList().collect({it.toString()})
        then :
            foods[0] == "Food[id=1, calories=0.0, carbs=50.0, fat=0.0, name=\"Chana Masala\", protein=25.0, ingredients=[]]"
            foods[1] == "Food[id=2, calories=0.0, carbs=100.0, fat=0.0, name=\"Mochi\", protein=10.0, ingredients=[]]"
            foods[2] == "Food[id=3, calories=0.0, carbs=5.0, fat=0.0, name=\"Saitan Steak\", protein=70.0, ingredients=[]]"

        when : 'We select all foods with more than 50 carbs and more than 20 protein...'
            foods = db.select(Food)
                            .where(Food::carbs)
                            .greaterThanOrEqual(50)
                            .and(Food::protein)
                            .greaterThan(20)
                            .asList()
        then :
            foods.size() == 1
            foods[0] == food1
    }

    def 'We can query "Foods" and their "Ingredients" using their properties.'()
    {
        given : 'We create a database instance for testing, the database will be opened in a test folder.'
            def db = DataBase.at(TEST_DB_FILE)
            db.dropAllTables()
        and : 'We create 2 test tables'
            db.createTablesFor(Food, Ingredient)
        and : 'We create some ingredients and foods'
            var ingredient1 = db.create(Ingredient)
            ingredient1.name().set("Chickpeas")
            var ingredient2 = db.create(Ingredient)
            ingredient2.name().set("Rice")
            var ingredient3 = db.create(Ingredient)
            ingredient3.name().set("Spices")
            var ingredient4 = db.create(Ingredient)
            ingredient4.name().set("Saitan")
            var ingredient5 = db.create(Ingredient)
            ingredient5.name().set("Beans")
            var food1 = db.create(Food)
            food1.name().set("Chana Masala")
            food1.calories().set(100)
            food1.ingredients().addAll(ingredient1, ingredient2, ingredient3)
            var food2 = db.create(Food)
            food2.name().set("Mochi")
            food2.calories().set(200)
            food2.ingredients().addAll(ingredient2, ingredient5)
            var food3 = db.create(Food)
            food3.name().set("Saitan Steak")
            food3.calories().set(300)
            food3.ingredients().addAll(ingredient3, ingredient4)
        when : 'We query the database for foods with a certain name.'
            var foods = db.select(Food)
                            .where(Food::name).is("Chana Masala")
                            .asList()
        then : 'We can confirm that only 1 food was found.'
            foods.size() == 1
            foods[0] == food1

        when : 'We query the database for foods with certain calories...'
            foods = db.select(Food)
                        .where(Food::calories).greaterThanOrEqual(200)
                        .asList()
        then : 'We can confirm that 2 foods were found.'
            foods.size() == 2
            foods[0] == food2
            foods[1] == food3
    }

    def 'A model exposes the "commit" method for doing transactional model modification.'()
    {
        reportInfo """
            By default any modification to the properties of a model will automatically be
            committed to the database. However, if you want to do multiple modifications
            to a model and then commit them all at once, you can use the "commit" method.
            For this feature specification we will use the following model:
            ```
                public interface Furniture extends Model<Furniture> 
                {
                    Var<String> name();
                    Var<String> material();
                    Var<Double> price();
                    Var<Integer> quantity();
                    Var<String> category();
                    Var<String> color();
                }
            ```
            We will create a database and add some furniture to it, but in a transactional way.
        """
        given : 'We create a database instance for testing, the database will be opened in a test folder.'
            def db = DataBase.at(TEST_DB_FILE)
            db.dropAllTables()
        and : 'We create a test table'
            db.createTablesFor(Furniture)
        and : 'We create some furniture'
            var furniture = db.create(Furniture)
        expect : 'By default all the fields are initialized to non-null default values.'
            furniture.name().get() == ""
            furniture.material().get() == ""
            furniture.price().get() == 0.0
            furniture.quantity().get() == 0
            furniture.category().get() == ""
            furniture.color().get() == ""
        when : 'We modify the furniture in a transactional way.'
            furniture.commit( asTable -> {
                asTable.name().set("Chair")
                asTable.material().set("Wood")
                asTable.price().set(100.0)
                asTable.quantity().set(10)
                asTable.category().set("Seating")
                asTable.color().set("Brown")
                assert asTable.name().get() == "Chair"
                assert asTable.material().get() == "Wood"
                assert asTable.price().get() == 100.0
                assert asTable.quantity().get() == 10
                assert asTable.category().get() == "Seating"
                assert asTable.color().get() == "Brown"
                // But the original furniture object is not modified yet.
                assert furniture.name().get() == ""
                assert furniture.material().get() == ""
                assert furniture.price().get() == 0.0
                assert furniture.quantity().get() == 0
                assert furniture.category().get() == ""
                assert furniture.color().get() == ""
            })
        then : 'The original furniture object is now modified.'
            furniture.name().get() == "Chair"
            furniture.material().get() == "Wood"
            furniture.price().get() == 100.0
            furniture.quantity().get() == 10
            furniture.category().get() == "Seating"
            furniture.color().get() == "Brown"
    }

    def 'We can create a "Person" and "Address" table.'()
    {
        reportInfo """
            Given we have the following interface defining "Person"
            ```
                public interface Person extends Model<Person> 
                {
                    interface FirstName extends Var<String> {}
                    interface LastName extends Var<String> {}
                    interface Address extends Var<dal.models.Address> {}
                    FirstName firstName();
                    LastName lastName();
                    Address address();
                }
            ```
            ...and the following interface defining "Address"
            ```
                public interface Address extends Model<Address> 
                {
                    interface PostalCode extends Var<String> {}
                    interface Street extends Var<String> {}
                    interface City extends Var<String> {}
                    interface Country extends Var<String> {}
                    PostalCode postalCode();
                    Street street();
                    City city();
                    Country country();
                }
            ```
            Using the database API we can create a database table for each of these models
            simply by passing the model class to database!
            See for yourself:
        """
        given : 'A database instance, opened in a test folder.'
            def db = DataBase.at(TEST_DB_FILE)
            db.dropAllTables() // just to be sure
        expect : 'Initially there are no tables in the database.'
            db.listOfAllTableNames() == []

        when : 'We now request a table creation for the model type in the database...'
            db.createTablesFor(Person, Address)
            var table1 = db.sqlCodeOfTable(Person)
            var table2 = db.sqlCodeOfTable(Address)

        then : 'The database should now contain two tables.'
            db.listOfAllTableNames() as Set == ["dal_models_Person_table", "dal_models_Address_table"] as Set
        and : 'The table code is as expected!'
            table1 == "CREATE TABLE dal_models_Person_table (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, firstName TEXT NOT NULL, lastName TEXT NOT NULL, fk_address_id INTEGER REFERENCES dal_models_Address_table(id))"
            table2 == "CREATE TABLE dal_models_Address_table (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, city TEXT NOT NULL, country TEXT NOT NULL, postalCode TEXT NOT NULL, street TEXT NOT NULL)"

        when : 'We try to create a table that already exists...'
            db.createTablesFor(Person)
        then : 'The database should still contain two tables.'
            db.listOfAllTableNames() as Set == ["dal_models_Person_table", "dal_models_Address_table"] as Set

        when : 'We create and modify a person...'
            var person = db.create(Person)
            person.firstName().set("John")
            person.lastName().set("Doe")
        then :
            person.firstName().get() == "John"
            person.lastName().get() == "Doe"
        when : 'We manually update the person...'
            db.execute("UPDATE dal_models_Person_table SET firstName = 'Jane', lastName = 'Doe' WHERE id = ${person.id().get()}")
        then :
            person.firstName().get() == "Jane"
            person.lastName().get() == "Doe"

        when : 'We create and modify an address...'
            var address = db.create(Address)
            address.country().set("Germany")
            address.postalCode().set("12345")
            address.street().set("Main Street")
            address.city().set("Berlin")
        then :
            address.country().get() == "Germany"
            address.postalCode().get() == "12345"
            address.street().get() == "Main Street"
            address.city().get() == "Berlin"
        when : 'We assign the address to Jane Doe...'
            person.address().set(address)
        then :
            person.address().get() == address
        and :
            person.address().get().country().get() == "Germany"
            person.address().get().postalCode().get() == "12345"
            person.address().get().street().get() == "Main Street"
            person.address().get().city().get() == "Berlin"
        when : 'We manually update the address...'
            db.execute("UPDATE dal_models_Address_table SET country = 'USA', postalCode = '54321', street = 'Main Street', city = 'New York' WHERE id = ${address.id().get()}")
        then :
            person.address().get().country().get() == "USA"
            person.address().get().postalCode().get() == "54321"
            person.address().get().street().get() == "Main Street"
            person.address().get().city().get() == "New York"

        when : 'We create a new person and assign the address to him...'
            var person2 = db.create(Person)
            person2.address().set(address)
        then :
            person2.address().get() == address

        cleanup:
            db.close()
    }

    def 'We can create a "Workplace" referencing multiple people.'()
    {
        reportInfo """
            This feature demonstrates how to create a table that references multiple other tables.
            It is based on the following example interface:
            ```
                public interface Workplace extends Model<Workplace> 
                {
                    interface Name extends Var<String> {}
                    interface Location extends Var<Address> {}
                    interface Employees extends Vars<Person> {}
                    Name name();
                    Location address();
                    Employees employees();
                }
            ```
            As you can see we use the `Vars` interface to define a list of `Person` objects.
        """
        given : 'A database instance, opened in a test folder.'
            def db = DataBase.at(TEST_DB_FILE)
            db.dropAllTables()
        expect : 'Initially there are no tables in the database.'
            db.listOfAllTableNames() == []

        when : 'We request the necessary table creations for the model type in the database...'
            db.createTablesFor(Workplace, Person, Address)

        then : 'The database should now contain two tables.'
            db.listOfAllTableNames() as Set == ["dal_models_Address_table", "dal_models_Workplace_table", "dal_models_Person_table", "employees_list_table"] as Set

        when : 'We create a new workplace with and address and 2 people working there.'
            var address = db.create(Address)
            var person   = db.create(Person)
            var person2  = db.create(Person)
            var workplace = db.create(Workplace)
            workplace.address().set(address)
            workplace.employees().add(person)
            workplace.employees().add(person2)
        then : 'The workplace should have the correct address and employees.'
            workplace.address().get() == address
            workplace.employees().toSet() == [person, person2] as Set

        and :
            workplace.toString() == "Workplace[" +
                                        "id=1, name=\"\", " +
                                        "address=Address[id=1, city=\"\", country=\"\", postalCode=\"\", street=\"\"], " +
                                        "employees=[" +
                                            "Person[id=1, firstName=\"\", lastName=\"\", address=Address[id=null, city=null, country=null, postalCode=null, street=null]], " +
                                            "Person[id=2, firstName=\"\", lastName=\"\", address=Address[id=null, city=null, country=null, postalCode=null, street=null]]" +
                                        "]]"

        when :
            person2.firstName().set("Jane")
        then :
            workplace.toString() == "Workplace[" +
                                        "id=1, name=\"\", " +
                                        "address=Address[id=1, city=\"\", country=\"\", postalCode=\"\", street=\"\"], " +
                                        "employees=[" +
                                            "Person[id=1, firstName=\"\", lastName=\"\", address=Address[id=null, city=null, country=null, postalCode=null, street=null]], " +
                                            "Person[id=2, firstName=\"Jane\", lastName=\"\", address=Address[id=null, city=null, country=null, postalCode=null, street=null]]" +
                                        "]]"

        when :
            db.delete(person2)
        then :
            workplace.toString() == "Workplace[" +
                                        "id=1, name=\"\", " +
                                        "address=Address[id=1, city=\"\", country=\"\", postalCode=\"\", street=\"\"], " +
                                        "employees=[Person[id=1, firstName=\"\", lastName=\"\", address=Address[id=null, city=null, country=null, postalCode=null, street=null]]]]"

        cleanup:
            db.close()
    }


    def 'We can use the fluent query API of the database to select "Atoms"!'()
    {
        reportInfo """
            In this feature you can see how to use the fluent query API of the database 
            build database queries to select "Atoms" from the database.
            This is the `Atom` model we are going to use:
            ```
                public interface Atom extends Model<Atom> 
                {
                    interface Name extends Var<String> {}
                    interface Mass extends Var<Double> {}
                    interface AtomicNumber extends Var<Integer> {}
                    Name name();
                    Mass mass();
                    AtomicNumber atomicNumber();
                }
            ```
        """
        given : 'We create a database instance for testing, the database will be opened in a test folder.'
            def db = DataBase.at(TEST_DB_FILE)
            db.dropAllTables()
        expect : 'Initially there are no tables in the database.'
            db.createTablesFor(Atom)
        and : 'We create and save different atoms:'
            var atom1 = db.create(Atom)
            atom1.name().set("Hydrogen")
            atom1.atomicNumber().set(1)
            atom1.mass().set(1.00794)
            var atom2 = db.create(Atom)
            atom2.name().set("Helium")
            atom2.atomicNumber().set(2)
            atom2.mass().set(4.002602)
            var atom3 = db.create(Atom)
            atom3.name().set("Lithium")
            atom3.atomicNumber().set(3)
            atom3.mass().set(6.941)
            var atom4 = db.create(Atom)
            atom4.name().set("Beryllium")
            atom4.atomicNumber().set(4)
            atom4.mass().set(9.012182)
            var atom5 = db.create(Atom)
            atom5.name().set("Boron")
            atom5.atomicNumber().set(5)
            atom5.mass().set(10.811)

        when : 'We select all atoms with an atomic number greater than 2...'
            var atoms = db.select(Atom)
                                        .where(Atom.AtomicNumber)
                                        .greaterThan(2)
                                        .asList()
        then :
            atoms.size() == 3
            atoms[0] == atom3
            atoms[1] == atom4
            atoms[2] == atom5

        when : 'We select all atoms with an atomic number greater than 2 and a mass smaller than 10...'
            atoms = db.select(Atom)
                        .where(Atom.AtomicNumber)
                        .greaterThan(2)
                        .and(Atom.Mass)
                        .lessThan(10)
                        .asList()
        then :
            atoms.size() == 2
            atoms[0] == atom3
            atoms[1] == atom4

        when : 'We select all atoms with an atomic number greater than 2 and a mass smaller than 10, but only the first found atom...'
            atoms = db.select(Atom)
                        .where(Atom.AtomicNumber)
                        .greaterThan(2)
                        .and(Atom.Mass)
                        .lessThan(10)
                        .limit(1)
        then :
            atoms.size() == 1
            atoms[0] == atom3


        when : 'We select all atoms with an atomic number greater than 2 and a mass smaller than 10, and sort them by atomic number...'
            atoms = db.select(Atom)
                        .where(Atom.AtomicNumber)
                        .greaterThan(2)
                        .and(Atom.Mass)
                        .lessThan(10)
                        .orderDescendingBy(Atom.AtomicNumber)
                        .asList()
        then :
            atoms.size() == 2
            atoms[0] == atom4
            atoms[1] == atom3
    }

    def 'We cannot create a table for a model with a method that is not a property getter and has no implementation.'()
    {
        reportInfo """
            A model can have default methods, but if you declare a method, that is not a simple property getter,
            then the DataBase will not know what the implementation of this method should be,
            so it will throw an exception.
            Here the model that demonstrates this:
            ```
                public interface InvalidModel extends Model<InvalidModel> 
                {
                    interface Name extends Var<String> {}
                    Name name();
                    boolean iAmInvalidBecauseIHaveNoImplementation();
                }
            ```
            The model has a method that is not a property getter and has no implementation
            and therefore the database cannot create a table for this model, it simply does not know what to do.
        """
        given : 'We create a database instance for testing, the database will be opened in a test folder.'
            def db = DataBase.at(TEST_DB_FILE)
            db.dropAllTables()
        when : 'We create an invalid table'
            db.createTablesFor(InvalidModel)
        then : 'The database will throw an exception, because a method is not a simple property getter.'
            thrown(IllegalArgumentException)
    }

    def 'A model can have default method, which we can call without exceptions occurring.'()
    {
        reportInfo """
            Here we use the following model:
            ```
                public interface ModeWithDefaults extends Model<ModeWithDefaults> 
                {
                    interface Story extends Var<String> {}
                    Story story();
                    default boolean storyContains(String text) { return story().get().contains(text); }
                }
            ```
            The model has a default method which we want to call.
        """

        given : 'We create a database instance for testing, the database will be opened in a test folder.'
            def db = DataBase.at(TEST_DB_FILE)
            db.dropAllTables()
        when : 'We create a test table'
            db.createTablesFor(ModeWithDefaults)
        and :
            var m = db.create(ModeWithDefaults)
        then :
            noExceptionThrown()

        when : 'We add some text to the model...'
            m.story().set("Once upon a time...")
        then : 'We can use the default method to confirm certain things about the model.'
            m.storyContains("upon")
            !m.storyContains("uppon")
    }

    def 'Inheritance only works for "concrete" interface which have nor subtypes.'()
    {
        given : 'We create a database instance for testing, the database will be opened in a test folder.'
            def db = DataBase.at(TEST_DB_FILE)
            db.dropAllTables()
        when : 'We try to create a table for a model is also a supertype of another model.'
            db.createTablesFor(Animal)
        then : 'The database will throw an exception, because a model cannot inherit from another model.'
            thrown(IllegalArgumentException)

        when : 'We try to create a table for a model that is a subtype of another model.'
            db.createTablesFor(Rabbit, Raccoon)
        then : 'The database will not throw an exception, because the models are not a supertypes of other models.'
            noExceptionThrown()

        when : 'We create a rabbit and a raccoon and store some data in them...'
            var rabbit = db.create(Rabbit)
            rabbit.name().set("Bugs Bunny")
            rabbit.favouriteCarrot().set("The one with the most sugar")
            var raccoon = db.create(Raccoon)
            raccoon.name().set("Rocky")
            raccoon.favouriteGarbage().set("The one with the most sugar")
        then : 'We can select the 2 animals independently of each other.'
            var rabbits = db.select(Rabbit).asList()
            rabbits.size() == 1
            rabbits[0] == rabbit
            rabbits[0].name().get() == "Bugs Bunny"
            rabbits[0].favouriteCarrot().get() == "The one with the most sugar"
            var raccoons = db.select(Raccoon).asList()
            raccoons.size() == 1
            raccoons[0] == raccoon
            raccoons[0].name().get() == "Rocky"
            raccoons[0].favouriteGarbage().get() == "The one with the most sugar"
    }

    def 'We can clone model instances and they will have the same data but different ids.'()
    {
        given : 'We create a database instance for testing, the database will be opened in a test folder.'
            def db = DataBase.at(TEST_DB_FILE)
            db.dropAllTables()
        when : 'We create a test table'
            db.createTablesFor(Address)
        and : 'We create an instance...'
            var address = db.create(Address)
            address.street().set("Main Street")
            address.city().set("New York")
            address.country().set("USA")
        then : 'The instance has an id.'
            address.id().get() != null
        and :
            address.id().get() > 0
        when : 'We clone the instance...'
            var clone = address.clone()
        then : 'The clone has a different id.'
            clone.id().get() != address.id().get()
        and : 'They share the same data.'
            clone.street().get() == address.street().get()
            clone.city().get() == address.city().get()
            clone.country().get() == address.country().get()
    }

}
