package app.models;

import dal.api.Model;
import sprouts.Var;
import sprouts.Vars;

public interface GameMasterModel extends Model<GameMasterModel>
{
    Var<UserModel> identity();
    Vars<CampaignModel> campaigns();
}
