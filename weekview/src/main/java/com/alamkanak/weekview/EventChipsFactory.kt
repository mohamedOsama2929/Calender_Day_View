package com.alamkanak.weekview

import java.util.Calendar

internal class EventChipsFactory {

    fun create(
        events: List<ResolvedWeekViewEntity>,
        viewState: ViewState
    ): List<EventChip> {
        val eventChips = convertEventsToEventChips(events, viewState)
        val groups = eventChips.groupedByDate().values

        for (group in groups) {
            computePositionOfEvents(group, viewState)
        }

        return eventChips
    }

    private fun convertEventsToEventChips(
        events: List<ResolvedWeekViewEntity>,
        viewState: ViewState
    ): List<EventChip> {
        return events.sortedByTime().sanitize(viewState).toEventChips(viewState)
    }

    private fun List<ResolvedWeekViewEntity>.sortedByTime(): List<ResolvedWeekViewEntity> {
        return sortedWith(compareBy({ it.startTime }, { it.endTime }))
    }

    private fun List<ResolvedWeekViewEntity>.sanitize(viewState: ViewState): List<ResolvedWeekViewEntity> {
        return map { it.sanitize(viewState) }
    }

    private fun List<ResolvedWeekViewEntity>.toEventChips(viewState: ViewState): List<EventChip> {
        return map { event ->
            val eventParts = event.split(viewState)
            eventParts.mapIndexed { index, eventPart ->
                EventChip(
                    event = event,
                    index = index,
                    startTime = eventPart.startTime,
                    endTime = eventPart.endTime,
                )
            }
        }.flatten()
    }

    /**
     * Forms [CollisionGroup]s for all event chips and uses them to expand the [EventChip]s to their
     * maximum width.
     *
     * @param eventChips A list of [EventChip]s
     */
    private fun computePositionOfEvents(eventChips: List<EventChip>, viewState: ViewState) {
        val singleEventChips = eventChips.filter { it.event.isNotAllDay }
        val allDayEventChips = eventChips.filter { it.event.isAllDay }

        val singleEventGroups = singleEventChips.toMultiColumnCollisionGroups()
        val allDayGroups = if (viewState.arrangeAllDayEventsVertically) {
            allDayEventChips.toSingleColumnCollisionGroups()
        } else {
            allDayEventChips.toMultiColumnCollisionGroups()
        }

        for (collisionGroup in singleEventGroups) {
            expandEventsToMaxWidth(collisionGroup, viewState)
        }

        for (collisionGroup in allDayGroups) {
            expandEventsToMaxWidth(collisionGroup, viewState)
        }
    }

    private fun List<EventChip>.toSingleColumnCollisionGroups(): List<CollisionGroup> {
        return map { CollisionGroup(it) }
    }

    private fun List<EventChip>.toMultiColumnCollisionGroups(): List<CollisionGroup> {
        val collisionGroups = mutableListOf<CollisionGroup>()

        for (eventChip in this) {
            val collidingGroup = collisionGroups.firstOrNull { it.collidesWith(eventChip) }

            if (collidingGroup != null) {
                collidingGroup.add(eventChip)
            } else {
                collisionGroups += CollisionGroup(eventChip)
            }
        }

        return collisionGroups
    }

    /**
     * Expands all [EventChip]s in a [CollisionGroup] to their maximum width.
     */
    private fun expandEventsToMaxWidth(collisionGroup: CollisionGroup, viewState: ViewState) {
        val columns = mutableListOf<Column>()
        columns += Column(index = 0)

        for (eventChip in collisionGroup.eventChips) {
            val fittingColumns = columns.filter { it.fits(eventChip) }
            when (fittingColumns.size) {
                0 -> {
                    val index = columns.size
                    columns += Column(index, eventChip)
                }
                1 -> {
                    val fittingColumn = fittingColumns.single()
                    fittingColumn.add(eventChip)
                }
                else -> {
                    // This event chip can span multiple columns.
                    val areAdjacentColumns = fittingColumns.map { it.index }.isContinuous
                    if (areAdjacentColumns) {
                        for (column in fittingColumns) {
                            column.add(eventChip)
                        }
                    } else {
                        val leftMostColumn = checkNotNull(fittingColumns.minByOrNull { it.index })
                        leftMostColumn.add(eventChip)
                    }
                }
            }
        }

        val rows = columns.map { it.size }.maxOrNull() ?: 0
        val columnWidth = 1f / columns.size

        for (row in 0 until rows) {
            val zipped = columns.zipWithPrevious()
            for ((previous, current) in zipped) {
                val hasEventInRow = current.size > row
                if (hasEventInRow) {
                    expandColumnEventToMaxWidth(current, previous, row, columnWidth, columns.size)
                }
            }
        }

        for (eventChip in collisionGroup.eventChips) {
            calculateMinutesFromStart(eventChip, viewState)
        }
    }

    private fun calculateMinutesFromStart(eventChip: EventChip, viewState: ViewState) {
        if (eventChip.event.isAllDay) {
            return
        }

        eventChip.minutesFromStartHour = viewState.minutesFromStart(eventChip.startTime)
    }

    private fun expandColumnEventToMaxWidth(
        current: Column,
        previous: Column?,
        row: Int,
        columnWidth: Float,
        columns: Int
    ) {
        val index = current.index
        val eventChip = current[row]

        val duplicateInPreviousColumn = previous?.findDuplicate(eventChip)

        if (duplicateInPreviousColumn != null) {
            duplicateInPreviousColumn.relativeWidth += columnWidth
        } else {
            // Every column gets the same width. For instance, if there are four columns,
            // then each column's width is 0.25.
            eventChip.relativeWidth = columnWidth

            // The start position is calculated based on the index of the column. For
            // instance, if there are four columns, the start positions will be 0.0, 0.25, 0.5
            // and 0.75.
            eventChip.relativeStart = index.toFloat() / columns
        }
    }

    /**
     * This class encapsulates [EventChip]s that collide with each other, meaning that
     * they overlap from a time perspective.
     *
     */
    private class CollisionGroup(
        val eventChips: MutableList<EventChip>
    ) {

        constructor(eventChip: EventChip) : this(mutableListOf(eventChip))

        /**
         * Returns whether an [EventChip] collides with any [EventChip] already in the
         * [CollisionGroup].
         *
         * @param eventChip An [EventChip]
         * @return Whether a collision exists
         */
        fun collidesWith(eventChip: EventChip): Boolean {
            return eventChips.any { it.event.collidesWith(eventChip.event) }
        }

        fun add(eventChip: EventChip) {
            eventChips.add(eventChip)
        }
    }

    /**
     * This class encapsulates [EventChip]s that are displayed in the same column.
     */
    private class Column(
        val index: Int,
        val eventChips: MutableList<EventChip> = mutableListOf()
    ) {

        constructor(index: Int, eventChip: EventChip) : this(index, mutableListOf(eventChip))

        val isEmpty: Boolean
            get() = eventChips.isEmpty()

        val size: Int
            get() = eventChips.size

        fun add(eventChip: EventChip) {
            eventChips.add(eventChip)
        }

        fun findDuplicate(eventChip: EventChip) = eventChips.firstOrNull { it == eventChip }

        operator fun get(index: Int): EventChip = eventChips[index]

        fun fits(eventChip: EventChip): Boolean {
            return isEmpty || !eventChips.last().event.collidesWith(eventChip.event)
        }
    }

    private val List<Int>.isContinuous: Boolean
        get() {
            val zipped = sorted().zipWithNext()
            return zipped.all { it.first + 1 == it.second }
        }

    private fun <T> List<T>.zipWithPrevious(): List<Pair<T?, T>> {
        val results = mutableListOf<Pair<T?, T>>()
        for (index in 0 until size) {
            val previous = getOrNull(index - 1)
            val current = get(index)
            results += Pair(previous, current)
        }
        return results
    }

    private fun List<EventChip>.groupedByDate(): Map<Calendar, List<EventChip>> {
        return groupBy { it.startTime.atStartOfDay }
    }
}

private fun ResolvedWeekViewEntity.sanitize(viewState: ViewState): ResolvedWeekViewEntity {
    return if (endTime.isAtStartOfPeriod(hour = viewState.minHour)) {
        createCopy(endTime = endTime.minusMillis(1))
    } else {
        this
    }
}
