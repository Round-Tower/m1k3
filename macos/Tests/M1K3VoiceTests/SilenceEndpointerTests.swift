import Foundation
import M1K3Voice
import Testing

/// Pins the silence endpointer that closes the recognizer-finality gap: a
/// non-empty partial that stops changing for the threshold means the user is
/// done. Driven with synthetic instants — no real clock.
struct SilenceEndpointerTests {
    private let start = ContinuousClock.now

    @Test("no endpoint while the partial keeps growing")
    func growingPartialNeverEndpoints() {
        var endpointer = SilenceEndpointer(silence: .seconds(1.8))
        endpointer.ingest(partial: "hello", at: start)
        endpointer.ingest(partial: "hello there", at: start.advanced(by: .seconds(1.5)))
        endpointer.ingest(partial: "hello there pal", at: start.advanced(by: .seconds(3.0)))
        #expect(!endpointer.shouldEndpoint(at: start.advanced(by: .seconds(4.0))))
    }

    @Test("a stable non-empty partial endpoints after the threshold")
    func stablePartialEndpoints() {
        var endpointer = SilenceEndpointer(silence: .seconds(1.8))
        endpointer.ingest(partial: "hello there", at: start)
        #expect(!endpointer.shouldEndpoint(at: start.advanced(by: .seconds(1.0))))
        #expect(endpointer.shouldEndpoint(at: start.advanced(by: .seconds(1.8))))
        #expect(endpointer.shouldEndpoint(at: start.advanced(by: .seconds(5.0))))
    }

    @Test("re-ingesting the SAME text does not reset the silence clock")
    func unchangedTextKeepsClock() {
        var endpointer = SilenceEndpointer(silence: .seconds(1.8))
        endpointer.ingest(partial: "hello", at: start)
        // The recognizer re-emits identical cumulative text on its window hops.
        endpointer.ingest(partial: "hello", at: start.advanced(by: .seconds(1.0)))
        #expect(endpointer.shouldEndpoint(at: start.advanced(by: .seconds(1.9))))
    }

    @Test("empty text never endpoints, however long the silence")
    func emptyNeverEndpoints() {
        var endpointer = SilenceEndpointer(silence: .seconds(1.8))
        endpointer.ingest(partial: "", at: start)
        #expect(!endpointer.shouldEndpoint(at: start.advanced(by: .seconds(60))))
        // No ingest at all → also no endpoint.
        let untouched = SilenceEndpointer(silence: .seconds(1.8))
        #expect(!untouched.shouldEndpoint(at: start.advanced(by: .seconds(60))))
    }

    @Test("reset clears the clock for the next listen")
    func resetClears() {
        var endpointer = SilenceEndpointer(silence: .seconds(1.8))
        endpointer.ingest(partial: "hello", at: start)
        endpointer.reset()
        #expect(!endpointer.shouldEndpoint(at: start.advanced(by: .seconds(10))))
        endpointer.ingest(partial: "again", at: start.advanced(by: .seconds(10)))
        #expect(endpointer.shouldEndpoint(at: start.advanced(by: .seconds(11.9))))
    }
}
