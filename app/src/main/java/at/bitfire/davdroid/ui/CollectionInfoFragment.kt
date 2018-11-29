/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.appcompat.app.AlertDialog
import at.bitfire.davdroid.R
import at.bitfire.davdroid.model.CollectionInfo
import kotlinx.android.synthetic.main.collection_properties.view.*

class CollectionInfoFragment : DialogFragment() {

    companion object {

        private const val ARGS_INFO = "info"

        fun newInstance(info: CollectionInfo): CollectionInfoFragment {
            val frag = CollectionInfoFragment()
            val args = Bundle(1)
            args.putParcelable(ARGS_INFO, info)
            frag.arguments = args
            return frag
        }

    }

    @SuppressLint("InflateParams")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val info = arguments!![ARGS_INFO] as CollectionInfo

        val view = requireActivity().layoutInflater.inflate(R.layout.collection_properties, null)
        view.url.text = info.url.toString()

        return AlertDialog.Builder(requireActivity())
                .setTitle(info.displayName)
                .setView(view)
                .create()
    }

}