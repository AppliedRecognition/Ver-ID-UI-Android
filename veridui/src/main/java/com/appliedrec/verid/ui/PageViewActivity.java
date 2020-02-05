package com.appliedrec.verid.ui;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;
import androidx.appcompat.app.AppCompatActivity;
import android.view.View;
import android.view.ViewGroup;

import java.lang.ref.WeakReference;

public abstract class PageViewActivity extends AppCompatActivity implements ViewPager.OnPageChangeListener {

    static class PageViewAdapter extends PagerAdapter {

        private final WeakReference<PageViewActivity> activityWeakReference;

        PageViewAdapter(PageViewActivity activity) {
            super();
            activityWeakReference = new WeakReference<>(activity);
        }

        @Override
        public int getCount() {
            PageViewActivity activity = activityWeakReference.get();
            if (activity != null) {
                return activity.getPageCount();
            } else {
                return 0;
            }
        }

        @Override
        @Nullable
        public Object instantiateItem(@NonNull ViewGroup container, int position) {
            PageViewActivity activity = activityWeakReference.get();
            if (activity != null) {
                View view = activity.createViewForPage(container, position);
                container.addView(view);
                return view;
            } else {
                return null;
            }
        }

        @Override
        public void destroyItem(ViewGroup container, int position, @NonNull Object object) {
            container.removeView((View)object);
        }

        @Override
        public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
            return view == object;
        }
    }

    private ViewPager viewPager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.page_view_activity);
        viewPager = findViewById(R.id.viewPager);
        viewPager.setAdapter(new PageViewAdapter(this));
        viewPager.addOnPageChangeListener(this);
    }

    @Override
    public void onBackPressed() {
        if (viewPager.getCurrentItem() == 0) {
            super.onBackPressed();
        } else {
            viewPager.setCurrentItem(viewPager.getCurrentItem() - 1, true);
        }
    }

    protected ViewPager getViewPager() {
        return viewPager;
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

    }

    @Override
    public void onPageSelected(int position) {

    }

    @Override
    public void onPageScrollStateChanged(int state) {

    }

    protected abstract int getPageCount();

    protected abstract View createViewForPage(ViewGroup container, int page);
}
