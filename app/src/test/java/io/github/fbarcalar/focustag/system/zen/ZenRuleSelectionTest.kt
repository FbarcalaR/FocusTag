package io.github.fbarcalar.focustag.system.zen

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ZenRuleSelectionTest {
    @Test
    fun `no rules keeps nothing`() {
        assertThat(chooseRule(emptyList())).isEqualTo(RuleChoice(keep = null, duplicates = emptyList()))
    }

    @Test
    fun `a single rule is kept`() {
        assertThat(chooseRule(listOf(OwnedRule("a", 5)))).isEqualTo(RuleChoice(keep = "a", duplicates = emptyList()))
    }

    @Test
    fun `the oldest rule is kept and the others are duplicates`() {
        val choice = chooseRule(listOf(OwnedRule("new", 30), OwnedRule("old", 10), OwnedRule("mid", 20)))

        assertThat(choice).isEqualTo(RuleChoice(keep = "old", duplicates = listOf("mid", "new")))
    }

    @Test
    fun `equal creation times are broken by id`() {
        val choice = chooseRule(listOf(OwnedRule("b", 0), OwnedRule("a", 0)))

        assertThat(choice).isEqualTo(RuleChoice(keep = "a", duplicates = listOf("b")))
    }
}
