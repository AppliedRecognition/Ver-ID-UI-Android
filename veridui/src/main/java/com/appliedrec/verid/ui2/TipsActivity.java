package com.appliedrec.verid.ui2;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

public class TipsActivity extends PageViewActivity {

    public static final String EXTRA_TRANSLATOR = "com.appliedrec.verid.EXTRA_TRANSLATOR";

    private IStringTranslator stringTranslator;
    private ITextSpeaker textSpeaker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getIntent() == null) {
            return;
        }
        if (getIntent().hasExtra(EXTRA_TRANSLATOR)) {
            stringTranslator = getIntent().getParcelableExtra(EXTRA_TRANSLATOR);
        }
        if (textSpeaker == null) {
            TextSpeaker.setup(getApplicationContext());
            textSpeaker = TextSpeaker.getInstance();
        }
        speak(tipText(0), true);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(translate("Tip 1 of 3"));
        }
    }

    void setStringTranslator(IStringTranslator stringTranslator) {
        this.stringTranslator = stringTranslator;
    }

    void setTextSpeaker(@Nullable ITextSpeaker textSpeaker) {
        this.textSpeaker = textSpeaker;
    }

    private String translate(String original, Object... args) {
        if (stringTranslator != null) {
            return stringTranslator.getTranslatedString(original, args);
        }
        return String.format(original, args);
    }

    private void speak(String text, boolean interrupt) {
        if (textSpeaker != null && stringTranslator != null) {
            textSpeaker.speak(text, stringTranslator.getLocale(), interrupt);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (textSpeaker != null && stringTranslator != null) {
            textSpeaker.speak(null, stringTranslator.getLocale(), true);
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
            menu.findItem(R.id.action_next).setTitle(translate("Done"));
        } else {
            menu.findItem(R.id.action_next).setTitle(translate("Next"));
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
                    getSupportActionBar().setTitle(translate("Tip 2 of 3"));
                    break;
                case 2:
                    getSupportActionBar().setTitle(translate("Tip 3 of 3"));
                    break;
                default:
                    getSupportActionBar().setTitle(translate("Tip 1 of 3"));
            }
        }
        String tipText = tipText(position);
        speak(tipText, true);
        invalidateOptionsMenu();
    }

    private String tipText(int page) {
        switch (page) {
            case 1:
                return translate("If you can, take off your glasses.");
            case 2:
                return translate("Avoid standing in front of busy backgrounds.");
            default:
                return translate("Avoid standing in a light that throws sharp shadows like in sharp sunlight or directly under a lamp.");
        }
    }
}
