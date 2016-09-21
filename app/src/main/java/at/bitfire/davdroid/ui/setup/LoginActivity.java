/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui.setup;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;

import java.util.ServiceLoader;

import at.bitfire.davdroid.App;
import at.bitfire.davdroid.Constants;
import at.bitfire.davdroid.R;

/**
 * Activity to initially connect to a server and create an account.
 * Fields for server/user data can be pre-filled with extras in the Intent.
 */
public class LoginActivity extends AppCompatActivity {

    /**
     * When set, "login by URL" will be activated by default, and the URL field will be set to this value.
     * When not set, "login by email" will be activated by default.
     */
    public static final String EXTRA_URL = "url";

    /**
     * When set, and {@link #EXTRA_PASSWORD} is set too, the user name field will be set to this value.
     * When set, and {@link #EXTRA_URL} is not set, the email address field will be set to this value.
     */
    public static final String EXTRA_USERNAME = "username";

    /**
     * When set, the password field will be set to this value.
     */
    public static final String EXTRA_PASSWORD = "password";


    private static final ServiceLoader<ILoginCredentialsFragment> loginFragmentLoader = ServiceLoader.load(ILoginCredentialsFragment.class);


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null)
            // first call, add fragment
            for (ILoginCredentialsFragment fragment : loginFragmentLoader)
                getSupportFragmentManager().beginTransaction()
                        .replace(android.R.id.content, fragment.getFragment())
                        .commit();

    }

    @Override
    protected void onResume() {
        super.onResume();

        App app = (App)getApplicationContext();
        if (app.getCertManager() != null)
            app.getCertManager().appInForeground = true;
    }

    @Override
    protected void onPause() {
        super.onPause();

        App app = (App)getApplicationContext();
        if (app.getCertManager() != null)
            app.getCertManager().appInForeground = false;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_login, menu);
        return true;
    }

    public void showHelp(MenuItem item) {
        startActivity(new Intent(Intent.ACTION_VIEW, Constants.webUri.buildUpon().appendEncodedPath("configuration/").build()));
    }
}
