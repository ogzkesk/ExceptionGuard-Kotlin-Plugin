package com.ogzkesk.exceptionguard.inspection

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.*
import com.ogzkesk.exceptionguard.action.NavigateAction
import com.ogzkesk.exceptionguard.action.SuppressAction
import com.ogzkesk.exceptionguard.action.TryCatchAction
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.singleCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsight.utils.findExistingEditor
import org.jetbrains.kotlin.kdoc.psi.impl.KDocSection
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getPossiblyQualifiedCallExpression
import org.jetbrains.kotlin.psi.psiUtil.plainContent

class UncaughtExceptionInspection : AbstractKotlinInspection() {

    @OptIn(KaExperimentalApi::class)
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : KtVisitorVoid() {

            override fun visitCallExpression(expression: KtCallExpression) {
                super.visitCallExpression(expression)

                if (expression.containingKtFile.isScript()) return
                if (expression.findExistingEditor()?.isDisposed == true) return
                if (isInsideRunCatching(expression)) return
                if (isInsideTryCatch(expression)) return
                if (isSuppressed(expression)) return

                analyzeCall(expression) { psi, exceptions ->
                    holder.registerProblem(
                        expression,
                        "Uncaught exception found: ${exceptions.joinToString(", ")}",
                        ProblemHighlightType.WARNING,
                        TryCatchAction(),
                        NavigateAction(elementPointer = SmartPointerManager.createPointer(psi)),
                        SuppressAction(suppressName = SUPPRESS_NAME)
                    )
                }
            }

            private fun analyzeCall(
                expression: KtCallExpression,
                onThrowFound: (psi: PsiElement, throws: Set<String>) -> Unit
            ) {
                analyze(expression) {

                    val callInfo = expression.resolveToCall()
                    val resolved = callInfo?.singleCallOrNull<KaFunctionCall<*>>()
                    val psi = resolved?.symbol?.psi ?: return

                    val declaredExceptions = checkDeclaredExceptions(psi)
                    if (declaredExceptions.isNotEmpty()) {
                        onThrowFound(psi, declaredExceptions)
                        return
                    }

                    val docExceptions = checkDoc(psi)
                    if (docExceptions.isNotEmpty()) {
                        onThrowFound(psi, docExceptions)
                        return
                    }

                    val annotationExceptions = checkThrowsAnnotation(psi)
                    if (annotationExceptions.isNotEmpty()) {
                        onThrowFound(psi, annotationExceptions)
                        return
                    }


                    val bodyExceptions = checkBody(psi)
                    if (bodyExceptions.isNotEmpty()) {
                        onThrowFound(psi, bodyExceptions)
                        return
                    }

                    val localExceptions = checkLocalKotlinFiles(psi)
                    if (localExceptions.isNotEmpty()) {
                        onThrowFound(psi, localExceptions)
                    }
                }
            }

            private fun checkThrowsAnnotation(psiElement: PsiElement): Set<String> {
                return (psiElement.navigationElement as? KtDeclaration)?.let { declaration ->
                    val throwsAnnotations =
                        declaration.annotationEntries.filter { it.shortName?.asString() == THROWS_ANNOTATION }

                    if (throwsAnnotations.isEmpty()) return emptySet()

                    throwsAnnotations
                        .mapNotNull { annotation ->
                            annotation.valueArgumentList?.arguments?.mapNotNull { arg ->
                                (arg.getArgumentExpression() as? KtClassLiteralExpression)?.let {
                                    (it.receiverExpression as? KtNameReferenceExpression)?.getReferencedName()
                                }
                            }
                        }
                        .flatMap { it }
                        .toSet()

                } ?: emptySet()
            }

            private fun checkBody(psiElement: PsiElement): Set<String> {
                val exceptions = mutableSetOf<String>()

                when (psiElement) {
                    is KtDeclaration -> {
                        if (psiElement is KtClass) {
                            return emptySet()
                        }

                        psiElement.navigationElement.accept(object : KtTreeVisitorVoid() {
                            override fun visitThrowExpression(expression: KtThrowExpression) {
                                super.visitThrowExpression(expression)
                                if (isInsideTryCatch(expression)) return
                                if (isInsideRunCatching(expression)) return
                                val callExpr =
                                    expression.thrownExpression?.getPossiblyQualifiedCallExpression() ?: return
                                analyze(callExpr) {
                                    val callInfo = expression.resolveToCall()
                                    val resolved = callInfo?.singleCallOrNull<KaFunctionCall<*>>()
                                    val returnType = resolved?.symbol?.returnType ?: return
                                    val shortName =
                                        returnType.symbol?.classId?.shortClassName?.asString() ?: UNKNOWN

                                    exceptions.add(shortName)
                                }
                            }
                        })
                    }

                    is PsiMethod -> {
                        psiElement.navigationElement.accept(object : JavaRecursiveElementVisitor() {
                            override fun visitThrowStatement(statement: PsiThrowStatement) {
                                super.visitThrowStatement(statement)
                                if (isInsideTryCatch(statement)) return
                                val newExpr = statement.exception as? PsiNewExpression ?: return
                                val classRef = newExpr.classReference ?: return
                                val shortName = classRef.referenceName ?: UNKNOWN

                                exceptions.add(shortName)
                            }
                        })
                    }
                }

                return exceptions
            }

            private fun checkDoc(psiElement: PsiElement): Set<String> {
                return when (val navElement = psiElement.navigationElement) {
                    is KtDeclaration -> {
                        val kdoc = navElement.docComment
                        val section: KDocSection? = kdoc?.getDefaultSection()
                        val throwsTags = section?.findTagsByName(DOC_TAG).orEmpty()
                        throwsTags.mapNotNull { it.getSubjectName()?.substringAfterLast(".") }.toSet()
                    }

                    is PsiMethod -> {
                        val docComment = navElement.docComment
                        val throwsTags = docComment?.findTagsByName(DOC_TAG).orEmpty()
                        throwsTags.mapNotNull { it.valueElement?.text?.substringAfterLast(".") }.toSet()
                    }

                    else -> emptySet()
                }
            }

            private fun checkDeclaredExceptions(psiElement: PsiElement): Set<String> {
                return (psiElement as? PsiMethod)?.throwsList?.referenceElements
                    ?.mapNotNull { it.qualifiedName?.substringAfterLast(".") }
                    ?.toSet()
                    ?: emptySet()
            }

            private fun isInsideTryCatch(element: PsiElement): Boolean {
                var parent = element.parent
                while (parent != null) {
                    if (parent is PsiTryStatement) {
                        val tryBlock = parent.tryBlock
                        if (parent.catchBlocks.isNotEmpty() && tryBlock != null) {
                            if (tryBlock.textRange.contains(element.textRange)) {
                                return true
                            }
                        }
                    }
                    parent = parent.parent
                }
                return false
            }

            private fun isInsideTryCatch(expression: KtExpression): Boolean {
                var parent = expression.parent
                while (parent != null) {
                    if (parent is KtTryExpression) {
                        if (parent.catchClauses.isNotEmpty() &&
                            parent.tryBlock.text.contains(expression.text)
                        ) {
                            return true
                        }
                    }
                    parent = parent.parent
                }
                return false
            }

            private fun isInsideRunCatching(expression: KtExpression): Boolean {
                var parent = expression.parent
                while (parent != null) {
                    if (parent is KtCallExpression) {
                        val calleeExpression = parent.calleeExpression
                        if (calleeExpression is KtNameReferenceExpression) {
                            val name = calleeExpression.getReferencedName()
                            if (name == "runCatching") {
                                parent.lambdaArguments.forEach { lambdaArg ->
                                    val lambdaExpr = lambdaArg.getLambdaExpression()
                                    if (lambdaExpr?.textRange?.contains(expression.textRange) == true) {
                                        return true
                                    }
                                }
                                parent.valueArgumentList?.arguments?.forEach { arg ->
                                    val lambdaExpr = arg.getArgumentExpression() as? KtLambdaExpression
                                    if (lambdaExpr?.textRange?.contains(expression.textRange) == true) {
                                        return true
                                    }
                                }
                            }
                        }
                    }
                    parent = parent.parent
                }
                return false
            }

            private fun isSuppressed(expression: KtCallExpression): Boolean {
                var current: PsiElement? = expression
                while (current != null) {
                    if (current is KtDeclaration) {
                        current.annotationEntries.forEach { annotation ->
                            val shortName = annotation.shortName?.asString()
                            if (shortName == SUPPRESS_ANNOTATION) {
                                annotation.valueArguments.forEach { arg ->
                                    val value = arg.getArgumentExpression()
                                    if (value is KtStringTemplateExpression) {
                                        val text = value.plainContent
                                        if (text == SUPPRESS_NAME || text == SUPPRESS_NAME_SECOND) {
                                            return true
                                        }
                                    }
                                }
                            }
                        }
                    }
                    current = current.parent
                }
                return false
            }

            private fun checkLocalKotlinFiles(psiElement: PsiElement): Set<String> {
                val exceptions = mutableSetOf<String>()

                val functionCall = psiElement as? KtNamedFunction
                functionCall?.accept(object : KtTreeVisitorVoid() {
                    override fun visitThrowExpression(expression: KtThrowExpression) {
                        super.visitThrowExpression(expression)
                        val shortName = getThrowShortName(expression) ?: return
                        exceptions.add(shortName)
                    }
                })

                val constructorCall = psiElement as? KtConstructor<*>
                if (constructorCall != null) {
                    constructorCall.accept(object : KtTreeVisitorVoid() {
                        override fun visitThrowExpression(expression: KtThrowExpression) {
                            super.visitThrowExpression(expression)
                            val shortName = getThrowShortName(expression) ?: return
                            exceptions.add(shortName)
                        }
                    })

                    val initBlock = constructorCall.getContainingClassOrObject()
                    initBlock.getAnonymousInitializers().forEach {
                        it.accept(object : KtTreeVisitorVoid() {
                            override fun visitThrowExpression(expression: KtThrowExpression) {
                                super.visitThrowExpression(expression)
                                val shortName = getThrowShortName(expression) ?: return
                                exceptions.add(shortName)
                            }
                        })
                    }
                }

                return exceptions
            }

            private fun getThrowShortName(expression: KtThrowExpression): String? {
                if (isInsideTryCatch(expression)) return null
                if (isInsideRunCatching(expression)) return null
                return expression.thrownExpression?.getPossiblyQualifiedCallExpression()?.text
            }
        }

    companion object {
        const val SUPPRESS_NAME = "KNOWN_EXCEPTION"
        const val SUPPRESS_NAME_SECOND = "ALL"
        const val SUPPRESS_ANNOTATION = "Suppress"
        const val THROWS_ANNOTATION = "Throws"
        const val DOC_TAG = "throws"
        const val UNKNOWN = "Unknown"
    }
}
