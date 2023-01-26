package dal

import dal.models.Address
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
        def db = new DataBase(TEST_DB_FILE)
        db.dropAllTables()
        db.close()
    }

    def 'We can create a "Person" and "Address" table.'() {

        given : 'We create a database instance for testing, the database will be opened in a test folder.'
            def db = new DataBase(TEST_DB_FILE)
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
            def db = new DataBase(TEST_DB_FILE)
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

        when :
            db.remove(person2)
        then :
            workplace.toString() == "Workplace[" +
                                        "id=1, " +
                                        "name=\"\", " +
                                        "fk_address_id=Address[id=1, country=\"\", street=\"\", postalCode=\"\", city=\"\"], " +
                                        "employees=[" +
                                            "Person[id=1, fk_address_id=Address[id=null, country=null, street=null, postalCode=null, city=null], lastName=\"\", firstName=\"\"], " +
                                        "]"

        cleanup:
            db.close()
    }

}
