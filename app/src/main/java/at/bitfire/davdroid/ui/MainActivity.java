/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package at.bitfire.davdroid.ui;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import at.bitfire.davdroid.BuildConfig;
import at.bitfire.davdroid.Constants;
import at.bitfire.davdroid.R;
import at.bitfire.davdroid.ui.settings.SettingsActivity;
import at.bitfire.davdroid.ui.setup.AddAccountActivity;
import lombok.Getter;

public class MainActivity extends Activity {

    private static final String libraries =
            "· <a href=\"https://commons.apache.org/\">Apache Commons</a> – <a href=\"https://www.apache.org/licenses/\">Apache License, Version 2.0</a><br/>" +
            "· <a href=\"http://www.xbill.org/dnsjava/\">dnsjava</a> – <a href=\"http://www.xbill.org/dnsjava/dnsjava-current/LICENSE\">BSD License</a><br/>" +
            "· <a href=\"https://github.com/mangstadt/ez-vcard\">ez-vcard</a> – <a href=\"http://opensource.org/licenses/BSD-3-Clause\">New BSD License</a><br/>" +
            "· <a href=\"https://github.com/ical4j/ical4j\">iCal4j</a> – <a href=\"https://github.com/ical4j/ical4j/blob/master/LICENSE\">New BSD License</a><br/>" +
            "· <a href=\"https://github.com/ge0rg/MemorizingTrustManager\">MemorizingTrustManager</a> – <a href=\"https://raw.githubusercontent.com/ge0rg/MemorizingTrustManager/master/LICENSE.txt\">MIT License</a><br/>" +
            "· <a href=\"https://square.github.io/okhttp/\">okhttp</a> – <a href=\"https://square.github.io/okhttp/#license\">Apache License, Version 2.0</a><br/>" +
            "· <a href=\"https://projectlombok.org/\">Project Lombok</a> – <a href=\"http://opensource.org/licenses/mit-license.php\">MIT License</a><br/>" +
            "· <a href=\"https://commons.apache.org/\">SLF4j</a> (Simple Logging Facade for Java) – <a href=\"http://www.slf4j.org/license.html\">MIT License</a>";

    @Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.main_activity);

		TextView tv = (TextView)findViewById(R.id.text_store_specific);
        final String installedFrom = installedFrom();
        if (installedFrom == null || installedFrom.startsWith("org.fdroid")) {
            if (savedInstanceState == null)
                new DonateDialogFragment().show(getFragmentManager(), "donate");
        } else if ("com.android.vending".equals(installedFrom))
            setHtmlText(R.id.text_store_specific, R.string.main_play_workaround_html);

        setPlainText(R.id.text_welcome, R.string.main_welcome, BuildConfig.VERSION_NAME);
        setHtmlText(R.id.text_what_is_davdroid, R.string.main_what_is_davdroid_html);
        setHtmlText(R.id.text_how_to_setup, R.string.main_how_to_setup_html);
        setHtmlText(R.id.text_support, R.string.main_support_html);
        setHtmlText(R.id.text_open_source_disclaimer, R.string.main_open_source_disclaimer_html);
        setHtmlText(R.id.text_license, R.string.main_license_html);
        setPlainText(R.id.text_libraries_heading, R.string.main_used_libraries_heading);
        setHtmlText(R.id.text_libraries_list, libraries);
    }

    private void setPlainText(int viewId, int stringId, Object... args) {
        TextView tv = (TextView)findViewById(viewId);
        tv.setVisibility(View.VISIBLE);
        tv.setText(getString(stringId, args));
    }

    private void setHtmlText(int viewId, int stringId, Object... args) {
        TextView tv = (TextView)findViewById(viewId);
        tv.setVisibility(View.VISIBLE);
        tv.setText(trim(Html.fromHtml(getString(stringId, args))));
        tv.setMovementMethod(LinkMovementMethod.getInstance());
    }

    private void setHtmlText(int viewId, String html) {
        TextView tv = (TextView)findViewById(viewId);
        tv.setVisibility(View.VISIBLE);
        tv.setText(trim(Html.fromHtml(html)));
        tv.setMovementMethod(LinkMovementMethod.getInstance());
    }

    private CharSequence trim(CharSequence text) {
        while (text.charAt(text.length() - 1) == '\n') {
            text = text.subSequence(0, text.length() - 1);
        }
        return text;
    }


	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
	    inflater.inflate(R.menu.main_activity, menu);
	    return true;
	}

	
	public void addAccount(MenuItem item) {
		startActivity(new Intent(this, AddAccountActivity.class));
	}

    public void showDebugInfo(MenuItem item) {
        startActivity(new Intent(this, DebugInfoActivity.class));
    }

	public void showSettings(MenuItem item) {
		startActivity(new Intent(this, SettingsActivity.class));
	}
	
	public void showSyncSettings(MenuItem item) {
		Intent intent = new Intent(Settings.ACTION_SYNC_SETTINGS);
		startActivity(intent);
	}

	public void showWebsite(MenuItem item) {
		Intent intent = new Intent(Intent.ACTION_VIEW);
		intent.setData(Uri.parse(Constants.WEB_URL_MAIN + "&pk_kwd=main-activity"));
		startActivity(intent);
	}
	
	
	private String installedFrom() {
		try {
			return getPackageManager().getInstallerPackageName("at.bitfire.davdroid");
		} catch(IllegalArgumentException e) {
            return null;
		}
	}
}
