package app.models;

import dal.api.Model;
import sprouts.Var;
import sprouts.Vars;

public interface PlayerModel extends Model<PlayerModel>
{
    Var<UserModel> identity();
    Var<CampaignModel> campaign();
    Vars<CharacterModel> characters();
}
