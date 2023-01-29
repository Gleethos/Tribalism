package dal

import dal.api.DataBase
import dal.models.Ingredient
import spock.lang.Specification
import swingtree.api.mvvm.Action
import swingtree.api.mvvm.ValDelegate

class DataBase_Model_Properties_Spec extends Specification
{
    def TEST_DB_LOCATION = "test_data/"
    def TEST_DB_FILE = TEST_DB_LOCATION + "my.db"

    def setup() {
        def db = DataBase.at(TEST_DB_FILE)
        db.dropAllTables()
        db.close()
    }

    def 'The id property of a model has some useful default methods.'()
    {
        reportInfo """
            This feature specification not only demonstrates that the id property 
            has some useful default methods, but also hints at the ability
            to create your own custom methods for your own custom properties
            inside your models.
            For this example, we will use the "Ingredients" model:
            ```
                public interface Ingredient extends Model<Ingredient> 
                {
                    Var<String> name();
                    Var<Double> amount();
                    Var<String> unit();
                }
            ```
        """
        given : 'We create a database instance for testing, the database will be opened in a test folder.'
            def db = DataBase.at(TEST_DB_FILE)
            db.dropAllTables()
        and : 'We create the test table.'
            db.createTablesFor(Ingredient)
        and : 'We create some ingredients.'
            var ingredient1 = db.create(Ingredient)
            var ingredient2 = db.create(Ingredient)
            var ingredient3 = db.create(Ingredient)
            var ingredient4 = db.create(Ingredient)
        expect : 'Based on autoincrement, the id property of the ingredients should be 1, 2, 3, 4.'
            ingredient1.id().get() == 1
            ingredient2.id().get() == 2
            ingredient3.id().get() == 3
            ingredient4.id().get() == 4
        and : 'The default methods on the ids work as expected!'
            ingredient1.id().lessThan(2)
            ingredient2.id().lessThan(3)
            ingredient3.id().lessThan(4)
            ingredient4.id().lessThan(5)
            ingredient1.id().greaterThan(0)
            ingredient2.id().greaterThan(1)
            ingredient3.id().greaterThan(2)
            ingredient4.id().greaterThan(3)
            ingredient1.id().lessThanOrEqual(1)
            ingredient2.id().lessThanOrEqual(2)
            ingredient3.id().lessThanOrEqual(3)
            ingredient4.id().lessThanOrEqual(4)
            ingredient1.id().greaterThanOrEqual(1)
            ingredient2.id().greaterThanOrEqual(2)
            ingredient3.id().greaterThanOrEqual(3)
            ingredient4.id().greaterThanOrEqual(4)
    }

    def 'You can register listeners on model properties which get triggered when they are set.'()
    {
        reportInfo """
            This feature specification not only demonstrates that the properties
            of a model have a method for registering listener lambdas.
            For this example, we will use the "Ingredients" model:
            ```
                public interface Ingredient extends Model<Ingredient> 
                {
                    Var<String> name();
                    Var<Double> amount();
                    Var<String> unit();
                }
            ```
        """
        given : 'We create a database instance for testing, the database will be opened in a test folder.'
            def db = DataBase.at(TEST_DB_FILE)
            db.dropAllTables()
        and : 'We create the test table.'
            db.createTablesFor(Ingredient)
        and : 'A simple ingredient.'
            Ingredient ingredient = db.create(Ingredient)
        when : 'We register a listener on the name property.'
            var listenerTrace = []
            ingredient.name().onShowItem(new Action<String>() {
                @Override
                void accept(String delegate) {
                    listenerTrace << delegate
                }
            })
        and : 'We set the name property.'
            ingredient.name().set("Tomato")
        then : 'The listener should have been triggered.'
            listenerTrace.size() == 1
            listenerTrace[0] == "Tomato"
    }

}
