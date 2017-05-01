package com.fuzz.processor

import com.fuzz.datacontroller.annotations.DB
import com.fuzz.datacontroller.annotations.Memory
import com.fuzz.datacontroller.annotations.Reuse
import com.fuzz.datacontroller.annotations.Targets
import com.fuzz.processor.utils.annotation
import com.fuzz.processor.utils.dataControllerAnnotation
import com.fuzz.processor.utils.toClassName
import com.fuzz.processor.utils.toTypeElement
import com.grosner.kpoet.*
import com.squareup.javapoet.*
import javax.lang.model.element.ExecutableElement

/**
 * Description: Represents a method containing annotations that construct our request methods.
 */
class DataRequestDefinition(executableElement: ExecutableElement, dataControllerProcessorManager: DataControllerProcessorManager)
    : BaseDefinition(executableElement, dataControllerProcessorManager), TypeAdder {

    var memory = false
    var db = false
    var singleDb = true
    var async = false
    var network = false
    var reuse = false
    var reuseMethodName = ""
    var targets = false

    var hasNetworkAnnotation = false
    var hasMemoryAnnotation = false
    var hasDBAnnotation = false

    val params: List<DataRequestParamDefinition>

    var dataType: ClassName? = null

    var specialParams = arrayListOf<DataRequestParamDefinition>()

    val controllerName: String

    init {

        targets = executableElement.annotation<Targets>() != null
        db = executableElement.annotation<DB>() != null
        if (db) hasDBAnnotation = true
        memory = executableElement.annotation<Memory>() != null
        if (memory) hasMemoryAnnotation = true
        executableElement.annotation<Reuse>()?.let {
            reuse = true
            reuseMethodName = it.value
        }

        executableElement.annotationMirrors.forEach {
            val typeName = it.annotationType.typeName
            if (retrofitMethodSet.contains(typeName)) {
                network = true
                hasNetworkAnnotation = true
            }
        }

        params = executableElement.parameters.map { DataRequestParamDefinition(it, managerDataController) }

        val nameAllocator = NameAllocator()

        if (!reuse) {
            controllerName = nameAllocator.newName(elementName)
        } else {
            controllerName = reuseMethodName
        }

        // needs a proper annotation otherwise we throw it away.
        if (!db && !memory && !reuse && !network) {
            valid = false
        } else {

            val returnType = executableElement.returnType.typeName
            (returnType as ParameterizedTypeName).let {
                val typeParameters = it.typeArguments
                dataType = typeParameters[0].toTypeElement().toClassName()
            }
        }

        // find special param types and keep track here.
        params.filter { it.isCallback }.getOrNull(0)?.let { specialParams.add(it) }
        params.filter { it.isErrorFilter }.getOrNull(0)?.let { specialParams.add(it) }

    }

    fun evaluateReuse(reqDefinitions: MutableList<DataRequestDefinition>) {
        if (reuse) {
            val def = reqDefinitions.find { it.controllerName == reuseMethodName }
            if (def == null) {
                managerDataController.logError(DataRequestDefinition::class,
                        "Could not find $reuseMethodName for method $elementName. Ensure you specify the name properly.")
            } else {
                if (def.dataType != dataType) {
                    managerDataController.logError(DataRequestDefinition::class,
                            "The referenced $reuseMethodName must match data types. found ${def.dataType} for referenced.")
                } else {
                    network = def.network
                    db = def.db
                    singleDb = def.singleDb
                    async = def.async
                }
            }
        }
    }

    fun MethodSpec.Builder.applyAnnotations() {
        // don't redeclare any library annotations. forward anything else through.
        (element as ExecutableElement).annotationMirrors.filterNot {
            it.dataControllerAnnotation()
        }.forEach {
            addAnnotation(AnnotationSpec.get(it))
        }
    }

    fun MethodSpec.Builder.addToConstructor() {
        if (!reuse) {
            code {
                add("$controllerName = \$T.controllerOf(", DATACONTROLLER)
                if (memory) {
                    add("\n\$T.<\$T>builderInstance().build()", MEMORY_SOURCE, dataType)
                }
                if (db) {
                    if (memory) {
                        add(",")
                    }
                    add("\n\$T.<\$T>builderInstance(\$T.class, ${async.L}).build()",
                            if (singleDb) DBFLOW_SINGLE_SOURCE else DBFLOW_LIST_SOURCE, dataType, dataType)
                }
                if (network) {
                    if (db || memory) {
                        add(",")
                    }
                    add("\n\$T.<\$T>builderInstance().build()", RETROFIT_SOURCE, dataType)
                }

                add(");\n")
            }
        }
    }

    fun TypeSpec.Builder.addToRetrofitInterface() {
        if (!reuse || hasNetworkAnnotation) {
            public(ParameterizedTypeName.get(CALL, dataType), elementName) {
                applyAnnotations()
                modifiers(abstract)
                params.filter { it.isQuery }.forEach { it.apply { this@public.addRetrofitParamCode() } }
                this
            }
        }
    }

    fun addServiceCall(codeBlock: CodeBlock.Builder) {
        codeBlock.add("${if (reuse && !hasNetworkAnnotation) controllerName else elementName}(")
        codeBlock.add(params.filter { it.isQuery }.joinToString { it.paramName })
        codeBlock.add(")")
    }

    override fun TypeSpec.Builder.addToType() {
        if (!reuse) {
            `private final field`(ParameterizedTypeName.get(DATACONTROLLER, dataType), controllerName)
        }

        public(DATACONTROLLER_REQUEST, elementName) {
            params.forEach { it.apply { addParamCode() } }
            applyAnnotations()
            annotation(Override::class)

            statement("\$T request = $controllerName.request()",
                    ParameterizedTypeName.get(DATACONTROLLER_REQUEST_BUILDER, dataType))
            specialParams.forEach { it.apply { addSpecialCode() } }
            if (network && (hasNetworkAnnotation || !targets)) {
                code {
                    add("request.targetSource(\$T.networkParams(),", DATA_SOURCE_PARAMS)
                    indent()
                    add("\n new \$T<>(service.", RETROFIT_SOURCE_PARAMS)
                    addServiceCall(this)
                    add("));\n")
                    unindent()
                }
            }
            if (db && (hasDBAnnotation || !targets)) {
                code {
                    add("request.targetSource(\$T.diskParams(),", DATA_SOURCE_PARAMS)
                    indent()
                    add("\nnew \$T(\n\$T.select().from(\$T.class).where()",
                            DBFLOW_PARAMS, SQLITE, dataType)
                    indent()
                    params.forEach {
                        if (it.isQuery) {
                            add("\n.and(\$T.${it.paramName}.eq(${it.paramName}))",
                                    ClassName.get(dataType!!.packageName(), "${dataType!!.simpleName()}_Table"))
                        }
                    }
                    unindent()

                    add("));\n")
                    unindent()
                }
            }

            if (targets) {
                if (hasDBAnnotation) {
                    statement("request.addRequestSourceTarget(\$T.diskParams())", DATA_SOURCE_PARAMS);
                }
                if (hasMemoryAnnotation) {
                    statement("request.addRequestSourceTarget(\$T.memoryParams())", DATA_SOURCE_PARAMS);
                }
                if (hasNetworkAnnotation) {
                    statement("request.addRequestSourceTarget(\$T.networkParams())", DATA_SOURCE_PARAMS);
                }
            }
            `return`("request.build()")
        }
    }
}