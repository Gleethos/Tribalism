package app.campaign;

import app.models.Campaign;
import app.models.Character;
import sprouts.Var;
import sprouts.Vars;

import java.util.Objects;

/**
 *  A master-detail view model for a campaign roster: the campaign name, the list of character
 *  cards, the currently selected card (whose sheet the detail pane shows), and the actions to
 *  add and select characters. Backed by {@link CampaignService}, so creating a character
 *  persists it; binding edits its sheet through to the database.
 *  <p>
 *  The view model imports no Swing types; the desktop {@code CampaignView} (and a future web
 *  view) bind to its properties.
 */
public final class CampaignViewModel
{
    private final CampaignService service;
    private final Campaign campaign;

    private final Vars<CharacterCardViewModel> characters = Vars.of(CharacterCardViewModel.class);
    private final Var<CharacterCardViewModel>  selected   = Var.ofNullable(CharacterCardViewModel.class, null);
    private final Var<String> newCharacterName = Var.of("");

    public CampaignViewModel( CampaignService service, Campaign campaign ) {
        this.service  = Objects.requireNonNull(service);
        this.campaign = Objects.requireNonNull(campaign);
        for ( Character character : service.charactersOf(campaign) )
            characters.add(new CharacterCardViewModel(character));
        if ( !characters.isEmpty() )
            selected.set(characters.at(0).get());
    }

    /** @return The campaign name (the persisted model property — edits save directly). */
    public Var<String> name() { return campaign.name(); }

    /** @return The roster of character cards. */
    public Vars<CharacterCardViewModel> characters() { return characters; }

    /** @return The currently selected card (its sheet is shown in the detail pane), or null. */
    public Var<CharacterCardViewModel> selected() { return selected; }

    /** @return The name typed into the "add character" field. */
    public Var<String> newCharacterName() { return newCharacterName; }

    /** Creates a new persisted character from {@link #newCharacterName()}, selects it, clears the field. */
    public void addCharacter() {
        String forename = newCharacterName.get().isBlank() ? "New character" : newCharacterName.get().trim();
        Character character = service.createCharacter(campaign, forename);
        CharacterCardViewModel card = new CharacterCardViewModel(character);
        characters.add(card);
        selected.set(card);
        newCharacterName.set("");
    }

    /** Selects a character card, switching the detail pane to its sheet. */
    public void select( CharacterCardViewModel card ) {
        selected.set(card);
    }
}
