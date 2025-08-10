package dal.models;

import dal.api.Model;
import sprouts.Var;

import java.time.DayOfWeek;

public interface CulturalEvent extends Model<CulturalEvent> {
    interface Name extends Var<String> {}
    interface Day extends Var<DayOfWeek> {}
    interface Location extends Var<String> {}
    Name name();
    Day day();
    Location location();
}
