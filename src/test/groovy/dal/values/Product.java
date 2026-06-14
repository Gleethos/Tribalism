package dal.values;

import dal.api.Value;

import java.time.LocalDateTime;

public record Product(
    String name,
    String description,
    LocalDateTime creationDate,
    double price
) implements Value {

    public Product withName(String name) { return new  Product(name, description, creationDate, price); }
    public Product withDescription( String description ) { return new  Product(description, description, creationDate, price); }
    public Product withCreationDate( LocalDateTime creationDate ) { return new  Product(name, description, creationDate, price); }
    public Product withPrice( double price ) { return new  Product(name, description, creationDate, price); }

}
