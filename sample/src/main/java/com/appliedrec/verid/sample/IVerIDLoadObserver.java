package com.appliedrec.verid.sample;

import com.appliedrec.verid.core.VerID;

public interface IVerIDLoadObserver {

    void onVerIDLoaded(VerID verid);

    void onVerIDUnloaded();
}
