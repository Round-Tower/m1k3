import M1K3Avatar
import Testing

struct ToolEmotionMapperTests {
    @Test("failure always returns sad")
    func failureAlwaysSad() {
        #expect(ToolEmotionMapper.emotionFor(toolID: "search_knowledge", success: false) == .sad)
        #expect(ToolEmotionMapper.emotionFor(toolID: "unknown_tool", success: false) == .sad)
        #expect(ToolEmotionMapper.emotionFor(toolID: "", success: false) == .sad)
    }

    @Test("search_knowledge success → thinking")
    func knowledgeSearch() {
        #expect(ToolEmotionMapper.emotionFor(toolID: "search_knowledge", success: true) == .thinking)
    }

    @Test("web_search success → thinking")
    func webSearch() {
        #expect(ToolEmotionMapper.emotionFor(toolID: "web_search", success: true) == .thinking)
    }

    @Test("set_timer success → excited")
    func timer() {
        #expect(ToolEmotionMapper.emotionFor(toolID: "set_timer", success: true) == .excited)
    }

    @Test("unknown tool success → happy (default)")
    func unknownTool() {
        #expect(ToolEmotionMapper.emotionFor(toolID: "some_new_tool", success: true) == .happy)
    }
}
