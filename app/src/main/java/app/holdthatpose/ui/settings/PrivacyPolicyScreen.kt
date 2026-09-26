package app.holdthatpose.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.holdthatpose.R
import app.holdthatpose.ui.components.AuroraBackground
import app.holdthatpose.ui.components.GlassIconButton
import app.holdthatpose.ui.components.SecondaryButton
import app.holdthatpose.ui.components.VSpace
import app.holdthatpose.ui.icons.PoseIcons
import app.holdthatpose.ui.theme.PoseColors
import app.holdthatpose.ui.theme.PoseType

/** One block of the policy, parsed from the bundled markdown. */
internal sealed interface PolicyBlock {
    data class Title(val text: String) : PolicyBlock
    data class Heading(val text: String) : PolicyBlock
    data class SubHeading(val text: String) : PolicyBlock
    data class Bullet(val text: String) : PolicyBlock
    data class Paragraph(val text: String) : PolicyBlock
}

/**
 * Minimal parser for the policy's markdown (`#`, `##`, `###`, `- ` bullets, paragraphs).
 * The same file is published on the web, so the app and the public URL never drift apart.
 */
internal fun parsePolicy(markdown: String): List<PolicyBlock> {
    val blocks = mutableListOf<PolicyBlock>()
    val paragraph = StringBuilder()
    fun flush() {
        if (paragraph.isNotBlank()) blocks += PolicyBlock.Paragraph(paragraph.toString().trim())
        paragraph.clear()
    }
    markdown.lineSequence().forEach { raw ->
        val line = raw.trim()
        when {
            line.isEmpty() -> flush()
            line.startsWith("### ") -> { flush(); blocks += PolicyBlock.SubHeading(line.removePrefix("### ")) }
            line.startsWith("## ") -> { flush(); blocks += PolicyBlock.Heading(line.removePrefix("## ")) }
            line.startsWith("# ") -> { flush(); blocks += PolicyBlock.Title(line.removePrefix("# ")) }
            line.startsWith("- ") -> { flush(); blocks += PolicyBlock.Bullet(line.removePrefix("- ")) }
            else -> {
                // Consecutive plain lines (e.g. the subtitle and the date) stay separate lines.
                if (paragraph.isNotEmpty()) paragraph.append('\n')
                paragraph.append(line)
            }
        }
    }
    flush()
    return blocks
}

private fun loadPolicy(context: Context): List<PolicyBlock> = runCatching {
    context.assets.open(POLICY_ASSET).bufferedReader().use { parsePolicy(it.readText()) }
}.getOrElse { listOf(PolicyBlock.Title("Privacy Policy"), PolicyBlock.Paragraph("The policy couldn't be loaded.")) }

internal const val POLICY_ASSET = "privacy-policy.md"

/** The full privacy policy, bundled with the app so it's readable offline. */
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val blocks = remember { loadPolicy(context) }

    Box(Modifier.fillMaxSize()) {
        AuroraBackground(intensity = 0.45f)
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Box(Modifier.padding(start = 22.dp, top = 12.dp, bottom = 8.dp)) {
                GlassIconButton(PoseIcons.Back, "Back", onBack)
            }
            LazyColumn(Modifier.weight(1f).padding(horizontal = 24.dp)) {
                items(blocks) { block ->
                    when (block) {
                        is PolicyBlock.Title -> {
                            VSpace(16.dp)
                            Text(block.text, style = PoseType.Title, color = PoseColors.Paper)
                            VSpace(8.dp)
                        }
                        is PolicyBlock.Heading -> {
                            VSpace(22.dp)
                            Text(block.text, style = PoseType.TitleSmall, color = PoseColors.Paper)
                            VSpace(6.dp)
                        }
                        is PolicyBlock.SubHeading -> {
                            VSpace(12.dp)
                            Text(block.text, style = PoseType.BodyStrong, color = PoseColors.Sky)
                            VSpace(4.dp)
                        }
                        is PolicyBlock.Bullet -> Row(Modifier.padding(vertical = 3.dp)) {
                            Text("•", style = PoseType.Body, color = PoseColors.Sky)
                            Box(Modifier.width(10.dp))
                            Text(block.text, style = PoseType.Body, color = PoseColors.PaperDim)
                        }
                        is PolicyBlock.Paragraph -> Text(
                            block.text,
                            style = PoseType.Body,
                            color = PoseColors.PaperDim,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                }
                item {
                    VSpace(24.dp)
                    SecondaryButton("View online", {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.privacy_policy_url)))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    })
                    VSpace(24.dp)
                }
            }
        }
    }
}
