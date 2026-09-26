package app.holdthatpose

import app.holdthatpose.ui.settings.PolicyBlock
import app.holdthatpose.ui.settings.parsePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PrivacyPolicyTest {

    /** The single source file that is both published and bundled into the app. */
    private val policy: String by lazy {
        listOf("../docs/legal/privacy-policy.md", "docs/legal/privacy-policy.md")
            .map(::File).first { it.exists() }.readText()
    }

    @Test
    fun parserHandlesTheMarkdownSubsetWeUse() {
        val blocks = parsePolicy("# Title\nLine one\nLine two\n\n## Section\n### Sub\n- a bullet\n\nText.")
        assertEquals(
            listOf(
                PolicyBlock.Title("Title"),
                PolicyBlock.Paragraph("Line one\nLine two"),
                PolicyBlock.Heading("Section"),
                PolicyBlock.SubHeading("Sub"),
                PolicyBlock.Bullet("a bullet"),
                PolicyBlock.Paragraph("Text."),
            ),
            blocks,
        )
    }

    @Test
    fun policyCoversWhatGooglePlayExpects() {
        val headings = parsePolicy(policy).filterIsInstance<PolicyBlock.Heading>().map { it.text }
        listOf(
            "What the app handles, and where it stays",
            "Permissions and why they are needed",
            "Third-party services",
            "Data sharing and sale",
            "Your control over your data",
            "Children",
            "Security",
            "Changes to this policy",
            "Contact",
        ).forEach { assertTrue("missing section: $it", it in headings) }
        assertTrue(policy.contains("Effective date:"))
        assertTrue(policy.contains("Hold That Pose"))
    }

    @Test
    fun everySensitivePermissionIsExplained() {
        listOf("Camera", "Nearby devices", "Location", "Notifications", "Foreground service", "Hide overlay windows")
            .forEach { assertTrue("permission not explained: $it", policy.contains(it)) }
    }

    @Test
    fun noPlaceholdersLeft() {
        listOf("TODO", "TBD", "[", "lorem", "example.com").forEach {
            assertFalse("placeholder '$it' in policy", policy.contains(it, ignoreCase = true))
        }
    }
}
