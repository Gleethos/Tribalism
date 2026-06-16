package app.campaign;

import app.models.CampaignModel;
import app.models.CharacterModel;
import app.models.GameMasterModel;
import app.models.UserModel;
import app.models.sheet.CharacterSheet;
import app.models.sheet.Identity;
import dal.api.DataBase;

import java.util.List;
import java.util.Objects;

/**
 *  The roster service: the headless operations that create and read the campaign/character
 *  graph for a user, so the desktop and (later) web views are thin binders over it
 *  (see {@code VISION.md} §5 / §10 Phase 1).
 *  <p>
 *  A {@link UserModel} can be a game master in one campaign and a player in another, so
 *  {@link GameMasterModel} is a role a user holds, found-or-created on demand here rather than at
 *  registration. New characters are created with an initialized {@link CharacterSheet} value —
 *  the canonical, data-oriented representation a {@code CharacterSheetViewModel} edits.
 */
public final class CampaignService
{
    private final DataBase db;

    public CampaignService( DataBase db ) {
        this.db = Objects.requireNonNull(db);
    }

    /**
     *  Returns the {@link GameMasterModel} role for a user, creating it the first time.
     *  Idempotent: a user has at most one game-master identity.
     *
     *  @param user The user whose game-master role to resolve.
     *  @return The user's (possibly freshly created) game master.
     */
    public GameMasterModel gameMasterOf( UserModel user ) {
        long userId = user.id().get();
        for ( GameMasterModel gm : db.selectAll(GameMasterModel.class) ) {
            UserModel identity = gm.identity().orElseNull();
            if ( identity != null && identity.id().is(userId) )
                return gm;
        }
        GameMasterModel gm = db.create(GameMasterModel.class);
        gm.identity().set(user);
        return gm;
    }

    /**
     *  Creates a campaign owned by the given game master.
     *
     *  @param gm   The owning game master.
     *  @param name The campaign name.
     *  @return The new campaign.
     */
    public CampaignModel createCampaign( GameMasterModel gm, String name ) {
        CampaignModel campaign = db.create(CampaignModel.class);
        campaign.state().set(app.models.Campaign.empty());
        campaign.name().set(name);
        campaign.description().set("");
        gm.campaigns().add(campaign);
        return campaign;
    }

    /** @return The campaigns owned by a game master. */
    public List<CampaignModel> campaignsOf( GameMasterModel gm ) {
        return gm.campaigns().toList();
    }

    /**
     *  Creates a player character in a campaign, initialized with a blank
     *  {@link CharacterSheet} carrying the given forename, and links it both ways
     *  ({@code campaign.characters()} ⇄ {@code character.campaign()}).
     *
     *  @param campaign The campaign the character belongs to.
     *  @param forename The character's forename (seeded into the sheet identity).
     *  @return The new character.
     */
    public CharacterModel createCharacter( CampaignModel campaign, String forename ) {
        CharacterModel character = db.create(CharacterModel.class);
        character.sheet().set(
            CharacterSheet.empty().withIdentity(Identity.empty().withForename(forename))
        );
        character.campaign().set(campaign);
        campaign.characters().add(character);
        return character;
    }

    /** @return The player characters of a campaign. */
    public List<CharacterModel> charactersOf( CampaignModel campaign ) {
        return campaign.characters().toList();
    }
}
