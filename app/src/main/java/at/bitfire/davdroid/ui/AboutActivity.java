/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.text.Spanned;
import android.text.util.Linkify;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.apache.commons.lang3.time.DateFormatUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;

import at.bitfire.davdroid.App;
import at.bitfire.davdroid.BuildConfig;
import at.bitfire.davdroid.R;
import ezvcard.Ezvcard;
import ezvcard.util.IOUtils;
import lombok.Cleanup;
import lombok.RequiredArgsConstructor;

public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        setSupportActionBar((Toolbar)findViewById(R.id.toolbar));
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        ViewPager viewPager = (ViewPager)findViewById(R.id.viewpager);
        viewPager.setAdapter(new TabsAdapter(getSupportFragmentManager()));

        TabLayout tabLayout = (TabLayout)findViewById(R.id.tabs);
        tabLayout.setupWithViewPager(viewPager);
    }


    @RequiredArgsConstructor
    private static class ComponentInfo {
        final String   title, version, website, copyright;
        final int licenseInfo;
        final String licenseTextFile;
    }

    private final static ComponentInfo components[] = {
            new ComponentInfo(
                    "DAVdroid", BuildConfig.VERSION_NAME, "https://davdroid.bitfire.at",
                    "© " + DateFormatUtils.format(BuildConfig.buildTime, "yyyy") + " Ricki Hirner, Bernhard Stockmann (bitfire web engineering)",
                    R.string.about_license_info_no_warranty, "gpl-3.0-standalone.html"
            ), new ComponentInfo(
                    "AmbilWarna", null, "https://github.com/yukuku/ambilwarna",
                    "© Yuku", R.string.about_license_info_no_warranty, "apache2.html"
            ), new ComponentInfo(
                    "Apache Commons", null, "http://commons.apache.org/",
                    "Apache Software Foundation", R.string.about_license_info_no_warranty, "apache2.html"
            ), new ComponentInfo(
                    "dnsjava", null, "http://dnsjava.org/",
                    "© Brian Wellington", R.string.about_license_info_no_warranty, "bsd.html"
            ), new ComponentInfo(
                    "ez-vcard", Ezvcard.VERSION, "https://github.com/mangstadt/ez-vcard",
                    "© Michael Angstadt", R.string.about_license_info_no_warranty, "bsd.html"
            ), new ComponentInfo(
                    "ical4j", "2.x", "http://ical4j.github.io/",
                    "© Ben Fortuna", R.string.about_license_info_no_warranty, "bsd-3clause.html"
            ), new ComponentInfo(
                    "MemorizingTrustManager", null, "https://github.com/ge0rg/MemorizingTrustManager",
                    "© Georg Lukas", R.string.about_license_info_no_warranty, "mit.html"
            ), new ComponentInfo(
                    "OkHttp", null, "https://square.github.io/okhttp/",
                    "© Square, Inc.", R.string.about_license_info_no_warranty, "apache2.html"
            ), new ComponentInfo(
                    "Project Lombok", null, "https://projectlombok.org/",
                    "© The Project Lombok Authors", R.string.about_license_info_no_warranty, "mit.html"
            )
    };


    private static class TabsAdapter extends FragmentPagerAdapter {
        public TabsAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public int getCount() {
            return components.length;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return components[position].title;
        }

        @Override
        public Fragment getItem(int position) {
            return ComponentFragment.instantiate(position);
        }
    }

    public static class ComponentFragment extends Fragment implements LoaderManager.LoaderCallbacks<Spanned> {
        private static final String
                KEY_POSITION = "position",
                KEY_FILE_NAME = "fileName";

        public static ComponentFragment instantiate(int position) {
            ComponentFragment frag = new ComponentFragment();
            Bundle args = new Bundle(1);
            args.putInt(KEY_POSITION, position);
            frag.setArguments(args);
            return frag;
        }

        @Nullable
        @Override
        @SuppressLint("SetTextI18n")
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            ComponentInfo info = components[getArguments().getInt(KEY_POSITION)];

            View v = inflater.inflate(R.layout.about_component, container, false);

            TextView tv = (TextView)v.findViewById(R.id.title);
            tv.setText(info.title + (info.version != null ? (" " + info.version) : ""));

            tv = (TextView)v.findViewById(R.id.website);
            tv.setAutoLinkMask(Linkify.WEB_URLS);
            tv.setText(info.website);

            tv = (TextView)v.findViewById(R.id.copyright);
            tv.setText(info.copyright);

            tv = (TextView)v.findViewById(R.id.license_info);
            tv.setText(info.licenseInfo);

            // load and format license text
            Bundle args = new Bundle(1);
            args.putString(KEY_FILE_NAME, info.licenseTextFile);
            getLoaderManager().initLoader(0, args, this);

            return v;
        }

        @Override
        public Loader<Spanned> onCreateLoader(int id, Bundle args) {
            return new LicenseLoader(getContext(), args.getString(KEY_FILE_NAME));
        }

        @Override
        public void onLoadFinished(Loader<Spanned> loader, Spanned license) {
            if (getView() != null) {
                TextView tv = (TextView)getView().findViewById(R.id.license_text);
                if (tv != null)
                    tv.setText(license);
            }
        }

        @Override
        public void onLoaderReset(Loader<Spanned> loader) {
        }
    }

    private static class LicenseLoader extends AsyncTaskLoader<Spanned> {
        final String fileName;
        Spanned content;

        LicenseLoader(Context context, String fileName) {
            super(context);
            this.fileName = fileName;
        }

        @Override
        protected void onStartLoading() {
            if (content == null)
                forceLoad();
            else
                deliverResult(content);
        }

        @Override
        public Spanned loadInBackground() {
            App.log.fine("Loading license file " + fileName);
            try {
                @Cleanup InputStream is = getContext().getResources().getAssets().open(fileName);
                byte[] raw = IOUtils.toByteArray(is);
                return content = Html.fromHtml(new String(raw));
            } catch (IOException e) {
                App.log.log(Level.SEVERE, "Couldn't read license file", e);
                return null;
            }
        }
    }

}
