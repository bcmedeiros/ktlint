package com.github.shyiko.ktlint.ruleset.standard

import com.github.shyiko.ktlint.core.Rule
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.com.intellij.lang.FileASTNode
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.com.intellij.psi.impl.source.tree.PsiWhiteSpaceImpl
import org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.psiUtil.children
import org.jetbrains.kotlin.psi.stubs.elements.KtStubElementTypes

class ParameterListWrappingRule : Rule("parameter-list-wrapping") {

    private var indentSize = -1
    private var maxLineLength: Int = -1

    override fun visit(
        node: ASTNode,
        autoCorrect: Boolean,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> Unit
    ) {
        if (node.elementType == KtStubElementTypes.FILE) {
            val ec = EditorConfig.from(node as FileASTNode)
            indentSize = ec.indentSize
            maxLineLength = ec.maxLineLength
            return
        }
        if (node.elementType == KtStubElementTypes.VALUE_PARAMETER_LIST &&
            // skip lambda parameters
            node.treeParent?.elementType != KtNodeTypes.FUNCTION_LITERAL) {
            // each parameter should be on a separate line if
            // - at least one of the parameters is
            // - maxLineLength exceeded (and separating parameters with \n would actually help)
            // in addition, "(" and ")" must be on separates line if any of the parameters are (otherwise on the same)
            val putParametersOnSeparateLines = node.textContains('\n')
                // maxLineLength > 0 && node.lineLength() > maxLineLength
            if (putParametersOnSeparateLines) {
                // aiming for
                // ... LPAR
                // <LPAR line indent + indentSize> VALUE_PARAMETER...
                // <LPAR line indent> RPAR
                val indent = "\n" + node.psi.lineIndent()
                val paramIndent = indent + " ".repeat(indentSize) // single indent as recommended by Jetbrains/Google
                nextChild@ for (child in node.children()) {
                    when (child.elementType) {
                        KtStubElementTypes.VALUE_PARAMETER,
                        KtTokens.RPAR -> {
                            val prevLeaf = child.psi.prevLeaf()!!
                            val intendedIndent = if (child.elementType == KtStubElementTypes.VALUE_PARAMETER)
                                paramIndent else indent
                            if (prevLeaf.node.elementType == KtTokens.WHITE_SPACE) {
                                val spacing = prevLeaf.text
                                val cut = spacing.lastIndexOf("\n")
                                if (cut > -1) {
                                    val childIndent = spacing.substring(cut)
                                    if (childIndent == intendedIndent) {
                                        continue@nextChild
                                    }
                                    emit(child.startOffset, "Unexpected indentation" +
                                        " (expected ${intendedIndent.length - 1}, actual ${childIndent.length - 1})", true)
                                } else {
                                    emit(child.startOffset, errorMessage(child), true)
                                }
                                if (autoCorrect) {
                                    val prefix = if (cut > -1) spacing.substring(0, cut) else ""
                                    prevLeaf.rawReplaceWithText(prefix + intendedIndent)
                                }
                            } else {
                                emit(child.startOffset, errorMessage(child), true)
                                if (autoCorrect) {
                                    node.addChild(PsiWhiteSpaceImpl(intendedIndent), child)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun errorMessage(node: ASTNode) =
        when (node.elementType) {
            KtStubElementTypes.VALUE_PARAMETER ->
                "Parameter should be on a separate line (unless all parameters can fit a single line)"
            KtTokens.RPAR -> """Missing newline before ")""""
            else -> throw UnsupportedOperationException()
        }

    private fun PsiElement.lineIndent(): String {
        var leaf = PsiTreeUtil.prevLeaf(this)
        while (leaf != null) {
            if (leaf.node.elementType == KtTokens.WHITE_SPACE && leaf.textContains('\n')) {
                return leaf.text.substring(leaf.text.lastIndexOf('\n') + 1)
            }
            leaf = PsiTreeUtil.prevLeaf(leaf)
        }
        return ""
    }
}