package job

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

val Job.name get() = (this as CoroutineScope).coroutineContext[CoroutineName.Key]?.name ?: "N/A"
