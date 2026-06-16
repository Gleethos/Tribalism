package dal.api;

/**
 *  The result of {@link Compare#isOfType(Class)}: a sum-typed (sealed value) field that has been
 *  narrowed to one of its permitted record subtypes {@code V}.
 *  <p>
 *  It is both:
 *  <ul>
 *      <li>a {@link Compare} of {@code V} — so a following {@link Compare#is(Object)} /
 *          {@link Compare#isNot(Object)} matches the whole narrowed value; and</li>
 *      <li>a terminal {@link Query} — so the type filter can stand on its own, e.g.
 *          {@code db.select(Drawing.class).where(Drawing::shape).isOfType(Circle.class).asList()}.</li>
 *  </ul>
 *
 * @param <M> The model type being queried.
 * @param <V> The permitted subtype the field was narrowed to.
 */
public interface NarrowedCompare<M extends Model<M>, V> extends Compare<M, V>, Query<M> {}
