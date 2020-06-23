package com.appliedrec.verid.ui2;

import com.appliedrec.verid.core2.session.VerIDSessionResult;

public interface ISessionResultActivity {

    void setSessionResult(VerIDSessionResult sessionResult);

    void setTranslator(IStringTranslator translator);

    default boolean didTapRetryButtonInSessionResultActivity() {
        return false;
    }
}
