/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android

import android.content.ContentResolver
import net.fortuna.ical4j.data.DefaultParameterFactorySupplier
import net.fortuna.ical4j.data.DefaultPropertyFactorySupplier
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.ParameterBuilder
import net.fortuna.ical4j.model.ParameterFactory
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.PropertyBuilder
import net.fortuna.ical4j.model.PropertyFactory
import org.json.JSONArray
import org.json.JSONObject

/**
 * Helpers to (de)serialize unknown properties as JSON to store it in an Android ExtendedProperty row.
 *
 * Format: `{ propertyName, propertyValue, { param1Name: param1Value, ... } }`, with the third
 * array (parameters) being optional.
 */
object UnknownProperty {

    /**
     * Use this value for [android.provider.CalendarContract.ExtendedProperties.NAME] and
     * [org.dmfs.tasks.contract.TaskContract.Properties.MIMETYPE].
     */
    const val CONTENT_ITEM_TYPE = ContentResolver.CURSOR_ITEM_BASE_TYPE + "/vnd.ical4android.unknown-property"

    /**
     * Recommended maximum size of properties for serialization. Won't be enforced by this
     * class (should be checked by caller).
     */
    const val MAX_UNKNOWN_PROPERTY_SIZE = 25000

    val propertyFactorySupplier: List<PropertyFactory<out Property>> = DefaultPropertyFactorySupplier().get()
    val parameterFactorySupplier: List<ParameterFactory<out Parameter>> = DefaultParameterFactorySupplier().get()


    /**
     * Deserializes a JSON string from an ExtendedProperty value to an ical4j property.
     *
     * @param jsonString JSON representation of an ical4j property
     * @return ical4j property, generated from [jsonString]
     * @throws org.json.JSONException when the input value can't be parsed
     */
    fun fromJsonString(jsonString: String): Property {
        val json = JSONArray(jsonString)
        val name = json.getString(0)
        val value = json.getString(1)

        val builder = PropertyBuilder(propertyFactorySupplier)
                .name(name)
                .value(value)

        json.optJSONObject(2)?.let { jsonParams ->
            for (paramName in jsonParams.keys())
                builder.parameter(
                        ParameterBuilder(parameterFactorySupplier)
                                .name(paramName)
                                .value(jsonParams.getString(paramName))
                                .build()
                )
        }

        return builder.build()
    }

    /**
     * Serializes an ical4j property to a JSON string that can be stored in an ExtendedProperty.
     *
     * @param prop property to serialize as JSON
     * @return JSON representation of [prop]
     */
    fun toJsonString(prop: Property): String {
        val json = JSONArray()
        json.put(prop.name)
        json.put(prop.value)

        if (prop.parameterList.all.isNotEmpty()) {
            val jsonParams = JSONObject()
            for (param in prop.parameterList.all)
                jsonParams.put(param.name, param.value)
            json.put(jsonParams)
        }

        return json.toString()
    }

}