package engine.world

import app.engine.world.Texture
import app.engine.world.TextureProfile
import spock.lang.Narrative
import spock.lang.Specification
import spock.lang.Title

@Title("TextureProfile - independent visual appearance qualities")
@Narrative('''

    A texture profile is a set of appearance qualities (grainy, liquid, hairy, ...),
    each with an independent intensity in [0, 1]. Unlike the old material model the
    qualities do NOT sum to one: a surface can be both fully grainy and fully liquid.
    The empty profile (every quality 0) is the null object: invisible air.

''')
class TextureProfile_Spec extends Specification
{
    def "The empty profile is invisible and reports zero for every quality."()
    {
        given:
            var profile = TextureProfile.none()
        expect:
            profile.isInvisible()
            profile.intensityOf(Texture.GRAINY) == 0
            profile.presentQualities().isEmpty()
    }

    def "Qualities are independent and need not sum to one."()
    {
        given: 'A grainy liquid: both at full strength.'
            var profile = TextureProfile.none()
                                .with(Texture.GRAINY, 1.0)
                                .with(Texture.LIQUID, 1.0)
        expect:
            profile.intensityOf(Texture.GRAINY) == 1.0
            profile.intensityOf(Texture.LIQUID) == 1.0
            !profile.isInvisible()
            profile.presentQualities().toSet() == [Texture.GRAINY, Texture.LIQUID].toSet()
    }

    def "Intensities are clamped to [0, 1]."()
    {
        expect:
            TextureProfile.none().with(Texture.ROUGH, 5.0).intensityOf(Texture.ROUGH) == 1.0
            TextureProfile.none().with(Texture.ROUGH, -2.0).intensityOf(Texture.ROUGH) == 0.0
    }

    def "Out-of-range intensities are clamped, so a profile is never invalid."()
    {
        expect: 'There is no way to construct an out-of-range intensity; it is clamped to [0, 1].'
            TextureProfile.of(Texture.WET, 1.5).intensityOf(Texture.WET) == 1.0
            TextureProfile.none().with(Texture.WET, -0.5).intensityOf(Texture.WET) == 0.0
    }

    def "Averaging profiles is the core of level-of-detail aggregation."()
    {
        given: 'One face is fully grainy, three are empty.'
            var grainy = TextureProfile.of(Texture.GRAINY, 1.0)
            var none = TextureProfile.none()
        when:
            var averaged = TextureProfile.average([grainy, none, none, none])
        then: 'The result is a quarter as grainy.'
            Math.abs(averaged.intensityOf(Texture.GRAINY) - 0.25) < 1e-12
    }

    def "Averaging no samples yields the empty profile."()
    {
        expect:
            TextureProfile.average([]) == TextureProfile.none()
    }

    def "Blending interpolates each quality towards the other profile."()
    {
        given:
            var dry = TextureProfile.of(Texture.WET, 0.0)
            var soaked = TextureProfile.of(Texture.WET, 1.0)
        expect:
            Math.abs(dry.blend(soaked, 0.25).intensityOf(Texture.WET) - 0.25) < 1e-12
    }

    def "A profile is a value: same intensities means equal (and equal hash codes)."()
    {
        expect: 'Built two different ways, same content => equal, regardless of representation.'
            TextureProfile.of(Texture.GRAINY, 0.5) == TextureProfile.none().with(Texture.GRAINY, 0.5)
            TextureProfile.of(Texture.GRAINY, 0.5).hashCode() == TextureProfile.none().with(Texture.GRAINY, 0.5).hashCode()
        and: 'Empty == averaged-nothing == a stored zero.'
            TextureProfile.none() == TextureProfile.average([])
            TextureProfile.none() == TextureProfile.none().with(Texture.WET, 0.0)
        and: 'Different intensities are not equal.'
            TextureProfile.of(Texture.GRAINY, 0.5) != TextureProfile.of(Texture.GRAINY, 0.6)
    }

    // ---- Inset: the per-face content recession that rides on the profile ---------

    def "A profile's inset defaults to zero and is set (clamped) by withInset, leaving appearance untouched."()
    {
        given:
            var grass = TextureProfile.of(Texture.MOSSY, 0.7)
        expect: 'No inset by default.'
            grass.inset() == 0.0
        and: 'withInset sets it, clamped to [0,1], and does not touch the qualities.'
            grass.withInset(0.5).inset() == 0.5
            grass.withInset(2.0).inset() == 1.0
            grass.withInset(-1.0).inset() == 0.0
            grass.withInset(0.5).intensityOf(Texture.MOSSY) == 0.7
    }

    def "The inset is part of value identity (equals/hashCode) but ignored by sameAppearance."()
    {
        given:
            var flush = TextureProfile.of(Texture.MOSSY, 0.7)
            var recessed = flush.withInset(0.5)
        expect: 'Two profiles that differ only in inset are NOT equal...'
            flush != recessed
            flush.hashCode() != recessed.hashCode()
        and: '...yet they have the same appearance, so the greedy mesher still merges them.'
            flush.sameAppearance(recessed)
            recessed.sameAppearance(flush)
        and: 'Different qualities are never the same appearance.'
            !TextureProfile.of(Texture.MOSSY, 0.7).sameAppearance(TextureProfile.of(Texture.MOSSY, 0.8))
    }

    def "average averages the inset alongside the qualities."()
    {
        given:
            var flush = TextureProfile.of(Texture.GRAINY, 1.0).withInset(0.0)
            var deep = TextureProfile.of(Texture.GRAINY, 1.0).withInset(0.8)
        expect:
            Math.abs(TextureProfile.average([flush, deep]).inset() - 0.4) < 1e-12
            TextureProfile.average([flush, deep]).intensityOf(Texture.GRAINY) == 1.0
    }
}