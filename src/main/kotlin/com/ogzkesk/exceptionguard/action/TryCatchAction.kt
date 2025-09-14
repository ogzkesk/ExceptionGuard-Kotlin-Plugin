package com.ogzkesk.exceptionguard.action

import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.psi.*

class TryCatchAction : LocalQuickFix, HighPriorityAction {

    override fun getFamilyName() = "Surround with try-catch"

    @OptIn(KaExperimentalApi::class)
    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement as? KtCallExpression ?: return
        val factory = KtPsiFactory(project)
        val target = getFullExpression(element)

        val tryCatchExpression = factory.createExpressionByPattern(
            "try { $0 } catch(e: Exception) { }",
            target.text
        )

        target.replace(tryCatchExpression)
    }

    private fun getFullExpression(expr: KtExpression): KtExpression {
        var current: KtExpression = expr

        while (true) {
            val parent = current.parent as? KtQualifiedExpression ?: break
            val selector = parent.selectorExpression

            if (selector is KtCallExpression || selector is KtNameReferenceExpression) {
                current = parent
            } else {
                break
            }
        }

        return current
    }
}