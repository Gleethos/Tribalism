package dal.values;

import dal.api.Value;

/**
 *  A leaf {@link Value} used to demonstrate multi-level zoom querying: it lives nested
 *  inside {@link User}, which in turn is held by a model.
 */
public record Address(
    String street,
    String postalCode,
    String city
) implements Value {
    public Address withStreet( String street ) { return new Address(street, postalCode, city); }
    public Address withPostalCode( String postalCode ) { return new Address(street, postalCode, city); }
    public Address withCity( String city ) { return new Address(street, postalCode, city); }
}
