package com.ogzkesk.exceptionguard.action

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.openapi.project.Project
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.ogzkesk.exceptionguard.getQualifiedName
import com.ogzkesk.exceptionguard.inspection.UncaughtExceptionInspection

class NavigateAction(private val elementPointer: SmartPsiElementPointer<PsiElement>) : LocalQuickFix {

    override fun startInWriteAction(): Boolean {
        return false
    }

    override fun getFamilyName(): @IntentionFamilyName String {
        return "Go to function declaration: ${elementPointer.element?.getQualifiedName() ?: UncaughtExceptionInspection.UNKNOWN}"
    }

    override fun applyFix(p0: Project, p1: ProblemDescriptor) {
        val target = elementPointer.element
        if (target is NavigatablePsiElement) {
            target.navigate(true)
        } else {
            val element = p1.psiElement
            if (element is NavigatablePsiElement) {
                element.navigate(true)
            }
        }
    }
}