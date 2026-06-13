package models.sheet

import app.models.sheet.AbilityScore
import app.models.sheet.CharacterSheet
import app.models.sheet.Identity
import app.models.sheet.InventoryItem
import app.models.sheet.SkillScore
import app.models.sheet.Vitals
import dal.api.DataBase
import groovy.transform.CompileDynamic
import spock.lang.Narrative
import spock.lang.Specification
import spock.lang.Title
import sprouts.Tuple

@Title("The CharacterSheet value tree")
@Narrative('''

    The `CharacterSheet` is the first real value tree in the application: a single
    immutable `Value` (identity, vitals, abilities, skills, inventory, notes) that is
    persisted whole through Topsoil and edited through lenses in the UI.

    This specification pins two things: the pure value semantics of the sheet (withers
    return new, structurally-equal-by-content values) and the Topsoil round-trip
    (a sheet set on a model reads back fully reconstructed, even from a fresh proxy).

''')
@CompileDynamic
class CharacterSheet_Spec extends Specification
{
    def TEST_DB_FILE = "test_data/sheet_spec.db"

    def setup() {
        // Topsoil's BasicSQLiteDataBase mkdirs() a non-existent ".db" path (turning it into a
        // directory it then cannot open). Pre-create the db as an empty file so it opens cleanly;
        // a zero-byte file is a valid empty SQLite database.
        def f = new File(TEST_DB_FILE)
        if ( f.isDirectory() ) f.deleteDir()
        f.parentFile?.mkdirs()
        if ( !f.exists() ) f.createNewFile()

        def db = DataBase.at(TEST_DB_FILE)
        db.dropAllTables()
        db.close()
    }

    def 'An empty sheet has blank identity, starting vitals and no abilities, skills or inventory.'() {
        when : 'We ask for the empty sheet.'
            var sheet = CharacterSheet.empty()
        then : 'Its identity is blank and its vitals are the starting vitals.'
            sheet.identity() == Identity.empty()
            sheet.vitals() == Vitals.starting()
        and : 'Its collections are empty and it has no notes.'
            sheet.abilities().isEmpty()
            sheet.skills().isEmpty()
            sheet.inventory().isEmpty()
            sheet.notes() == ""
    }

    def 'Withers produce a new sheet without mutating the original (value semantics).'() {
        given : 'An empty sheet.'
            var original = CharacterSheet.empty()
        when : 'We derive a new sheet with a different name and notes.'
            var renamed = original.withIdentity(original.identity().withForename("Aragorn"))
                                  .withNotes("Heir of Isildur")
        then : 'The new sheet carries the changes.'
            renamed.identity().forename() == "Aragorn"
            renamed.notes() == "Heir of Isildur"
        and : 'The original is untouched.'
            original.identity().forename() == ""
            original.notes() == ""
        and : 'Two sheets built the same way are equal by content.'
            renamed == CharacterSheet.empty()
                            .withIdentity(Identity.empty().withForename("Aragorn"))
                            .withNotes("Heir of Isildur")
    }

    def 'withAbilityLevel inserts a new ability and then updates it in place by name.'() {
        given : 'An empty sheet.'
            var sheet = CharacterSheet.empty()
        when : 'We set strength to 12 (an insert) and dexterity to 9 (another insert).'
            sheet = sheet.withAbilityLevel("strength", 12).withAbilityLevel("dexterity", 9)
        then : 'Both abilities are present with their levels.'
            sheet.abilities().size() == 2
            sheet.abilities().contains(AbilityScore.of("strength", 12))
            sheet.abilities().contains(AbilityScore.of("dexterity", 9))
        when : 'We set strength again to 15 (an update, not a second insert).'
            sheet = sheet.withAbilityLevel("strength", 15)
        then : 'There are still two abilities and strength now reads 15.'
            sheet.abilities().size() == 2
            sheet.abilities().contains(AbilityScore.of("strength", 15))
            !sheet.abilities().contains(AbilityScore.of("strength", 12))
    }

    def 'withSkillLevel inserts then updates a skill by name.'() {
        given : 'An empty sheet.'
            var sheet = CharacterSheet.empty()
        when : 'We set the same skill twice.'
            sheet = sheet.withSkillLevel("Aim", 3).withSkillLevel("Aim", 7)
        then : 'Only one skill entry exists, at the latest level.'
            sheet.skills().size() == 1
            sheet.skills().get(0).skill() == "Aim"
            sheet.skills().get(0).level() == 7
    }

    def 'Inventory items carry a stable id so duplicates of equal content stay distinct.'() {
        when : 'We make two items with the same name.'
            var a = InventoryItem.named("Torch")
            var b = InventoryItem.named("Torch")
        then : 'They have the same name but different ids (HasId), so they are not equal.'
            a.name() == b.name()
            a.id() != b.id()
            a != b
    }

    def 'A sheet set on a model reads back fully reconstructed, even from a fresh proxy.'() {
        given : 'A database with the sheet value tables and the test holder model.'
            var db = DataBase.at(TEST_DB_FILE)
            db.dropAllTables()
            db.createTablesFor(
                    SheetHolder, CharacterSheet, Identity, Vitals,
                    AbilityScore, SkillScore, InventoryItem
            )
        and : 'A richly populated sheet.'
            var sheet = CharacterSheet.empty()
                    .withIdentity(Identity.empty().withForename("Bilbo").withSurname("Baggins").withRole("Burglar").withAge(50))
                    .withVitals(Vitals.starting().withMaxHealth(8).withCurrentHealth(8))
                    .withAbilityLevel("dexterity", 14)
                    .withAbilityLevel("charisma", 11)
                    .withSkillLevel("Aim", 5)
                    .withInventory(Tuple.of(InventoryItem.named("Sting"), InventoryItem.named("Ring")))
                    .withNotes("There and back again.")
        and : 'A holder model carrying it.'
            var holder = db.create(SheetHolder)
            holder.name().set("Bilbo's sheet")
            holder.sheet().set(sheet)
        when : 'We read the sheet back through the same proxy.'
            var readBack = holder.sheet().get()
        then : 'It is structurally equal to what we stored, all the way down.'
            readBack == sheet
            readBack.identity().forename() == "Bilbo"
            readBack.abilities().contains(AbilityScore.of("dexterity", 14))
        when : 'We select a brand-new proxy for the same row and read the sheet.'
            var reloaded = db.select(SheetHolder, holder.id().get())
        then : 'The value tree comes back intact from the database, not an in-memory cache.'
            reloaded.sheet().get() == sheet
            reloaded.sheet().get().vitals() == Vitals.starting().withMaxHealth(8).withCurrentHealth(8)
            reloaded.sheet().get().inventory().size() == 2
        cleanup :
            db?.close()
    }

    def 'Replacing the sheet on a model swaps the whole value tree.'() {
        given : 'A holder with an initial sheet.'
            var db = DataBase.at(TEST_DB_FILE)
            db.dropAllTables()
            db.createTablesFor(
                    SheetHolder, CharacterSheet, Identity, Vitals,
                    AbilityScore, SkillScore, InventoryItem
            )
            var holder = db.create(SheetHolder)
            holder.sheet().set(CharacterSheet.empty().withNotes("first"))
        when : 'We replace it with a different sheet.'
            var second = CharacterSheet.empty().withNotes("second").withAbilityLevel("wisdom", 3)
            holder.sheet().set(second)
        then : 'A freshly selected proxy sees only the new sheet.'
            db.select(SheetHolder, holder.id().get()).sheet().get() == second
        cleanup :
            db?.close()
    }
}
