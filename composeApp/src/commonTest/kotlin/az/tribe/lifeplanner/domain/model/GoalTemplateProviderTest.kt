package az.tribe.lifeplanner.domain.model

import az.tribe.lifeplanner.data.repository.GoalTemplateProvider
import az.tribe.lifeplanner.domain.enum.GoalCategory
import kotlin.test.*

class GoalTemplateProviderTest {

    // ── getAllTemplates ───────────────────────────────────────────────

    @Test
    fun `getAllTemplates returns non-empty list`() {
        val templates = GoalTemplateProvider.getAllTemplates()
        assertTrue(templates.isNotEmpty(), "Template list should not be empty")
    }

    @Test
    fun `getAllTemplates returns 28 templates`() {
        val templates = GoalTemplateProvider.getAllTemplates()
        assertEquals(28, templates.size, "Expected 28 templates (4 per category x 7 categories)")
    }

    // ── getTemplatesByCategory ────────────────────────────────────────

    @Test
    fun `getTemplatesByCategory returns non-empty list for each GoalCategory`() {
        for (category in GoalCategory.entries) {
            val templates = GoalTemplateProvider.getTemplatesByCategory(category)
            assertTrue(
                templates.isNotEmpty(),
                "Templates for $category should not be empty"
            )
        }
    }

    @Test
    fun `getTemplatesByCategory returns templates with matching category`() {
        for (category in GoalCategory.entries) {
            val templates = GoalTemplateProvider.getTemplatesByCategory(category)
            templates.forEach { template ->
                assertEquals(
                    category,
                    template.category,
                    "Template '${template.id}' should have category $category but was ${template.category}"
                )
            }
        }
    }

    // ── getTemplateById ──────────────────────────────────────────────

    @Test
    fun `getTemplateById returns template for valid id`() {
        val template = GoalTemplateProvider.getTemplateById("career_promotion")
        assertNotNull(template)
        assertEquals("career_promotion", template.id)
        assertEquals("Get a Promotion", template.title)
        assertEquals(GoalCategory.CAREER, template.category)
    }

    @Test
    fun `getTemplateById returns null for invalid id`() {
        val template = GoalTemplateProvider.getTemplateById("nonexistent_template_xyz")
        assertNull(template)
    }

    // ── Template content quality ─────────────────────────────────────

    @Test
    fun `each template has non-blank title and description`() {
        val templates = GoalTemplateProvider.getAllTemplates()
        templates.forEach { template ->
            assertTrue(
                template.title.isNotBlank(),
                "Template '${template.id}' has blank title"
            )
            assertTrue(
                template.description.isNotBlank(),
                "Template '${template.id}' has blank description"
            )
        }
    }

    @Test
    fun `each template has at least 1 milestone`() {
        val templates = GoalTemplateProvider.getAllTemplates()
        templates.forEach { template ->
            assertTrue(
                template.suggestedMilestones.isNotEmpty(),
                "Template '${template.id}' has no milestones"
            )
        }
    }
}
