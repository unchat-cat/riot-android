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

import android.app.Activity
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.content.Intent
import android.support.v7.app.AlertDialog
import android.view.MenuItem
import im.vector.R
import im.vector.activity.util.WaitingViewData
import im.vector.fragments.verification.*
import org.matrix.androidsdk.crypto.verification.CancelCode
import org.matrix.androidsdk.crypto.verification.IncomingSASVerificationTransaction
import org.matrix.androidsdk.crypto.verification.OutgoingSASVerificationRequest
import org.matrix.androidsdk.crypto.verification.SASVerificationTransaction

class SASVerificationActivity : SimpleFragmentActivity() {


    companion object {

        private const val EXTRA_TRANSACTION_ID = "EXTRA_TRANSACTION_ID"
        private const val EXTRA_OTHER_USER_ID = "EXTRA_OTHER_USER_ID"
        private const val EXTRA_OTHER_DEVICE_ID = "EXTRA_OTHER_DEVICE_ID"
        private const val EXTRA_IS_INCOMING = "EXTRA_IS_INCOMING"

        /* ==========================================================================================
         * INPUT
         * ========================================================================================== */

        fun incomingIntent(context: Context, matrixID: String, otherUserId: String, transactionID: String): Intent {
            val intent = Intent(context, SASVerificationActivity::class.java)
            intent.putExtra(EXTRA_MATRIX_ID, matrixID)
            intent.putExtra(EXTRA_TRANSACTION_ID, transactionID)
            intent.putExtra(EXTRA_OTHER_USER_ID, otherUserId)
            intent.putExtra(EXTRA_IS_INCOMING, true)
            return intent
        }

        fun outgoingIntent(context: Context, matrixID: String, otherUserId: String, otherDeviceId: String): Intent {
            val intent = Intent(context, SASVerificationActivity::class.java)
            intent.putExtra(EXTRA_MATRIX_ID, matrixID)
            intent.putExtra(EXTRA_OTHER_DEVICE_ID, otherDeviceId)
            intent.putExtra(EXTRA_OTHER_USER_ID, otherUserId)
            intent.putExtra(EXTRA_IS_INCOMING, false)
            return intent
        }

        /* ==========================================================================================
         * OUTPUT
         * ========================================================================================== */

        fun getOtherUserId(intent: Intent?): String? {
            return intent?.getStringExtra(EXTRA_OTHER_USER_ID)
        }

        fun getOtherDeviceId(intent: Intent?): String? {
            return intent?.getStringExtra(EXTRA_OTHER_DEVICE_ID)
        }
    }

    override fun getTitleRes() = R.string.title_activity_verify_device


    private lateinit var viewModel: SasVerificationViewModel

    override fun initUiAndData() {
        super.initUiAndData()
        viewModel = ViewModelProviders.of(this).get(SasVerificationViewModel::class.java)
        val transactionID: String? = intent.getStringExtra(EXTRA_TRANSACTION_ID)

        if (isFirstCreation()) {
            val isIncoming = intent.getBooleanExtra(EXTRA_IS_INCOMING, false)
            if (isIncoming) {
                //incoming always have a transaction id
                viewModel.initIncoming(mSession, intent.getStringExtra(EXTRA_OTHER_USER_ID), transactionID)
            } else {
                viewModel.initOutgoing(mSession, intent.getStringExtra(EXTRA_OTHER_USER_ID), intent.getStringExtra(EXTRA_OTHER_DEVICE_ID))
            }

            if (isIncoming) {
                val incoming = viewModel.transaction as IncomingSASVerificationTransaction
                when (incoming.uxState) {
                    IncomingSASVerificationTransaction.State.UNKNOWN,
                    IncomingSASVerificationTransaction.State.SHOW_ACCEPT,
                    IncomingSASVerificationTransaction.State.WAIT_FOR_KEY_AGREEMENT -> {
                        supportActionBar?.setTitle(R.string.sas_incoming_request_title)
                        supportFragmentManager.beginTransaction()
                                .setCustomAnimations(R.anim.no_anim, R.anim.exit_fade_out)
                                .replace(R.id.container, SASVerificationIncomingFragment.newInstance())
                                .commitNow()
                    }
                    IncomingSASVerificationTransaction.State.WAIT_FOR_VERIFICATION,
                    IncomingSASVerificationTransaction.State.SHOW_SAS -> {
                        supportFragmentManager.beginTransaction()
                                .setCustomAnimations(R.anim.no_anim, R.anim.exit_fade_out)
                                .replace(R.id.container, SASVerificationShortCodeFragment.newInstance())
                                .commitNow()
                    }
                    IncomingSASVerificationTransaction.State.VERIFIED -> {
                        supportFragmentManager.beginTransaction()
                                .setCustomAnimations(R.anim.no_anim, R.anim.exit_fade_out)
                                .replace(R.id.container, SASVerificationVerifiedFragment.newInstance())
                                .commitNow()
                    }
                    IncomingSASVerificationTransaction.State.CANCELLED_BY_ME,
                    IncomingSASVerificationTransaction.State.CANCELLED_BY_OTHER -> {
                        viewModel.navigateCancel()
                    }
                }
            } else {
                val outgoing = viewModel.transaction as? OutgoingSASVerificationRequest
                //transaction can be null, as not yet created
                when (outgoing?.uxState) {
                    null,
                    OutgoingSASVerificationRequest.State.UNKNOWN,
                    OutgoingSASVerificationRequest.State.WAIT_FOR_START,
                    OutgoingSASVerificationRequest.State.WAIT_FOR_KEY_AGREEMENT -> {
                        supportFragmentManager.beginTransaction()
                                .setCustomAnimations(R.anim.no_anim, R.anim.exit_fade_out)
                                .replace(R.id.container, SASVerificationStartFragment.newInstance())
                                .commitNow()
                    }
                    OutgoingSASVerificationRequest.State.SHOW_SAS,
                    OutgoingSASVerificationRequest.State.WAIT_FOR_VERIFICATION -> {
                        supportFragmentManager.beginTransaction()
                                .setCustomAnimations(R.anim.no_anim, R.anim.exit_fade_out)
                                .replace(R.id.container, SASVerificationShortCodeFragment.newInstance())
                                .commitNow()
                    }
                    OutgoingSASVerificationRequest.State.VERIFIED -> {
                        supportFragmentManager.beginTransaction()
                                .setCustomAnimations(R.anim.no_anim, R.anim.exit_fade_out)
                                .replace(R.id.container, SASVerificationVerifiedFragment.newInstance())
                                .commitNow()
                    }
                    OutgoingSASVerificationRequest.State.CANCELLED_BY_ME,
                    OutgoingSASVerificationRequest.State.CANCELLED_BY_OTHER -> {
                        viewModel.navigateCancel()
                    }
                }
            }
        }

        viewModel.navigateEvent.observe(this, Observer { uxStateEvent ->
            when (uxStateEvent?.getContentIfNotHandled()) {
                SasVerificationViewModel.NAVIGATE_FINISH -> {
                    finish()
                }
                SasVerificationViewModel.NAVIGATE_FINISH_SUCCESS -> {
                    val dataResult = Intent()
                    dataResult.putExtra(EXTRA_OTHER_DEVICE_ID, viewModel.otherDeviceId)
                    dataResult.putExtra(EXTRA_OTHER_USER_ID, viewModel.otherUserId)
                    setResult(Activity.RESULT_OK, dataResult)
                    finish()
                }
                SasVerificationViewModel.NAVIGATE_SAS_DISPLAY -> {
                    supportFragmentManager.beginTransaction()
                            .setCustomAnimations(R.anim.enter_from_right, R.anim.exit_fade_out)
                            .replace(R.id.container, SASVerificationShortCodeFragment.newInstance())
                            .commitNow()
                }
                SasVerificationViewModel.NAVIGATE_SUCCESS -> {
                    supportFragmentManager.beginTransaction()
                            .setCustomAnimations(R.anim.enter_from_right, R.anim.exit_fade_out)
                            .replace(R.id.container, SASVerificationVerifiedFragment.newInstance())
                            .commitNow()
                }
                SasVerificationViewModel.NAVIGATE_CANCELLED -> {
                    val isCancelledByMe = viewModel.transaction?.state == SASVerificationTransaction.SASVerificationTxState.Cancelled
                    val humanReadableReason = when (viewModel.transaction?.cancelledReason) {
                        CancelCode.User -> getString(R.string.sas_error_m_user)
                        CancelCode.Timeout -> getString(R.string.sas_error_m_timeout)
                        CancelCode.UnknownTransaction -> getString(R.string.sas_error_m_unknown_transaction)
                        CancelCode.UnknownMethod -> getString(R.string.sas_error_m_unknown_method)
                        CancelCode.MismatchedCommitment -> getString(R.string.sas_error_m_mismatched_commitment)
                        CancelCode.MismatchedSas -> getString(R.string.sas_error_m_mismatched_sas)
                        CancelCode.UnexpectedMessage -> getString(R.string.sas_error_m_unexpected_message)
                        CancelCode.InvalidMessage -> getString(R.string.sas_error_m_invalid_message)
                        CancelCode.MismatchedKeys -> getString(R.string.sas_error_m_key_mismatch)
                        // Use user error
                        CancelCode.UserMismatchError -> getString(R.string.sas_error_m_user_error)
                        null -> getString(R.string.sas_error_unknown)
                    }
                    val message =
                            if (isCancelledByMe) getString(R.string.sas_cancelled_by_me, humanReadableReason)
                            else getString(R.string.sas_cancelled_by_other, humanReadableReason)
                    //Show a dialog
                    if (!this.isFinishing) {
                        AlertDialog.Builder(this)
                                .setTitle(R.string.sas_cancelled_dialog_title)
                                .setMessage(message)
                                .setCancelable(false)
                                .setPositiveButton(R.string.ok) { _, _ ->
                                    //nop
                                    finish()
                                }
                                .show()
                    }
                }
            }
        })

        viewModel.loadingLiveEvent.observe(this, Observer {
            if (it == null) {
                hideWaitingView()
            } else {
                val status = if (it == -1) "" else getString(it)
                updateWaitingView(WaitingViewData(status, isIndeterminate = true))
            }
        })
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            //we want to cancel the transaction
            viewModel.cancelTransaction()
        }

        return super.onOptionsItemSelected(item)
    }


    override fun onBackPressed() {
        //we want to cancel the transaction
        viewModel.cancelTransaction()
    }
}
