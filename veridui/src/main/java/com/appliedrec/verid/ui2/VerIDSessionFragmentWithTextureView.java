package com.appliedrec.verid.ui2;

import android.view.TextureView;

public class VerIDSessionFragmentWithTextureView extends AbstractSessionFragment<TextureView> {
    @Override
    protected TextureView createPreviewView() {
        return new TextureView(requireContext());
    }
}
