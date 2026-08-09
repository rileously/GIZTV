package android.content

import java.io.File
import java.util.Properties

class PackageManager {
    fun hasSystemFeature(feature: String): Boolean = true
    companion object {
        const val FEATURE_LEANBACK = "android.software.leanback"
    }
}

class AssetManager {
    fun open(fileName: String): java.io.InputStream =
        java.io.ByteArrayInputStream(ByteArray(0))
}

open class Context {
    open val applicationContext: Context get() = this
    open val packageManager: PackageManager get() = PackageManager()
    open val filesDir: File get() = File(System.getProperty("user.home"), ".auroratv").apply { mkdirs() }
    open val assets: AssetManager get() = AssetManager()

    open fun getSystemService(name: String): Any? {
        return when (name) {
            NSD_SERVICE -> android.net.nsd.NsdManager()
            AUDIO_SERVICE -> android.media.AudioManager()
            else -> null
        }
    }

    open fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
        return DesktopSharedPreferences(name)
    }

    companion object {
        const val MODE_PRIVATE = 0
        const val NSD_SERVICE = "servicediscovery"
        const val AUDIO_SERVICE = "audio"
    }
}

class ContextWrapper(base: Context) : Context()

interface SharedPreferences {
    fun contains(key: String): Boolean
    fun getString(key: String, defValue: String?): String?
    fun getStringSet(key: String, defValues: Set<String>?): Set<String>?
    fun getInt(key: String, defValue: Int): Int
    fun getLong(key: String, defValue: Long): Long
    fun getFloat(key: String, defValue: Float): Float
    fun getBoolean(key: String, defValue: Boolean): Boolean
    val all: Map<String, *>
    fun edit(): Editor

    interface Editor {
        fun putString(key: String, value: String?): Editor
        fun putStringSet(key: String, values: Set<String>?): Editor
        fun putInt(key: String, value: Int): Editor
        fun putLong(key: String, value: Long): Editor
        fun putFloat(key: String, value: Float): Editor
        fun putBoolean(key: String, value: Boolean): Editor
        fun remove(key: String): Editor
        fun clear(): Editor
        fun apply()
        fun commit(): Boolean
    }
}

class DesktopSharedPreferences(private val name: String) : SharedPreferences {
    private val file: File
    private val props = Properties()

    init {
        val dir = File(System.getProperty("user.home"), ".auroratv")
        dir.mkdirs()
        file = File(dir, "$name.properties")
        if (file.exists()) {
            runCatching { file.inputStream().use { props.load(it) } }
        }
    }

    private fun save() {
        runCatching { file.outputStream().use { props.store(it, null) } }
    }

    override fun contains(key: String): Boolean = props.containsKey(key)
    override fun getString(key: String, defValue: String?): String? = props.getProperty(key, defValue)
    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? {
        val valStr = props.getProperty(key) ?: return defValues
        return valStr.split("|||").toSet()
    }
    override fun getInt(key: String, defValue: Int): Int = props.getProperty(key)?.toIntOrNull() ?: defValue
    override fun getLong(key: String, defValue: Long): Long = props.getProperty(key)?.toLongOrNull() ?: defValue
    override fun getFloat(key: String, defValue: Float): Float = props.getProperty(key)?.toFloatOrNull() ?: defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean = props.getProperty(key)?.toBoolean() ?: defValue
    override val all: Map<String, *> get() = props.entries.associate { it.key.toString() to it.value.toString() }

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        override fun putString(key: String, value: String?): SharedPreferences.Editor {
            if (value != null) props.setProperty(key, value) else props.remove(key)
            return this
        }
        override fun putStringSet(key: String, values: Set<String>?): SharedPreferences.Editor {
            if (values != null) props.setProperty(key, values.joinToString("|||")) else props.remove(key)
            return this
        }
        override fun putInt(key: String, value: Int): SharedPreferences.Editor {
            props.setProperty(key, value.toString())
            return this
        }
        override fun putLong(key: String, value: Long): SharedPreferences.Editor {
            props.setProperty(key, value.toString())
            return this
        }
        override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
            props.setProperty(key, value.toString())
            return this
        }
        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
            props.setProperty(key, value.toString())
            return this
        }
        override fun remove(key: String): SharedPreferences.Editor {
            props.remove(key)
            return this
        }
        override fun clear(): SharedPreferences.Editor {
            props.clear()
            return this
        }
        override fun apply() { save() }
        override fun commit(): Boolean { save(); return true }
    }
}
