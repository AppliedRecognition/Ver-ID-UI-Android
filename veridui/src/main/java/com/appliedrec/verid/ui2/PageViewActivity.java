package com.appliedrec.verid.ui2;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.appliedrec.verid.ui2.databinding.PageViewActivityBinding;

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

    private PageViewActivityBinding viewBinding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewBinding = PageViewActivityBinding.inflate(getLayoutInflater());
        setContentView(viewBinding.getRoot());
        viewBinding.viewPager.setAdapter(new PageViewAdapter(this));
        viewBinding.viewPager.addOnPageChangeListener(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        viewBinding = null;
    }

    @Override
    public void onBackPressed() {
        if (viewBinding.viewPager.getCurrentItem() == 0) {
            super.onBackPressed();
        } else {
            viewBinding.viewPager.setCurrentItem(viewBinding.viewPager.getCurrentItem() - 1, true);
        }
    }

    protected ViewPager getViewPager() {
        return viewBinding.viewPager;
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
