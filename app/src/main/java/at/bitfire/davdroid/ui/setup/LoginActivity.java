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

import at.bitfire.davdroid.Constants;
import at.bitfire.davdroid.R;

public class LoginActivity extends AppCompatActivity {

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
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_login, menu);
        return true;
    }

    public void showHelp(MenuItem item) {
        startActivity(new Intent(Intent.ACTION_VIEW, Constants.webUri.buildUpon().appendEncodedPath("configuration/").build()));
    }
}
