package models.sheet

import app.models.Campaign
import app.models.Character
import app.models.CharacterModel
import app.models.GameMap
import app.models.Player
import app.models.User
import app.models.sheet.AbilityScore
import app.models.sheet.CharacterSheet
import app.models.sheet.Identity
import app.models.sheet.InventoryItem
import app.models.sheet.SkillScore
import app.models.sheet.Vitals
import app.user.CharacterSheetViewModel
import dal.api.DataBase
import groovy.transform.CompileDynamic
import spock.lang.Narrative
import spock.lang.Specification
import spock.lang.Title
import sprouts.From

@Title("CharacterSheet on the real Character model")
@Narrative('''

    The SheetHolder specs prove the CharacterSheet value tree round-trips in isolation. This
    spec proves it on the *real* Character model, through its full relational closure (Campaign,
    GameMap, Player, CharacterModel, User), and that a CharacterSheetViewModel bound to a
    persisted Character.sheet() property writes lens edits straight to the database.

''')
@CompileDynamic
class CharacterSheetOnCharacter_Spec extends Specification
{
    def TEST_DB_FILE = "test_data/character_sheet_spec.db"

    def setup() {
        def f = new File(TEST_DB_FILE)
        if ( f.isDirectory() ) f.deleteDir()
        f.parentFile?.mkdirs()
        if ( !f.exists() ) f.createNewFile()
        def db = DataBase.at(TEST_DB_FILE)
        db.dropAllTables()
        db.close()
    }

    private DataBase freshDb() {
        var db = DataBase.at(TEST_DB_FILE)
        db.dropAllTables()
        db.createTablesFor(
                // The Character relational closure:
                Character, CharacterModel, Campaign, GameMap, Player, User,
                // The CharacterSheet value tree:
                CharacterSheet, Identity, Vitals, AbilityScore, SkillScore, InventoryItem
        )
        return db
    }

    def 'A Character persists its CharacterSheet value, reconstructed from a fresh proxy.'() {
        given : 'A database with the full Character closure, and a character carrying a sheet.'
            var db = freshDb()
            var character = db.create(Character)
            var sheet = CharacterSheet.empty()
                    .withIdentity(Identity.empty().withForename("Gimli").withRole("Fighter"))
                    .withVitals(Vitals.starting().withMaxHealth(16).withCurrentHealth(16))
                    .withAbilityLevel("strength", 15)
                    .withInventory(sprouts.Tuple.of(InventoryItem.named("Axe").withWeight(2.0d)))
            character.sheet().set(sheet)
        when : 'We select a brand-new proxy for the same character.'
            var reloaded = db.select(Character, character.id().get())
        then : 'The whole sheet value tree comes back intact.'
            reloaded.sheet().get() == sheet
            reloaded.sheet().get().identity().forename() == "Gimli"
            reloaded.sheet().get().abilities().contains(AbilityScore.of("strength", 15))
        cleanup :
            db?.close()
    }

    def 'Lens edits via the view model on a persisted Character.sheet() reach the database.'() {
        given : 'A character with an empty sheet, and a view model bound to its persisted sheet property.'
            var db = freshDb()
            var character = db.create(Character)
            character.sheet().set(CharacterSheet.empty())
            var vm = new CharacterSheetViewModel(character.sheet())
        when : 'We edit through the view model lenses as the desktop view would.'
            vm.forename().set(From.VIEW, "Legolas")
            vm.currentHealth().set(From.VIEW, 9)
            vm.abilityLevel("dexterity").set(From.VIEW, 18)
            vm.addInventoryItem()
        then : 'A freshly selected character sees every persisted edit.'
            var reloaded = db.select(Character, character.id().get())
            reloaded.sheet().get().identity().forename() == "Legolas"
            reloaded.sheet().get().vitals().currentHealth() == 9
            reloaded.sheet().get().abilities().contains(AbilityScore.of("dexterity", 18))
            reloaded.sheet().get().inventory().size() == 1
        cleanup :
            db?.close()
    }
}
