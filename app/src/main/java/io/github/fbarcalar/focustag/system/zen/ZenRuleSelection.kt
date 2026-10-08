package io.github.fbarcalar.focustag.system.zen

/** One of our rules as listed by the OS. */
data class OwnedRule(val id: String, val createdAtMs: Long)

/** Which rule to keep and which duplicates to remove. */
data class RuleChoice(val keep: String?, val duplicates: List<String>)

/** Keeps the oldest rule (ties broken by id) so repeated runs converge on the same one. */
fun chooseRule(rules: List<OwnedRule>): RuleChoice {
    val ordered = rules.sortedWith(compareBy(OwnedRule::createdAtMs, OwnedRule::id))
    return RuleChoice(keep = ordered.firstOrNull()?.id, duplicates = ordered.drop(1).map(OwnedRule::id))
}
