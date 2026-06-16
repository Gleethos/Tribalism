package app.user;

import app.models.sheet.AbilityScore;
import app.models.sheet.CharacterSheet;
import app.models.sheet.Identity;
import app.models.sheet.InventoryItem;
import app.models.sheet.SkillScore;
import app.models.sheet.Vitals;
import sprouts.Tuple;
import sprouts.Var;
import swingtree.UI;
import swingtree.UIForAnySwing;
import swingtree.threading.EventProcessor;

import javax.swing.JPanel;

import static swingtree.UI.*;

/**
 *  A lens-driven desktop character sheet. Every editable field is bound to a {@code zoomTo}
 *  lens exposed by {@link CharacterSheetViewModel}, which all focus on the single
 *  {@code Var<CharacterSheet>} root — so a keystroke produces a brand-new immutable sheet value
 *  (and, when the root is a persisted {@code CharacterModel.sheet()}, writes it straight to the
 *  database). See {@code SWING_TREE_SKILL.md} §4 and {@code VISION.md} §5.3.
 *  <p>
 *  Scalar fields (identity, vitals, notes) and the by-name ability/skill level editors are
 *  focus-stable; the inventory is a dynamic {@code Var<Tuple<InventoryItem>>} bound with
 *  per-item lenses (items implement {@code HasId}), demonstrating add/remove/edit of a list
 *  inside an immutable value tree.
 */
public final class CharacterSheetView extends JPanel
{
    public CharacterSheetView( CharacterSheetViewModel vm ) {
        of(this).withLayout(FILL.and(WRAP(1)).and(INS(16)))
        .withPrefSize(560, 760)
        .add(GROW_X, identitySection(vm))
        .add(GROW_X, vitalsSection(vm))
        .add(GROW_X, abilitiesSection(vm))
        .add(GROW_X, skillsSection(vm))
        .add(GROW_X.and(GROW_Y), inventorySection(vm))
        .add(GROW_X, notesSection(vm));
    }

    private static UIForAnySwing<?,?> identitySection( CharacterSheetViewModel vm ) {
        return titled("Identity",
            panel(FILL_X.and(WRAP(2)), "[shrink][grow]")
            .add(label("Forename"))    .add(GROW_X, textField(vm.forename()))
            .add(label("Surname"))     .add(GROW_X, textField(vm.surname()))
            .add(label("RoleModel"))        .add(GROW_X, textField(vm.role()))
            .add(label("Age"))         .add(GROW_X, numericTextField(vm.age()))
            .add(label("Height (m)"))  .add(GROW_X, numericTextField(vm.height()))
            .add(label("Weight (kg)")) .add(GROW_X, numericTextField(vm.weight()))
            .add(label("Description")).add(GROW_X, textField(vm.description()))
        );
    }

    private static UIForAnySwing<?,?> vitalsSection( CharacterSheetViewModel vm ) {
        return titled("Vitals",
            panel(FILL_X.and(WRAP(4)), "[shrink][grow][shrink][grow]")
            .add(label("HP"))    .add(GROW_X, numericTextField(vm.currentHealth()))
            .add(label("Max HP")).add(GROW_X, numericTextField(vm.maxHealth()))
            .add(label("Level")) .add(GROW_X, numericTextField(vm.level()))
            .add(label("XP"))    .add(GROW_X, numericTextField(vm.experience()))
        );
    }

    private static UIForAnySwing<?,?> abilitiesSection( CharacterSheetViewModel vm ) {
        return titled("Abilities",
            panel(FILL_X.and(WRAP(2)), "[grow][shrink]")
            .apply(ui -> {
                Tuple<AbilityScore> abilities = vm.abilities();
                for ( int i = 0; i < abilities.size(); i++ ) {
                    String ability = abilities.get(i).ability();
                    ui.add(GROW_X, label(ability));
                    ui.add("width 70px::", numericTextField(vm.abilityLevel(ability)));
                }
            })
        );
    }

    private static UIForAnySwing<?,?> skillsSection( CharacterSheetViewModel vm ) {
        return titled("Skills",
            panel(FILL_X.and(WRAP(2)), "[grow][shrink]")
            .apply(ui -> {
                Tuple<SkillScore> skills = vm.skills();
                for ( int i = 0; i < skills.size(); i++ ) {
                    String skill = skills.get(i).skill();
                    ui.add(GROW_X, label(skill));
                    ui.add("width 70px::", numericTextField(vm.skillLevel(skill)));
                }
            })
        );
    }

    private static UIForAnySwing<?,?> inventorySection( CharacterSheetViewModel vm ) {
        Var<Tuple<InventoryItem>> inventory = vm.inventory();
        return titled("Inventory",
            panel(FILL.and(WRAP(1)))
            .add(GROW,
                scrollPanels().addAll(inventory, (Var<InventoryItem> entry) -> {
                    Var<String>  name = entry.zoomTo(InventoryItem::name,     InventoryItem::withName);
                    Var<Integer> qty  = entry.zoomTo(InventoryItem::quantity, InventoryItem::withQuantity);
                    Var<Double>  wgt  = entry.zoomTo(InventoryItem::weight,   InventoryItem::withWeight);
                    return panel(FILL_X.and(WRAP(6)), "[grow][shrink][shrink][shrink][shrink][shrink]")
                        .add(GROW_X, textField(name))
                        .add(label("×")).add("width 60px::", numericTextField(qty))
                        .add(label("kg")).add("width 60px::", numericTextField(wgt))
                        .add(button("✕").onClick(it -> vm.removeInventoryItem(entry.get())));
                })
            )
            .add(GROW_X, button("Add item").onClick(it -> vm.addInventoryItem()))
        );
    }

    private static UIForAnySwing<?,?> notesSection( CharacterSheetViewModel vm ) {
        return titled("Notes", panel(FILL).add(GROW, textArea(vm.notes())));
    }

    private static UIForAnySwing<?,?> titled( String title, UIForAnySwing<?,?> body ) {
        return panel(FILL.and(WRAP(1)))
            .withStyle(it -> it.borderRadius(10).border(1, color(0.8, 0.8, 0.85)).padding(10).margin(4))
            .add(html("<b>" + title + "</b>"))
            .add(GROW, body);
    }

    /** Standalone demo: a sample sheet so every section shows content. */
    public static void main( String[] args ) {
        CharacterSheet sample = CharacterSheet.empty()
            .withIdentity(Identity.empty().withForename("Lyra").withSurname("Stormborn").withRole("Ranger").withAge(27))
            .withVitals(Vitals.starting().withMaxHealth(14).withCurrentHealth(11).withLevel(3))
            .withAbilityLevel("strength", 11).withAbilityLevel("dexterity", 16)
            .withAbilityLevel("constitution", 13).withAbilityLevel("wisdom", 14)
            .withSkillLevel("Aim", 6).withSkillLevel("Track", 5)
            .withInventory(Tuple.of(
                InventoryItem.named("Longbow").withQuantity(1).withWeight(0.9),
                InventoryItem.named("Arrow").withQuantity(20).withWeight(0.05)
            ))
            .withNotes("Last seen near the Whispering Pines.");

        CharacterSheetViewModel vm = new CharacterSheetViewModel(Var.of(sample));
        UI.show(f -> new CharacterSheetView(vm));
        EventProcessor.DECOUPLED.join();
    }
}
