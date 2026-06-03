package engine.util

import app.engine.util.Lazy
import spock.lang.Narrative
import spock.lang.Specification
import spock.lang.Title

import java.util.concurrent.atomic.AtomicInteger

@Title("Lazy - a write-once memoized value")
@Narrative('''

    A Lazy defers an expensive computation until first access and then caches it,
    so repeated reads are free. This is how immutable value objects (like a
    camera) carry derived state without recomputing it every query.

''')
class Lazy_Spec extends Specification
{
    def "The supplier runs only on first access, then the value is cached."()
    {
        given:
            var computations = new AtomicInteger(0)
            var lazy = Lazy.of({ -> computations.incrementAndGet(); 42 })
        expect: 'Nothing is computed until we ask for the value.'
            computations.get() == 0
        when:
            var first = lazy.get()
            var second = lazy.get()
        then: 'The value is correct and the supplier ran exactly once.'
            first == 42
            second == 42
            computations.get() == 1
    }

    def "Concurrent first-access still computes the value exactly once."()
    {
        given:
            var computations = new AtomicInteger(0)
            var lazy = Lazy.of({ -> computations.incrementAndGet(); Thread.sleep(5); "value" })
        when: 'Many threads race to be the first reader.'
            var threads = (1..16).collect { new Thread({ assert lazy.get() == "value" }) }
            threads*.start()
            threads*.join()
        then: 'The guarded computation ran only once.'
            computations.get() == 1
    }
}