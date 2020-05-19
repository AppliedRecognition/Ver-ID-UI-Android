package com.appliedrec.verid.sample;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.appliedrec.verid.core.Bearing;
import com.appliedrec.verid.core.Face;
import com.appliedrec.verid.core.RegistrationSessionSettings;
import com.appliedrec.verid.core.VerID;
import com.appliedrec.verid.core.VerIDSessionResult;
import com.appliedrec.verid.core.VerIDSessionSettings;
import com.appliedrec.verid.sample.preferences.PreferenceKeys;
import com.appliedrec.verid.sample.preferences.SettingsActivity;
import com.appliedrec.verid.sample.sharing.RegistrationImportReviewActivity;
import com.appliedrec.verid.ui.PageViewActivity;
import com.appliedrec.verid.ui.VerIDSessionActivity;
import com.appliedrec.verid.ui.VerIDSessionIntent;

import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

public class IntroActivity extends PageViewActivity implements IVerIDLoadObserver {

    private static final int REQUEST_CODE_REGISTER = 0;
    public static final String EXTRA_SHOW_REGISTRATION = "showRegistration";
    private static final int REQUEST_CODE_IMPORT = 2;
    private static final int REQUEST_CODE_IMPORT_REVIEW = 3;
    private boolean showRegistration = true;
    private VerID verID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showRegistration = getIntent().getBooleanExtra(EXTRA_SHOW_REGISTRATION, true);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.intro, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.action_import).setVisible(showRegistration);
        menu.findItem(R.id.action_settings).setVisible(showRegistration);
        MenuItem menuItem = menu.findItem(R.id.action_next);
        boolean menuItemEnabled = true;
        int title;
        if (getViewPager().getCurrentItem() < getPageCount() - 1) {
            title = R.string.next;
        } else if (showRegistration) {
            menuItemEnabled = verID != null;
            title = R.string.register;
        } else {
            title = R.string.done;
        }
        menuItem.setEnabled(menuItemEnabled);
        menuItem.setTitle(title);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_next) {
            if (getViewPager().getCurrentItem() < getPageCount() - 1) {
                getViewPager().setCurrentItem(getViewPager().getCurrentItem() + 1, true);
            } else if (showRegistration && verID != null) {
                register();
            } else {
                finish();
            }
            return true;
        }
        if (item.getItemId() == R.id.action_import) {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("application/verid-registration");
            startActivityForResult(intent, REQUEST_CODE_IMPORT);
            return true;
        }
        if (item.getItemId() == R.id.action_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            return true;
        }
        return false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_REGISTER && resultCode == RESULT_OK && data != null) {
            VerIDSessionResult result = data.getParcelableExtra(VerIDSessionActivity.EXTRA_RESULT);
            if (result != null) {
                if (result.getError() == null) {
                    Iterator<Map.Entry<Face, Uri>> imageFaceIterator = result.getFaceImages(Bearing.STRAIGHT).entrySet().iterator();
                    if (imageFaceIterator.hasNext()) {
                        Map.Entry<Face, Uri> entry = imageFaceIterator.next();
                        AsyncTask.execute(() -> {
                            try {
                                new ProfilePhotoHelper(this).setProfilePhotoFromUri(entry.getValue(), entry.getKey().getBounds());
                            } catch (Exception ignore) {
                            }
                        });
                    }
                    Intent intent = new Intent(this, RegisteredUserActivity.class);
                    startActivity(intent);
                    finish();
                } else {
                    Intent intent = new Intent(this, SessionResultActivity.class);
                    intent.putExtras(data);
                    startActivity(intent);
                }
            }
        } else if (resultCode == RESULT_OK && requestCode == REQUEST_CODE_IMPORT && data != null) {
            Intent intent = new Intent(this, RegistrationImportReviewActivity.class);
            intent.putExtras(data);
            intent.setData(data.getData());
            startActivityForResult(intent, REQUEST_CODE_IMPORT_REVIEW);
        } else if (resultCode == RESULT_OK && requestCode == REQUEST_CODE_IMPORT_REVIEW && data != null && data.getIntExtra(RegistrationImportReviewActivity.EXTRA_IMPORTED_FACE_COUNT, 0) > 0) {
            startActivity(new Intent(this, RegisteredUserActivity.class));
            finish();
        }
    }

    private void register() {
        RegistrationSessionSettings settings = new RegistrationSessionSettings(VerIDUser.DEFAULT_USER_ID);
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        if (preferences != null) {
            if (preferences.contains(PreferenceKeys.REGISTRATION_FACE_COUNT)) {
                settings.setNumberOfResultsToCollect(Integer.parseInt(preferences.getString(PreferenceKeys.REGISTRATION_FACE_COUNT, Integer.toString(settings.getNumberOfResultsToCollect()))));
            }
            if (preferences.getBoolean(PreferenceKeys.USE_BACK_CAMERA, false)) {
                settings.setFacingOfCameraLens(VerIDSessionSettings.LensFacing.BACK);
            }
            if (preferences.getBoolean(PreferenceKeys.SPEAK_PROMPTS, false)) {
                settings.shouldSpeakPrompts(true);
            }
            settings.getFaceBoundsFraction().x = preferences.getFloat(PreferenceKeys.FACE_BOUNDS_WIDTH_FRACTION, settings.getFaceBoundsFraction().x);
            settings.getFaceBoundsFraction().y = preferences.getFloat(PreferenceKeys.FACE_BOUNDS_HEIGHT_FRACTION, settings.getFaceBoundsFraction().y);
        }
        settings.shouldRecordSessionVideo(true);
        Intent intent = new VerIDSessionIntent<>(IntroActivity.this, verID, settings);
        startActivityForResult(intent, REQUEST_CODE_REGISTER);
    }

    @Override
    protected int getPageCount() {
        return 3;
    }

    @Override
    public void onPageSelected(int position) {
        super.onPageSelected(position);
        invalidateOptionsMenu();
    }

    @Override
    protected View createViewForPage(ViewGroup container, int page) {
        return IntroFragment.createView(getLayoutInflater(), container, page);
    }

    @Override
    public void onVerIDLoaded(VerID verid) {
        this.verID = verid;
        invalidateOptionsMenu();
    }

    @Override
    public void onVerIDUnloaded() {

    }

    public static class IntroFragment extends Fragment {

        static final int[] imageResourceIds = new int[]{
                R.mipmap.guide_head_straight,
                R.mipmap.multiple_heads,
                R.mipmap.authentication
        };
        static final int[] titleResourceIds = new int[]{
                R.string.verid_person_sdk,
                R.string.one_registration,
                R.string.two_authentication
        };
        static final int[] textResourceIds = new int[]{
                R.string.verid_person_sdk_text,
                R.string.one_registration_text,
                R.string.two_authentication_text
        };

        static View createView(LayoutInflater inflater, ViewGroup container, int index) {
            View view = inflater.inflate(R.layout.intro_page_fragment, container, false);
            ((ImageView)view.findViewById(R.id.imageView)).setImageResource(imageResourceIds[index]);
            ((TextView)view.findViewById(R.id.title)).setText(titleResourceIds[index]);
            ((TextView)view.findViewById(R.id.text)).setText(textResourceIds[index]);
            return view;
        }

        public static IntroFragment newInstance(int index) {
            Bundle args = new Bundle();
            args.putInt("index", index);
            IntroFragment fragment = new IntroFragment();
            fragment.setArguments(args);
            return fragment;
        }

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            Bundle args = getArguments();
            int index = Objects.requireNonNull(args).getInt("index", 0);
            return IntroFragment.createView(inflater, container, index);
        }
    }

    private void showError(String description) {
        Intent intent = new Intent(this, ErrorActivity.class);
        intent.putExtra(Intent.EXTRA_TEXT, description);
        startActivity(intent);
    }

    private void showError(Throwable error) {
        showError(error.getLocalizedMessage());
    }
}
