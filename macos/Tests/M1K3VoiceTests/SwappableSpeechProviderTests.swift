import Foundation
import M1K3Voice
import Testing

// MARK: - Fakes

/// Records what it was asked to speak. Plain SpeechProvider (no lifecycle).
private final class RecordingSpeechProvider: SpeechProvider, @unchecked Sendable {
    let name: String
    private let lock = NSLock()
    private var _spoken: [String] = []
    private var _stopCount = 0
    private var _speaking = false

    init(name: String, speaking: Bool = false) {
        self.name = name
        _speaking = speaking
    }

    var isAvailable: Bool {
        true
    }

    var spoken: [String] {
        lock.withLock { _spoken }
    }

    var stopCount: Int {
        lock.withLock { _stopCount }
    }

    func speak(_ utterance: SpeechUtterance) async {
        lock.withLock { _spoken.append(utterance.text) }
    }

    func stop() async {
        lock.withLock { _stopCount += 1 }
    }

    func isSpeaking() async -> Bool {
        lock.withLock { _speaking }
    }
}

/// A lifecycle-reporting fake whose callbacks can be invoked by the test.
private final class LifecycleSpeechProvider: SpeechProviderWithLifecycle, @unchecked Sendable {
    let name = "lifecycle-fake"
    var isAvailable: Bool {
        true
    }

    var onSpeakingStarted: (@Sendable () -> Void)?
    var onSpeakingEnded: (@Sendable () -> Void)?

    func speak(_: SpeechUtterance) async {}
    func stop() async {}
    func isSpeaking() async -> Bool {
        false
    }
}

private final class Box: @unchecked Sendable {
    var value = false
}

// MARK: - Tests

struct SwappableSpeechProviderTests {
    @Test("forwards speak to the active provider")
    func forwardsSpeak() async {
        let fake = RecordingSpeechProvider(name: "a")
        let swappable = SwappableSpeechProvider(fake)
        await swappable.speak("hello")
        #expect(fake.spoken == ["hello"])
    }

    @Test("forwards stop and isSpeaking")
    func forwardsStopAndIsSpeaking() async {
        let fake = RecordingSpeechProvider(name: "a", speaking: true)
        let swappable = SwappableSpeechProvider(fake)
        let speaking = await swappable.isSpeaking()
        #expect(speaking == true)
        await swappable.stop()
        #expect(fake.stopCount == 1)
    }

    @Test("setProvider routes subsequent calls to the new provider")
    func swapRoutesToNewProvider() async {
        let first = RecordingSpeechProvider(name: "first")
        let second = RecordingSpeechProvider(name: "second")
        let swappable = SwappableSpeechProvider(first)

        await swappable.speak("one")
        swappable.setProvider(second)
        await swappable.speak("two")

        #expect(first.spoken == ["one"])
        #expect(second.spoken == ["two"])
        #expect(swappable.active.name == "second")
    }

    @Test("lifecycle callbacks are applied to the active provider")
    func callbacksAppliedToActive() {
        let lifecycle = LifecycleSpeechProvider()
        let swappable = SwappableSpeechProvider(lifecycle)
        let box = Box()

        swappable.onSpeakingStarted = { box.value = true }
        // The callback should have been forwarded onto the lifecycle fake.
        lifecycle.onSpeakingStarted?()
        #expect(box.value == true)
    }

    @Test("callbacks are re-applied after a provider swap")
    func callbacksReappliedAfterSwap() {
        let first = LifecycleSpeechProvider()
        let second = LifecycleSpeechProvider()
        let swappable = SwappableSpeechProvider(first)
        let box = Box()

        swappable.onSpeakingEnded = { box.value = true }
        swappable.setProvider(second)

        // The new provider should now carry the same callback.
        #expect(second.onSpeakingEnded != nil)
        second.onSpeakingEnded?()
        #expect(box.value == true)
    }
}
