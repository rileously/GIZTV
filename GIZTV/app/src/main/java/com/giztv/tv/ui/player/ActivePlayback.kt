package com.giztv.tv.ui.player

/**
 * One thing at a time is allowed to be heard.
 *
 * The player is not the only thing in this app able to play a film. Resolving a title means loading
 * a provider's own page and letting its player start, because that is what makes the video address
 * appear at all, and the prefetcher does exactly that behind whatever is on screen. A provider that
 * has to be told to press play — Videasy is the one that needs it — therefore starts a second film
 * behind the one being watched.
 *
 * Everything able to make a sound registers here. Whoever begins playing claims it, and everything
 * else registered is silenced: a page being read for its video address is muted where it stands, so
 * that it can still be read, and a page being looked at is stopped.
 *
 * [Source.silence] is called on the caller's thread, so an implementation that touches a view must
 * post to its own looper.
 */
internal object ActivePlayback {
  /** Something that can be told to stop making sound. */
  fun interface Source {
    fun silence()
  }

  private val lock = Any()
  private val sources = mutableListOf<Source>()

  /** Says that [source] exists and can be silenced. Registering twice is the same as once. */
  fun register(source: Source) {
    synchronized(lock) {
      if (sources.none { it === source }) sources += source
    }
  }

  fun unregister(source: Source) {
    synchronized(lock) { sources.removeAll { it === source } }
  }

  /**
   * Says that [source] is now playing, which silences everything else that is registered.
   *
   * The claimant need not be registered itself: nothing here ever silences whoever is claiming.
   */
  fun claim(source: Source) {
    // Copied out of the lock, so silencing something is free to unregister it.
    val others = synchronized(lock) { sources.filter { it !== source } }
    others.forEach(Source::silence)
  }
}
