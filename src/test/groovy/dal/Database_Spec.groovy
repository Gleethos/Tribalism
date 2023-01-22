package dal

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
class Database_Spec extends Specification
{
    def TEST_DB_LOCATION = "test_data/"
    def TEST_DB_FILE = TEST_DB_LOCATION + "my.db"

    def setup() {
        def db = new DataBase(TEST_DB_FILE)
        db.dropAllTables()
        db.close()
    }

    def 'The "TestModel" and "TestModel2" types can be turned into database tables'()
    {
        given : 'We creat a database instance for testing, the database will be opened in a test folder.'
            def db = new DataBase(TEST_DB_FILE)
            db.dropAllTables()
        expect : 'Initially there are no tables in the database.'
            db.listOfAllTableNames() == []

        when : 'We request a table creation for the model type in the database...'
            db.createTablesFor(Model1)

        then : 'The database should now contain a table with the name "TestModel"'
            db.listOfAllTableNames() == ["dal_Model1_table"]

        when : 'We want to create a table for another model...'
            db.createTablesFor(Model2)

        then : 'The database should now contain two tables.'
            db.listOfAllTableNames() == ["dal_Model1_table", "dal_Model2_table"]

        when : 'We try to creat a table that already exists...'
            db.createTablesFor(Model1)

        then : 'The database should still contain two tables.'
            db.listOfAllTableNames() == ["dal_Model1_table", "dal_Model2_table"]

        cleanup:
            db.close()
    }

    def 'A POJO can be stored in the database.'()
    {
        given : 'We create a database instance for testing, the database will be opened in a test folder.'
            def db = new DataBase(TEST_DB_FILE)
            db.dropAllTables()
            db.createTablesFor(Model1)
        and : 'We create a model instance.'
            def model = new Model1()
            model.id = 0
            model.name = "Test"
            model.age = 42
            model.address = "Test Address"
        when : 'We store the model in the database.'
            db.insert(model)
        then : 'The database should contain a table with the name "dal_Model1_table"'
            db.listOfAllTableNames() == ["dal_Model1_table"]
        and : 'The table should contain one entry.'
            db.selectAll(Model1).size() == 1
        and : 'The entry should be the same as the model.'
            var found = db.selectAll(Model1)[0]
            found.name == model.name
            found.age == model.age
            found.address == model.address

        when : 'We insert another test model with some random data...'
            def model2 = new Model1()
            model2.id = 0
            model2.email = "abc@gmx.com"
            model2.name = "Test2"
            model2.phone = 1234L
            db.insert(model2)
        then : 'The table should contain two entries.'
            db.selectAll(Model1).size() == 2
        and : 'The entries should be the same as the models.'
            var found2 = db.selectAll(Model1)[1]
            found2.id == 2
            found2.name == model2.name
            found2.age == model2.age
            found2.address == model2.address
            found2.email == model2.email
            found2.phone == model2.phone
    }

    def 'Trying to insert a POJO with an id that is 0 or null will raise an exception.'()
    {
        reportInfo """
            This is to prevent the user from inserting a model that already exists in the database.
            If the user wants to update a model, he should use the update method.
        """

        given : 'We create a database instance for testing, the database will be opened in a test folder.'
            def db = new DataBase(TEST_DB_FILE)
            db.dropAllTables()
            db.createTablesFor(Model1)
        and : 'We create a model instance.'
            def model = new Model1()
            model.id = 42
            model.name = "Test"
            model.age = 42
            model.address = "Test Address"
        when : 'We try to insert the model with an id of 0.'
            db.insert(model)
        then : 'An exception should be thrown.'
            thrown(IllegalArgumentException)
    }


}
