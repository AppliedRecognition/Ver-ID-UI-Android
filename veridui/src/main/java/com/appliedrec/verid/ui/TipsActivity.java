package com.appliedrec.verid.ui;

import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;

public class TipsActivity extends PageViewActivity {

    private TranslatedStrings translatedStrings = new TranslatedStrings();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getIntent() == null) {
            return;
        }
        final String translationFilePath = getIntent().getStringExtra(VerIDSessionActivity.EXTRA_TRANSLATION_FILE_PATH);
        final String translationAssetPath = getIntent().getStringExtra(VerIDSessionActivity.EXTRA_TRANSLATION_ASSET_PATH);
        if (translationFilePath != null) {
            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        translatedStrings.loadTranslatedStrings(translationFilePath);
                    } catch (XmlPullParserException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
        } else if (translationAssetPath != null) {
            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        InputStream inputStream = getAssets().open(translationAssetPath);
                        translatedStrings.loadTranslatedStrings(inputStream);
                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (XmlPullParserException e) {
                        e.printStackTrace();
                    }
                }
            });
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.tips, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (getViewPager().getCurrentItem() == getPageCount() - 1) {
            menu.findItem(R.id.action_next).setTitle(translatedStrings.getTranslatedString("Done"));
        } else {
            menu.findItem(R.id.action_next).setTitle(translatedStrings.getTranslatedString("Next"));
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_next) {
            if (getViewPager().getCurrentItem() >= getPageCount() - 1) {
                finish();
            } else {
                getViewPager().setCurrentItem(getViewPager().getCurrentItem() + 1, true);
            }
            return true;
        }
        return false;
    }

    @Override
    protected int getPageCount() {
        return 3;
    }

    @Override
    protected View createViewForPage(ViewGroup container, int page) {
        return TipFragment.createView(getLayoutInflater(), container, page, translatedStrings);
    }

    @Override
    public void onPageSelected(int position) {
        if (getSupportActionBar() != null) {
            switch (position) {
                case 1:
                    getSupportActionBar().setTitle(translatedStrings.getTranslatedString("Tip 2 of 3"));
                    break;
                case 2:
                    getSupportActionBar().setTitle(translatedStrings.getTranslatedString("Tip 3 of 3"));
                    break;
                default:
                    getSupportActionBar().setTitle(translatedStrings.getTranslatedString("Tip 1 of 3"));
            }
        }
        invalidateOptionsMenu();
    }
}
