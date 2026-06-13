package models.sheet

import app.models.sheet.AbilityScore
import app.models.sheet.CharacterSheet
import app.models.sheet.InventoryItem
import app.user.CharacterSheetViewModel
import groovy.transform.CompileDynamic
import spock.lang.Narrative
import spock.lang.Specification
import spock.lang.Title
import sprouts.From
import sprouts.Tuple
import sprouts.Var

@Title("The lens-driven CharacterSheet view model")
@Narrative('''

    The desktop (and, later, web) character sheet binds its fields to lenses exposed by
    `CharacterSheetViewModel`, all focused on one `Var<CharacterSheet>` root. This pins the
    lens-editing contract headlessly: writing through a field lens (as a SwingTree text field
    would) produces a new sheet on the root, and a field lens reflects changes made to the
    root from elsewhere.

''')
@CompileDynamic
class CharacterSheetViewModel_Spec extends Specification
{
    def 'Writing through a scalar lens updates the root sheet immutably.'() {
        given : 'A view model over an empty sheet.'
            var vm = CharacterSheetViewModel.empty()
            var before = vm.sheet().get()
        when : 'We set the forename and current health through their lenses (as the view would).'
            vm.forename().set(From.VIEW, "Gandalf")
            vm.currentHealth().set(From.VIEW, 7)
        then : 'The root sheet reflects both edits.'
            vm.sheet().get().identity().forename() == "Gandalf"
            vm.sheet().get().vitals().currentHealth() == 7
        and : 'The original sheet value is untouched (value semantics).'
            before.identity().forename() == ""
            before.vitals().currentHealth() == Vitals_starting_health()
    }

    def 'A by-name ability lens upserts and then updates that ability only.'() {
        given : 'A view model whose sheet already has one ability.'
            var sheet = Var.of(CharacterSheet.empty().withAbilityLevel("strength", 10))
            var vm = new CharacterSheetViewModel(sheet)
        when : 'We edit strength (an update) and dexterity (an insert) through by-name lenses.'
            vm.abilityLevel("strength").set(From.VIEW, 13)
            vm.abilityLevel("dexterity").set(From.VIEW, 8)
        then : 'Both abilities have their levels, and there is no duplicate strength.'
            sheet.get().abilities().contains(AbilityScore.of("strength", 13))
            sheet.get().abilities().contains(AbilityScore.of("dexterity", 8))
            sheet.get().abilities().size() == 2
        and : 'Reading the lens back yields the stored level.'
            vm.abilityLevel("strength").get() == 13
            vm.abilityLevel("dexterity").get() == 8
    }

    def 'A field lens reflects a change made to the root from elsewhere.'() {
        given : 'A view model over an empty sheet.'
            var vm = CharacterSheetViewModel.empty()
        when : 'Something replaces the whole sheet on the root (e.g. a reload).'
            vm.sheet().set(CharacterSheet.empty().withNotes("rewritten"))
        then : 'The notes lens reflects the new value.'
            vm.notes().get() == "rewritten"
    }

    def 'Adding and removing inventory items goes through the inventory lens.'() {
        given : 'A view model over an empty sheet.'
            var vm = CharacterSheetViewModel.empty()
        when : 'We add two items.'
            vm.addInventoryItem()
            vm.addInventoryItem()
        then : 'The sheet has two items.'
            vm.inventory().get().size() == 2
        when : 'We edit the first item through a per-item lens and remove the second.'
            var first = vm.inventory().get().get(0)
            var second = vm.inventory().get().get(1)
            Var<InventoryItem> firstLens = vm.inventory().zoomTo(
                    { Tuple<InventoryItem> t -> t.get(0) },
                    { Tuple<InventoryItem> t, InventoryItem it -> t.setAt(0, it) }
            )
            firstLens.set(From.VIEW, first.withName("Lantern").withQuantity(2))
            vm.removeInventoryItem(second)
        then : 'Only the edited first item remains, with its new name and quantity.'
            vm.inventory().get().size() == 1
            vm.inventory().get().get(0).name() == "Lantern"
            vm.inventory().get().get(0).quantity() == 2
        and : 'Its stable id is preserved across the edit (same item, new content).'
            vm.inventory().get().get(0).id() == first.id()
    }

    private static int Vitals_starting_health() { return app.models.sheet.Vitals.starting().currentHealth() }
}
