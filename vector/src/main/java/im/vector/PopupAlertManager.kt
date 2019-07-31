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
package im.vector

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.support.annotation.ColorRes
import android.support.annotation.DrawableRes
import android.util.Log
import android.view.View
import com.tapadoo.alerter.Alerter
import com.tapadoo.alerter.OnHideAlertListener
import im.vector.activity.SASVerificationActivity
import java.lang.ref.WeakReference

/**
 * Responsible of displaying important popup alerts on top of the screen.
 * Alerts are stacked and will be displayed sequentially
 */
object PopupAlertManager {


    private var weakCurrentActivity: WeakReference<Activity>? = null
    private var currentAlerter: VectorAlert? = null

    private val alertFiFo = ArrayList<VectorAlert>()

    private val LOG_TAG = PopupAlertManager::class.java.name


    fun postVectorAlert(alert: VectorAlert) {
        synchronized(alertFiFo) {
            alertFiFo.add(alert)
        }
        displayNextIfPossible()
    }

    fun cancelAlert(uid: String) {
        synchronized(alertFiFo) {
            alertFiFo.listIterator().apply {
                while (this.hasNext()) {
                    val next = this.next()
                    if (next.uid == uid) {
                        this.remove()
                    }
                }
            }
        }

        //it could also be the current one
        if (currentAlerter?.uid == uid) {
            Alerter.hide()
            currentIsDismissed()
        }
    }


    fun onNewActivityDisplayed(activity: Activity) {
        //we want to remove existing popup on previous activity and display it on new one
        if (currentAlerter != null) {
            weakCurrentActivity?.get()?.let {
                Alerter.clearCurrent(it)
            }
        }

        if (shouldIgnoreActivity(activity)) {
            return
        }

        weakCurrentActivity = WeakReference(activity)

        if (currentAlerter != null) {
            if (currentAlerter!!.expirationTimestamp != null && System.currentTimeMillis() > currentAlerter!!.expirationTimestamp!!) {
                //this alert has expired, remove it
                //perform dismiss
                try {
                    currentAlerter?.dismissedAction?.run()
                } catch (e: Exception) {
                    Log.e(LOG_TAG, "## failed to perform action")
                }
                currentAlerter = null
                Handler(Looper.getMainLooper()).postDelayed({
                    displayNextIfPossible()
                }, 2000)
            } else {
                showAlert(currentAlerter!!, activity, animate = false)
            }
        } else {
            Handler(Looper.getMainLooper()).postDelayed({
                displayNextIfPossible()
            }, 2000)
        }
    }

    private fun shouldIgnoreActivity(activity: Activity) = activity is SASVerificationActivity


    private fun displayNextIfPossible() {
        val currentActivity = weakCurrentActivity?.get()
        if (Alerter.isShowing || currentActivity == null) {
            //will retry later
            return
        }
        val next: VectorAlert?
        synchronized(alertFiFo) {
            next = alertFiFo.firstOrNull()
            if (next != null) alertFiFo.remove(next)
        }
        currentAlerter = next
        next?.let {
            val currentTime = System.currentTimeMillis()
            if (next.expirationTimestamp != null && currentTime > next.expirationTimestamp!!) {
                //skip
                try {
                    next.dismissedAction?.run()
                } catch (e: java.lang.Exception) {
                    Log.e(LOG_TAG, "## failed to perform action")
                }
                displayNextIfPossible()
            } else {
                showAlert(it, currentActivity)
            }
        }
    }

    private fun showAlert(alert: VectorAlert, activity: Activity, animate: Boolean = true) {
        alert.weakCurrentActivity = WeakReference(activity)
        Alerter.create(activity)
                .setTitle(alert.title)
                .setText(alert.description)
                .apply {

                    if (!animate) {
                        setEnterAnimation(R.anim.anim_alerter_no_anim)
                    }

                    alert.iconId?.let {
                        setIcon(it)
                    }
                    alert.actions.forEach { action ->
                        addButton(action.title, R.style.AlerterButton, View.OnClickListener {
                            if (action.autoClose) {
                                currentIsDismissed()
                                Alerter.hide()
                            }
                            try {
                                action.action.run()
                            } catch (e: java.lang.Exception) {
                                Log.e(LOG_TAG, "## failed to perform action")
                            }

                        })
                    }
                    setOnClickListener(View.OnClickListener { _ ->
                        alert.contentAction?.let {
                            currentIsDismissed()
                            Alerter.hide()
                            try {
                                it.run()
                            } catch (e: java.lang.Exception) {
                                Log.e(LOG_TAG, "## failed to perform action")
                            }
                        }
                    })

                }
                .setOnHideListener(OnHideAlertListener {
                    //called when dismissed on swipe
                    try {
                        alert.dismissedAction?.run()
                    } catch (e: java.lang.Exception) {
                        Log.e(LOG_TAG, "## failed to perform action")
                    }
                    currentIsDismissed()
                })
                .enableSwipeToDismiss()
                .enableInfiniteDuration(true)
                .setBackgroundColorRes(alert.colorRes ?: R.color.notification_accent_color)
                .show()
    }

    private fun currentIsDismissed() {
        //current alert has been hidden
        currentAlerter = null
        Handler(Looper.getMainLooper()).postDelayed({
            displayNextIfPossible()
        }, 500)
    }

    /**
     * Dataclass to describe an important alert with actions.
     */
    data class VectorAlert(val uid: String,
                           val title: String,
                           val description: String,
                           @DrawableRes val iconId: Int?) {

        data class Button(val title: String, val action: Runnable, val autoClose: Boolean)

        //will be set by manager, and accessible by actions at runtime
        var weakCurrentActivity: WeakReference<Activity>? = null

        val actions = ArrayList<Button>()

        var contentAction: Runnable? = null
        var dismissedAction: Runnable? = null

        /** If this timestamp is after current time, this alert will be skipped */
        var expirationTimestamp: Long? = null

        fun addButton(title: String, action: Runnable, autoClose: Boolean = true) {
            actions.add(Button(title, action, autoClose))
        }

        @ColorRes
        var colorRes: Int? = null
    }
}