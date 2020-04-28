package com.appliedrec.verid.sample;

import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
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
import androidx.lifecycle.Lifecycle;

import com.appliedrec.verid.core.Bearing;
import com.appliedrec.verid.core.RegistrationSessionSettings;
import com.appliedrec.verid.core.VerIDSessionSettings;
import com.appliedrec.verid.ui.PageViewActivity;
import com.appliedrec.verid.ui.VerIDSessionIntent;
import com.trello.lifecycle2.android.lifecycle.AndroidLifecycle;
import com.trello.rxlifecycle3.LifecycleProvider;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class IntroActivity extends PageViewActivity {

    private static final int REQUEST_CODE_REGISTER = 0;
    public static final String EXTRA_SHOW_REGISTRATION = "showRegistration";
    private static final int REQUEST_CODE_IMPORT = 2;
    private boolean showRegistration = true;
    private final LifecycleProvider<Lifecycle.Event> lifecycleProvider = AndroidLifecycle.createLifecycleProvider(this);
    private SampleApplication application;
    private final HashSet<Disposable> disposables = new HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showRegistration = getIntent().getBooleanExtra(EXTRA_SHOW_REGISTRATION, true);
        application = (SampleApplication)getApplication();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.intro, menu);
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Iterator<Disposable> iterator = disposables.iterator();
        while (iterator.hasNext()) {
            iterator.next().dispose();
            iterator.remove();
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        Intent importIntent = new Intent("com.appliedrec.ACTION_IMPORT_REGISTRATION");
        ComponentName importActivity = importIntent.resolveActivity(getPackageManager());
        menu.findItem(R.id.action_import).setVisible(showRegistration && importActivity != null);
        int title;
        if (getViewPager().getCurrentItem() < getPageCount() - 1) {
            title = R.string.next;
        } else if (showRegistration) {
            title = R.string.register;
        } else {
            title = R.string.done;
        }
        menu.findItem(R.id.action_next).setTitle(title);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_next) {
            if (getViewPager().getCurrentItem() < getPageCount() - 1) {
                getViewPager().setCurrentItem(getViewPager().getCurrentItem() + 1, true);
            } else if (showRegistration) {
                register();
            } else {
                finish();
            }
            return true;
        }
        if (item.getItemId() == R.id.action_import) {
            // If you want to be able to import face registrations from other devices create an activity
            // that scans a QR code and returns a URL string in its intent's Intent.EXTRA_TEXT extra.
            Intent intent = new Intent("com.appliedrec.ACTION_IMPORT_REGISTRATION");
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
            disposables.add(application.getRxVerID()
                    .getSessionResultFromIntent(data)
                    .flatMapObservable(result -> application.getRxVerID().getFacesAndImageUrisFromSessionResult(result, Bearing.STRAIGHT))
                    .firstOrError()
                    .flatMapCompletable(detectedFace -> new ProfilePhotoHelper(IntroActivity.this).setProfilePhotoFromUri(detectedFace.getImageUri(), detectedFace.getFace().getBounds()))
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .compose(lifecycleProvider.bindToLifecycle())
                    .subscribe(
                            () -> {
                                Intent intent = new Intent(this, RegisteredUserActivity.class);
                                startActivity(intent);
                                finish();
                            },
                            this::showError
                    ));
        } else if (requestCode == REQUEST_CODE_IMPORT) {
            disposables.add(application.getRxVerID()
                    .getUsers()
                    .firstOrError()
                    .ignoreElement()
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .compose(lifecycleProvider.bindToLifecycle())
                    .subscribe(
                            () -> {
                                Intent intent = new Intent(IntroActivity.this, RegisteredUserActivity.class);
                                startActivity(intent);
                                finish();
                            },
                            error -> showError(getString(R.string.failed_to_import_registration))
                    ));
        }
    }

    private void register() {
        disposables.add(application.getRxVerID()
                .getUsers()
                .flatMapCompletable(application.getRxVerID()::deleteUser)
                .andThen(application.getRxVerID().getVerID())
                .compose(lifecycleProvider.bindToLifecycle())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        verID -> {
                            RegistrationSessionSettings settings = new RegistrationSessionSettings(VerIDUser.DEFAULT_USER_ID);
                            settings.setShowResult(true);
                            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                            settings.setNumberOfResultsToCollect(Integer.parseInt(Objects.requireNonNull(preferences.getString(getString(R.string.pref_key_number_of_faces_to_register), "1"))));
                            settings.getFaceBoundsFraction().x = (float) preferences.getInt(getString(R.string.pref_key_face_bounds_width), (int)(settings.getFaceBoundsFraction().x * 20)) * 0.05f;
                            settings.getFaceBoundsFraction().y = (float) preferences.getInt(getString(R.string.pref_key_face_bounds_height), (int)(settings.getFaceBoundsFraction().y * 20)) * 0.05f;
                            if (preferences.getBoolean(getString(R.string.pref_key_use_back_camera), false)) {
                                settings.setFacingOfCameraLens(VerIDSessionSettings.LensFacing.BACK);
                            }
                            if (preferences.getBoolean(getString(R.string.pref_key_speak_prompts), false)) {
                                settings.shouldSpeakPrompts(true);
                            }
                            Intent intent = new VerIDSessionIntent<>(IntroActivity.this, verID, settings);
                            startActivityForResult(intent, REQUEST_CODE_REGISTER);
                        },
                        this::showError
                ));
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
