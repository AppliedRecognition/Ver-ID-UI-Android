package com.appliedrec.verid.sample;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.appliedrec.verid.core2.Bearing;
import com.appliedrec.verid.core2.VerID;
import com.appliedrec.verid.core2.session.FaceExtents;
import com.appliedrec.verid.core2.session.RegistrationSessionSettings;
import com.appliedrec.verid.core2.session.VerIDSessionResult;
import com.appliedrec.verid.sample.preferences.MimeTypes;
import com.appliedrec.verid.sample.preferences.PreferenceKeys;
import com.appliedrec.verid.sample.preferences.SettingsActivity;
import com.appliedrec.verid.sample.sharing.RegistrationImportContract;
import com.appliedrec.verid.sample.sharing.RegistrationImportReviewActivity;
import com.appliedrec.verid.ui2.CameraLocation;
import com.appliedrec.verid.ui2.ISessionActivity;
import com.appliedrec.verid.ui2.IVerIDSession;
import com.appliedrec.verid.ui2.PageViewActivity;
import com.appliedrec.verid.ui2.VerIDSession;
import com.appliedrec.verid.ui2.VerIDSessionDelegate;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Objects;

public class IntroActivity extends PageViewActivity implements IVerIDLoadObserver, VerIDSessionDelegate {

    public static final String EXTRA_SHOW_REGISTRATION = "showRegistration";
    private boolean showRegistration = true;
    private VerID verID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showRegistration = getIntent().getBooleanExtra(EXTRA_SHOW_REGISTRATION, true);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
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

    private void reviewRegistrationImport(Uri uri) {
        Intent intent = new Intent(this, RegistrationImportReviewActivity.class);
        intent.setDataAndType(uri, MimeTypes.REGISTRATION.getType());
        registrationImportReview.launch(intent);
    }

    private final ActivityResultLauncher<Void> registrationImport = registerForActivityResult(new RegistrationImportContract(), uri -> {
        if (uri != null) {
            reviewRegistrationImport(uri);
        }
    });

    private final ActivityResultLauncher<Intent> registrationImportReview = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result != null && result.getResultCode() == RESULT_OK) {
            startActivity(new Intent(this, RegisteredUserActivity.class));
            finish();
        }
    });

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
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
            registrationImport.launch(null);
            return true;
        }
        if (item.getItemId() == R.id.action_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            return true;
        }
        return false;
    }

    private void register() {
        RegistrationSessionSettings settings = new RegistrationSessionSettings(VerIDUser.DEFAULT_USER_ID);
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        if (preferences != null) {
            if (preferences.contains(PreferenceKeys.REGISTRATION_FACE_COUNT)) {
                settings.setFaceCaptureCount(Integer.parseInt(preferences.getString(PreferenceKeys.REGISTRATION_FACE_COUNT, Integer.toString(settings.getFaceCaptureCount()))));
            }
            settings.setExpectedFaceExtents(new FaceExtents(
                    preferences.getFloat(PreferenceKeys.FACE_BOUNDS_WIDTH_FRACTION, settings.getExpectedFaceExtents().getProportionOfViewWidth()),
                    preferences.getFloat(PreferenceKeys.FACE_BOUNDS_HEIGHT_FRACTION, settings.getExpectedFaceExtents().getProportionOfViewHeight())
            ));
            settings.setFaceCoveringDetectionEnabled(preferences.getBoolean(PreferenceKeys.ENABLE_MASK_DETECTION, settings.isFaceCoveringDetectionEnabled()));
        }
        VerIDSession session = new VerIDSession(verID, settings);
        session.setDelegate(this);
        session.start();
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

    @Override
    public void onSessionFinished(@NonNull IVerIDSession<?> session, @NonNull VerIDSessionResult result) {
        if (!result.getError().isPresent()) {
            result.getFirstFaceCapture(Bearing.STRAIGHT).ifPresent(faceCapture -> {
                try {
                    new ProfilePhotoHelper(this).setProfilePhoto(faceCapture.getFaceImage());
                } catch (Exception ignore) {
                }
            });
            Intent intent = new Intent(this, RegisteredUserActivity.class);
            startActivity(intent);
            finish();
        }
    }

    @Override
    public boolean shouldSessionDisplayResult(@NonNull IVerIDSession<?> session, @NonNull VerIDSessionResult result) {
        return result.getError().isPresent();
    }

    @NonNull
    @Override
    public <A extends Activity & ISessionActivity> Class<A> getSessionResultActivityClass(@NonNull IVerIDSession<?> session, @NonNull VerIDSessionResult result) {
        return (Class<A>) SessionResultActivity.class;
    }

    @Override
    public boolean shouldSessionSpeakPrompts(@NonNull IVerIDSession<?> session) {
        return PreferenceManager.getDefaultSharedPreferences(this).getBoolean(PreferenceKeys.SPEAK_PROMPTS, false);
    }

    @NonNull
    @Override
    public CameraLocation getSessionCameraLocation(@NonNull IVerIDSession<?> session) {
        return PreferenceManager.getDefaultSharedPreferences(this).getBoolean(PreferenceKeys.USE_BACK_CAMERA, false) ? CameraLocation.BACK : CameraLocation.FRONT;
    }

    public static class IntroFragment extends Fragment {

        static final int[] imageResourceIds = new int[]{
                com.appliedrec.verid.ui2.R.mipmap.guide_head_straight,
                com.appliedrec.verid.ui2.R.mipmap.multiple_heads,
                com.appliedrec.verid.ui2.R.mipmap.authentication
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
