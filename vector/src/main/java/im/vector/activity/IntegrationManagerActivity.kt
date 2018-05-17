/*
 * Copyright 2017 Vector Creations Ltd
 * Copyright 2018 New Vector Ltd
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

import android.content.Context
import android.content.Intent
import android.support.annotation.CallSuper
import android.text.TextUtils
import im.vector.R
import im.vector.util.toJsonMap
import im.vector.widgets.WidgetsManager
import org.matrix.androidsdk.rest.callback.ApiCallback
import org.matrix.androidsdk.rest.model.Event
import org.matrix.androidsdk.rest.model.MatrixError
import org.matrix.androidsdk.rest.model.RoomMember
import org.matrix.androidsdk.util.Log
import java.net.URLEncoder
import java.util.*

class IntegrationManagerActivity : AbstractScalarActivity() {

    /* ==========================================================================================
     * parameters
     * ========================================================================================== */

    private var mWidgetId: String? = null
    private var mScreenId: String? = null

    override fun getLayoutRes() = R.layout.activity_integration_manager

    /* ==========================================================================================
     * LIFECYCLE
     * ========================================================================================== */

    @CallSuper
    override fun initUiAndData() {
        mWidgetId = intent.getStringExtra(EXTRA_WIDGET_ID)
        mScreenId = intent.getStringExtra(EXTRA_SCREEN_ID)

        waitingView = findViewById(R.id.integration_progress_layout)

        showWaitingView()

        super.initUiAndData()
    }

    override fun displayInFullscreen() = true

    /* ==========================================================================================
     * IMPLEMENTS METHOD
     * ========================================================================================== */

    /**
     * Compute the integration URL
     *
     * @return the integration URL
     */
    override fun buildInterfaceUrl(scalarToken: String): String? {
        try {
            var url = WidgetsManager.INTEGRATION_UI_URL + "?" +
                    "scalar_token=" + URLEncoder.encode(scalarToken, "utf-8") + "&" +
                    "room_id=" + URLEncoder.encode(mRoom!!.roomId, "utf-8")

            if (null != mScreenId) {
                url += "&screen=" + URLEncoder.encode(mScreenId, "utf-8")
            }

            if (null != mWidgetId) {
                // 'widgetId' ?
                url += "&integ_id=" + URLEncoder.encode(mWidgetId, "utf-8")
            }
            return url
        } catch (e: Exception) {
            Log.e(LOG_TAG, "## buildInterfaceUrl() failed " + e.message)
        }

        return null
    }

    /**
     * A Scalar message has been received, deals with it and send the response
     */
    override fun dealsWithScalarMessage(eventData: Map<String, Any>) {
        val roomIdInEvent = eventData["room_id"] as String?
        val userId = eventData["user_id"] as String?
        val action = eventData["action"] as String?
        val userWidget = eventData["userWidget"] as Boolean?

        when {
            action == "close_scalar" -> finish()

        // User widget
            userWidget == true
                    && action == "set_widget" -> setWidget(eventData, true)

        // other APIs requires a roomId
            null == roomIdInEvent -> sendError(getString(R.string.widget_integration_missing_room_id), eventData)

        // Room ids must match
            !TextUtils.equals(roomIdInEvent, mRoom!!.roomId) -> sendError(getString(R.string.widget_integration_room_not_visible), eventData)

        // These APIs don't require userId
            action == "join_rules_state" -> getJoinRules(eventData)
            action == "set_plumbing_state" -> setPlumbingState(eventData)
            action == "get_membership_count" -> getMembershipCount(eventData)
            action == "set_widget" -> setWidget(eventData)
            action == "get_widgets" -> getWidgets(eventData)
            action == "can_send_event" -> canSendEvent(eventData)

        // For the next APIs, a userId is required
        // FIXME Unknown action is not treated properly then
            null == userId -> sendError(getString(R.string.widget_integration_missing_user_id), eventData)

            action == "membership_state" -> getMembershipState(userId, eventData)
            action == "invite" -> inviteUser(userId, eventData)
            action == "bot_options" -> getBotOptions(userId, eventData)
            action == "set_bot_options" -> setBotOptions(userId, eventData)
            action == "set_bot_power" -> setBotPower(userId, eventData)

            else -> Log.e(LOG_TAG, "## dealsWithScalarMessage() : Unhandled postMessage event with action $action")
        }
    }

    /*
     * *********************************************************************************************
     * Private methods
     * *********************************************************************************************
     */

    /*
     * *********************************************************************************************
     * Modular postMessage methods
     * *********************************************************************************************
     */

    /**
     * Invite an user to this room
     *
     * @param userId    the user id
     * @param eventData the modular data
     */
    private fun inviteUser(userId: String, eventData: Map<String, Any>) {
        val description = "Received request to invite " + userId + " into room " + mRoom!!.roomId

        Log.d(LOG_TAG, description)

        val member = mRoom!!.getMember(userId)

        if (null != member && TextUtils.equals(member.membership, RoomMember.MEMBERSHIP_JOIN)) {
            sendObjectResponse(HashMap(mSucceedResponse), eventData)
        } else {
            mRoom!!.invite(userId, IntegrationManagerApiCallback(eventData, description))
        }
    }

    /**
     * Set a new widget
     *
     * @param eventData the modular data
     */
    private fun setWidget(eventData: Map<String, Any>, isUserWidget: Boolean = false) {
        if (isUserWidget) {
            Log.d(LOG_TAG, "Received request to set widget for user")
        } else {
            Log.d(LOG_TAG, "Received request to set widget in room " + mRoom!!.roomId)
        }

        val widgetId = eventData["widget_id"] as String?
        val widgetType = eventData["type"] as String?
        val widgetUrl = eventData["url"] as String?

        // optional
        val widgetName = eventData["name"] as String?
        // optional
        val widgetData = eventData["data"] as Map<Any, Any>?

        if (null == widgetId) {
            sendError(getString(R.string.widget_integration_unable_to_create), eventData)
            return
        }

        val widgetEventContent = HashMap<String, Any>()

        if (null != widgetUrl) {
            if (null == widgetType) {
                sendError(getString(R.string.widget_integration_unable_to_create), eventData)
                return
            }

            widgetEventContent["type"] = widgetType
            widgetEventContent["url"] = widgetUrl

            if (null != widgetName) {
                widgetEventContent["name"] = widgetName
            }

            if (null != widgetData) {
                widgetEventContent["data"] = widgetData
            }
        }

        if (isUserWidget) {
            val addUserWidgetBody = HashMap<String, Any>().apply {
                put(widgetId, HashMap<String, Any>().apply {
                    put("content", widgetEventContent)

                    put("state_key", widgetId)
                    put("id", widgetId)
                    put("sender", mSession!!.myUserId)
                    put("type", "m.widget")
                })
            }

            mSession!!.addUserWidget(addUserWidgetBody,
                    IntegrationManagerApiCallback(eventData, "## setWidget()"))
        } else {
            mSession!!.roomsApiClient.sendStateEvent(mRoom!!.roomId,
                    WidgetsManager.WIDGET_EVENT_TYPE,
                    widgetId,
                    widgetEventContent,
                    IntegrationManagerApiCallback(eventData, "## setWidget()"))
        }
    }

    /**
     * Provide the widgets list
     *
     * @param eventData the modular data
     */
    private fun getWidgets(eventData: Map<String, Any>) {
        Log.d(LOG_TAG, "Received request to get widget in room " + mRoom!!.roomId)

        val widgets = WidgetsManager.getSharedInstance().getActiveWidgets(mSession, mRoom)
        val responseData = ArrayList<Map<String, Any>>()

        for (widget in widgets) {
            val map = widget.widgetEvent.toJsonMap()

            if (null != map) {
                responseData.add(map)
            }
        }

        // Add user Widgets
        mSession!!.userWidgets
                .forEach {
                    responseData.add(it.value as Map<String, Any>)
                }

        Log.d(LOG_TAG, "## getWidgets() returns $responseData")

        sendObjectResponse(responseData, eventData)
    }

    /**
     * Check if the user can send an event of predefined type
     *
     * @param eventData the modular data
     */
    private fun canSendEvent(eventData: Map<String, Any>) {
        Log.d(LOG_TAG, "Received request canSendEvent in room " + mRoom!!.roomId)

        val member = mRoom!!.liveState.getMember(mSession!!.myUserId)

        if (null == member || !TextUtils.equals(RoomMember.MEMBERSHIP_JOIN, member.membership)) {
            sendError(getString(R.string.widget_integration_must_be_in_room), eventData)
            return
        }

        val eventType = eventData["event_type"] as String
        val isState = eventData["is_state"] as Boolean

        Log.d(LOG_TAG, "## canSendEvent() : eventType $eventType isState $isState")

        val powerLevels = mRoom!!.liveState.powerLevels

        val userPowerLevel = powerLevels!!.getUserPowerLevel(mSession!!.myUserId)

        val canSend = if (isState) {
            userPowerLevel >= powerLevels.minimumPowerLevelForSendingEventAsStateEvent(eventType)
        } else {
            userPowerLevel >= powerLevels.minimumPowerLevelForSendingEventAsMessage(eventType)
        }

        if (canSend) {
            Log.d(LOG_TAG, "## canSendEvent() returns true")
            sendBoolResponse(true, eventData)
        } else {
            Log.d(LOG_TAG, "## canSendEvent() returns widget_integration_no_permission_in_room")
            sendError(getString(R.string.widget_integration_no_permission_in_room), eventData)
        }
    }

    /**
     * Provides the membership state
     *
     * @param userId    the user id
     * @param eventData the modular data
     */
    private fun getMembershipState(userId: String, eventData: Map<String, Any>) {
        Log.d(LOG_TAG, "membership_state of " + userId + " in room " + mRoom!!.roomId + " requested")

        mRoom!!.getMemberEvent(userId, object : ApiCallback<Event> {
            override fun onSuccess(event: Event?) {
                Log.d(LOG_TAG, "membership_state of " + userId + " in room " + mRoom!!.roomId + " returns " + event)

                if (null != event) {
                    sendObjectAsJsonMap(event.content, eventData)
                } else {
                    sendObjectResponse(null, eventData)
                }
            }

            override fun onNetworkError(e: Exception) {
                Log.e(LOG_TAG, "membership_state of " + userId + " in room " + mRoom!!.roomId + " failed " + e.message)
                sendError(getString(R.string.widget_integration_failed_to_send_request), eventData)
            }

            override fun onMatrixError(e: MatrixError) {
                Log.e(LOG_TAG, "membership_state of " + userId + " in room " + mRoom!!.roomId + " failed " + e.message)
                sendError(getString(R.string.widget_integration_failed_to_send_request), eventData)
            }

            override fun onUnexpectedError(e: Exception) {
                Log.e(LOG_TAG, "membership_state of " + userId + " in room " + mRoom!!.roomId + " failed " + e.message)
                sendError(getString(R.string.widget_integration_failed_to_send_request), eventData)
            }
        })
    }

    /**
     * Request the latest joined room event
     *
     * @param eventData the modular data
     */
    private fun getJoinRules(eventData: Map<String, Any>) {
        Log.d(LOG_TAG, "Received request join rules  in room " + mRoom!!.roomId)
        val joinedEvents = mRoom!!.liveState.getStateEvents(HashSet(Arrays.asList(Event.EVENT_TYPE_STATE_ROOM_JOIN_RULES)))

        if (joinedEvents.size > 0) {
            Log.d(LOG_TAG, "Received request join rules returns " + joinedEvents[joinedEvents.size - 1])
            sendObjectAsJsonMap(joinedEvents[joinedEvents.size - 1], eventData)
        } else {
            Log.e(LOG_TAG, "Received request join rules failed widget_integration_failed_to_send_request")
            sendError(getString(R.string.widget_integration_failed_to_send_request), eventData)
        }
    }

    /**
     * Update the 'plumbing state"
     *
     * @param eventData the modular data
     */
    private fun setPlumbingState(eventData: Map<String, Any>) {
        val description = "Received request to set plumbing state to status " + eventData["status"] + " in room " + mRoom!!.roomId + " requested"
        Log.d(LOG_TAG, description)

        val status = eventData["status"] as String

        val params = HashMap<String, Any>()
        params["status"] = status

        mSession!!.roomsApiClient.sendStateEvent(mRoom!!.roomId,
                Event.EVENT_TYPE_ROOM_PLUMBING,
                null,
                params,
                IntegrationManagerApiCallback(eventData, description))
    }

    /**
     * Retrieve the latest botOptions event
     *
     * @param userId    the userID
     * @param eventData the modular data
     */
    private fun getBotOptions(userId: String, eventData: Map<String, Any>) {
        Log.d(LOG_TAG, "Received request to get options for bot " + userId + " in room " + mRoom!!.roomId + " requested")

        val stateEvents = mRoom!!.liveState.getStateEvents(HashSet(Arrays.asList(Event.EVENT_TYPE_ROOM_BOT_OPTIONS)))

        var botOptionsEvent: Event? = null
        val stateKey = "_$userId"

        for (stateEvent in stateEvents) {
            if (TextUtils.equals(stateEvent.stateKey, stateKey)) {
                if (null == botOptionsEvent || stateEvent.getAge() > botOptionsEvent.getAge()) {
                    botOptionsEvent = stateEvent
                }
            }
        }

        if (null != botOptionsEvent) {
            Log.d(LOG_TAG, "Received request to get options for bot $userId returns $botOptionsEvent")
            sendObjectAsJsonMap(botOptionsEvent, eventData)
        } else {
            Log.d(LOG_TAG, "Received request to get options for bot $userId returns null")
            sendObjectResponse(null, eventData)
        }
    }

    /**
     * Update the bot options
     *
     * @param userId    the userID
     * @param eventData the modular data
     */
    private fun setBotOptions(userId: String, eventData: Map<String, Any>) {
        val description = "Received request to set options for bot " + userId + " in room " + mRoom!!.roomId
        Log.d(LOG_TAG, description)

        val content = eventData["content"] as Map<String, Any>
        val stateKey = "_$userId"

        mSession!!.roomsApiClient.sendStateEvent(mRoom!!.roomId,
                Event.EVENT_TYPE_ROOM_BOT_OPTIONS,
                stateKey,
                content,
                IntegrationManagerApiCallback(eventData, description))
    }

    /**
     * Update the bot power levels
     *
     * @param userId    the userID
     * @param eventData the modular data
     */
    private fun setBotPower(userId: String, eventData: Map<String, Any>) {
        val description = "Received request to set power level to " + eventData["level"] + " for bot " + userId + " in room " + mRoom!!.roomId

        Log.d(LOG_TAG, description)

        val level = eventData["level"] as Int

        if (level >= 0) {
            mRoom!!.updateUserPowerLevels(userId, level, IntegrationManagerApiCallback(eventData, description))
        } else {
            Log.e(LOG_TAG, "## setBotPower() : Power level must be positive integer.")
            sendError(getString(R.string.widget_integration_positive_power_level), eventData)
        }
    }

    /**
     * Provides the number of members in the rooms
     *
     * @param eventData the modular data
     */
    private fun getMembershipCount(eventData: Map<String, Any>) {
        sendIntegerResponse(mRoom!!.joinedMembers.size, eventData)
    }

    /* ==========================================================================================
     * companion
     * ========================================================================================== */

    companion object {
        private val LOG_TAG = IntegrationManagerActivity::class.java.simpleName

        /**
         * the parameters
         */
        internal const val EXTRA_MATRIX_ID = "EXTRA_MATRIX_ID"
        internal const val EXTRA_ROOM_ID = "EXTRA_ROOM_ID"
        internal const val EXTRA_WIDGET_ID = "EXTRA_WIDGET_ID"
        private const val EXTRA_SCREEN_ID = "EXTRA_SCREEN_ID"

        fun getIntent(context: Context, matrixId: String, roomId: String): Intent {
            return Intent(context, IntegrationManagerActivity::class.java)
                    .apply {
                        putExtra(EXTRA_MATRIX_ID, matrixId)
                        putExtra(EXTRA_ROOM_ID, roomId)
                    }
        }
    }
}