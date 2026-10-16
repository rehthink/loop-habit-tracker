package app.loop.core.domain

import javax.inject.Qualifier

/** Inject `@Dispatcher(LoopDispatcher.IO) CoroutineDispatcher` instead of hard-coding Dispatchers.IO. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class Dispatcher(val dispatcher: LoopDispatcher)

enum class LoopDispatcher { DEFAULT, IO }

/** An application-wide CoroutineScope that outlives any screen. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationScope
