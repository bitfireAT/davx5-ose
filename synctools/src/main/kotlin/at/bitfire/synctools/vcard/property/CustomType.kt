/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.vcard.property

import ezvcard.parameter.EmailType
import ezvcard.parameter.RelatedType
import ezvcard.parameter.TelephoneType

/**
 * Custom TYPE parameter definitions
 */
object CustomType {

    const val HOME = "home"
    const val WORK = "work"

    object Email {
        val MOBILE = EmailType.get("x-mobile")
    }

    object Nickname {
        const val INITIALS = "x-initials"
        const val MAIDEN_NAME = "x-maiden-name"
        const val SHORT_NAME = "x-short-name"
    }

    object Phone {
        val ASSISTANT = TelephoneType.get("x-assistant")!!
        val CALLBACK = TelephoneType.get("x-callback")!!
        val COMPANY_MAIN = TelephoneType.get("x-company_main")!!
        val MMS = TelephoneType.get("x-mms")!!
        val RADIO = TelephoneType.get("x-radio")!!
    }

    object Related {
        val ASSISTANT = RelatedType.get("assistant")
        val BROTHER = RelatedType.get("brother")
        val DOMESTIC_PARTNER = RelatedType.get("domestic-partner")
        val FATHER = RelatedType.get("father")
        val MANAGER = RelatedType.get("manager")
        val MOTHER = RelatedType.get("mother")
        val PARTNER = RelatedType.get("partner")
        val REFERRED_BY = RelatedType.get("referred-by")
        val SISTER = RelatedType.get("sister")

        val OTHER = RelatedType.get("other")
    }

    object Url {
        const val TYPE_HOMEPAGE = "x-homepage"
        const val TYPE_BLOG = "x-blog"
        const val TYPE_PROFILE = "x-profile"
        const val TYPE_FTP = "x-ftp"
    }

}