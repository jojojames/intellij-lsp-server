package com.ruin.intel.commands

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.javadoc.ColorUtil
import com.intellij.codeInsight.javadoc.JavaDocInfoGenerator
import com.intellij.codeInsight.javadoc.JavaDocUtil
import com.intellij.codeInsight.javadoc.NonCodeAnnotationGenerator
import com.intellij.lang.LangBundle
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.*
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.search.EverythingGlobalScope
import com.intellij.psi.util.PsiFormatUtil
import com.intellij.psi.util.PsiFormatUtilBase
import java.lang.StringBuilder
import java.util.*

/**
 * Creates a one-line documentation string for use in minibuffers and the like when hovering over a symbol.
 * Mainly copied from JavaDocInfoGenerator.
 */
class OneLineJavaDocInfoGenerator(val myProject: Project, val myElement: PsiElement) : JavaDocInfoGenerator(myProject, myElement) {
    override fun generateDocInfoCore(buffer: StringBuilder, generatePrologueAndEpilogue: Boolean): Boolean {
        when (myElement) {
            is PsiClass -> generateClassJavaDoc(buffer, myElement)
            is PsiMethod -> generateMethodJavaDoc(buffer, myElement)
            is PsiField -> generateFieldJavaDoc(buffer, myElement)
            is PsiVariable -> generateVariableJavaDoc(buffer, myElement)
            else -> return false
        }

        return true
    }
}

private val LOG = Logger.getInstance(OneLineJavaDocInfoGenerator::class.java)
private val THROWS_KEYWORD = "throws"
private val LT = "<"
private val GT = ">"
private val NBSP = " "

private fun generateClassJavaDoc(buffer: StringBuilder, aClass: PsiClass) {
    if (aClass is PsiAnonymousClass) return

    val file = aClass.containingFile
    if (file is PsiJavaFile) {
        val packageName = (file).packageName
        if (!packageName.isEmpty()) {
            buffer.append(packageName)
        }
    }

    buffer.append(" ")

    generateClassSignature(buffer, aClass)
}

private fun generateClassSignature(buffer: StringBuilder, aClass: PsiClass): Boolean {
    val modifiers = PsiFormatUtil.formatModifiers(aClass, PsiFormatUtilBase.JAVADOC_MODIFIERS_ONLY)
    if (!modifiers.isEmpty()) {
        buffer.append(modifiers)
        buffer.append(" ")
    }
    buffer.append(LangBundle.message(if (aClass.isInterface) "java.terms.interface" else "java.terms.class"))
    buffer.append(" ")
    val refText = JavaDocUtil.getReferenceText(aClass.project, aClass)
    if (refText == null) {
        buffer.setLength(0)
        return true
    }
    val labelText = JavaDocUtil.getLabelText(aClass.project, aClass.manager, refText, aClass)
    buffer.append(labelText)

    buffer.append(generateTypeParameters(aClass, false))

    buffer.append(" ")

    var refs = aClass.extendsListTypes

    val qName = aClass.qualifiedName

    if (refs.isNotEmpty() || !aClass.isInterface && (qName == null || qName != CommonClassNames.JAVA_LANG_OBJECT)) {
        buffer.append("extends ")
        if (refs.isEmpty()) {
            buffer.append(CommonClassNames.JAVA_LANG_OBJECT)
        } else {
            for (i in refs.indices) {
                generateType(buffer, refs[i], aClass)
                if (i < refs.size - 1) {
                    buffer.append(",")
                    buffer.append(NBSP)
                }
            }
        }
        buffer.append(" ")
    }

    refs = aClass.implementsListTypes

    if (refs.size > 0) {
        buffer.append("implements ")
        for (i in refs.indices) {
            generateType(buffer, refs[i], aClass)
            if (i < refs.size - 1) {
                buffer.append(",")
                buffer.append(NBSP)
            }
        }
        buffer.append("\n")
    }
    if (buffer[buffer.length - 1] == '\n') {
        buffer.setLength(buffer.length - 1)
    }
    return false
}

private fun generateMethodJavaDoc(buffer: StringBuilder, method: PsiMethod) {
    generateMethodSignature(buffer, method)
}

private fun generateMethodSignature(buffer: StringBuilder, method: PsiMethod) {
    val modifiers = PsiFormatUtil.formatModifiers(method, PsiFormatUtilBase.JAVADOC_MODIFIERS_ONLY)
    var indent = 0
    if (!modifiers.isEmpty()) {
        buffer.append(modifiers)
        buffer.append(NBSP)
        indent += modifiers.length + 1
    }

    val typeParamsString = generateTypeParameters(method, true)
    indent += StringUtil.unescapeXml(StringUtil.stripHtml(typeParamsString, true)).length
    if (!typeParamsString.isEmpty()) {
        buffer.append(typeParamsString)
        buffer.append(NBSP)
        indent++
    }

    if (method.returnType != null) {
        indent += generateType(buffer, method.returnType!!, method)
        buffer.append(NBSP)
        indent++
    }
    val name = method.name
    buffer.append(name)
    indent += name.length

    buffer.append("(")

    val parameters = method.parameterList.parameters
    for (i in parameters.indices) {
        val parm = parameters[i]
        generateType(buffer, parm.type, method)
        buffer.append(NBSP)
        if (parm.name != null) {
            buffer.append(parm.name)
        }
        if (i < parameters.size - 1) {
            buffer.append(", ")
            buffer.append(StringUtil.repeat(" ", indent))
        }
    }
    buffer.append(")")

    val refs = method.throwsList.referencedTypes
    if (refs.isNotEmpty()) {
        buffer.append("\n")
        indent -= THROWS_KEYWORD.length + 1
        for (i in 0 until indent) {
            buffer.append(" ")
        }
        indent += THROWS_KEYWORD.length + 1
        buffer.append(THROWS_KEYWORD)
        buffer.append(" ")
        for (i in refs.indices) {
            buffer.append(refs[i].presentableText)
            if (i < refs.size - 1) {
                buffer.append(",")
                for (j in 0 until indent) {
                    buffer.append(" ")
                }
            }
        }
    }
}


/**
 * @return Length of the generated label.
 */
fun generateType(buffer: StringBuilder, type: PsiType, context: PsiElement): Int {
    return generateType(buffer, type, context, true)
}

/**
 * @return Length of the generated label.
 */
fun generateType(buffer: StringBuilder, type: PsiType, context: PsiElement, generateLink: Boolean): Int {
    return generateType(buffer, type, context, generateLink, false)
}

/**
 * @return Length of the generated label.
 */
fun generateType(buffer: StringBuilder, type: PsiType?, context: PsiElement, generateLink: Boolean, useShortNames: Boolean): Int {
    var type = type
    if (type is PsiPrimitiveType) {
        val text = type.canonicalText
        buffer.append(text)
        return text.length
    }

    if (type is PsiArrayType) {
        val rest = generateType(buffer, type.componentType, context, generateLink, useShortNames)
        if (type is PsiEllipsisType) {
            buffer.append("...")
            return rest + 3
        } else {
            buffer.append("[]")
            return rest + 2
        }
    }

    if (type is PsiCapturedWildcardType) {
        type = type.wildcard
    }

    if (type is PsiWildcardType) {
        val wt = type as PsiWildcardType?
        buffer.append("?")
        val bound = wt!!.bound
        if (bound != null) {
            val keyword = if (wt.isExtends) " extends " else " super "
            buffer.append(keyword)
            return generateType(buffer, bound, context, generateLink, useShortNames) + 1 + keyword.length
        } else {
            return 1
        }
    }

    if (type is PsiClassType) {
        val result: PsiClassType.ClassResolveResult
        try {
            result = type.resolveGenerics()
        } catch (e: IndexNotReadyException) {
            LOG.debug(e)
            val text = type.className
            buffer.append(text)
            return text.length
        }

        val psiClass = result.element
        val psiSubst = result.substitutor

        if (psiClass == null) {
            val canonicalText = type.canonicalText
            val text = canonicalText
            buffer.append(text)
            return canonicalText.length
        }

        val qName = psiClass.qualifiedName

        if (qName == null || psiClass is PsiTypeParameter) {
            val text = if (useShortNames) type.presentableText else type.canonicalText
            buffer.append(text)
            return text.length
        }

        val name = if (useShortNames) type.rawType().presentableText else qName

        buffer.append(name)
        var length = buffer.length

        if (psiClass.hasTypeParameters()) {
            val subst = StringBuilder()

            val params = psiClass.typeParameters

            subst.append(LT)
            length += 1
            var goodSubst = true
            for (i in params.indices) {
                val t = psiSubst.substitute(params[i])

                if (t == null) {
                    goodSubst = false
                    break
                }

                length += generateType(subst, t, context, generateLink, useShortNames)

                if (i < params.size - 1) {
                    subst.append(", ")
                }
            }

            subst.append(GT)
            length += 1
            if (goodSubst) {
                val text = subst.toString()

                buffer.append(text)
            }
        }

        return length
    }

    if (type is PsiDisjunctionType || type is PsiIntersectionType) {
        if (!generateLink) {
            val canonicalText = if (useShortNames) type.presentableText else type.canonicalText
            val text = canonicalText
            buffer.append(text)
            return canonicalText.length
        } else {
            val separator = if (type is PsiDisjunctionType) " | " else " & "
            val componentTypes: List<PsiType>
            if (type is PsiIntersectionType) {
                componentTypes = Arrays.asList(*type.conjuncts)
            } else {
                componentTypes = (type as PsiDisjunctionType).disjunctions
            }
            var length = 0
            for (psiType in componentTypes) {
                if (length > 0) {
                    buffer.append(separator)
                    length += 3
                }
                length += generateType(buffer, psiType, context, true, useShortNames)
            }
            return length
        }
    }

    return 0
}

private fun generateTypeParameters(owner: PsiTypeParameterListOwner, useShortNames: Boolean): String {
    if (owner.hasTypeParameters()) {
        val parameters = owner.typeParameters

        val buffer = StringBuilder()
        buffer.append(LT)

        for (i in parameters.indices) {
            val p = parameters[i]

            buffer.append(p.name)

            val refs = JavaDocUtil.getExtendsList(p)
            if (refs.isNotEmpty()) {
                buffer.append(" extends ")
                for (j in refs.indices) {
                    generateType(buffer, refs[j], owner, true, useShortNames)
                    if (j < refs.size - 1) {
                        buffer.append(" & ")
                    }
                }
            }

            if (i < parameters.size - 1) {
                buffer.append(", ")
            }
        }

        buffer.append(GT)
        return buffer.toString()
    }

    return ""
}

private fun generateFieldJavaDoc(buffer: StringBuilder, field: PsiField) {
    generateFieldSignature(buffer, field)

    ColorUtil.appendColorPreview(field, buffer)
}

private fun generateFieldSignature(buffer: StringBuilder, field: PsiField) {
    val modifiers = PsiFormatUtil.formatModifiers(field, PsiFormatUtilBase.JAVADOC_MODIFIERS_ONLY)
    if (!modifiers.isEmpty()) {
        buffer.append(modifiers)
        buffer.append(" ")
    }
    generateType(buffer, field.type, field)
    buffer.append(" ")
    buffer.append(field.name)
    appendInitializer(buffer, field)
    JavaDocInfoGenerator.enumConstantOrdinal(buffer, field, field.containingClass, "\n")
}

private fun generateVariableJavaDoc(buffer: StringBuilder, variable: PsiVariable) {
    val modifiers = PsiFormatUtil.formatModifiers(variable, PsiFormatUtilBase.JAVADOC_MODIFIERS_ONLY)
    if (!modifiers.isEmpty()) {
        buffer.append(modifiers)
        buffer.append(" ")
    }
    generateType(buffer, variable.getType(), variable)
    buffer.append(" ")
    buffer.append(variable.getName())
    appendInitializer(buffer, variable)

    ColorUtil.appendColorPreview(variable, buffer)
}

private fun appendInitializer(buffer: StringBuilder, variable: PsiVariable) {
    val initializer = variable.initializer
    if (initializer != null) {
        buffer.append(" = ")

        var text = initializer.text
        text = text.trim { it <= ' ' }
        var index1 = text.indexOf('\n')
        if (index1 < 0) index1 = text.length
        var index2 = text.indexOf('\r')
        if (index2 < 0) index2 = text.length
        val index = Math.min(index1, index2)
        val trunc = index < text.length
        if (trunc) {
            text = text.substring(0, index)
            buffer.append(text)
            buffer.append("...")
        } else {
            initializer.accept(MyVisitor(buffer))
        }
        val constantInitializer = JavaDocInfoGenerator.calcInitializerExpression(variable)
        if (constantInitializer != null) {
            buffer.append(" ")
            appendExpressionValue(buffer, constantInitializer, CodeInsightBundle.message("javadoc.resolved.value"))
        }
    }
}

fun appendExpressionValue(buffer: StringBuilder, initializer: PsiExpression, label: String) {
    var text = initializer.text.trim { it <= ' ' }
    var index1 = text.indexOf('\n')
    if (index1 < 0) index1 = text.length
    var index2 = text.indexOf('\r')
    if (index2 < 0) index2 = text.length
    val index = Math.min(index1, index2)
    val trunc = index < text.length
    text = text.substring(0, index)
    buffer.append(label)
    buffer.append(text)
    if (trunc) {
        buffer.append("...")
    }
}

private class MyVisitor internal constructor(private val myBuffer: StringBuilder) : JavaElementVisitor() {
    override fun visitNewExpression(expression: PsiNewExpression) {
        myBuffer.append("new ")
        val type = expression.type
        if (type != null) {
            generateType(myBuffer, type, expression)
        }
        val dimensions = expression.arrayDimensions
        if (dimensions.isNotEmpty()) {
            LOG.assertTrue(myBuffer[myBuffer.length - 1] == ']')
            myBuffer.setLength(myBuffer.length - 1)
            for (dimension in dimensions) {
                dimension.accept(this)
                myBuffer.append(", ")
            }
            myBuffer.setLength(myBuffer.length - 2)
            myBuffer.append(']')
        } else {
            expression.acceptChildren(this)
        }
    }

    override fun visitExpressionList(list: PsiExpressionList) {
        myBuffer.append("(")
        val separator = ", "
        val expressions = list.expressions
        for (expression in expressions) {
            expression.accept(this)
            myBuffer.append(separator)
        }
        if (expressions.size > 0) {
            myBuffer.setLength(myBuffer.length - separator.length)
        }
        myBuffer.append(")")
    }

    override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
        myBuffer.append(expression.methodExpression.text)
        expression.argumentList.accept(this)
    }

    override fun visitExpression(expression: PsiExpression) {
        myBuffer.append(expression.text)
    }

    override fun visitReferenceExpression(expression: PsiReferenceExpression?) {
        myBuffer.append(expression!!.text)
    }
}