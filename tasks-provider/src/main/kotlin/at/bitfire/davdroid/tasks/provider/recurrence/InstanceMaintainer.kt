/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.tasks.provider.recurrence

import at.bitfire.davdroid.tasks.provider.db.TaskEntity
import at.bitfire.davdroid.tasks.provider.db.TaskInstanceEntity
import at.bitfire.davdroid.tasks.provider.db.TasksDatabase

/**
 * Keeps [at.bitfire.tasks.contract.TaskInstances] in sync with [TaskEntity] writes: whenever a
 * task (main or RECURRENCE-ID exception) is inserted, updated, or deleted,
 * [at.bitfire.davdroid.tasks.provider.DavTasksProvider] calls [refreshFamily] for the affected
 * main task, and the whole family's instances are cleared and regenerated from scratch via
 * [RecurrenceExpander].
 *
 * "Whole family" = the main task ([rootTaskId]) plus every task row with
 * `original_instance_id = rootTaskId`. Regenerating the full family on every write (rather than
 * patching individual rows) keeps this simple and correct, at the cost of being a bit wasteful for
 * large recurring series - acceptable given [RecurrenceExpander]'s own instance/horizon bounds.
 *
 * [distanceFromCurrent] is computed against wall-clock time *at write time* and therefore goes
 * stale as time passes without a further write to that family - there is deliberately no
 * background job rolling it forward (see [RecurrenceExpander] for why).
 */
class InstanceMaintainer(
    private val database: TasksDatabase,
    private val now: () -> Long = System::currentTimeMillis
) {

    fun refreshFamily(rootTaskId: Long) {
        val taskDao = database.taskDao()
        val instanceDao = database.taskInstanceDao()

        // clear the whole family's instances first, including exceptions that are about to be
        // dropped from the regenerated set (e.g. a tombstoned exception's own leftover row)
        val allExceptions = taskDao.getAllExceptions(rootTaskId)
        instanceDao.deleteByTasks(listOf(rootTaskId) + allExceptions.map { it.id })

        val main = taskDao.get(rootTaskId) ?: return
        if (main.deleted) return

        val liveExceptions = allExceptions.filterNot { it.deleted }
        val candidates = RecurrenceExpander.expand(main.toSource(), liveExceptions.map { it.toExceptionSource() })
        if (candidates.isEmpty()) return

        instanceDao.insertAll(withDistanceFromCurrent(candidates))
    }

    private fun withDistanceFromCurrent(candidates: List<InstanceCandidate>): List<TaskInstanceEntity> {
        val sorted = candidates.sortedBy { it.instanceStart ?: Long.MAX_VALUE }
        val nowMillis = now()
        // "current" = the latest instance that has already started; if none have started yet,
        // the soonest upcoming one is "current" (distance 0)
        val currentIndex = sorted.indexOfLast { (it.instanceStart ?: Long.MAX_VALUE) <= nowMillis }.let { if (it == -1) 0 else it }

        return sorted.mapIndexed { position, candidate ->
            TaskInstanceEntity(
                taskId = candidate.taskId,
                instanceStart = candidate.instanceStart,
                instanceDue = candidate.instanceDue,
                instanceStartSorting = candidate.instanceStartSorting,
                instanceDueSorting = candidate.instanceDueSorting,
                distanceFromCurrent = (position - currentIndex).toLong()
            )
        }
    }

    private fun TaskEntity.toSource() = RecurringTaskSource(
        taskId = id,
        dtstart = dtstart,
        dtstartTz = dtstartTz,
        due = due,
        duration = duration,
        isAllDay = isAllDay,
        rrule = rrule,
        rdate = rdate,
        exdate = exdate
    )

    private fun TaskEntity.toExceptionSource() = RecurrenceExceptionSource(
        taskId = id,
        originalInstanceTime = requireNotNull(originalInstanceTime) { "Exception row $id has no original_instance_time" },
        dtstart = dtstart,
        dtstartTz = dtstartTz,
        due = due,
        duration = duration,
        isAllDay = isAllDay
    )

}
