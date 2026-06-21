@testable import M1K3Inference
import Testing

struct ThinkStripperTests {
    @Test("strips a matched <think>…</think> pair")
    func matchedPair() {
        let input = "<think>internal reasoning here</think>The actual answer."
        #expect(ThinkStripper.strip(input) == "The actual answer.")
    }

    @Test("strips Qwen-style lone </think> close tag")
    func loneClose() {
        let input = "some reasoning\n</think>\nThe real output."
        #expect(ThinkStripper.strip(input) == "The real output.")
    }

    @Test("strips multi-line think blocks")
    func multiLine() {
        let input = """
        <think>
        Step 1: analyse
        Step 2: plan
        Step 3: consider persona rules
        </think>
        Overview: The call was about billing.
        """
        #expect(ThinkStripper.strip(input) == "Overview: The call was about billing.")
    }

    @Test("passes through plain text unchanged")
    func plainText() {
        let input = "Just a normal summary with no think tags."
        #expect(ThinkStripper.strip(input) == input)
    }

    @Test("strips multiple think blocks in one response")
    func multiplePairs() {
        let input = "<think>first</think>Hello <think>second</think>world."
        #expect(ThinkStripper.strip(input) == "Hello world.")
    }

    @Test("handles repetitive think-contaminated output")
    func repetitiveThink() {
        let input = "<think>long chain of thought repeated 6 times\nlong chain of thought repeated 6 times</think>Overview: A clean summary."
        #expect(ThinkStripper.strip(input) == "Overview: A clean summary.")
    }
}
