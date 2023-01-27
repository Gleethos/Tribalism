package dal

import dal.api.DataBase
import dal.models.Address
import dal.models.Atom
import dal.models.InvalidModel
import dal.models.ModeWithDefaults
import dal.models.Person
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
        def db = DataBase.of(TEST_DB_FILE)
        db.dropAllTables()
        db.close()
    }

    def 'We can create a "Person" and "Address" table.'() {

        given : 'We create a database instance for testing, the database will be opened in a test folder.'
            def db = DataBase.of(TEST_DB_FILE)
            db.dropAllTables()
        expect : 'Initially there are no tables in the database.'
            db.listOfAllTableNames() == []

        when : 'We request a table creation for the model type in the database...'
            db.createTablesFor(Person, Address)
            var table1 = db.sqlCodeOfTable(Person)
            var table2 = db.sqlCodeOfTable(Address)

        then : 'The database should now contain two tables.'
            db.listOfAllTableNames() as Set == ["dal_models_Person_table", "dal_models_Address_table"] as Set
        and : 'The table code is as expected!'
            table1 == "CREATE TABLE dal_models_Person_table (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, fk_address_id INTEGER REFERENCES dal_models_Address_table(id), lastName TEXT NOT NULL, firstName TEXT NOT NULL)"
            table2 == "CREATE TABLE dal_models_Address_table (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, country TEXT NOT NULL, street TEXT NOT NULL, postalCode TEXT NOT NULL, city TEXT NOT NULL)"


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
        """
        given : 'We create a database instance for testing, the database will be opened in a test folder.'
            def db = DataBase.of(TEST_DB_FILE)
            db.dropAllTables()
        expect : 'Initially there are no tables in the database.'
            db.listOfAllTableNames() == []

        when : 'We request the necessary table creations for the model type in the database...'
            db.createTablesFor(Workplace, Person, Address)

        then : 'The database should now contain two tables.'
            db.listOfAllTableNames() as Set == ["dal_models_Address_table", "dal_models_Workplace_table", "dal_models_Person_table", "employees_list_table"] as Set

        when : 'We create a new workplace with and address and 2 people working there.'
            var address = db.create(Address)
            var person = db.create(Person)
            var person2 = db.create(Person)
            var workplace = db.create(Workplace)
            workplace.address().set(address)
            workplace.employees().add(person)
            workplace.employees().add(person2)
        then :
            workplace.address().get() == address
            workplace.employees().toSet() == [person, person2] as Set

        and :
            workplace.toString() == "Workplace[" +
                                        "id=1, " +
                                        "name=\"\", " +
                                        "fk_address_id=Address[id=1, country=\"\", street=\"\", postalCode=\"\", city=\"\"], " +
                                        "employees=[" +
                                            "Person[id=1, fk_address_id=Address[id=null, country=null, street=null, postalCode=null, city=null], lastName=\"\", firstName=\"\"], " +
                                            "Person[id=2, fk_address_id=Address[id=null, country=null, street=null, postalCode=null, city=null], lastName=\"\", firstName=\"\"], ]" +
                                        "]"

        when :
            person2.firstName().set("Jane")
        then :
            workplace.toString() == "Workplace[" +
                                        "id=1, " +
                                        "name=\"\", " +
                                        "fk_address_id=Address[id=1, country=\"\", street=\"\", postalCode=\"\", city=\"\"], " +
                                        "employees=[" +
                                            "Person[id=1, fk_address_id=Address[id=null, country=null, street=null, postalCode=null, city=null], lastName=\"\", firstName=\"\"], " +
                                            "Person[id=2, fk_address_id=Address[id=null, country=null, street=null, postalCode=null, city=null], lastName=\"\", firstName=\"Jane\"], ]" +
                                        "]"

        //when :
        //    db.remove(person2)
        //then :
        //    workplace.toString() == "Workplace[" +
        //                                "id=1, " +
        //                                "name=\"\", " +
        //                                "fk_address_id=Address[id=1, country=\"\", street=\"\", postalCode=\"\", city=\"\"], " +
        //                                "employees=[" +
        //                                    "Person[id=1, fk_address_id=Address[id=null, country=null, street=null, postalCode=null, city=null], lastName=\"\", firstName=\"\"], " +
        //                                "]"

        cleanup:
            db.close()
    }


    def 'We can use the fluent query API of the database to select "Atoms"!'()
    {
        reportInfo """
            In this feature you can see how to use the fluent query API of the database 
            build database queries to select "Atoms" from the database.
        """
        given : 'We create a database instance for testing, the database will be opened in a test folder.'
            def db = DataBase.of(TEST_DB_FILE)
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
        """
        given : 'We create a database instance for testing, the database will be opened in a test folder.'
            def db = DataBase.of(TEST_DB_FILE)
            db.dropAllTables()
        when : 'We create an invalid table'
            db.createTablesFor(InvalidModel)
        then : 'The database will throw an exception, because a method is not a simple property getter.'
            thrown(IllegalArgumentException)
    }

    def 'A model can have default method, which we can call without exceptions occurring.'()
    {
        given : 'We create a database instance for testing, the database will be opened in a test folder.'
            def db = DataBase.of(TEST_DB_FILE)
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

}
