package com.appliedrec.verid.ui;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

public class TipsActivity extends PageViewActivity implements IStringTranslator, ITranslationSettable {

    private TranslatedStrings translatedStrings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (translatedStrings != null) {
            setTranslatedStrings(translatedStrings);
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

    @Override
    public String getTranslatedString(String original, Object... args) {
        return translatedStrings.getTranslatedString(original, args);
    }

    @Override
    public void setTranslatedStrings(TranslatedStrings translatedStrings) {
        if (translatedStrings == null) {
            this.translatedStrings = new TranslatedStrings(this, null);
        } else {
            this.translatedStrings = translatedStrings;
        }
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(translatedStrings.getTranslatedString("Tip 1 of 3"));
        }
    }
}
