package com.appliedrec.verid.ui2;

public class VerIDSessionFragment extends AbstractSessionFragment<CameraSurfaceView> {

    @Override
    protected CameraSurfaceView createPreviewView() {
        return new CameraSurfaceView(requireContext());
    }
}
