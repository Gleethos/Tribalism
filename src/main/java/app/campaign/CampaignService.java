package app.campaign;

import app.models.Campaign;
import app.models.Character;
import app.models.GameMaster;
import app.models.User;
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
 *  A {@link User} can be a game master in one campaign and a player in another, so
 *  {@link GameMaster} is a role a user holds, found-or-created on demand here rather than at
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
     *  Returns the {@link GameMaster} role for a user, creating it the first time.
     *  Idempotent: a user has at most one game-master identity.
     *
     *  @param user The user whose game-master role to resolve.
     *  @return The user's (possibly freshly created) game master.
     */
    public GameMaster gameMasterOf( User user ) {
        long userId = user.id().get();
        for ( GameMaster gm : db.selectAll(GameMaster.class) ) {
            User identity = gm.identity().orElseNull();
            if ( identity != null && identity.id().is(userId) )
                return gm;
        }
        GameMaster gm = db.create(GameMaster.class);
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
    public Campaign createCampaign( GameMaster gm, String name ) {
        Campaign campaign = db.create(Campaign.class);
        campaign.name().set(name);
        campaign.description().set("");
        gm.campaigns().add(campaign);
        return campaign;
    }

    /** @return The campaigns owned by a game master. */
    public List<Campaign> campaignsOf( GameMaster gm ) {
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
    public Character createCharacter( Campaign campaign, String forename ) {
        Character character = db.create(Character.class);
        character.sheet().set(
            CharacterSheet.empty().withIdentity(Identity.empty().withForename(forename))
        );
        character.campaign().set(campaign);
        campaign.characters().add(character);
        return character;
    }

    /** @return The player characters of a campaign. */
    public List<Character> charactersOf( Campaign campaign ) {
        return campaign.characters().toList();
    }
}
