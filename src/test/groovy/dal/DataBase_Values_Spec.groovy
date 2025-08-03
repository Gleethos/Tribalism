package dal

import dal.api.DataBase
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
            db.sqlCodeOfTable(ClassRoom) == "CREATE TABLE dal_values_ClassRoom_table (" +
                        "id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                        "hash_code BIGINT NOT NULL, " +
                        "usages BIGINT NOT NULL, " +
                        "grade INTEGER NOT NULL, " +
                        "name TEXT NOT NULL, " +
                        "fk_teacher_id INTEGER REFERENCES dal_values_Person_table(id)" +
                    ")"
            db.sqlCodeOfTable(FullName) == "CREATE TABLE dal_values_FullName_table (" +
                        "id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                        "hash_code BIGINT NOT NULL, " +
                        "usages BIGINT NOT NULL, " +
                        "firstName TEXT NOT NULL, " +
                        "lastName TEXT NOT NULL" +
                    ")"
            db.sqlCodeOfTable(Person) == "CREATE TABLE dal_values_Person_table (" +
                        "id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                        "hash_code BIGINT NOT NULL, " +
                        "usages BIGINT NOT NULL, " +
                        "age INTEGER NOT NULL, " +
                        "fk_name_id INTEGER REFERENCES dal_values_FullName_table(id)" +
                    ")"
    }


    def 'We can create a `School` of students and store it in the database.'()
    {
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
            school.toString() == ""
    }

}
