package com.piyushpatnaik.stashkeeper.data

import android.app.Activity
import android.os.Build
import android.os.CancellationSignal
import android.util.Log
import androidx.core.content.ContextCompat
import com.piyushpatnaik.stashkeeper.logic.AssistantRejected
import com.piyushpatnaik.stashkeeper.logic.AssistantReply
import com.piyushpatnaik.stashkeeper.logic.assistantExpiring
import com.piyushpatnaik.stashkeeper.logic.assistantFind
import com.piyushpatnaik.stashkeeper.logic.assistantNewItem
import com.piyushpatnaik.stashkeeper.logic.assistantShopping
import com.piyushpatnaik.stashkeeper.logic.assistantShoppingName
import java.lang.reflect.Proxy
import java.util.concurrent.Executor

/**
 * Android 17 AppFunctions, registered while StashKeeper's activity is started.
 * The platform drops the registration when that activity is gone, so the
 * actions are available for the time the app is open. compileSdk stays on
 * the installed platform, so the Android 17 types are reached by reflection.
 */
object StashFunctions {
    private const val tag = "StashKeeper"
    private val ids = listOf("findItems", "listExpiring", "addItem", "shoppingList", "addShoppingItem")
    private var holders = 0
    private var registration: Any? = null

    fun acquire(activity: Activity) {
        if (Build.VERSION.SDK_INT < 37) return
        holders += 1
        if (registration != null) return
        registration = runCatching { register(activity) }.onFailure {
            Log.i(tag, "Assistant actions are not available yet: ${it.message}")
        }.getOrNull()
    }

    fun release() {
        if (Build.VERSION.SDK_INT < 37) return
        holders = (holders - 1).coerceAtLeast(0)
        if (holders > 0) return
        val current = registration ?: return
        registration = null
        runCatching { current.javaClass.getMethod("unregister").invoke(current) }
    }

    private fun register(activity: Activity): Any {
        val manager = activity.getSystemService("app_function")
            ?: error("This device has no app function service.")
        val functionClass = Class.forName("android.app.appfunctions.AppFunction")
        val requestClass = Class.forName("android.app.appfunctions.RegisterAppFunctionRequest")
        val executor: Executor = ContextCompat.getMainExecutor(activity)
        val implementation = Proxy.newProxyInstance(
            functionClass.classLoader,
            arrayOf(functionClass),
        ) { proxy, method, args ->
            when (method.name) {
                "onExecuteAppFunction" -> {
                    execute(activity, args?.getOrNull(0), args?.getOrNull(1), args?.getOrNull(2))
                    null
                }
                "equals" -> proxy === args?.getOrNull(0)
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "StashFunctions"
                else -> null
            }
        }
        val constructor = requestClass.getConstructor(String::class.java, Executor::class.java, functionClass)
        val requests = ids.map { constructor.newInstance(it, executor, implementation) }
        return manager.javaClass.getMethod("registerAppFunctions", List::class.java).invoke(manager, requests)
            ?: error("App function registration returned nothing.")
    }

    private fun execute(activity: Activity, request: Any?, cancellation: Any?, callback: Any?) {
        if (request == null || callback == null) return
        if (cancellation is CancellationSignal && cancellation.isCanceled) {
            fail(callback, 2001, "Cancelled")
            return
        }
        val id = request.javaClass.getMethod("getFunctionIdentifier").invoke(request) as? String ?: ""
        val parameters = request.javaClass.getMethod("getParameters").invoke(request)
        runCatching { reply(activity, id, parameters) }
            .onSuccess { deliver(callback, document(id, it), error = false) }
            .onFailure { error ->
                when (error) {
                    is AssistantRejected -> fail(callback, 1001, error.message ?: "That request was not usable.")
                    is UnknownFunction -> fail(callback, 1003, error.message ?: "Unknown function.")
                    else -> fail(callback, 3000, error.message ?: "Couldn't finish that.")
                }
            }
    }

    private fun reply(activity: Activity, id: String, parameters: Any?): AssistantReply {
        val store = StashSession.get(activity)
        return when (id) {
            "findItems" -> assistantFind(store.items, store.places, text(parameters, "query"), store.today(), store.warningDays)
            "listExpiring" -> assistantExpiring(store.items, store.places, store.today(), store.warningDays)
            "addItem" -> {
                val place = text(parameters, "place").ifBlank { null }
                val locationId = place?.let { store.ensurePlace(it) }
                val item = assistantNewItem(text(parameters, "name"), quantity(parameters), locationId, System.currentTimeMillis(), store.householdName)
                store.add(item)
                val where = place?.let { " in $it" }.orEmpty()
                AssistantReply("Added ${item.quantity} ${item.name}$where.", listOf(item.name))
            }
            "shoppingList" -> assistantShopping(store.items, store.needs)
            "addShoppingItem" -> {
                val name = assistantShoppingName(text(parameters, "name"))
                store.addNeed(name)
                AssistantReply("Added $name to the shopping list.", listOf(name))
            }
            else -> throw UnknownFunction(id)
        }
    }

    private fun document(id: String, reply: AssistantReply): Any {
        val builderClass = Class.forName("android.app.appsearch.GenericDocument\$Builder")
        val builder = builderClass
            .getConstructor(String::class.java, String::class.java, String::class.java)
            .newInstance("com.piyushpatnaik.stashkeeper", id, "StashResult")
        val setString = builderClass.getMethod("setPropertyString", String::class.java, Array<String>::class.java)
        setString.invoke(builder, "androidAppfunctionsReturnValue", arrayOf(reply.speech))
        if (reply.lines.isNotEmpty()) setString.invoke(builder, "lines", reply.lines.toTypedArray())
        val document = builderClass.getMethod("build").invoke(builder)
        val documentClass = Class.forName("android.app.appsearch.GenericDocument")
        val responseClass = Class.forName("android.app.appfunctions.ExecuteAppFunctionResponse")
        return responseClass.getConstructor(documentClass).newInstance(document)
    }

    private fun fail(callback: Any, code: Int, message: String) {
        val error = runCatching {
            Class.forName("android.app.appfunctions.AppFunctionException")
                .getConstructor(Int::class.javaPrimitiveType, String::class.java)
                .newInstance(code, message)
        }.getOrElse {
            deliver(callback, IllegalStateException(message), error = true)
            return
        }
        deliver(callback, error, error = true)
    }

    private fun deliver(callback: Any, value: Any, error: Boolean) {
        val name = if (error) "onError" else "onResult"
        val method = callback.javaClass.methods.firstOrNull { it.name == name && it.parameterTypes.size == 1 } ?: return
        method.invoke(callback, value)
    }

    private fun text(parameters: Any?, name: String): String {
        if (parameters == null) return ""
        val value = parameters.javaClass.getMethod("getPropertyString", String::class.java).invoke(parameters, name) as? String
        return value.orEmpty()
    }

    private fun quantity(parameters: Any?): Int? {
        if (parameters == null || !has(parameters, "quantity")) return null
        val written = text(parameters, "quantity").toIntOrNull()
        if (written != null) return written
        val number = parameters.javaClass.getMethod("getPropertyLong", String::class.java).invoke(parameters, "quantity") as Long
        return number.toInt()
    }

    private fun has(parameters: Any, name: String): Boolean {
        val names = parameters.javaClass.getMethod("getPropertyNames").invoke(parameters) as Set<*>
        return name in names
    }

    private class UnknownFunction(id: String) : Exception("Unknown function: $id")
}
