package com.pinterest.ktlint.ruleset.standard.rules

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.ElementType.BLOCK_COMMENT
import com.pinterest.ktlint.rule.engine.core.api.ElementType.CONSTRUCTOR_CALLEE
import com.pinterest.ktlint.rule.engine.core.api.ElementType.EOL_COMMENT
import com.pinterest.ktlint.rule.engine.core.api.ElementType.FUNCTION_TYPE
import com.pinterest.ktlint.rule.engine.core.api.ElementType.IDENTIFIER
import com.pinterest.ktlint.rule.engine.core.api.ElementType.KDOC_START
import com.pinterest.ktlint.rule.engine.core.api.ElementType.LPAR
import com.pinterest.ktlint.rule.engine.core.api.ElementType.PRIMARY_CONSTRUCTOR
import com.pinterest.ktlint.rule.engine.core.api.ElementType.RPAR
import com.pinterest.ktlint.rule.engine.core.api.ElementType.SUPER_KEYWORD
import com.pinterest.ktlint.rule.engine.core.api.ElementType.SUPER_TYPE_CALL_ENTRY
import com.pinterest.ktlint.rule.engine.core.api.ElementType.TYPE_ARGUMENT_LIST
import com.pinterest.ktlint.rule.engine.core.api.ElementType.VALUE_ARGUMENT_LIST
import com.pinterest.ktlint.rule.engine.core.api.ElementType.VALUE_PARAMETER_LIST
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import com.pinterest.ktlint.rule.engine.core.api.SinceKtlint
import com.pinterest.ktlint.rule.engine.core.api.SinceKtlint.Status.STABLE
import com.pinterest.ktlint.rule.engine.core.api.ifAutocorrectAllowed
import com.pinterest.ktlint.rule.engine.core.api.isWhiteSpaceWithoutNewline
import com.pinterest.ktlint.rule.engine.core.api.nextLeaf
import com.pinterest.ktlint.rule.engine.core.api.prevLeaf
import com.pinterest.ktlint.rule.engine.core.api.prevSibling
import com.pinterest.ktlint.ruleset.standard.StandardRule
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.com.intellij.psi.PsiWhiteSpace

/**
 * Ensures there are no extra spaces around parentheses.
 *
 * See https://kotlinlang.org/docs/reference/coding-conventions.html#horizontal-whitespace
 */
@SinceKtlint("0.24", STABLE)
public class SpacingAroundParensRule : StandardRule("paren-spacing") {
    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision,
    ) {
        if (node.elementType == LPAR || node.elementType == RPAR) {
            val prevLeaf = node.prevLeaf()
            val nextLeaf = node.nextLeaf()
            val spacingBefore =
                if (node.elementType == LPAR) {
                    prevLeaf is PsiWhiteSpace &&
                        !prevLeaf.textContains('\n') &&
                        (
                            prevLeaf.prevLeaf()?.elementType == IDENTIFIER &&
                                // val foo: @Composable () -> Unit
                                node.treeParent?.treeParent?.elementType != FUNCTION_TYPE ||
                                // Super keyword needs special-casing
                                prevLeaf.prevLeaf()?.elementType == SUPER_KEYWORD ||
                                prevLeaf.prevLeaf()?.treeParent?.elementType == PRIMARY_CONSTRUCTOR ||
                                (
                                    // Disallow:
                                    //     class Foo : Bar ("test")
                                    //     class Foo : Bar<String> ("test")
                                    node.treeParent.treeParent.elementType == SUPER_TYPE_CALL_ENTRY &&
                                        prevLeaf.prevSibling()?.elementType == CONSTRUCTOR_CALLEE &&
                                        prevLeaf.prevLeaf()?.treeParent?.elementType == TYPE_ARGUMENT_LIST
                                )
                        ) &&
                        (
                            node.treeParent?.elementType == VALUE_PARAMETER_LIST ||
                                node.treeParent?.elementType == VALUE_ARGUMENT_LIST
                        )
                } else {
                    prevLeaf.isWhiteSpaceWithoutNewline() && prevLeaf?.prevLeaf()?.elementType != LPAR
                }
            val spacingAfter =
                if (node.elementType == LPAR) {
                    nextLeaf is PsiWhiteSpace &&
                        (!nextLeaf.textContains('\n') || nextLeaf.nextLeaf()?.elementType == RPAR) &&
                        !nextLeaf.isNextLeafAComment()
                } else {
                    nextLeaf.isWhiteSpaceWithoutNewline() && nextLeaf?.nextLeaf()?.elementType == RPAR
                }
            when {
                spacingBefore && spacingAfter -> {
                    emit(node.startOffset, "Unexpected spacing around \"${node.text}\"", true)
                        .ifAutocorrectAllowed {
                            prevLeaf!!.treeParent.removeChild(prevLeaf)
                            nextLeaf!!.treeParent.removeChild(nextLeaf)
                        }
                }

                spacingBefore -> {
                    emit(prevLeaf!!.startOffset, "Unexpected spacing before \"${node.text}\"", true)
                        .ifAutocorrectAllowed {
                            prevLeaf.treeParent.removeChild(prevLeaf)
                        }
                }

                spacingAfter -> {
                    emit(node.startOffset + 1, "Unexpected spacing after \"${node.text}\"", true)
                        .ifAutocorrectAllowed {
                            nextLeaf!!.treeParent.removeChild(nextLeaf)
                        }
                }
            }
        }
    }

    private fun ASTNode.isNextLeafAComment(): Boolean {
        val commentTypes = setOf(EOL_COMMENT, BLOCK_COMMENT, KDOC_START)
        return nextLeaf()?.elementType in commentTypes
    }
}

public val SPACING_AROUND_PARENS_RULE_ID: RuleId = SpacingAroundParensRule().ruleId
