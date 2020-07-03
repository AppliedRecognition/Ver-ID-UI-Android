package com.appliedrec.verid.ui2;

import androidx.camera.view.PreviewView;

public class VerIDSessionFragmentCameraX extends AbstractSessionFragment<PreviewView> {

    @Override
    protected PreviewView createPreviewView() {
        return new PreviewView(requireContext());
    }
}
