package com.ogzkesk.exceptionguard

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtNamedFunction

fun PsiElement.getQualifiedName(): String {
    return when (this) {
        is KtNamedFunction -> this.name ?: "<anonymous>"
        is PsiMethod -> if (this.isConstructor) "<init>" else this.name
        is KtConstructor<*> -> this.name ?: "<init>"
        is KtClass -> "<init>"
        else -> this.kotlinFqName?.asString() ?: "<anonymous>"
    }
}