package com.appliedrec.verid.ui;

import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

public class TipsActivity extends PageViewActivity {

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.tips, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (getViewPager().getCurrentItem() == getPageCount() - 1) {
            menu.findItem(R.id.action_next).setTitle(R.string.done);
        } else {
            menu.findItem(R.id.action_next).setTitle(R.string.next);
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
        return TipFragment.createView(getLayoutInflater(), container, page);
    }

    @Override
    public void onPageSelected(int position) {
        if (getSupportActionBar() != null) {
            switch (position) {
                case 1:
                    getSupportActionBar().setTitle(R.string.tip_2_of_3);
                    break;
                case 2:
                    getSupportActionBar().setTitle(R.string.tip_3_of_3);
                    break;
                default:
                    getSupportActionBar().setTitle(R.string.tip_1_of_3);
            }
        }
        invalidateOptionsMenu();
    }
}
