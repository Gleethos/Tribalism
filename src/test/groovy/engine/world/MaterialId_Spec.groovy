package engine.world

import app.engine.world.MaterialId
import spock.lang.Narrative
import spock.lang.Specification
import spock.lang.Title

@Title("MaterialId - the single substance of a sector, as a sum type")
@Narrative('''

    A material id is either Specific(int) - one concrete substance - or the Diverse
    null object, meaning "many different materials". A leaf block is always Specific;
    only coarse aggregates of disagreeing children become Diverse.

''')
class MaterialId_Spec extends Specification
{
    def "Specific ids carry an integer and are value-equal."()
    {
        expect:
            MaterialId.of(7) == MaterialId.of(7)
            MaterialId.of(7) != MaterialId.of(8)
            !MaterialId.of(7).isDiverse()
            (MaterialId.of(7) as MaterialId.Specific).id() == 7
    }

    def "Diverse is the shared null object."()
    {
        expect:
            MaterialId.diverse().isDiverse()
            MaterialId.diverse() == MaterialId.DIVERSE
            MaterialId.diverse() instanceof MaterialId.Diverse
    }

    def "Merging agreeing ids keeps the shared id."()
    {
        expect:
            MaterialId.merge([MaterialId.of(3), MaterialId.of(3), MaterialId.of(3)]) == MaterialId.of(3)
    }

    def "Merging disagreeing ids collapses to Diverse."()
    {
        expect: 'Two different specifics...'
            MaterialId.merge([MaterialId.of(3), MaterialId.of(4)]).isDiverse()
        and: '...or any Diverse child...'
            MaterialId.merge([MaterialId.of(3), MaterialId.diverse()]).isDiverse()
        and: '...or nothing at all.'
            MaterialId.merge([]).isDiverse()
    }
}