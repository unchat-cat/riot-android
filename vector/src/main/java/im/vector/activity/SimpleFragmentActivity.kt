/*
 * Copyright 2019 New Vector Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package im.vector.activity

import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isGone
import androidx.core.view.isVisible
import butterknife.BindView
import im.vector.R
import im.vector.activity.util.WaitingViewData

/**
 * Simple activity with a toolbar, a waiting overlay, and a fragment container and a mxSession.
 */
open class SimpleFragmentActivity : MXCActionBarActivity() {

    override fun getLayoutRes() = R.layout.activity

    @BindView(R.id.waiting_view_status_circular_progress)
    lateinit var waitingCircularProgress: View

    @BindView(R.id.waiting_view_status_text)
    lateinit var waitingStatusText: TextView

    @BindView(R.id.waiting_view_status_horizontal_progress)
    lateinit var waitingHorizontalProgress: ProgressBar

    override fun initUiAndData() {
        mSession = getSession(intent)
        configureToolbar()
        waitingView = findViewById(R.id.waiting_view)
    }

    /**
     * Displays a progress indicator with a message to the user.
     * Blocks user interactions.
     */
    fun updateWaitingView(data: WaitingViewData?) {
        data?.let {
            waitingStatusText.text = data.message

            if (data.progress != null && data.progressTotal != null) {
                waitingHorizontalProgress.isIndeterminate = false
                waitingHorizontalProgress.progress = data.progress
                waitingHorizontalProgress.max = data.progressTotal
                waitingHorizontalProgress.isVisible = true
                waitingCircularProgress.isVisible = false
            } else if (data.isIndeterminate) {
                waitingHorizontalProgress.isIndeterminate = true
                waitingHorizontalProgress.isVisible = true
                waitingCircularProgress.isVisible = false
            } else {
                waitingHorizontalProgress.isVisible = false
                waitingCircularProgress.isVisible = true
            }

            showWaitingView()
        } ?: run {
            hideWaitingView()
        }
    }

    override fun showWaitingView() {
        dismissKeyboard(this)
        waitingStatusText.isGone = waitingStatusText.text.isNullOrBlank()
        super.showWaitingView()
    }

    override fun hideWaitingView() {
        waitingStatusText.text = null
        waitingStatusText.isGone = true
        waitingHorizontalProgress.progress = 0
        waitingHorizontalProgress.isVisible = false
        super.hideWaitingView()
    }

    override fun onBackPressed() {
        if (waitingView!!.isVisible) {
            // ignore
            return
        }
        super.onBackPressed()
    }
}