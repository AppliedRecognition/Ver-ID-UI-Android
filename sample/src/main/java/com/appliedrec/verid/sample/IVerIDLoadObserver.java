package com.appliedrec.verid.sample;

import com.appliedrec.verid.core2.VerID;

public interface IVerIDLoadObserver {

    void onVerIDLoaded(VerID verid);

    void onVerIDUnloaded();
}
