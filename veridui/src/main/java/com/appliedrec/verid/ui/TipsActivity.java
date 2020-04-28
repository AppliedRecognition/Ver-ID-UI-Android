package com.appliedrec.verid.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.appliedrec.verid.core.VerIDSessionSettings;

public class TipsActivity extends PageViewActivity implements IStringTranslator {

    private TranslatedStrings translatedStrings;
    private VerIDSessionSettings sessionSettings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getIntent() == null) {
            return;
        }
        translatedStrings = getIntent().getParcelableExtra(VerIDSessionActivity.EXTRA_TRANSLATION);
        if (translatedStrings == null) {
            translatedStrings = new TranslatedStrings(this, getIntent());
        }
        sessionSettings = getIntent().getParcelableExtra(VerIDSessionActivity.EXTRA_SETTINGS);
        if (sessionSettings != null && sessionSettings.shouldSpeakPrompts()) {
            TextSpeaker.getInstance().speak(tipText(0), translatedStrings.getLocale(), true);
        }
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(translatedStrings.getTranslatedString("Tip 1 of 3"));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        TextSpeaker.getInstance().speak(null, translatedStrings.getLocale(), true);
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
        int imgSrc;
        switch (page) {
            case 1:
                imgSrc = R.mipmap.head_with_glasses;
                break;
            case 2:
                imgSrc = R.mipmap.busy_background;
                break;
            default:
                imgSrc = R.mipmap.tip_sharp_light;
        }
        return TipFragment.createView(getLayoutInflater(), container, imgSrc, tipText(page));
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
        String tipText = tipText(position);
        if (sessionSettings != null && sessionSettings.shouldSpeakPrompts()) {
            TextSpeaker.getInstance().speak(tipText, translatedStrings.getLocale(), true);
        }
        invalidateOptionsMenu();
    }

    @Override
    public String getTranslatedString(String original, Object... args) {
        return translatedStrings.getTranslatedString(original, args);
    }

    private String tipText(int page) {
        switch (page) {
            case 1:
                return getTranslatedString("If you can, take off your glasses.");
            case 2:
                return getTranslatedString("Avoid standing in front of busy backgrounds.");
            default:
                return getTranslatedString("Avoid standing in a light that throws sharp shadows like in sharp sunlight or directly under a lamp.");
        }
    }
}
