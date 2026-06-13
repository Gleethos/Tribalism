package app.models.sheet;

import dal.api.Value;

/**
 *  A character's score in one skill, the immutable {@link Value} analogue of the
 *  {@code Skill} model. The skill is referenced by {@code name} (a {@code SkillType} name),
 *  which is its natural key — a character has at most one score per skill type.
 */
public record SkillScore(
    String  skill,
    int     level,
    boolean proficient,
    double  learnability
) implements Value {

    public SkillScore {
        if ( skill == null ) {
            throw new IllegalArgumentException("Skill name must not be null");
        }
    }

    public static SkillScore of(String skill, int level) {
        return new SkillScore(skill, level, false, 1.0);
    }

    public SkillScore withLevel(int level)               { return new SkillScore(skill, level, proficient, learnability); }
    public SkillScore withProficient(boolean proficient) { return new SkillScore(skill, level, proficient, learnability); }
    public SkillScore withLearnability(double learnability) { return new SkillScore(skill, level, proficient, learnability); }
}
