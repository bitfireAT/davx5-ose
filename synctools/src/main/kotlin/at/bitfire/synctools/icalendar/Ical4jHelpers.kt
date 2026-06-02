/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.icalendar

import at.bitfire.ical4android.util.DateUtils
import at.bitfire.synctools.BuildConfig
import at.bitfire.synctools.exception.InvalidICalendarException
import net.fortuna.ical4j.model.Component
import net.fortuna.ical4j.model.ComponentContainer
import net.fortuna.ical4j.model.ComponentList
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.PropertyContainer
import net.fortuna.ical4j.model.PropertyList
import net.fortuna.ical4j.model.component.CalendarComponent
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.DtEnd
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.Due
import net.fortuna.ical4j.model.property.RecurrenceId
import net.fortuna.ical4j.model.property.Sequence
import net.fortuna.ical4j.model.property.Uid
import java.time.temporal.Temporal
import kotlin.jvm.optionals.getOrNull

/**
 * The used version of ical4j.
 */
@Suppress("unused")
const val ical4jVersion = BuildConfig.version_ical4j


// component access helpers

fun<T: CalendarComponent> componentListOf(vararg components: T): ComponentList<T> =
    ComponentList(components.toList())

fun propertyListOf(vararg properties: Property): PropertyList =
    PropertyList(properties.toList())

val CalendarComponent.uid: Uid?
    get() = getProperty<Uid>(Property.UID).getOrNull()

val CalendarComponent.recurrenceId: RecurrenceId<*>?
    get() = getProperty<RecurrenceId<*>>(Property.RECURRENCE_ID).getOrNull()

val CalendarComponent.sequence: Sequence?
    get() = getProperty<Sequence>(Property.SEQUENCE).getOrNull()

fun <T: Temporal> CalendarComponent.dtStart(): DtStart<T>? {
    return getProperty<DtStart<T>>(Property.DTSTART).getOrNull()
}

fun <T: Temporal> CalendarComponent.dtEnd(): DtEnd<T>? {
    return getProperty<DtEnd<T>>(Property.DTEND).getOrNull()
}

fun <T: Temporal> CalendarComponent.due(): Due<T>? {
    return getProperty<Due<T>>(Property.DUE).getOrNull()
}

fun <T: Temporal> VEvent.requireDtStart(): DtStart<T> =
    getProperty<DtStart<T>>(Property.DTSTART).getOrNull() ?: throw InvalidICalendarException("Missing DTSTART in VEVENT")

/**
 * Whether this VTODO represents an all-day task.
 *
 * All-day tasks use DATE values (not DATE-TIME). Returns `true` if:
 * - DTSTART is a DATE value, or
 * - DTSTART is absent and DUE is a DATE value, or
 * - both DTSTART and DUE are absent.
 */
fun VToDo.isAllDay(): Boolean =
    dtStart<Temporal>()?.let { DateUtils.isDate(it) }
        ?: due<Temporal>()?.let { DateUtils.isDate(it) }
        ?: true

operator fun PropertyContainer.plusAssign(property: Property) {
    add<PropertyContainer>(property)
}

operator fun PropertyList.plusAssign(property: Property) {
    add(property)
}

operator fun Property.plusAssign(parameter: Parameter) {
    add<Property>(parameter)
}

operator fun <T : Component> ComponentContainer<T>.plusAssign(component: T) {
    add<ComponentContainer<T>>(component)
}