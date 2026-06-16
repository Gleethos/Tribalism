package campaign

import app.campaign.CampaignService
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
import spock.lang.Narrative
import spock.lang.Specification
import spock.lang.Title

@Title("The roster CampaignService")
@Narrative('''

    CampaignService is the headless roster layer: it finds-or-creates the GameMasterModel role for a
    user, creates and lists campaigns, and creates characters with an initialized CharacterSheet,
    linking the relations both ways. This pins those operations and, importantly, that the model
    relations (a game master's campaigns, a campaign's characters) actually persist.

''')
@CompileDynamic
class CampaignService_Spec extends Specification
{
    def TEST_DB_FILE = "test_data/campaign_service_spec.db"

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

    private UserModel newUser( DataBase db, String name ) {
        var user = db.create(UserModel)
        user.state().set(app.models.User.empty())
        user.username().set(name)
        user.password().set("secret")
        return user
    }

    def 'gameMasterOf finds-or-creates exactly one game master per user.'() {
        given : 'A database with a user and the service.'
            var db = freshDb()
            var user = newUser(db, "dan")
            var service = new CampaignService(db)
        when : 'We resolve the game master twice.'
            var gm1 = service.gameMasterOf(user)
            var gm2 = service.gameMasterOf(user)
        then : 'Both calls return the same game master, and only one exists.'
            gm1.id().get() == gm2.id().get()
            db.selectAll(GameMasterModel).size() == 1
        and : 'Its identity is the user.'
            gm1.identity().get().id().get() == user.id().get()
        cleanup :
            db?.close()
    }

    def 'createCampaign links the campaign to its game master and persists the relation.'() {
        given : 'A user with a game master.'
            var db = freshDb()
            var service = new CampaignService(db)
            var gm = service.gameMasterOf(newUser(db, "dan"))
        when : 'We create two campaigns.'
            service.createCampaign(gm, "Lost Mines")
            service.createCampaign(gm, "Curse of Strahd")
        then : 'Both appear among the game master campaigns.'
            service.campaignsOf(gm).collect { it.name().get() }.sort() == ["Curse of Strahd", "Lost Mines"]
        and : 'A freshly selected game master still sees them — the relation persisted.'
            db.select(GameMasterModel, gm.id().get()).campaigns().toList().collect { it.name().get() }.sort() ==
                    ["Curse of Strahd", "Lost Mines"]
        cleanup :
            db?.close()
    }

    def 'createCharacter initializes the sheet, links both ways, and persists.'() {
        given : 'A campaign owned by a game master.'
            var db = freshDb()
            var service = new CampaignService(db)
            var gm = service.gameMasterOf(newUser(db, "dan"))
            var campaign = service.createCampaign(gm, "Lost Mines")
        when : 'We create a character in it.'
            var character = service.createCharacter(campaign, "Aragorn")
        then : 'The character has an initialized sheet carrying the forename.'
            character.sheet().get().identity().forename() == "Aragorn"
            character.sheet().get() == CharacterSheet.empty().withIdentity(Identity.empty().withForename("Aragorn"))
        and : 'The character links back to its campaign.'
            character.campaign().get().id().get() == campaign.id().get()
        and : 'The campaign lists the character.'
            service.charactersOf(campaign).collect { it.id().get() } == [character.id().get()]
        and : 'A freshly selected campaign sees the character, and its sheet survives.'
            db.select(CampaignModel, campaign.id().get()).characters().toList().size() == 1
            db.select(CharacterModel, character.id().get()).sheet().get().identity().forename() == "Aragorn"
        cleanup :
            db?.close()
    }
}
