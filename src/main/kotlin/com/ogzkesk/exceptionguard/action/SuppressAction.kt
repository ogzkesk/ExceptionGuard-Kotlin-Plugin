package com.ogzkesk.exceptionguard.action

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.ogzkesk.exceptionguard.inspection.UncaughtExceptionInspection
import org.jetbrains.kotlin.psi.*

class SuppressAction(private val suppressName: String) : LocalQuickFix {

    override fun getFamilyName(): String = "Suppress with '$suppressName'"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement
        val factory = KtPsiFactory(project)
        val annotationEntry = factory.createAnnotationEntry("@Suppress(\"$suppressName\")")

        val functionParent: KtNamedFunction? = getParentDeclaration(element)
        if (functionParent != null) {
            if (!addOnAnnotationIfExists(factory, functionParent)) {
                functionParent.addAnnotationEntry(annotationEntry)
            }
            return
        }

        val propertyParent: KtProperty? = getParentDeclaration(element)
        if (propertyParent != null) {
            if (!addOnAnnotationIfExists(factory, propertyParent)) {
                propertyParent.addAnnotationEntry(annotationEntry)
            }
            return
        }

        val constructorParent: KtConstructor<*>? = getParentDeclaration(element)
        if (constructorParent != null) {
            if (!addOnAnnotationIfExists(factory, constructorParent)) {
                constructorParent.addAnnotationEntry(annotationEntry)
            }
            return
        }
    }

    private fun addOnAnnotationIfExists(factory: KtPsiFactory, element: KtDeclaration): Boolean {
        element.annotationEntries.find { it.shortName?.asString() == UncaughtExceptionInspection.SUPPRESS_ANNOTATION }
            ?.valueArgumentList
            ?.addArgument(factory.createArgument(factory.createExpression("\"$suppressName\"")))
            ?: return false
        return true
    }

    private inline fun <reified T : KtDeclaration> getParentDeclaration(element: PsiElement): T? {
        var parent = element.parent

        while (parent != null) {
            if (parent is T) return parent
            parent = parent.parent
        }
        return null
    }
}
