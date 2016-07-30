package at.bitfire.davdroid.ui;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.view.MenuItem;

import at.bitfire.davdroid.App;
import at.bitfire.davdroid.BuildConfig;
import at.bitfire.davdroid.Constants;
import at.bitfire.davdroid.R;

public class DefaultAccountsDrawerHandler implements IAccountsDrawerHandler {

    @Override
    public boolean onNavigationItemSelected(@NonNull Activity activity, MenuItem item) {
        switch (item.getItemId()) {
            case R.id.nav_about:
                activity.startActivity(new Intent(activity, AboutActivity.class));
                break;
            case R.id.nav_app_settings:
                activity.startActivity(new Intent(activity, AppSettingsActivity.class));
                break;
            case R.id.nav_twitter:
                activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://twitter.com/davdroidapp")));
                break;
            case R.id.nav_website:
                activity.startActivity(new Intent(Intent.ACTION_VIEW, Constants.webUri));
                break;
            case R.id.nav_faq:
                activity.startActivity(new Intent(Intent.ACTION_VIEW, Constants.webUri.buildUpon().appendEncodedPath("faq/").build()));
                break;
            case R.id.nav_forums:
                activity.startActivity(new Intent(Intent.ACTION_VIEW, Constants.webUri.buildUpon().appendEncodedPath("forums/").build()));
                break;
            case R.id.nav_donate:
                if (BuildConfig.FLAVOR != App.FLAVOR_GOOGLE_PLAY)
                    activity.startActivity(new Intent(Intent.ACTION_VIEW, Constants.webUri.buildUpon().appendEncodedPath("donate/").build()));
                break;
	    default:
                return false;
        }

        return true;
    }

}
