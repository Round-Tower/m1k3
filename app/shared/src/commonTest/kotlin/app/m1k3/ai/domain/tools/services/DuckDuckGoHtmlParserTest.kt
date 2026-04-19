package app.m1k3.ai.domain.tools.services

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DuckDuckGoHtmlParserTest {
    @Test
    fun `returns empty list for empty html`() {
        assertEquals(emptyList(), DuckDuckGoHtmlParser.parse("", maxResults = 5))
    }

    @Test
    fun `returns empty list for html with no result blocks`() {
        val html = "<html><body><p>No results found</p></body></html>"
        assertEquals(emptyList(), DuckDuckGoHtmlParser.parse(html, maxResults = 5))
    }

    @Test
    fun `extracts title url and snippet from a single organic result`() {
        val html =
            """
            <div class="result">
              <a rel="nofollow" class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fwww.met.ie%2Fweather%2Dforecast%2Fdublin%2Dcity&amp;rut=abc">Dublin City Weather - Met Éireann</a>
              <a class="result__snippet" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fwww.met.ie%2Fweather%2Dforecast%2Fdublin%2Dcity">Get the latest <b>weather</b> information and forecast for <b>Dublin</b> City, Ireland.</a>
            </div>
            """.trimIndent()

        val results = DuckDuckGoHtmlParser.parse(html, maxResults = 5)

        assertEquals(1, results.size)
        val first = results[0]
        assertEquals("Dublin City Weather - Met Éireann", first.title)
        assertEquals("https://www.met.ie/weather-forecast/dublin-city", first.url)
        assertEquals("Get the latest weather information and forecast for Dublin City, Ireland.", first.snippet)
    }

    @Test
    fun `skips ad results with y_js in url`() {
        val html =
            """
            <a rel="nofollow" class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fduckduckgo.com%2Fy.js%3Fad_domain%3Dfoo.com%26ad_provider%3Dbing">Sponsored Title</a>
            <a class="result__snippet" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fduckduckgo.com%2Fy.js">Sponsored snippet</a>
            <a rel="nofollow" class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Forganic.example.com%2Fpage&amp;rut=xyz">Organic Result</a>
            <a class="result__snippet" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Forganic.example.com%2Fpage">Organic snippet text</a>
            """.trimIndent()

        val results = DuckDuckGoHtmlParser.parse(html, maxResults = 5)

        assertEquals(1, results.size)
        assertEquals("Organic Result", results[0].title)
        assertEquals("https://organic.example.com/page", results[0].url)
    }

    @Test
    fun `caps results at maxResults`() {
        val html =
            (1..10).joinToString("\n") { i ->
                """
                <a rel="nofollow" class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.com%2F$i">Result $i</a>
                <a class="result__snippet" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.com%2F$i">Snippet $i</a>
                """.trimIndent()
            }

        val results = DuckDuckGoHtmlParser.parse(html, maxResults = 3)

        assertEquals(3, results.size)
        assertEquals("Result 1", results[0].title)
        assertEquals("Result 3", results[2].title)
    }

    @Test
    fun `strips bold tags and decodes html entities in titles and snippets`() {
        val html =
            """
            <a rel="nofollow" class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.com">Fish &amp; Chips: <b>Best</b> of London</a>
            <a class="result__snippet" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.com">Find &quot;the best&quot; <b>fish</b> &amp; chips in town &#x27;n&#x27; beyond.</a>
            """.trimIndent()

        val results = DuckDuckGoHtmlParser.parse(html, maxResults = 5)

        assertEquals(1, results.size)
        assertEquals("Fish & Chips: Best of London", results[0].title)
        assertEquals("Find \"the best\" fish & chips in town 'n' beyond.", results[0].snippet)
    }

    @Test
    fun `decodes percent encoded utf8 in urls`() {
        val html =
            """
            <a rel="nofollow" class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.com%2Fcaf%C3%A9">Café Page</a>
            <a class="result__snippet" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.com%2Fcaf%C3%A9">Café results</a>
            """.trimIndent()

        val results = DuckDuckGoHtmlParser.parse(html, maxResults = 5)

        assertEquals(1, results.size)
        assertEquals("https://example.com/café", results[0].url)
    }

    @Test
    fun `handles title without matching snippet by using empty snippet`() {
        val html =
            """
            <a rel="nofollow" class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.com">Orphan Title</a>
            """.trimIndent()

        val results = DuckDuckGoHtmlParser.parse(html, maxResults = 5)

        assertEquals(1, results.size)
        assertEquals("Orphan Title", results[0].title)
        assertTrue(results[0].snippet.isEmpty())
    }

    @Test
    fun `format renders results as a readable block for the LLM`() {
        val results =
            listOf(
                DuckDuckGoHtmlParser.SearchResult(
                    title = "Met Éireann",
                    url = "https://www.met.ie/forecast",
                    snippet = "Current forecast for Dublin.",
                ),
                DuckDuckGoHtmlParser.SearchResult(
                    title = "BBC Weather",
                    url = "https://www.bbc.com/weather",
                    snippet = "Hourly and 7-day forecast.",
                ),
            )

        val rendered = DuckDuckGoHtmlParser.format(results, query = "weather dublin")

        // The LLM needs to see: query context, numbered list, title+url+snippet per result.
        assertTrue(rendered.contains("weather dublin"), "output should mention the query")
        assertTrue(rendered.contains("Met Éireann"))
        assertTrue(rendered.contains("https://www.met.ie/forecast"))
        assertTrue(rendered.contains("Current forecast for Dublin."))
        assertTrue(rendered.contains("BBC Weather"))
    }
}
