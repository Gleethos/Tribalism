package dal;

public @interface Reference {

    Class<?> entityClass();

    String field() default "id";

}
