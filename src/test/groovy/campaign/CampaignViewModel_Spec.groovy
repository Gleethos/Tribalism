package campaign

import app.campaign.CampaignService
import app.campaign.CampaignViewModel
import app.models.CampaignModel
import app.models.CharacterModel
import app.models.CharacterModel
import app.models.GameMapModel
import app.models.GameMasterModel
import app.models.PlayerModel
import app.models.UserModel
import app.models.sheet.AbilityScore
import app.models.sheet.CharacterSheet
import app.models.sheet.Identity
import app.models.sheet.InventoryItem
import app.models.sheet.SkillScore
import app.models.sheet.Vitals
import dal.api.DataBase
import groovy.transform.CompileDynamic
import spock.lang.Specification
import spock.lang.Title
import sprouts.From

@Title("The CampaignViewModel roster (master-detail)")
@CompileDynamic
class CampaignViewModel_Spec extends Specification
{
    def TEST_DB_FILE = "test_data/campaign_vm_spec.db"

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
                GameMasterModel, CharacterModel, CharacterModel, CampaignModel, GameMapModel, PlayerModel, UserModel, app.models.User, app.models.Campaign, app.models.GameMap,
                CharacterSheet, Identity, Vitals, AbilityScore, SkillScore, InventoryItem
        )
        return db
    }

    private CampaignViewModel newCampaignVm( DataBase db ) {
        var service = new CampaignService(db)
        var user = db.create(UserModel); user.state().set(app.models.User.empty()); user.username().set("dan"); user.password().set("x")
        var gm = service.gameMasterOf(user)
        var campaign = service.createCampaign(gm, "Lost Mines")
        return new CampaignViewModel(service, campaign)
    }

    def 'Adding a character grows the roster, selects it, and persists it.'() {
        given : 'A campaign view model with an empty roster.'
            var db = freshDb()
            var vm = newCampaignVm(db)
        expect : 'It starts empty with nothing selected.'
            vm.characters().isEmpty()
            vm.selected().orElseNull() == null
        when : 'We type a name and add a character.'
            vm.newCharacterName().set(From.VIEW, "Aragorn")
            vm.addCharacter()
        then : 'The roster has one card, it is selected, and the input is cleared.'
            vm.characters().size() == 1
            vm.selected().orElseNull() != null
            vm.newCharacterName().get() == ""
        and : 'The selected card displays the name and the character is persisted.'
            vm.selected().get().displayName().get() == "Aragorn"
            db.selectAll(CharacterModel).size() == 1
    }

    def 'Editing the selected character sheet through the card persists to the database.'() {
        given : 'A campaign with one character.'
            var db = freshDb()
            var vm = newCampaignVm(db)
            vm.newCharacterName().set(From.VIEW, "Gimli")
            vm.addCharacter()
            var card = vm.selected().get()
            var characterId = card.character().id().get()
        when : 'We edit the sheet through the card sheet view model lenses.'
            card.sheet().currentHealth().set(From.VIEW, 17)
            card.sheet().abilityLevel("strength").set(From.VIEW, 16)
        then : 'A freshly selected character sees the persisted edits.'
            var reloaded = db.select(CharacterModel, characterId)
            reloaded.sheet().get().vitals().currentHealth() == 17
            reloaded.sheet().get().abilities().contains(AbilityScore.of("strength", 16))
        and : 'The card display name follows the sheet identity.'
            card.displayName().get() == "Gimli"
    }

    def 'An existing campaign loads its characters into the roster.'() {
        given : 'A campaign that already has two characters.'
            var db = freshDb()
            var service = new CampaignService(db)
            var gm = service.gameMasterOf(db.create(UserModel).tap { it.state().set(app.models.User.empty()); it.username().set("dan"); it.password().set("x") })
            var campaign = service.createCampaign(gm, "Lost Mines")
            service.createCharacter(campaign, "Frodo")
            service.createCharacter(campaign, "Sam")
        when : 'We open a view model on that campaign.'
            var vm = new CampaignViewModel(service, campaign)
        then : 'Both characters are in the roster and the first is selected.'
            vm.characters().size() == 2
            vm.characters().toList().collect { it.displayName().get() }.sort() == ["Frodo", "Sam"]
            vm.selected().orElseNull() != null
    }
}
