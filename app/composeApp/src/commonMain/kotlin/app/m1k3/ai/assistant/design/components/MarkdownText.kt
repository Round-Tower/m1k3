package app.m1k3.ai.assistant.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.m1k3.ai.assistant.design.tokens.*
import app.m1k3.ai.domain.chat.markdown.MarkdownNode
import app.m1k3.ai.domain.chat.markdown.MarkdownParser
import org.jetbrains.compose.ui.tooling.preview.Preview
import app.m1k3.ai.assistant.design.theme.MaTheme

/**
 * Renders markdown-formatted text in chat bubbles.
 *
 * Parses markdown into AST nodes, then renders each as
 * appropriate Compose elements using the M1K3 design system.
 *
 * @param text Raw markdown text from AI response
 * @param isError If true, renders all text in error color
 * @param modifier Optional modifier
 */
@Composable
fun MarkdownText(
    text: String,
    isError: Boolean = false,
    modifier: Modifier = Modifier
) {
    // Strip < *think *>...</ *think *> blocks before rendering — safety net for any path
    // that doesn't run through ArtifactParser (e.g. welcome message, legacy path)
    val cleanText = remember(text) {
        text.replace(Regex("< *think *>[\\s\\S]*?</ *think *>", RegexOption.IGNORE_CASE), "").trim()
    }
    val parser = remember { MarkdownParser() }
    val nodes = remember(cleanText) { parser.parse(cleanText) }
    val textColor = if (isError) MaColors.Error else MaColors.textPrimary()
    val codeBlockBg = MaColors.bgHighElevated()
    val inlineCodeBg = MaColors.bgElevated()

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(MaSpacing.sm)
    ) {
        for (node in nodes) {
            RenderBlock(node, textColor, codeBlockBg, inlineCodeBg)
        }
    }
}

@Composable
private fun RenderBlock(
    node: MarkdownNode,
    textColor: Color,
    codeBlockBg: Color,
    inlineCodeBg: Color
) {
    when (node) {
        is MarkdownNode.Paragraph -> {
            Text(
                text = buildInlineAnnotatedString(node.children, textColor, inlineCodeBg),
                style = MaTypography.bodyLarge,
            )
        }

        is MarkdownNode.Heading -> {
            val style = when (node.level) {
                1 -> MaTypography.titleLarge
                2 -> MaTypography.titleMedium
                else -> MaTypography.titleSmall
            }
            Text(
                text = buildInlineAnnotatedString(node.children, MaColors.Orange, inlineCodeBg),
                style = style,
                fontWeight = FontWeight.Bold
            )
        }

        is MarkdownNode.CodeBlock -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(MaRadius.sm))
                    .background(codeBlockBg)
                    .padding(MaSpacing.md)
            ) {
                Text(
                    text = node.code,
                    style = MaTypography.bodySmall.copy(
                        fontFamily = MaFontFamilyMono,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    ),
                    color = textColor,
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                )
            }
        }

        is MarkdownNode.ListItem -> {
            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                val bullet = if (node.ordered) "${node.index ?: 1}." else "\u2022"
                Text(
                    text = bullet,
                    style = MaTypography.bodyLarge,
                    color = MaColors.Orange,
                    modifier = Modifier.width(if (node.ordered) 24.dp else 16.dp)
                )
                Text(
                    text = buildInlineAnnotatedString(node.children, textColor, inlineCodeBg),
                    style = MaTypography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        is MarkdownNode.LineBreak -> {
            Spacer(modifier = Modifier.height(MaSpacing.xs))
        }

        // Inline nodes shouldn't appear at block level, but handle gracefully
        is MarkdownNode.Text -> {
            Text(
                text = node.content,
                style = MaTypography.bodyLarge,
                color = textColor
            )
        }
        is MarkdownNode.Bold,
        is MarkdownNode.Italic,
        is MarkdownNode.BoldItalic,
        is MarkdownNode.InlineCode -> {
            Text(
                text = buildInlineAnnotatedString(listOf(node), textColor, inlineCodeBg),
                style = MaTypography.bodyLarge,
            )
        }
    }
}

/**
 * Build an AnnotatedString from inline markdown nodes.
 *
 * Handles bold, italic, bold+italic, inline code spans.
 */
@Composable
private fun buildInlineAnnotatedString(
    nodes: List<MarkdownNode>,
    textColor: Color,
    inlineCodeBg: Color
) = buildAnnotatedString {
    for (node in nodes) {
        appendInlineNode(node, textColor, inlineCodeBg)
    }
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendInlineNode(
    node: MarkdownNode,
    textColor: Color,
    inlineCodeBg: Color
) {
    when (node) {
        is MarkdownNode.Text -> {
            withStyle(SpanStyle(color = textColor)) {
                append(node.content)
            }
        }

        is MarkdownNode.Bold -> {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = textColor)) {
                for (child in node.children) {
                    appendInlineNode(child, textColor, inlineCodeBg)
                }
            }
        }

        is MarkdownNode.Italic -> {
            withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = textColor)) {
                for (child in node.children) {
                    appendInlineNode(child, textColor, inlineCodeBg)
                }
            }
        }

        is MarkdownNode.BoldItalic -> {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, color = textColor)) {
                for (child in node.children) {
                    appendInlineNode(child, textColor, inlineCodeBg)
                }
            }
        }

        is MarkdownNode.InlineCode -> {
            withStyle(SpanStyle(
                fontFamily = MaFontFamilyMono,
                color = MaColors.Orange,
                background = inlineCodeBg,
                fontSize = 13.sp
            )) {
                append(" ${node.code} ")
            }
        }

        // Block-level nodes shouldn't appear here, but handle gracefully
        else -> {
            withStyle(SpanStyle(color = textColor)) {
                append(node.toString())
            }
        }
    }
}

// ============================================================
// Previews
// ============================================================

@Preview
@Composable
private fun MarkdownTextPlainPreview() {
    MaTheme {
        Box(Modifier.background(MaColors.bgPrimary()).padding(MaSpacing.base)) {
            MarkdownText(text = "Hello world, this is plain text.")
        }
    }
}

@Preview
@Composable
private fun MarkdownTextBoldItalicPreview() {
    MaTheme {
        Box(Modifier.background(MaColors.bgPrimary()).padding(MaSpacing.base)) {
            MarkdownText(text = "This is **bold** and *italic* and ***both***.")
        }
    }
}

@Preview
@Composable
private fun MarkdownTextInlineCodePreview() {
    MaTheme {
        Box(Modifier.background(MaColors.bgPrimary()).padding(MaSpacing.base)) {
            MarkdownText(text = "Use `println()` to print and `val x = 42` for variables.")
        }
    }
}

@Preview
@Composable
private fun MarkdownTextCodeBlockPreview() {
    MaTheme {
        Box(Modifier.background(MaColors.bgPrimary()).padding(MaSpacing.base)) {
            MarkdownText(
                text = """
Here's some code:

```kotlin
fun greet(name: String) {
    println("Hello, ${'$'}name!")
}
```

That's it!
""".trimIndent()
            )
        }
    }
}

@Preview
@Composable
private fun MarkdownTextHeadingsPreview() {
    MaTheme {
        Box(Modifier.background(MaColors.bgPrimary()).padding(MaSpacing.base)) {
            MarkdownText(
                text = """
# Main Title

## Section Header

### Sub-section

Some body text with **bold** emphasis.
""".trimIndent()
            )
        }
    }
}

@Preview
@Composable
private fun MarkdownTextListsPreview() {
    MaTheme {
        Box(Modifier.background(MaColors.bgPrimary()).padding(MaSpacing.base)) {
            MarkdownText(
                text = """
Here are the key points:

- First item with **bold**
- Second item with `code`
- Third item

And ordered:

1. Step one
2. Step two
3. Step three
""".trimIndent()
            )
        }
    }
}

@Preview
@Composable
private fun MarkdownTextFullResponsePreview() {
    MaTheme {
        Box(Modifier.background(MaColors.bgPrimary()).padding(MaSpacing.base)) {
            MarkdownText(
                text = """
## Machine Learning

Machine learning is a subset of **artificial intelligence** where systems learn from *experience* without being explicitly programmed.

There are three main types:

1. **Supervised learning** — uses labeled data
2. **Unsupervised learning** — finds hidden patterns
3. **Reinforcement learning** — reward-based

Here's a simple example in Python:

```python
from sklearn.linear_model import LinearRegression
model = LinearRegression()
model.fit(X_train, y_train)
```

Use `model.predict(X_test)` to make predictions.
""".trimIndent()
            )
        }
    }
}

@Preview
@Composable
private fun MarkdownTextErrorPreview() {
    MaTheme {
        Box(Modifier.background(MaColors.bgPrimary()).padding(MaSpacing.base)) {
            MarkdownText(
                text = "Something went wrong with **model initialization**.",
                isError = true
            )
        }
    }
}
