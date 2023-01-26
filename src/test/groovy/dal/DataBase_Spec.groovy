package dal

import dal.models.Address
import dal.models.Person
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
            table2 == "CREATE TABLE dal_models_Address_table (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, country TEXT NOT NULL, postalCode TEXT NOT NULL, street TEXT NOT NULL, city TEXT NOT NULL)"


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

        cleanup:
            db.close()
    }

}
