package app.campaign;

import app.user.CharacterSheetView;
import dal.api.DataBase;
import swingtree.UI;
import swingtree.UIForAnySwing;

import javax.swing.JPanel;
import java.io.File;

import static swingtree.UI.*;

/**
 *  The desktop campaign roster: a master-detail view. The left pane lists the campaign's
 *  characters and an "add character" field; selecting one shows its {@link CharacterSheetView}
 *  in the right pane. Everything binds to {@link CampaignViewModel}; edits to a character sheet
 *  round-trip to the database because the sheet view model edits the persisted
 *  {@code CharacterModel.sheet()} property.
 */
public final class CampaignView extends JPanel
{
    public CampaignView( CampaignViewModel vm ) {
        of(this).withLayout(FILL.and(INS(12)))
        .withPrefSize(940, 780)
        .add(GROW,
            splitPane(UI.Align.HORIZONTAL)
            .withDividerAt(280)
            .add(rosterPane(vm))
            .add(detailPane(vm))
        );
    }

    private static UIForAnySwing<?,?> rosterPane( CampaignViewModel vm ) {
        return panel(FILL.and(WRAP(1)), "[grow]")
            .add(GROW_X, html("<h2>CampaignModel</h2>"))
            .add(GROW_X, textField(vm.name()))
            .add(GROW_X, label("Characters"))
            .add(GROW,
                scrollPanels().addAll(vm.characters(), (CharacterCardViewModel card) ->
                    button(card.displayName())
                    .withStyle(it -> it.margin(2).padding(6))
                    .onClick(it -> vm.select(card))
                )
            )
            .add(GROW_X,
                panel(FILL_X.and(WRAP(2)), "[grow][shrink]")
                .add(GROW_X, textField(vm.newCharacterName()))
                .add(button("Add").onClick(it -> vm.addCharacter()))
            );
    }

    private static UIForAnySwing<?,?> detailPane( CampaignViewModel vm ) {
        return panel(FILL)
            .add(GROW, vm.selected(), (CharacterCardViewModel card) ->
                card == null
                    ? panel(FILL).add(label("Select or add a character to edit its sheet."))
                    : UI.of(new CharacterSheetView(card.sheet()))
            );
    }

    /**
     *  Standalone demo against a throwaway database so the roster + sheet editing are visible.
     *  <p>
     *  Everything — database creation, setup and the UI — runs on the AWT event thread so the
     *  database connection lives on the same thread the GUI reads it from. (The real application
     *  instead marshals database access to its app thread through {@code AppContext}'s decoupled
     *  processor; either keeps Topsoil's per-thread connections consistent.)
     */
    public static void main( String[] args ) {
        UI.runLater(() -> {
            try {
                // Topsoil resolves db paths relative to the working directory and mkdirs() a
                // non-existent ".db" path into a directory, so use a relative path and pre-create it.
                String dbPath = "build/campaign_demo.db";
                File dbFile = new File(dbPath);
                dbFile.getParentFile().mkdirs();
                if ( dbFile.exists() ) dbFile.delete();
                dbFile.createNewFile();
                DataBase db = DataBase.at(dbPath);
                db.dropAllTables();
                db.createTablesFor(
                    app.models.GameMasterModel.class, app.models.CharacterModel.class, app.models.CharacterModel.class,
                    app.models.CampaignModel.class, app.models.GameMapModel.class, app.models.PlayerModel.class, app.models.UserModel.class,
                    app.models.sheet.CharacterSheet.class, app.models.sheet.Identity.class, app.models.sheet.Vitals.class,
                    app.models.sheet.AbilityScore.class, app.models.sheet.SkillScore.class, app.models.sheet.InventoryItem.class
                );
                var service = new CampaignService(db);
                var user = db.create(app.models.UserModel.class);
                user.username().set("demo"); user.password().set("demo");
                var gm = service.gameMasterOf(user);
                var campaign = service.createCampaign(gm, "The Sunless Citadel");
                service.createCharacter(campaign, "Lyra");
                service.createCharacter(campaign, "Borin");

                var vm = new CampaignViewModel(service, campaign);
                UI.show(f -> new CampaignView(vm));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
