package dal;

import java.util.List;

public class Person {

    public int id;

    public Person parent;
    public List<Person> children;

    public String name;
    public int age;
    public Address address;
    public List<Item> items;

}
