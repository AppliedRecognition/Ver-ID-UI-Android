package com.appliedrec.verid.ui;

import android.app.Activity;
import android.content.Intent;
import android.util.SparseArray;

import androidx.fragment.app.Fragment;

import com.appliedrec.verid.core.Face;
import com.appliedrec.verid.core.VerID;
import com.appliedrec.verid.core.VerIDSessionSettings;

import java.lang.ref.WeakReference;

public class VerIDSession<T extends VerIDSessionSettings> {

    public interface Delegate {
        void veridSessionDidFinishWithResult(VerIDSession session, VerIDSessionResult result);
        void veridSessionWasCancelled(VerIDSession session);
    }

    private final Activity activity;
    private final Fragment supportFragment;
    private final android.app.Fragment fragment;
    private final T settings;
    private final VerID verID;
    private WeakReference<Delegate> delegate;
    private final int id;

    static SparseArray<VerIDSession> sessions = new SparseArray<>();
    private static int lastId = 0;

    public VerIDSession(Activity activity, VerID verID, T settings) {
        this.activity = activity;
        this.supportFragment = null;
        this.fragment = null;
        this.verID = verID;
        this.settings = settings;
        this.id = lastId++;
        sessions.put(this.id, this);
    }

    public VerIDSession(Fragment fragment, VerID verID, T settings) {
        this.activity = null;
        this.supportFragment = fragment;
        this.fragment = null;
        this.verID = verID;
        this.settings = settings;
        this.id = lastId++;
        sessions.put(this.id, this);
    }

    public VerIDSession(android.app.Fragment fragment, VerID verID, T settings) {
        this.activity = null;
        this.supportFragment = null;
        this.fragment = null;
        this.verID = verID;
        this.settings = settings;
        this.id = lastId++;
        sessions.put(this.id, this);
    }

    public Delegate getDelegate() {
        if (delegate == null) {
            return  null;
        }
        return delegate.get();
    }

    public void setDelegate(Delegate delegate) {
        this.delegate = new WeakReference<>(delegate);
    }

    public T getSettings() {
        return settings;
    }

    public VerID getVerID() {
        return verID;
    }

    public int getId() {
        return id;
    }

    public void start() {
        Intent intent = createProxyIntent();
        if (activity != null) {
            activity.startActivity(intent);
        } else if (supportFragment != null) {
            supportFragment.startActivity(intent);
        } else if (fragment != null) {
            fragment.startActivity(intent);
        }
    }

    <U extends Face> void onSessionFinished(VerIDSessionResult<U> result) {
        sessions.delete(id);
        if (getDelegate() != null) {
            getDelegate().veridSessionDidFinishWithResult(this, result);
        }
    }

    void onSessionCancelled() {
        sessions.delete(id);
        if (getDelegate() != null) {
            getDelegate().veridSessionWasCancelled(this);
        }
    }

    private Intent createProxyIntent() throws NullPointerException {
        Intent intent;
        if (activity != null) {
            intent = new Intent(activity, VerIDSessionProxyActivity.class);
        } else if (supportFragment != null) {
            intent = new Intent(supportFragment.getContext(), VerIDSessionProxyActivity.class);
        } else if (fragment != null) {
            intent = new Intent(fragment.getActivity(), VerIDSessionProxyActivity.class);
        } else {
            throw new NullPointerException();
        }
        intent.putExtra(VerIDSessionActivity.EXTRA_SETTINGS, settings);
        intent.putExtra(VerIDSessionActivity.EXTRA_VERID_INSTANCE_ID, verID.getInstanceId());
        intent.putExtra(VerIDSessionProxyActivity.EXTRA_SESSION_ID, id);
        return intent;
    }
}
