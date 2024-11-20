package dal

import dal.api.DataBase
import dal.models.*
import groovy.transform.CompileDynamic
import spock.lang.Narrative
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Title
import sprouts.*

import java.time.Month
import java.util.concurrent.TimeUnit

@Title("Property Views")
@Narrative('''

    A property is more than just a wrapper around values.
    It has a rich APIs that exposes a plethora of methods,
    many of which are designed to inform you about
    their contents without actually exposing them to you.
    
    For example, there are methods like `Val::isEmpty` and `Val::isPresent`,
    which are used to check if a property is empty or not.
    These facts about the property can also be observed through a "view".
    For which we have the `viewIsEmpty()` and `viewIsPresent()` methods.
    
    Each of these methods return a `Viewable` property which will always be "up to date"
    with respect to the thing that is observed, and will be updated
    automatically when the observed thing changes.

''')
@Subject([Viewable, Var, Val, Vars, Vals])
@CompileDynamic
class DataBase_Model_Property_Views_Spec extends Specification
{
    def TEST_DB_LOCATION = "test_data/"
    def TEST_DB_FILE = TEST_DB_LOCATION + "my.db"

    def setup() {
        def db = DataBase.at(TEST_DB_FILE)
        db.dropAllTables()
        db.close()
    }

    def 'Use the "viewAs" method to create a dynamically updated view of a property.'()
    {
        reportInfo """
            The "viewAs" method is used to create a dynamically updated view of a database property.
            In essence it is a property observing another property and updating its value
            whenever the observed property changes.
            
            It is based on the following example interface:
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
        """
        given : 'We create a database instance for testing, the database will be opened in a test folder.'
            def db = DataBase.at(TEST_DB_FILE)
            db.dropAllTables()
            db.createTablesFor(Furniture)
            Furniture furniture = db.create(Furniture)
            furniture.name().set("Hello World")
        and : 'We create a property from the database.'
            Var<String> property = furniture.name()
        and : 'We create an integer view of the property.'
            Viewable<Integer> view = property.viewAs(Integer, { it.length() })
        expect : 'The view has the expected value.'
            view.orElseNull() == 11

        when : 'We change the value of the property.'
            property.set("Tofu")
        then : 'The view is updated.'
            view.orElseNull() == 4
    }

    def 'A primitive or string type view will map nulls to the types null object.'()
    {
        reportInfo """
            A nullable property, which is a property that allows null values, can be viewed as a 
            property of a primitive type, in which case the null values will be mapped to
            the "null object" of the given primitive type.
            
            For example, the null object of an Integer is 0, and the null object of a Boolean is false.
            The null object of a String is "" and so on...
            
            For this unit test we are using the following two tables:
            ```java
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
            And a `Person` model:
            ```java
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
        """
        given : 'We create a database instance for testing, the database will be opened in a test folder.'
            def db = DataBase.at(TEST_DB_FILE)
            db.dropAllTables()
            db.createTablesFor(Address, Person)
        and : 'We create a person without an address.'
            Person person = db.create(Person)
        and : 'A nullable property...'
            Var<Address> address = person.address()
        and : 'A couple of views...'
            Val<Boolean> exists = address.view( false, a -> !a.city().orElse("").isEmpty() )
            Val<Integer> size = address.viewAsInt( a -> (int) a.city().orElse("").length() )
            Val<String> name = address.viewAsString( a -> a.city().orElse("") )
            Val<Long> lastModified = address.view( 0L, a -> Long.parseLong(a.postalCode().orElse("")) )
            Val<Character> firstChar = address.view( '\u0000' as char, f -> f.city().orElse("").charAt(0) )
        expect : 'All views are non-nullable:'
            !exists.allowsNull()
            !size.allowsNull()
            !name.allowsNull()
            !lastModified.allowsNull()
            !firstChar.allowsNull()

        and : 'The views have the expected values.'
            exists.get() == false
            size.get() == 0
            name.get() == ""
            lastModified.get() == 0
            firstChar.get() == '\u0000'

        when : 'We change the value of the property to an actual address.'
            var newAddress = db.create(Address)
            newAddress.city().set("Wuppertal")
            newAddress.postalCode().set("42103")
            address.set(newAddress)
        then : 'The views are updated.'
            exists.get() == true
            size.get() != 0
            name.get() == "Wuppertal"
            lastModified.get() > 0
            firstChar.get() == 'W'
    }

    def 'Map null to custom values when viewing them as primitive types.'()
    {
        reportInfo """
            When viewing a nullable property as a primitive type, you can map the null values to
            custom values of the given type.
            
            For example, the following two tables are used:
            ```java
                public interface Ingredient extends Model<Ingredient>
                {
                    Var<String> name();
                    Var<Double> amount();
                    Var<String> unit();
                }
            ```
            And a `Food` model:
            ```java
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
        """
        given : 'We create a database instance for testing, the database will be opened in a test folder.'
            def db = DataBase.at(TEST_DB_FILE)
            db.dropAllTables()
            db.createTablesFor(Ingredient, Food)
        and :
            Ingredient ingredientModel = db.create(Ingredient)
            Food foodModel = db.create(Food)
            foodModel.name().set("Vegan Burger")
            foodModel.calories().set(300.0)
            foodModel.fat().set(10.0)
            foodModel.carbs().set(40.0)
            foodModel.protein().set(20.0)
            foodModel.ingredients().add(ingredientModel)
        and : 'A couple of views...'
            Var<Ingredient> ingredientProp = foodModel.ingredients().at(0)
            Val<Integer> anInt = ingredientProp.viewAsInt( r -> r == null ? 42 : r.name().orElse("").length() )
            Val<Double> aDouble = ingredientProp.viewAsDouble( r -> r == null ? 3.14 : r.amount().orElse(0.0) )
            Val<Short> aShort = ingredientProp.viewAs(Short, r -> r == null ? (short)-1 : (short)r.unit().orElse("").length() )
            Val<String> aString = ingredientProp.viewAsString( r -> r == null ? "?" : r.name().orElse("") )
        expect : 'All views are non-nullable:'
            !anInt.allowsNull()
            !aDouble.allowsNull()
            !aShort.allowsNull()
            !aString.allowsNull()

        and : 'The views have the expected values.'
            anInt.get() == 0
            aDouble.get() == 0.0
            aShort.get() == 0
            aString.get() == ""

        when : 'We change the value of the property.'
            var newIngredient = db.create(Ingredient)
            newIngredient.name().set("Noodles")
            newIngredient.amount().set(100.0)
            newIngredient.unit().set("g")
            ingredientProp.set(newIngredient)
        then : 'The views are updated.'
            anInt.get() == 7
            aDouble.get() == 100.0
            aShort.get() == 1
            aString.get() == "Noodles"
    }

    def 'Use the "view" to create a view of a property of the same type.'()
    {
        reportInfo """
            The "view" method can be used to create a view of a property of the same type,
            but with some transformation applied to it.
            
            For this example we use the `Atom` model:
            ```java
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
        given :
            def db = DataBase.at(TEST_DB_FILE)
            db.dropAllTables()
            db.createTablesFor(Atom)
        and : 'An `Atom` model and one of its properties.'
            Atom atom = db.create(Atom)
            atom.name().set("John")
            Var<String> name = atom.name()
        and : 'A view of the property...'
            Val<String> nameView = name.view( n -> n + " Doe" )
        expect : 'The view has the expected value.'
            nameView.get() == "John Doe"

        when : 'We change the value of the property.'
            name.set("Jane")
        then : 'The view is updated.'
            nameView.get() == "Jane Doe"
    }

    def 'The `viewAsString()` method can be used to create a null safe view of a property of any type as a String.'()
    {
        reportInfo """
            The `viewAsString()` method can be used to create a view of a property of any type as a String.
            The null values are mapped to the empty string in order to make the view null safe, 
            which is important inside of a GUI or when displaying the value in a user interface
            where null pointer exceptions are not acceptable.
            
            In this example, we use the `Person` model:
            ```java
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
        """
        given :
            def db = DataBase.at(TEST_DB_FILE)
            db.dropAllTables()
            db.createTablesFor(Person, Address)
        and : "A property based on the models... let's say Address..."
            Person person = db.create(Person)
            Var<Address> address = person.address()
        and : "A view of the property as a String..."
            Val<String> addressView = address.viewAsString()
        expect : "The string based view is null safe:"
            addressView.type() == String
            addressView.get() == ""
            !addressView.allowsNull()

        when : "We change the value of the property."
            var newAddress = db.create(Address)
            newAddress.city().set("Kyoto")
            newAddress.country().set("Japan")
            address.set(newAddress)
        then : "The view is updated to string representation of the date."
            addressView.get() == "Address[id=1, city=\"Kyoto\", country=\"Japan\", postalCode=\"\", street=\"\"]"
    }

    def 'The `viewAsInt()` method can be used to create a null safe view of a property of any type as an int.'()
    {
        reportInfo """
            The `viewAsInt()` method can be used to create a view of a property of any type as an int.
            The integer is computed by first converting the value to a string and then parsing the string to an int.
            So it is important to make sure that the value can be converted to a string and that the 
            string can be parsed to an int.
            In this example, we use a `Short` property, which can easily be converted to a string and parsed to an int.
            
            The null values are mapped to 0 in order to make the view null safe, 
            which is important inside of a GUI or when displaying the value in a user interface
            where null pointer exceptions are not acceptable.
            
            In this example, we use the `Atom` model:
            ```java
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
        given :
            def db = DataBase.at(TEST_DB_FILE)
            db.dropAllTables()
            db.createTablesFor(Atom)
        and : "A property based on... let's say a Short..."
            Atom atom = db.create(Atom)
            Val<Short> numShortView = atom.atomicNumber().viewAs(Short, it -> (short)it)
        and : "A view of the property as an int..."
            Val<Integer> numIntView = numShortView.viewAsInt()
        expect : 'The source property as well as the view do not allow nulls for integer.'
            !numShortView.allowsNull()
            !atom.atomicNumber().allowsNull()
        and : "The (integer) view on the other hand is null safe."
            numIntView.type() == Integer
            numIntView.get() == 0
            !numIntView.allowsNull()

        when : "We change the value of the atomic number."
            atom.atomicNumber().set(42)
        then : "The view is updated to the int representation of the short."
            numIntView.get() == 42
    }

    def 'The `viewAsDouble()` method can be used to create a null safe view of a property of any type as a double.'()
    {
        reportInfo """
            The `viewAsDouble()` method can be used to create a view of a property of any type as a double.
            The double is computed by first converting the value to a string and then parsing the string to a double.
            So it is important to make sure that the value can be converted to a string and that the 
            string can be parsed to a double.
            In this example, we use a `Float` property, which can easily be converted to a string and parsed to a double.
            
            The null values are mapped to 0.0 in order to make the view null safe, 
            which is important inside of a GUI or when displaying the value in a user interface
            where null pointer exceptions are not acceptable.
            
            In this example, we use the `Train` model:
            ```java
                public interface Train extends dal.api.Model<Train>
                {
                    interface Name extends sprouts.Var<String> {}
                    interface Waggons extends sprouts.Var<Integer> {}
                    interface Speed extends sprouts.Var<Float> {}
                    interface IsElectric extends sprouts.Var<Boolean> {}
                    Name name();
                    Waggons waggons();
                    Speed speed();
                    IsElectric isElectric();
                }
            ```
        """
        given : 'We first set up the database and the tables.'
            def db = DataBase.at(TEST_DB_FILE)
            db.dropAllTables()
            db.createTablesFor(Train)
        and :
            Train train = db.create(Train)
        and : "A property based on... let's say a Float representing the speed of the train."
            Var<Float> speed = train.speed()
        and : "A view of the property as a double..."
            Val<Double> numView = speed.viewAsDouble()

        expect : 'The source property is not nullable, because float is a primitive type.'
            !speed.allowsNull()
        and : "The (double) view on the other hand is null safe."
            numView.type() == Double
            numView.get() == 0.0
            !numView.allowsNull()

        when : "We change the value of the property."
            speed.set(3.14f)
        then : "The view is updated to the double representation of the float."
            numView.get() == 3.14
    }

    def 'A view can handle viewing different sub-types of the given source type.'()
    {
        reportInfo """
            The `viewAs(Class,..)` method takes 2 arguments: the type we want to view 
            through the view property, and a function that transforms the value of the source property.
            If the viewed type is a more general type than the source type, the view will be able to handle
            viewing different sub-types of the given source type depending on the transformation function.
            
            For this test case we are going to use the `Atom` model,
            which looks like this:
            ```java
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
        given :
            def db = DataBase.at(TEST_DB_FILE)
            db.dropAllTables()
            db.createTablesFor(Atom)
        and : 'A property based on the generic type `Integer`...'
            Atom atom = db.create(Atom)
            atom.atomicNumber().set(42)
            Var<Integer> num = atom.atomicNumber()
        and : 'A view of the property as a generic `Number`...'
            Viewable<Number> numView = num.viewAs(Number, n -> n < 0 ? n.floatValue() : n.doubleValue() )
        expect : 'The view is of the given type and has the expected value.'
            numView.type() == Number
            numView.get() == 42.0
            numView.get() instanceof Double

        when : 'We change the value of the property so that the view holds a float.'
            num.set(-3)
        then : 'The view is updated to the float representation of the integer.'
            numView.type() == Number
            numView.get() == -3.0
            numView.get() instanceof Float
    }

    def 'A view can use specific items to indicate mapping to `null` or exceptions during mapping.'()
    {
        reportInfo """
            The `view` method allows to provide a specific `nullObject` to be used when the mapping function returns
            `null` and an `errorObject` to be used when an error occurs.
            
            For this test case we are going to use the `Bike` model,
            which looks like this:
            ```java
                public interface Bike extends dal.api.Model<Bike>
                {
                    interface Brand extends sprouts.Var<String> {}
                    interface Model extends sprouts.Var<String> {}
                    interface Year extends sprouts.Var<String> {}
                    Brand brand();
                    Model model();
                    Year year();
                }
            ```
        """
        given : 'We first set up the database and the tables.'
            def db = DataBase.at(TEST_DB_FILE)
            db.dropAllTables()
            db.createTablesFor(Bike)
        and : 'A property of type integer.'
            Bike bike = db.create(Bike)
            bike.brand().set("Slick and cool")
            bike.year().set("2026")
            var integerVar = bike.year().viewAsInt( y -> Integer.parseInt(y) - 2020 )
        and : 'A string view based on the property.'
            var view = integerVar.view("negative", "error", i -> i < 0 ? null : String.format("3 / %d = %.1f", i, 3 / i))
        expect : 'The view has the expected value.'
            view.get() == "3 / 6 = 0.5"
        when : 'We update the property so that the mapping function returns `null`.'
            bike.year().set("2019")
        then : 'The view has the expected `nullValue`.'
            view.get() == "negative"
        when : 'We update the property so that the mapping function throws an exception.'
            bike.year().set("2020")
        then : 'The view has the expected `errorValue`.'
            view.get() == "error"
    }

    def 'A view is updated only once for every change, or not updated at all if no change occurred.'()
    {
        reportInfo """
            The state of a view is only updated when the source property changes.
            And this is done only a single time for every change.
            However, if a change event is triggered manually, the view is also updated
            even if the value of the source property has not changed.
            
            For this test case we are going to use the `Ingredient` model,
            which looks like this:
            ```java
                public interface Ingredient extends Model<Ingredient>
                {
                    interface Name extends Var<String> {}
                    interface Amount extends Var<Double> {}
                    interface Unit extends Var<String> {}
                    Name name();
                    Amount amount();
                    Unit unit();
                }
            ```
        """
        given :
            def db = DataBase.at(TEST_DB_FILE)
            db.dropAllTables()
            db.createTablesFor(Ingredient)
        and : 'A simple source property...'
            Ingredient ingredient = db.create(Ingredient)
            ingredient.name().set("Onion")
            Var<String> source = ingredient.name()
            var changes = 0
        and : 'A view of the source property as a byte representation of the length of the string.'
            Viewable<Byte> view = source.viewAs(Byte, s -> {
                changes++
                return (byte) s.length()
            })
        expect : 'The view has the expected value.'
            view.get() == 5
            changes == 1

        when : 'We change the value of the source property to the same value.'
            source.set("Onion")
        then : 'The view is not updated.'
            view.get() == 5
            changes == 1

        when : 'We change the value of the source property to a different value.'
            source.set("Tomato")
        then : 'The view is updated.'
            view.get() == 6
            changes == 2

        when : 'We try to trigger a view update through a manual change event.'
            source.fireChange(From.VIEW_MODEL)
        then : 'The view is updated despite the value of the source property not changing.'
            view.get() == 6
            changes == 3
    }

    def 'There are various kinds of convenience methods for creating live views of properties.'()
    {
        reportInfo """
            There are various convenience methods for creating live views of properties.
            These methods are used to create views of properties of different types.
            
            For this test case we are going to use the `Food` model,
            which looks like this:
            ```java
                public interface Food extends Model<Food>
                {
                    interface Name extends Var<String> {}
                    interface Calories extends Var<Double> {}
                    interface Fat extends Var<Double> {}
                    interface Carbs extends Var<Double> {}
                    interface Protein extends Var<Double> {}
                    Vars<Ingredient> ingredients();
                    Name name();
                    Calories calories();
                    Fat fat();
                    Carbs carbs();
                    Protein protein();
                }
            ```
        """
        given :
            def db = DataBase.at(TEST_DB_FILE)
            db.dropAllTables()
            db.createTablesFor(Food, Ingredient)
        and : 'We create a property...'
            Food foodModel = db.create(Food)
            foodModel.name().set("Channa Masala")
            Var<String> foodName = foodModel.name()
        and : 'Different kinds of views:'
            Viewable<Integer> words    = foodName.viewAsInt( f -> f.split(" ").length )
            Viewable<Integer> words2   = words.view({it * 2})
            Viewable<Double>  average  = foodName.viewAsDouble( f -> f.chars().average().orElse(0) )
            Viewable<Boolean> isLong   = foodName.viewAs(Boolean, f -> f.length() > 14 )
            Viewable<String> firstWord = foodName.view( f -> f.split(" ")[0] )
            Viewable<String> lastWord  = foodName.view( f -> f.split(" ")[f.split(" ").length-1] )
        expect : 'The views have the expected values.'
            words.get() == 2
            words2.get() == 4
            average.get().round(2) == 92.92d
            isLong.get() == false
            firstWord.get() == "Channa"
            lastWord.get() == "Masala"

        when : 'We change the value of the property.'
            foodName.set("Tofu Tempeh Saitan")
        then : 'The views are updated.'
            words.get() == 3
            words2.get() == 6
            average.get().round(2) == 94.28d
            isLong.get() == true
            firstWord.get() == "Tofu"
            lastWord.get() == "Saitan"
    }

    def 'The `viewIsEmpty()` method returns a property that is true when the original property is empty, and false otherwise.'()
    {
        reportInfo """
            Calling `viewIsEmpty()` on a property will
            be a view on the `isEmpty()` method of the property.
            So when the boolean returned by `isEmpty()` changes,
            the value of the view will change too.
            
            Note that in this test we use a nullable property!
            This is be cause only a nullable property can be empty.
            
            For this test case we are going to use the `Person` model:
            ```java
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
        """
        given :
            def db = DataBase.at(TEST_DB_FILE)
            db.dropAllTables()
            db.createTablesFor(Person, Address)
        and : 'A property which is not empty.'
            Person person = db.create(Person)
            person.firstName().set("John")
            person.lastName().set("Doe")
            person.address().set(db.create(Address))
            Var<String> address = person.address()
        and : 'A view of the "emptiness" of the property.'
            Val<Boolean> isEmpty = address.viewIsEmpty()
        expect : 'The view is false initially.'
            !isEmpty.get()
        when : 'We change the value of the property to null.'
            address.set(null)
        then : 'The view becomes true.'
            isEmpty.get()
    }

    def 'A `viewIsEmpty()` property from a non nullable property is always false.'()
    {
        reportInfo """
            A non-nullable property does not permit null items,
            which means that it cannot be empty.
            Therefore, the view returned by `viewIsEmpty()`
            will always be false.
            
            For this test case we are going to use the `Person` model:
            ```java
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
        """
        given :
            def db = DataBase.at(TEST_DB_FILE)
            db.dropAllTables()
            db.createTablesFor(Person, Address)
        and : 'A non-nullable property.'
            Person person = db.create(Person)
            person.firstName().set("John")
            Var<String> name = person.firstName()
        and : 'A view of thr `isEmpty()` flag of the property.'
            Viewable<Boolean> isEmpty = name.viewIsEmpty()
        expect : 'Initially, the view is false.'
            !isEmpty.get()

        when : 'We change the value of the property to an empty string.'
            name.set("")
        then : 'The view is still false, because the property does not contain null!'
            !isEmpty.get()

        when : 'We try to sneak in a null value to make it empty...'
            name.set(null)
        then : 'Boom! The property fights back by throwing an exception.'
            thrown(NullPointerException)
    }

    def 'The `viewIsPresent()` method returns a property that is true when the original property is not empty, and false otherwise.'()
    {
        reportInfo """
            Calling `viewIsPresent()` on a property will
            be a view on the `isPresent()` method of the property.
            So when the boolean returned by `isPresent()` changes,
            the value of the view will change too.
            
            Note that in this test we use a nullable property!
            This is be cause only a nullable property can be empty.
            
            In this test case we are going to use the `Person` model,
            where an `Address` serves as a nullable property.
            ```java
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
            And the `Address` model:
            ```java
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
        """
        given :
            def db = DataBase.at(TEST_DB_FILE)
            db.dropAllTables()
            db.createTablesFor(Person, Address)
        and : 'A nullable property which is not empty.'
            Person person = db.create(Person)
            person.firstName().set("John")
            person.lastName().set("Doe")
            person.address().set(db.create(Address))
            Var<Address> address = person.address()
        and : 'A view of the "presence" of the item of the age property.'
            Viewable<Boolean> isPresent = address.viewIsPresent()
        expect : 'The view is true initially, because the address is not null.'
            isPresent.get()
        when : 'We change the value of the property to null, to make it empty.'
            address.set(null)
        then : 'The view becomes false, because now the property has null as its item.'
            !isPresent.get()
    }

    def 'A `viewIsPresent()` property from a non nullable property is always true.'()
    {
        reportInfo """
            A non-nullable property does not permit null items,
            which means that it cannot be empty.
            Therefore, the view returned by `viewIsPresent()`
            will always be true.
            
            For this test case we are going to use the `Atom` model:
            ```java
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
        given :
            def db = DataBase.at(TEST_DB_FILE)
            db.dropAllTables()
            db.createTablesFor(Atom)
        and : 'A non-nullable property.'
            Atom atom = db.create(Atom)
            atom.name().set("Unobtainium")
            atom.atomicNumber().set(25)
            Var<Integer> atomicNum = atom.atomicNumber()
        and : 'A view of the `isPresent()` flag of the property.'
            Viewable<Boolean> isPresent = atomicNum.viewIsPresent()
        expect : 'The view is true initially, because 25 is not null.'
            isPresent.get()

        when : 'We try to change the value of the property to null.'
            atomicNum.set(null)
        then : 'The property fights back by throwing an exception.'
            thrown(NullPointerException)
    }

    def 'Use `viewAsInt(int,Function)` to view a nullable property as a non null integer.'() {
        reportInfo """
            The `viewAsInt(int,Function)` method creates and returns an integer based live property view
            from a nullable property of any type that uses a default value to represent null and a function
            to convert the non null value to an integer.
            The view will be updated automatically
            when the original property changes.
        """
        given : 'A String property holding a japanese sentence.'
            Var<String> sentence = Var.ofNullable(String, "ブランコツリーはいいですね")
        and : 'A view on the length of the sentence with a unique default value.'
            Viewable<Integer> length = sentence.viewAsInt(42,String::length)
        expect : 'The view is 13 initially and it confirms that it is indeed a view.'
            length.get() == 13
            length.isView()
        when : 'We change the value of the property to null.'
            sentence.set(null)
        then : 'The view becomes 42.'
            length.get() == 42
        when : 'We change the value of the property to an empty string.'
            sentence.set("")
        then : 'The view becomes 0.'
            length.get() == 0
    }

    def 'Use `viewAsDouble(double,Function)` to view a nullable property as a non null double.'() {
        reportInfo """
            The `viewAsDouble(double,Function)` method creates and returns a double based live property view
            from a nullable property of any type that uses a default value to represent null and a function
            to convert the non null value to a double.
            The view will be updated automatically
            when the original property changes.
            
            For this test case we are going to use the `Book` model:
            ```java
                public interface Book extends Model<Book> 
                {
                    interface Title extends Var<String> {}
                    interface Author extends Var<Person> {}
                    interface ISBN extends Var<String> {}
                    Title title();
                    Author author();
                    ISBN isbn();
                }
            ```
        """
        given :
            def db = DataBase.at(TEST_DB_FILE)
            db.dropAllTables()
            db.createTablesFor(Book, Person, Address)
        and : 'A String property holding a nullable author sentence.'
            var book = db.create(Book)
            book.title().set("Animal Liberation - Peter Singer")
            book.isbn().set("978-0-06-171130-5")
            Person peter = db.create(Person)
            peter.firstName().set("Peter")
            peter.lastName().set("Singer")
            book.author().set(peter)
            Var<Person> author = book.author()
        and : 'A view on the average word length of the authors name with a unique default value.'
            Viewable<Double> averageWordLength = author.viewAsDouble(-0.5, a -> {
                                                        if ( a == null )
                                                            return null
                                                        var words = a.firstName().orElse("").split(" ") as List<String>
                                                        return words.stream().mapToInt(String::length).average().orElse(-1)
                                                    })
        expect : 'The view is 4.0 initially and it confirms that it is indeed a view.'
            averageWordLength.get() == 5.0
            averageWordLength.isView()
        when : 'We change the value of the property to null.'
            author.set(null)
        then : 'The view becomes -0.5.'
            averageWordLength.get() == -0.5
        when : 'We change the value of the property to a new author without name.'
            Person newAuthor = db.create(Person)
            newAuthor.firstName().set("")
            author.set(newAuthor)
        then : 'The view contains 0.0 because the average of an empty string is 0.'
            averageWordLength.get() == 0.0
    }

    def 'Use the `viewAsString(String,Function)` method to view a nullable property as a non null String.'() {
        reportInfo """
            The `viewAsString(String,Function)` method creates and returns a String based live property view
            from a nullable property of any type that uses a default value to represent null and a function
            to convert the non null value to a String.
            The view will be updated automatically
            when the original property changes.
            
            For this test case we are going to use the `Book` model:
            ```java
                public interface Book extends Model<Book> 
                {
                    interface Title extends Var<String> {}
                    interface Author extends Var<Person> {}
                    interface ISBN extends Var<String> {}
                    Title title();
                    Author author();
                    ISBN isbn();
                }
            ```
        """
        given :
            def db = DataBase.at(TEST_DB_FILE)
            db.dropAllTables()
            db.createTablesFor(Book, Person, Address)
        and : 'A property holding nullable `TimeUnit` enum items.'
            Book book = db.create(Book)
            Person author = db.create(Person)
            author.firstName().set("Peter")
            author.lastName().set("Singer")
            book.author().set(author)
            Var<Person> authorProp = book.author()
        and : 'A view on the lowercase name of the time unit with a unique default value.'
            Viewable<String> lowerCaseName = authorProp.viewAsString("unknown", u -> u.firstName().map(String::toLowerCase).orElseNull())
        expect : 'The view is "seconds" initially and it confirms that it is indeed a view.'
            lowerCaseName.get() == "peter"
            lowerCaseName.isView()
        when : 'We change the value of the property to null.'
            authorProp.set(null)
        then : 'The view becomes "unknown" because the property is empty.'
            lowerCaseName.get() == "unknown"
    }

    def 'The channel of a property change event will propagate to its views.'()
    {
        reportInfo """
            Every mutation to a property can have a channel associated with it.
            You can call the `Var.set(Channel,T)` method to mutate the property with a custom channel,
            and then in your change listeners you can check the channel on the property delegate!
            
            This exact same principle is also true for the views of a property
            whose change event listeners will also receive the channel of the origin property.
            
            For this test case we are going to use the `Region` model:
            ```java
                public interface Region extends Model<Region>
                {
                    Var<Month> warmestMonth();
                    Var<Month> coldestMonth();
                    Var<String> name();
                }
            ```
        """
        given :
            def db = DataBase.at(TEST_DB_FILE)
            db.dropAllTables()
            db.createTablesFor(Region)
        and : 'A property based on an enum and 3 different views.'
            var region = db.create(Region)
            region.name().set("Tropical")
            region.warmestMonth().set(Month.AUGUST)
            region.coldestMonth().set(Month.JANUARY)
            var monthProperty = region.warmestMonth()
            var intView = monthProperty.viewAsInt(Month::ordinal)
            var stringView = monthProperty.viewAsString(Month::name)
            var firstMonthOfQuarter = monthProperty.view(Month::firstMonthOfQuarter)
        and : 'A trace list and a change listener that listens to changes on the views.'
            var trace1 = []
            var trace2 = []
            var trace3 = []
            intView.onChange(From.ALL, i -> trace1 << i.channel())
            stringView.onChange(From.VIEW, s -> trace2 << s.channel())
            firstMonthOfQuarter.onChange(From.VIEW_MODEL, m -> trace3 << m.channel())
        expect : 'The trace list is empty and there are no change listeners registered.'
            trace1.isEmpty()
            trace2.isEmpty()
            trace3.isEmpty()

        when : 'We change the value of the property 3 times with different channels and values.'
            monthProperty.set(From.ALL, Month.JANUARY)
            monthProperty.set(From.VIEW, Month.NOVEMBER)
            monthProperty.set(From.VIEW_MODEL, Month.SEPTEMBER)
        then : 'The listeners are notified of the new value of the views with the correct channels.'
            trace1 == [From.ALL, From.VIEW, From.VIEW_MODEL]
            trace2 == [From.ALL, From.VIEW]
            trace3 == [From.ALL, From.VIEW_MODEL]
    }

    def 'Changing the value of a property through the `.set(From.VIEW, T)` method will also affect its views'()
    {
        reportInfo """
            Note that you should use `.set(From.VIEW, T)` inside your view to change 
            the value of the original property.
            It is different from a regular `set(T)` (=`.set(From.VIEW_MODEL, T)`) in 
            that the `set(T)` method
            runs the mutation through the `From.VIEW_MODEL` channel.
            This the difference here is the purpose and origin of the mutation,
            `VIEW` changes are usually caused by user actions and `VIEW_MODEL`
            changes are caused by the application logic.
            Irrespective as to how the value of the original property is changed,
            the views will be updated.
            
            For this test case we are going to use the `Movie` model:
            ```java
                public interface Movie extends Model<Movie>
                {
                    interface Title extends Var<String> {}
                    interface Year extends Var<Integer> {}
                    interface Rating extends Var<Double> {}
                    Title title();
                    Year year();
                    Rating rating();
                }
            ```
        """
        given :
            def db = DataBase.at(TEST_DB_FILE)
            db.dropAllTables()
            db.createTablesFor(Movie)
        and : 'We create a property...'
            Movie movie = db.create(Movie)
            movie.title().set("Earthlings Documentary")
            Var<String> title = movie.title()
        and : 'We create a view of the property.'
            Viewable<Integer> words = title.viewAsInt( f -> f.split(" ").length )
        expect : 'The view has the expected value.'
            words.get() == 2

        when : 'We change the value of the food property through the `.set(From.VIEW, T)` method.'
            title.set(From.VIEW, "Faster Than Light")
        then : 'The view is updated.'
            words.get() == 3
    }

    def 'You can recognize a property view from its String representation.'()
    {
        reportInfo """
            A property view has a specific string representation that can be used to recognize it.
            The string representation of a property view starts with "View" followed by the item
            type and square brackets
            containing the current item of the view.
            
            For this test case we are going to use the `Train` model:
            ```java
                public interface Train extends dal.api.Model<Train>
                {
                    interface Name extends sprouts.Var<String> {}
                    interface Waggons extends sprouts.Var<Integer> {}
                    interface Speed extends sprouts.Var<Float> {}
                    interface IsElectric extends sprouts.Var<Boolean> {}
                    Name name();
                    Waggons waggons();
                    Speed speed();
                    IsElectric isElectric();
                }
            ```
        """
        given :
            def db = DataBase.at(TEST_DB_FILE)
            db.dropAllTables()
            db.createTablesFor(Train)
        and : 'A property based on a string.'
            Train train = db.create(Train)
            train.name().set("Shinkansen")
            var stringProperty = train.name()
        and : 'A view of the property as a byte representation of the length of the string.'
            Val<Byte> view = stringProperty.viewAs(Byte, s -> (byte) s.length())
        expect : 'The string representation of the view is as expected.'
            view.toString() == "View<Byte>[10]"

        when : 'We update the view to have a custom id String.'
            view = view.withId("patient_age")
        then : 'The string representation of the view is as expected.'
            view.toString() == "View<Byte>[patient_age=10]"
    }

}
