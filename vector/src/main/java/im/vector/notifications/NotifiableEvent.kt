/*
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
package im.vector.notifications

import android.support.v4.app.NotificationCompat
import org.matrix.androidsdk.rest.model.Event
import java.io.Serializable

interface NotifiableEvent : Serializable {
    val eventId: String
    val noisy: Boolean
    val title: String
    val description: String?
    val type: String
    val timestamp: Long
    //NotificationCompat.VISIBILITY_PUBLIC , VISIBILITY_PRIVATE , VISIBILITY_SECRET
    var lockScreenVisibility: Int
    // Compat: Only for android <7, for newer version the sound is defined in the channel
    var soundName: String?
    var hasBeenDisplayed: Boolean
}

data class SimpleNotifiableEvent(
        override val eventId: String,
        override val noisy: Boolean,
        override val title: String,
        override val description: String,
        override val type: String,
        override val timestamp: Long,
        override var soundName: String?) : NotifiableEvent {

    override var hasBeenDisplayed: Boolean = false

    override var lockScreenVisibility = NotificationCompat.VISIBILITY_PUBLIC

}

data class NotifiableMessageEvent(
        override val eventId: String,
        override val noisy: Boolean,
        override val timestamp: Long,
        var senderName: String?,
        var body: String?,
        var roomId: String,
        var roomName: String?
) : NotifiableEvent {

    override var soundName: String? = null
    override var lockScreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
    override var hasBeenDisplayed: Boolean = false

    var roomAvatarPath: String? = null
    var senderAvatarPath: String? = null

    override val type: String
        get() = Event.EVENT_TYPE_MESSAGE

    override val description: String?
        get() = body ?: ""

    override val title: String
        get() = senderName ?: ""

    //This is used for >N notification, as the result of a smart reply
    var outGoingMessage = false
    var outGoingMessageFailed = false

}