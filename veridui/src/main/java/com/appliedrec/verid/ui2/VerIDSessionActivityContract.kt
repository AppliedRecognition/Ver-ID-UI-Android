package com.appliedrec.verid.ui2

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import com.appliedrec.verid.core2.session.VerIDSessionResult

class VerIDSessionActivityContract :
    ActivityResultContract<VerIDSessionActivitySettings, VerIDSessionResult?>() {

    override fun createIntent(context: Context, input: VerIDSessionActivitySettings): Intent {
        return Intent(context, VerIDSessionActivity::class.java).apply {
            putExtra("settings", input)
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): VerIDSessionResult? {
        if (resultCode == Activity.RESULT_CANCELED) {
            return null
        }
        val resultId = intent?.getIntExtra("resultId", -1) ?: -1
        return SessionResultStorage.removeResult(resultId)
    }
}