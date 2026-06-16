package app.models;

import dal.api.Value;

/** The immutable scalar state of a {@link SkillModel} (its type is a separate model relation). */
public record Skill(int level, boolean isProficient, double learnability) implements Value {
    public static Skill empty() { return new Skill(0, false, 0.0); }
    public Skill withLevel(int level)                  { return new Skill(level, isProficient, learnability); }
    public Skill withIsProficient(boolean isProficient){ return new Skill(level, isProficient, learnability); }
    public Skill withLearnability(double learnability) { return new Skill(level, isProficient, learnability); }
}
