package cn.qinxiandiqi.photochecker.feature.about

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract

/**
 *
 * created by Jianan on 2024/3/21
 */
class LinkContract : ActivityResultContract<Uri, Any>() {

    override fun createIntent(context: Context, input: Uri): Intent {
        return Intent(Intent.ACTION_VIEW, input)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Any {
        return resultCode == Activity.RESULT_OK
    }
}