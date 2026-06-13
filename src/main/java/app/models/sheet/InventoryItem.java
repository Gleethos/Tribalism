package app.models.sheet;

import dal.api.Value;
import sprouts.HasId;

import java.util.UUID;

/**
 *  One entry in a character's inventory. Unlike abilities and skills (which have a natural
 *  name key), inventory items can legitimately duplicate, so each carries a stable
 *  identifier and implements {@link HasId} — this is what lets SwingTree bind a mutable
 *  {@code Var<Tuple<InventoryItem>>} with per-item editing lenses (see
 *  {@code SWING_TREE_SKILL.md} §5.2).
 *  <p>
 *  The persisted identifier component is named {@code uid} (a UUID held as a {@link String}
 *  so it round-trips as a plain {@code TEXT} column). It is deliberately <b>not</b> called
 *  {@code id}: Topsoil reserves the column name {@code id} for every value table's synthetic
 *  auto-increment primary key. The {@link HasId#id()} contract is satisfied by an explicit
 *  accessor that returns {@code uid}, which — not being a record component — is not persisted
 *  as its own column.
 */
public record InventoryItem(
    String uid,
    String name,
    int    quantity,
    double weight,
    String description
) implements Value, HasId<String> {

    public InventoryItem {
        if ( uid == null || name == null || description == null ) {
            throw new IllegalArgumentException("InventoryItem uid/name/description must not be null");
        }
    }

    /** {@inheritDoc} The stable per-item identity, backed by the persisted {@code uid}. */
    @Override
    public String id() { return uid; }

    /** @return A new item with a freshly generated uid, quantity 1 and no weight/description. */
    public static InventoryItem named(String name) {
        return new InventoryItem(UUID.randomUUID().toString(), name, 1, 0.0, "");
    }

    public InventoryItem withName(String name)               { return new InventoryItem(uid, name, quantity, weight, description); }
    public InventoryItem withQuantity(int quantity)          { return new InventoryItem(uid, name, quantity, weight, description); }
    public InventoryItem withWeight(double weight)           { return new InventoryItem(uid, name, quantity, weight, description); }
    public InventoryItem withDescription(String description) { return new InventoryItem(uid, name, quantity, weight, description); }
}
