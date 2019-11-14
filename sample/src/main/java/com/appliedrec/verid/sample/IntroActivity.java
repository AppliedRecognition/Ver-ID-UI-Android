package com.appliedrec.verid.sample;

import android.app.AlertDialog;
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
import android.widget.ProgressBar;
import android.widget.TextView;

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

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

public class IntroActivity extends PageViewActivity {

    private static final int REQUEST_CODE_REGISTER = 0;
    public static final String EXTRA_SHOW_REGISTRATION = "showRegistration";
    private static final int QR_CODE_SCAN_REQUEST_CODE = 1;
    private static final int REQUEST_CODE_IMPORT = 2;
    boolean showRegistration = true;
    private AlertDialog tempDialog;
    private final LifecycleProvider<Lifecycle.Event> lifecycleProvider = AndroidLifecycle.createLifecycleProvider(this);

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
        menu.findItem(R.id.action_import).setVisible(showRegistration && ((SampleApplication)getApplication()).getRegistrationDownload() != null);
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
            Intent intent = new Intent("com.appliedrec.ACTION_SCAN_QR_CODE");
            startActivityForResult(intent, QR_CODE_SCAN_REQUEST_CODE);
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
            RxVerIDInstance.get()
                    .getSessionResultFromIntent(data)
                    .flatMapObservable(result -> RxVerIDInstance.get().getFacesAndImageUrisFromSessionResult(result, Bearing.STRAIGHT))
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
                            error -> {

                            }
                    );
        } else if (requestCode == QR_CODE_SCAN_REQUEST_CODE && resultCode == RESULT_OK && data != null && data.hasExtra(Intent.EXTRA_TEXT)) {
            tempDialog = new AlertDialog.Builder(this)
                    .setTitle(R.string.downloading)
                    .setView(new ProgressBar(this))
                    .create();
            tempDialog.show();
            RegistrationDownload.downloadRegistrations(data.getStringExtra(Intent.EXTRA_TEXT), ((SampleApplication)getApplication()).getRegistrationDownload())
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .compose(lifecycleProvider.bindToLifecycle())
                    .subscribe(
                            bundle -> {
                                if (tempDialog != null) {
                                    tempDialog.dismiss();
                                    tempDialog = null;
                                }
                                Intent intent = new Intent(IntroActivity.this, RegistrationImportActivity.class);
                                intent.putExtras(bundle);
                                startActivityForResult(intent, REQUEST_CODE_IMPORT);
                            },
                            error -> {
                                if (tempDialog != null) {
                                    tempDialog.dismiss();
                                    tempDialog = null;
                                }
                                showImportError();
                            }
                    );
        } else if (requestCode == REQUEST_CODE_IMPORT) {
            RxVerIDInstance.get()
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
                            error -> {}
                    );
        }
    }

    private void register() {
        RxVerIDInstance.get()
                .getUsers()
                .flatMapCompletable(RxVerIDInstance.get()::deleteUser)
                .andThen(RxVerIDInstance.get().getVerID())
                .compose(lifecycleProvider.bindToLifecycle())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        verID -> {
                            RegistrationSessionSettings settings = new RegistrationSessionSettings(VerIDUser.DEFAULT_USER_ID);
                            settings.setShowResult(true);
                            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                            settings.setNumberOfResultsToCollect(Integer.parseInt(preferences.getString(getString(R.string.pref_key_number_of_faces_to_register), "1")));
                            settings.getFaceBoundsFraction().x = (float) preferences.getInt(getString(R.string.pref_key_face_bounds_width), (int)(settings.getFaceBoundsFraction().x * 20)) * 0.05f;
                            settings.getFaceBoundsFraction().y = (float) preferences.getInt(getString(R.string.pref_key_face_bounds_height), (int)(settings.getFaceBoundsFraction().y * 20)) * 0.05f;
                            if (preferences.getBoolean(getString(R.string.pref_key_use_back_camera), false)) {
                                settings.setFacingOfCameraLens(VerIDSessionSettings.LensFacing.BACK);
                            }
                            Intent intent = new VerIDSessionIntent<>(IntroActivity.this, verID, settings);
                            startActivityForResult(intent, REQUEST_CODE_REGISTER);
                        },
                        error -> {

                        }
                );
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

        static int[] imageResourceIds = new int[]{
                R.mipmap.guide_head_straight,
                R.mipmap.multiple_heads,
                R.mipmap.authentication
        };
        static int[] titleResourceIds = new int[]{
                R.string.verid_person_sdk,
                R.string.one_registration,
                R.string.two_authentication
        };
        static int[] textResourceIds = new int[]{
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
        public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            Bundle args = getArguments();
            int index = args.getInt("index", 0);
            return IntroFragment.createView(inflater, container, index);
        }
    }

    private void showImportError() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.error)
                .setMessage(R.string.failed_to_import_registration)
                .setPositiveButton(android.R.string.ok, null)
                .create()
                .show();
    }
    //endregion
}
