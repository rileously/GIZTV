package android.util

open class LruCache<K : Any, V : Any>(val maxSize: Int) {
    private val map = object : LinkedHashMap<K, V>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
            return size > maxSize
        }
    }

    open fun sizeOf(key: K, value: V): Int = 1

    @Synchronized
    open fun get(key: K): V? = map[key]

    @Synchronized
    open fun put(key: K, value: V): V? = map.put(key, value)

    @Synchronized
    open fun remove(key: K): V? = map.remove(key)

    @Synchronized
    open fun evictAll() {
        map.clear()
    }
}
