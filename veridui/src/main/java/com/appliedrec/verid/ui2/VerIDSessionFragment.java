package com.appliedrec.verid.ui2;

import android.view.SurfaceView;

public class VerIDSessionFragment extends AbstractSessionFragment<SurfaceView> {

    @Override
    protected SurfaceView createPreviewView() {
        return new SurfaceView(requireContext());
    }
}
