/*
 * Copyright (C) 2008 Esmertec AG.
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alex.mms.com.android.mms.transaction;

import static com.alex.mms.com.google.android.mms.pdu.PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND;
import static com.alex.mms.com.google.android.mms.pdu.PduHeaders.MESSAGE_TYPE_RETRIEVE_CONF;

import com.alex.mms.android.database.sqlite.SqliteWrapper;
import com.alex.mms.android.provider.Telephony.Mms;
import com.alex.mms.android.provider.Telephony.Sms;
import com.alex.mms.com.android.mms.LogTag;
import com.alex.mms.com.android.mms.R;
import com.alex.mms.com.android.mms.data.Contact;
import com.alex.mms.com.android.mms.data.Conversation;
import com.alex.mms.com.android.mms.data.WorkingMessage;
import com.alex.mms.com.android.mms.model.SlideModel;
import com.alex.mms.com.android.mms.model.SlideshowModel;
import com.alex.mms.com.android.mms.ui.ComposeMessageActivity;
import com.alex.mms.com.android.mms.ui.ConversationList;
import com.alex.mms.com.android.mms.ui.MessagingPreferenceActivity;
import com.alex.mms.com.android.mms.util.AddressUtils;
import com.alex.mms.com.android.mms.util.DownloadManager;
import com.alex.mms.com.android.mms.widget.MmsWidgetProvider;
import com.alex.mms.com.google.android.mms.MmsException;
import com.alex.mms.com.google.android.mms.pdu.EncodedStringValue;
import com.alex.mms.com.google.android.mms.pdu.GenericPdu;
import com.alex.mms.com.google.android.mms.pdu.MultimediaMessagePdu;
import com.alex.mms.com.google.android.mms.pdu.PduHeaders;
import com.alex.mms.com.google.android.mms.pdu.PduPersister;


import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
// TDH: Use support library.
//import android.app.TaskStackBuilder;
import android.support.v4.app.TaskStackBuilder;
import com.jakewharton.notificationcompat2.NotificationCompat2;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.text.style.TextAppearanceSpan;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * This class is used to update the notification indicator. It will check whether
 * there are unread messages. If yes, it would show the notification indicator,
 * otherwise, hide the indicator.
 */
public class MessagingNotification {

    private static final String TAG = LogTag.APP;
    private static final boolean DEBUG = false;  // TODO turn off before ship

    private static final int NOTIFICATION_ID = 123;
    public static final int MESSAGE_FAILED_NOTIFICATION_ID = 789;
    public static final int DOWNLOAD_FAILED_NOTIFICATION_ID = 531;
    /**
     * This is the volume at which to play the in-conversation notification sound,
     * expressed as a fraction of the system notification volume.
     */
    private static final float IN_CONVERSATION_NOTIFICATION_VOLUME = 0.25f;

    // This must be consistent with the column constants below.
    private static final String[] MMS_STATUS_PROJECTION = new String[] {
        Mms.THREAD_ID, Mms.DATE, Mms._ID, Mms.SUBJECT, Mms.SUBJECT_CHARSET };

    // This must be consistent with the column constants below.
    private static final String[] SMS_STATUS_PROJECTION = new String[] {
        Sms.THREAD_ID, Sms.DATE, Sms.ADDRESS, Sms.SUBJECT, Sms.BODY };

    // These must be consistent with MMS_STATUS_PROJECTION and
    // SMS_STATUS_PROJECTION.
    private static final int COLUMN_THREAD_ID   = 0;
    private static final int COLUMN_DATE        = 1;
    private static final int COLUMN_MMS_ID      = 2;
    private static final int COLUMN_SMS_ADDRESS = 2;
    private static final int COLUMN_SUBJECT     = 3;
    private static final int COLUMN_SUBJECT_CS  = 4;
    private static final int COLUMN_SMS_BODY    = 4;

    private static final String[] SMS_THREAD_ID_PROJECTION = new String[] { Sms.THREAD_ID };
    private static final String[] MMS_THREAD_ID_PROJECTION = new String[] { Mms.THREAD_ID };

    private static final String NEW_INCOMING_SM_CONSTRAINT =
            "(" + Sms.TYPE + " = " + Sms.MESSAGE_TYPE_INBOX
            + " AND " + Sms.SEEN + " = 0)";

    private static final String NEW_DELIVERY_SM_CONSTRAINT =
        "(" + Sms.TYPE + " = " + Sms.MESSAGE_TYPE_SENT
        + " AND " + Sms.STATUS + " = "+ Sms.STATUS_COMPLETE +")";

    private static final String NEW_INCOMING_MM_CONSTRAINT =
            "(" + Mms.MESSAGE_BOX + "=" + Mms.MESSAGE_BOX_INBOX
            + " AND " + Mms.SEEN + "=0"
            + " AND (" + Mms.MESSAGE_TYPE + "=" + MESSAGE_TYPE_NOTIFICATION_IND
            + " OR " + Mms.MESSAGE_TYPE + "=" + MESSAGE_TYPE_RETRIEVE_CONF + "))";

    private static final NotificationInfoComparator INFO_COMPARATOR =
            new NotificationInfoComparator();

    private static final Uri UNDELIVERED_URI = Uri.parse("content://mms-sms/undelivered");

	// TDH
    private final static String NOTIFICATION_DELETED_ACTION =
            "com.concentriclivers.mms.com.android.mms.NOTIFICATION_DELETED_ACTION";

    public static class OnDeletedReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
                Log.d(TAG, "[MessagingNotification] clear notification: mark all msgs seen");
            }

            Conversation.markAllConversationsAsSeen(context);
        }
    }

    public static final long THREAD_ALL = -1;
    public static final long THREAD_NONE = -2;
    /**
     * Keeps track of the thread ID of the conversation that's currently displayed to the user
     */
    private static long sCurrentlyDisplayedThreadId;
    private static final Object sCurrentlyDisplayedThreadLock = new Object();

    private static OnDeletedReceiver sNotificationDeletedReceiver = new OnDeletedReceiver();
    private static Intent sNotificationOnDeleteIntent;
    private static Handler sToastHandler = new Handler();
    private static PduPersister sPduPersister;
    private static final int MAX_BITMAP_DIMEN_DP = 360;
    private static float sScreenDensity;

    /**
     * mNotificationSet is kept sorted by the incoming message delivery time, with the
     * most recent message first.
     */
    private static SortedSet<NotificationInfo> sNotificationSet =
            new TreeSet<NotificationInfo>(INFO_COMPARATOR);

    private static final int MAX_MESSAGES_TO_SHOW = 8;  // the maximum number of new messages to
                                                        // show in a single notification.


    private MessagingNotification() {
    }

    public static void init(Context context) {
        // set up the intent filter for notification deleted action
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(NOTIFICATION_DELETED_ACTION);

        // TODO: should we unregister when the app gets killed?
        context.registerReceiver(sNotificationDeletedReceiver, intentFilter);
        sPduPersister = PduPersister.getPduPersister(context);

        // initialize the notification deleted action
        sNotificationOnDeleteIntent = new Intent(NOTIFICATION_DELETED_ACTION);

        sScreenDensity = context.getResources().getDisplayMetrics().density;
    }

    /**
     * Specifies which message thread is currently being viewed by the user. New messages in that
     * thread will not generate a notification icon and will play the notification sound at a lower
     * volume. Make sure you set this to THREAD_NONE when the UI component that shows the thread is
     * no longer visible to the user (e.g. Activity.onPause(), etc.)
     * @param threadId The ID of the thread that the user is currently viewing. Pass THREAD_NONE
     *  if the user is not viewing a thread, or THREAD_ALL if the user is viewing the conversation
     *  list (note: that latter one has no effect as of this implementation)
     */
    public static void setCurrentlyDisplayedThreadId(long threadId) {
        synchronized (sCurrentlyDisplayedThreadLock) {
            sCurrentlyDisplayedThreadId = threadId;
        }
    }

    /**
     * Checks to see if there are any "unseen" messages or delivery
     * reports.  Shows the most recent notification if there is one.
     * Does its work and query in a worker thread.
     *
     * @param context the context to use
     */
    public static void nonBlockingUpdateNewMessageIndicator(final Context context,
            final long newMsgThreadId,
            final boolean isStatusMessage) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                blockingUpdateNewMessageIndicator(context, newMsgThreadId, isStatusMessage);
            }
        }, "MessagingNotification.nonBlockingUpdateNewMessageIndicator").start();
    }

    /**
     * Checks to see if there are any "unseen" messages or delivery
     * reports and builds a sorted (by delivery date) list of unread notifications.
     *
     * @param context the context to use
     * @param newMsgThreadId The thread ID of a new message that we're to notify about; if there's
     *  no new message, use THREAD_NONE. If we should notify about multiple or unknown thread IDs,
     *  use THREAD_ALL.
     * @param isStatusMessage
     */
    public static void blockingUpdateNewMessageIndicator(Context context, long newMsgThreadId,
            boolean isStatusMessage) {
    	// TDH
    	Log.d("NotificationDebug", "blockingUpdateNewMessageIndicator: newMsgThreadId: " + newMsgThreadId);

        synchronized (sCurrentlyDisplayedThreadLock) {
            if (newMsgThreadId > 0 && newMsgThreadId == sCurrentlyDisplayedThreadId) {
                if (DEBUG) {
                    Log.d(TAG, "blockingUpdateNewMessageIndicator: newMsgThreadId == " +
                            "sCurrentlyDisplayedThreadId so NOT showing notification," +
                            " but playing soft sound. threadId: " + newMsgThreadId);
                }
            	// TDH
            	Log.d("NotificationDebug", "Calling playInConversationNotificationSound");

                playInConversationNotificationSound(context);
                return;
            }
        }
    	// TDH
    	Log.d("NotificationDebug", "Calling sNotificationSet.clear();");

        sNotificationSet.clear();

        MmsSmsDeliveryInfo delivery = null;
        Set<Long> threads = new HashSet<Long>(4);

        int count = 0;
        addMmsNotificationInfos(context, threads);
        addSmsNotificationInfos(context, threads);
        
    	// TDH
    	Log.d("NotificationDebug", "Cancelling notification");

        cancelNotification(context, NOTIFICATION_ID);
        if (!sNotificationSet.isEmpty()) {
            if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
                Log.d(TAG, "blockingUpdateNewMessageIndicator: count=" + count +
                        ", newMsgThreadId=" + newMsgThreadId);
            }
        	// TDH
        	Log.d("NotificationDebug", "Calling updateNotification()");

            updateNotification(context, newMsgThreadId != THREAD_NONE, threads.size());
        }

        // And deals with delivery reports (which use Toasts). It's safe to call in a worker
        // thread because the toast will eventually get posted to a handler.
        delivery = getSmsNewDeliveryInfo(context);
        if (delivery != null) {
            delivery.deliver(context, isStatusMessage);
        }
    }

    /**
     * Play the in-conversation notification sound (it's the regular notification sound, but
     * played at half-volume
     */
    private static void playInConversationNotificationSound(Context context) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        String ringtoneStr = sp.getString(MessagingPreferenceActivity.NOTIFICATION_RINGTONE,
                null);
        if (TextUtils.isEmpty(ringtoneStr)) {
            // Nothing to play
            return;
        }
        Uri ringtoneUri = Uri.parse(ringtoneStr);
        NotificationPlayer player = new NotificationPlayer(LogTag.APP);
        player.play(context, ringtoneUri, false, AudioManager.STREAM_NOTIFICATION,
                IN_CONVERSATION_NOTIFICATION_VOLUME);
    }

    /**
     * Updates all pending notifications, clearing or updating them as
     * necessary.
     */
    public static void blockingUpdateAllNotifications(final Context context) {
        nonBlockingUpdateNewMessageIndicator(context, THREAD_NONE, false);
        nonBlockingUpdateSendFailedNotification(context);
        updateDownloadFailedNotification(context);
        MmsWidgetProvider.notifyDatasetChanged(context);
    }

    private static final class MmsSmsDeliveryInfo {
        public CharSequence mTicker;
        public long mTimeMillis;

        public MmsSmsDeliveryInfo(CharSequence ticker, long timeMillis) {
            mTicker = ticker;
            mTimeMillis = timeMillis;
        }

        public void deliver(Context context, boolean isStatusMessage) {
            updateDeliveryNotification(
                    context, isStatusMessage, mTicker, mTimeMillis);
        }
    }

    private static final class NotificationInfo {
        public final Intent mClickIntent;
        public final String mMessage;
        public final CharSequence mTicker;
        public final long mTimeMillis;
        public final String mTitle;
        public final Bitmap mAttachmentBitmap;
        public final Contact mSender;
        public final boolean mIsSms;
        public final int mAttachmentType;
        public final String mSubject;
        public final long mThreadId;

        /**
         * @param isSms true if sms, false if mms
         * @param clickIntent where to go when the user taps the notification
         * @param message for a single message, this is the message text
         * @param subject text of mms subject
         * @param ticker text displayed ticker-style across the notification, typically formatted
         * as sender: message
         * @param timeMillis date the message was received
         * @param title for a single message, this is the sender
         * @param attachmentBitmap a bitmap of an attachment, such as a picture or video
         * @param sender contact of the sender
         * @param attachmentType of the mms attachment
         * @param threadId thread this message belongs to
         */
        public NotificationInfo(boolean isSms,
                Intent clickIntent, String message, String subject,
                CharSequence ticker, long timeMillis, String title,
                Bitmap attachmentBitmap, Contact sender,
                int attachmentType, long threadId) {
            mIsSms = isSms;
            mClickIntent = clickIntent;
            mMessage = message;
            mSubject = subject;
            mTicker = ticker;
            mTimeMillis = timeMillis;
            mTitle = title;
            mAttachmentBitmap = attachmentBitmap;
            mSender = sender;
            mAttachmentType = attachmentType;
            mThreadId = threadId;
        }

        public long getTime() {
            return mTimeMillis;
        }

        // This is the message string used in bigText and bigPicture notifications.
        public CharSequence formatBigMessage(Context context) {
            final TextAppearanceSpan notificationSubjectSpan = new TextAppearanceSpan(
                    context, R.style.NotificationPrimaryText);

            // Change multiple newlines (with potential white space between), into a single new line
            final String message =
                    !TextUtils.isEmpty(mMessage) ? mMessage.replaceAll("\\n\\s+", "\n") : "";

            SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();
            if (!TextUtils.isEmpty(mSubject)) {
                spannableStringBuilder.append(mSubject);
                spannableStringBuilder.setSpan(notificationSubjectSpan, 0, mSubject.length(), 0);
            }
            if (mAttachmentType > WorkingMessage.TEXT) {
                if (spannableStringBuilder.length() > 0) {
                    spannableStringBuilder.append('\n');
                }
                spannableStringBuilder.append(getAttachmentTypeString(context, mAttachmentType));
            }
            if (mMessage != null) {
                if (spannableStringBuilder.length() > 0) {
                    spannableStringBuilder.append('\n');
                }
                spannableStringBuilder.append(mMessage);
            }
            return spannableStringBuilder;
        }

        // This is the message string used in each line of an inboxStyle notification.
        public CharSequence formatInboxMessage(Context context) {
          final TextAppearanceSpan notificationSenderSpan = new TextAppearanceSpan(
                  context, R.style.NotificationPrimaryText);

          final TextAppearanceSpan notificationSubjectSpan = new TextAppearanceSpan(
                  context, R.style.NotificationSubjectText);

          // Change multiple newlines (with potential white space between), into a single new line
          final String message =
                  !TextUtils.isEmpty(mMessage) ? mMessage.replaceAll("\\n\\s+", "\n") : "";

          SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();
          final String sender = mSender.getName();
          if (!TextUtils.isEmpty(sender)) {
              spannableStringBuilder.append(sender);
              spannableStringBuilder.setSpan(notificationSenderSpan, 0, sender.length(), 0);
          }
          String separator = context.getString(R.string.notification_separator);
          if (!mIsSms) {
              if (!TextUtils.isEmpty(mSubject)) {
                  if (spannableStringBuilder.length() > 0) {
                      spannableStringBuilder.append(separator);
                  }
                  int start = spannableStringBuilder.length();
                  spannableStringBuilder.append(mSubject);
                  spannableStringBuilder.setSpan(notificationSubjectSpan, start,
                          start + mSubject.length(), 0);
              }
              if (mAttachmentType > WorkingMessage.TEXT) {
                  if (spannableStringBuilder.length() > 0) {
                      spannableStringBuilder.append(separator);
                  }
                  spannableStringBuilder.append(getAttachmentTypeString(context, mAttachmentType));
              }
          }
          if (message.length() > 0) {
              if (spannableStringBuilder.length() > 0) {
                  spannableStringBuilder.append(separator);
              }
              int start = spannableStringBuilder.length();
              spannableStringBuilder.append(message);
              spannableStringBuilder.setSpan(notificationSubjectSpan, start,
                      start + message.length(), 0);
          }
          return spannableStringBuilder;
        }

        // This is the summary string used in bigPicture notifications.
        public CharSequence formatPictureMessage(Context context) {
            final TextAppearanceSpan notificationSubjectSpan = new TextAppearanceSpan(
                    context, R.style.NotificationPrimaryText);

            // Change multiple newlines (with potential white space between), into a single new line
            final String message =
                    !TextUtils.isEmpty(mMessage) ? mMessage.replaceAll("\\n\\s+", "\n") : "";

            // Show the subject or the message (if no subject)
            SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();
            if (!TextUtils.isEmpty(mSubject)) {
                spannableStringBuilder.append(mSubject);
                spannableStringBuilder.setSpan(notificationSubjectSpan, 0, mSubject.length(), 0);
            }
            if (message.length() > 0 && spannableStringBuilder.length() == 0) {
                spannableStringBuilder.append(message);
                spannableStringBuilder.setSpan(notificationSubjectSpan, 0, message.length(), 0);
            }
            return spannableStringBuilder;
        }
    }

    // Return a formatted string with all the sender names separated by commas.
    private static CharSequence formatSenders(Context context,
            ArrayList<NotificationInfo> senders) {
        final TextAppearanceSpan notificationSenderSpan = new TextAppearanceSpan(
                context, R.style.NotificationPrimaryText);

        String separator = context.getString(R.string.enumeration_comma);   // ", "
        SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();
        int len = senders.size();
        for (int i = 0; i < len; i++) {
            if (i > 0) {
                spannableStringBuilder.append(separator);
            }
            spannableStringBuilder.append(senders.get(i).mSender.getName());
        }
        spannableStringBuilder.setSpan(notificationSenderSpan, 0,
                spannableStringBuilder.length(), 0);
        return spannableStringBuilder;
    }

    // Return a formatted string with the attachmentType spelled out as a string. For
    // no attachment (or just text), return null.
    private static CharSequence getAttachmentTypeString(Context context, int attachmentType) {
        final TextAppearanceSpan notificationAttachmentSpan = new TextAppearanceSpan(
                context, R.style.NotificationSecondaryText);
        int id = 0;
        switch (attachmentType) {
            case WorkingMessage.AUDIO: id = R.string.attachment_audio; break;
            case WorkingMessage.VIDEO: id = R.string.attachment_video; break;
            case WorkingMessage.SLIDESHOW: id = R.string.attachment_slideshow; break;
            case WorkingMessage.IMAGE: id = R.string.attachment_picture; break;
        }
        if (id > 0) {
            final SpannableString spannableString = new SpannableString(context.getString(id));
            spannableString.setSpan(notificationAttachmentSpan,
                    0, spannableString.length(), 0);
            return spannableString;
        }
        return null;
     }

    /**
     *
     * Sorts by the time a notification was received in descending order -- newer first.
     *
     */
    private static final class NotificationInfoComparator
            implements Comparator<NotificationInfo> {
        @Override
        public int compare(
                NotificationInfo info1, NotificationInfo info2) {
            return Long.signum(info2.getTime() - info1.getTime());
        }
    }

    private static final void addMmsNotificationInfos(
            Context context, Set<Long> threads) {
        ContentResolver resolver = context.getContentResolver();

        // This query looks like this when logged:
        // I/Database(  147): elapsedTime4Sql|/data/data/com.android.providers.telephony/databases/
        // mmssms.db|0.362 ms|SELECT thread_id, date, _id, sub, sub_cs FROM pdu WHERE ((msg_box=1
        // AND seen=0 AND (m_type=130 OR m_type=132))) ORDER BY date desc

        Cursor cursor = SqliteWrapper.query(context, resolver, Mms.CONTENT_URI,
                            MMS_STATUS_PROJECTION, NEW_INCOMING_MM_CONSTRAINT,
                            null, Mms.DATE + " desc");

        if (cursor == null) {
            return;
        }

        try {
            while (cursor.moveToNext()) {

                long msgId = cursor.getLong(COLUMN_MMS_ID);
                Uri msgUri = Mms.CONTENT_URI.buildUpon().appendPath(
                        Long.toString(msgId)).build();
                String address = AddressUtils.getFrom(context, msgUri);

                Contact contact = Contact.get(address, false);
                if (contact.getSendToVoicemail()) {
                    // don't notify, skip this one
                    continue;
                }

                String subject = getMmsSubject(
                        cursor.getString(COLUMN_SUBJECT), cursor.getInt(COLUMN_SUBJECT_CS));
                long threadId = cursor.getLong(COLUMN_THREAD_ID);
                long timeMillis = cursor.getLong(COLUMN_DATE) * 1000;

                if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
                    Log.d(TAG, "addMmsNotificationInfos: count=" + cursor.getCount() +
                            ", addr = " + address + ", thread_id=" + threadId);
                }

                // Extract the message and/or an attached picture from the first slide
                Bitmap attachedPicture = null;
                String messageBody = null;
                int attachmentType = WorkingMessage.TEXT;
                try {
                    GenericPdu pdu = sPduPersister.load(msgUri);
                    if (pdu != null && pdu instanceof MultimediaMessagePdu) {
                        SlideshowModel slideshow = SlideshowModel.createFromPduBody(context,
                                ((MultimediaMessagePdu)pdu).getBody());
                        attachmentType = getAttachmentType(slideshow);
                        SlideModel firstSlide = slideshow.get(0);
                        if (firstSlide != null) {
                            if (firstSlide.hasImage()) {
                                int maxDim = dp2Pixels(MAX_BITMAP_DIMEN_DP);
                                attachedPicture = firstSlide.getImage().getBitmap(maxDim, maxDim);
                            }
                            if (firstSlide.hasText()) {
                                messageBody = firstSlide.getText().getText();
                            }
                        }
                    }
                } catch (final MmsException e) {
                    Log.e(TAG, "MmsException loading uri: " + msgUri, e);
                }

                NotificationInfo info = getNewMessageNotificationInfo(context,
                        false /* isSms */,
                        address,
                        messageBody, subject,
                        threadId,
                        timeMillis,
                        attachedPicture,
                        contact,
                        attachmentType);

                sNotificationSet.add(info);

                threads.add(threadId);
            }
        } finally {
            cursor.close();
        }
    }

    // Look at the passed in slideshow and determine what type of attachment it is.
    private static int getAttachmentType(SlideshowModel slideshow) {
        int slideCount = slideshow.size();

        if (slideCount == 0) {
            return WorkingMessage.TEXT;
        } else if (slideCount > 1) {
            return WorkingMessage.SLIDESHOW;
        } else {
            SlideModel slide = slideshow.get(0);
            if (slide.hasImage()) {
                return WorkingMessage.IMAGE;
            } else if (slide.hasVideo()) {
                return WorkingMessage.VIDEO;
            } else if (slide.hasAudio()) {
                return WorkingMessage.AUDIO;
            }
        }
        return WorkingMessage.TEXT;
    }

    private static final int dp2Pixels(int dip) {
        return (int) (dip * sScreenDensity + 0.5f);
    }

    private static final MmsSmsDeliveryInfo getSmsNewDeliveryInfo(Context context) {
        ContentResolver resolver = context.getContentResolver();
        Cursor cursor = SqliteWrapper.query(context, resolver, Sms.CONTENT_URI,
                    SMS_STATUS_PROJECTION, NEW_DELIVERY_SM_CONSTRAINT,
                    null, Sms.DATE);

        if (cursor == null) {
            return null;
        }

        try {
            if (!cursor.moveToLast()) {
                return null;
            }

            String address = cursor.getString(COLUMN_SMS_ADDRESS);
            long timeMillis = 3000;

            Contact contact = Contact.get(address, false);
            String name = contact.getNameAndNumber();

            return new MmsSmsDeliveryInfo(context.getString(R.string.delivery_toast_body, name),
                timeMillis);

        } finally {
            cursor.close();
        }
    }

    private static final void addSmsNotificationInfos(
            Context context, Set<Long> threads) {
    	// TDH
    	Log.d("NotificationDebug", "addSmsNotificationInfos");
    	
    	// TDH: On my Samsung phone, the message has a type of 4 (OUTBOX)
    	// instead of 1 (INBOX)! God knows why. Samsung are fucking retards.
    	
    	// Ok, but that seems to be only when I send a text to myself.
    	// When I get one from someone else the type is 1 (INBOX) but seen
    	// is also 1! How am I supposed to know... blargh!
    	// read is also 1. I'm getting to the point where I will need
    	// to add duplicate columns to the DB (e.g. seen_by_tdh).
    	// Alternatively maintain another database just for "seen".
    	// Or as a hacky workaround, maintain a count of the number of unread
    	// messages in each thread id.
    	
    	// TDH: TODO: Do I really need this custom constraint? It
    	// seems to be working as expected now...
        final String NEW_INCOMING_SM_CONSTRAINT_TDH =
                "((" + Sms.TYPE + " = " + Sms.MESSAGE_TYPE_INBOX
                + " OR " + Sms.TYPE + " = " + Sms.MESSAGE_TYPE_OUTBOX + ")"
                + " AND " + Sms.SEEN + " = 0)";
        ContentResolver resolver = context.getContentResolver();
        Cursor cursor = SqliteWrapper.query(context, resolver, Sms.CONTENT_URI,
                            SMS_STATUS_PROJECTION, NEW_INCOMING_SM_CONSTRAINT_TDH,
                            null, Sms.DATE + " desc");
        
        if (cursor == null) {
        	// TDH: TODO: This happens because our SQL code above
        	// assumes columns exist that don't or something...
        	// but to get the error I need to read logcat and automatically
        	// send myself a message.
        	Log.d("NotificationDebug", "cursor null");

            return;
        }
        
        // TDH
    	Log.d("NotificationDebug", "Cursor count: " + cursor.getCount());
    	
    	String[] testProjection = new String[] {
    	        Sms.THREAD_ID, Sms.DATE, Sms.ADDRESS, Sms.SUBJECT, Sms.BODY, Sms.SEEN, Sms.READ, Sms.TYPE };
    	// TDH: Test
    	Cursor cursor2 = SqliteWrapper.query(context, resolver, Sms.CONTENT_URI,
    			testProjection, null, null, Sms.DATE + " desc");
    	if (cursor2 == null)
    	{
    		Log.d("NotificationDebug", "Cursor2 null");
    		
    		return;
    	}
    	Log.d("NotificationDebug", "Cursor2 count: " + cursor2.getCount());
    	int i = 0;
    	while (cursor2.moveToNext() && ++i < 3)
    	{
    		Log.d("NotificationDebug", "message: " + cursor2.getString(COLUMN_SMS_BODY)
    				+ ", threadId: " + cursor2.getLong(COLUMN_THREAD_ID)
    				+ ", seen: " + cursor2.getInt(5)
    				+ ", read: " + cursor2.getInt(6)
    				+ ", type: " + cursor2.getInt(7));
    	}
        
        // TDH: Ok we get here. cursor isn't null but something still fails below.
        // I think it is still because it is looking up the SMS in the database
        // and it doesn't exist yet. Yeah seems to be that way.
        // Ok I will just add a delay for 2 second to the receive code.

        try {
            while (cursor.moveToNext()) {
            	Log.d("NotificationDebug", "movedToNext.");
                String address = cursor.getString(COLUMN_SMS_ADDRESS);
            	Log.d("NotificationDebug", "address: " + address);

                Contact contact = Contact.get(address, false);
            	Log.d("NotificationDebug", "contact: " + contact);

                if (contact.getSendToVoicemail()) {
                	Log.d("NotificationDebug", "getSendToVoicemail() = true");

                    // don't notify, skip this one
                    continue;
                }

                String message = cursor.getString(COLUMN_SMS_BODY);
                long threadId = cursor.getLong(COLUMN_THREAD_ID);
                
            	Log.d("NotificationDebug", "message: " + message + ", threadId: " + threadId);
                
            	// TDH: Never gets to here!
            	Log.d("NotificationDebug", "Got thread id: " + threadId);

                long timeMillis = cursor.getLong(COLUMN_DATE);

                if (Log.isLoggable(LogTag.APP, Log.VERBOSE))
                {
                    Log.d(TAG, "addSmsNotificationInfos: count=" + cursor.getCount() +
                            ", addr=" + address + ", thread_id=" + threadId);
                }


                NotificationInfo info = getNewMessageNotificationInfo(context, true /* isSms */,
                        address, message, null /* subject */,
                        threadId, timeMillis, null /* attachmentBitmap */,
                        contact, WorkingMessage.TEXT);

                sNotificationSet.add(info);

                threads.add(threadId);
                threads.add(cursor.getLong(COLUMN_THREAD_ID));
            }
        }
        catch (Exception e)
        {
        	// TDH
        	e.printStackTrace();
        }
        finally {
            cursor.close();
        }
    }

    private static final NotificationInfo getNewMessageNotificationInfo(
            Context context,
            boolean isSms,
            String address,
            String message,
            String subject,
            long threadId,
            long timeMillis,
            Bitmap attachmentBitmap,
            Contact contact,
            int attachmentType) {
    	// TDH
    	Log.d("NotificationDebug", "getNewMessageNotificationInfo");
    	
        Intent clickIntent = ComposeMessageActivity.createIntent(context, threadId);
        clickIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        String senderInfo = buildTickerMessage(
                context, address, null, null).toString();
        String senderInfoName = senderInfo.substring(
                0, senderInfo.length() - 2);
        CharSequence ticker = buildTickerMessage(
                context, address, subject, message);
        
    	// TDH
    	Log.d("NotificationDebug", "senderInfo: " + senderInfo + ", ticker: " + ticker);


        return new NotificationInfo(isSms,
                clickIntent, message, subject, ticker, timeMillis,
                senderInfoName, attachmentBitmap, contact, attachmentType, threadId);
    }

    public static void cancelNotification(Context context, int notificationId) {
        NotificationManager nm = (NotificationManager) context.getSystemService(
                Context.NOTIFICATION_SERVICE);

        nm.cancel(notificationId);
    }

    private static void updateDeliveryNotification(final Context context,
                                                   boolean isStatusMessage,
                                                   final CharSequence message,
                                                   final long timeMillis) {
        if (!isStatusMessage) {
            return;
        }


        if (!MessagingPreferenceActivity.getNotificationEnabled(context)) {
            return;
        }

        sToastHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context, message, (int)timeMillis).show();
            }
        });
    }

    /**
     * updateNotification is *the* main function for building the actual notification handed to
     * the NotificationManager
     * @param context
     * @param isNew if we've got a new message, show the ticker
     * @param uniqueThreadCount
     */
    private static void updateNotification(
            Context context,
            boolean isNew,
            int uniqueThreadCount) {
    	// TDH
    	Log.d("NotificationDebug", "updateNotification()");

        // If the user has turned off notifications in settings, don't do any notifying.
        if (!MessagingPreferenceActivity.getNotificationEnabled(context)) {
            if (DEBUG) {
                Log.d(TAG, "updateNotification: notifications turned off in prefs, bailing");
            }
        	// TDH
        	Log.d("NotificationDebug", "Notifications not enabled!");

            return;
        }

        // Figure out what we've got -- whether all sms's, mms's, or a mixture of both.
        int messageCount = sNotificationSet.size();
        
    	// TDH:
    	Log.d("NotificationDebug", "messageCount: " + messageCount);
    	if (messageCount == 0)
    	{
    		Log.d("NotificationDebug", "WTF. Should have at least one message.");
    		return;
    	}

        NotificationInfo mostRecentNotification = sNotificationSet.first();

        // TDH: Use NotificationCompat2 (and in other places but it is obvious where).
        final NotificationCompat2.Builder noti = new NotificationCompat2.Builder(context)
                .setWhen(mostRecentNotification.mTimeMillis);

        if (isNew) {
            noti.setTicker(mostRecentNotification.mTicker);
        }
    	// TDH
    	Log.d("NotificationDebug", "Creating TaskStackBuilder");

        TaskStackBuilder taskStackBuilder = TaskStackBuilder.create(context);
    	// TDH
    	Log.d("NotificationDebug", "Created TaskStackBuilder. UniqueThreadCount: " + uniqueThreadCount);

        // If we have more than one unique thread, change the title (which would
        // normally be the contact who sent the message) to a generic one that
        // makes sense for multiple senders, and change the Intent to take the
        // user to the conversation list instead of the specific thread.

        // Cases:
        //   1) single message from single thread - intent goes to ComposeMessageActivity
        //   2) multiple messages from single thread - intent goes to ComposeMessageActivity
        //   3) messages from multiple threads - intent goes to ConversationList

        final Resources res = context.getResources();
        String title = null;
        Bitmap avatar = null;
        if (uniqueThreadCount > 1) {    // messages from multiple threads
            Intent mainActivityIntent = new Intent(Intent.ACTION_MAIN);

            mainActivityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP);

            mainActivityIntent.setType("vnd.android-dir/mms-sms");
            taskStackBuilder.addNextIntent(mainActivityIntent);
            title = context.getString(R.string.message_count_notification, messageCount);
        } else {    // same thread, single or multiple messages
            title = mostRecentNotification.mTitle;
            BitmapDrawable contactDrawable = (BitmapDrawable)mostRecentNotification.mSender
                    .getAvatar(context, null);
            if (contactDrawable != null) {
                // Show the sender's avatar as the big icon. Contact bitmaps are 96x96 so we
                // have to scale 'em up to 128x128 to fill the whole notification large icon.
                avatar = contactDrawable.getBitmap();
                if (avatar != null) {
                    final int idealIconHeight =
                        res.getDimensionPixelSize(android.R.dimen.notification_large_icon_height);
                    final int idealIconWidth =
                         res.getDimensionPixelSize(android.R.dimen.notification_large_icon_width);
                    if (avatar.getHeight() < idealIconHeight) {
                        // Scale this image to fit the intended size
                        avatar = Bitmap.createScaledBitmap(
                                avatar, idealIconWidth, idealIconHeight, true);
                    }
                    if (avatar != null) {
                        noti.setLargeIcon(avatar);
                    }
                }
            }

            taskStackBuilder.addParentStack(ComposeMessageActivity.class);
            taskStackBuilder.addNextIntent(mostRecentNotification.mClickIntent);
        }
        
    	// TDH
    	Log.d("NotificationDebug", "title: " + title);
        
        // Always have to set the small icon or the notification is ignored
        noti.setSmallIcon(R.drawable.stat_notify_sms);

        NotificationManager nm = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);

        // Update the notification.
        noti.setContentTitle(title)
            .setContentIntent(
                    taskStackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT));
            // TDH: Can't do these yet.
//            .addKind(Notification.KIND_MESSAGE)
//            .setPriority(Notification.PRIORITY_DEFAULT);     // TODO: set based on contact coming
//                                                             // from a favorite.

        int defaults = 0;

        if (isNew) {
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
            String vibrateWhen;
            if (sp.contains(MessagingPreferenceActivity.NOTIFICATION_VIBRATE_WHEN)) {
                vibrateWhen =
                    sp.getString(MessagingPreferenceActivity.NOTIFICATION_VIBRATE_WHEN, null);
            } else if (sp.contains(MessagingPreferenceActivity.NOTIFICATION_VIBRATE)) {
                vibrateWhen =
                        sp.getBoolean(MessagingPreferenceActivity.NOTIFICATION_VIBRATE, false) ?
                    context.getString(R.string.prefDefault_vibrate_true) :
                    context.getString(R.string.prefDefault_vibrate_false);
            } else {
                vibrateWhen = context.getString(R.string.prefDefault_vibrateWhen);
            }

            boolean vibrateAlways = vibrateWhen.equals("always");
            boolean vibrateSilent = vibrateWhen.equals("silent");
            AudioManager audioManager =
                (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
            boolean nowSilent =
                audioManager.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE;

            if (vibrateAlways || vibrateSilent && nowSilent) {
                defaults |= Notification.DEFAULT_VIBRATE;
            }

            String ringtoneStr = sp.getString(MessagingPreferenceActivity.NOTIFICATION_RINGTONE,
                    null);
            noti.setSound(TextUtils.isEmpty(ringtoneStr) ? null : Uri.parse(ringtoneStr));
            if (DEBUG) {
                Log.d(TAG, "updateNotification: new message, adding sound to the notification");
            }
        }

        defaults |= Notification.DEFAULT_LIGHTS;

        noti.setDefaults(defaults);

        // set up delete intent
        noti.setDeleteIntent(PendingIntent.getBroadcast(context, 0,
                sNotificationOnDeleteIntent, 0));

        final Notification notification;

        if (messageCount == 1) {
            // We've got a single message
        	
        	// TDH
        	Log.d("NotificationDebug", "Single message, with text: " + mostRecentNotification.formatBigMessage(context));


            // This sets the text for the collapsed form:
            noti.setContentText(mostRecentNotification.formatBigMessage(context));

            if (mostRecentNotification.mAttachmentBitmap != null) {
                // The message has a picture, show that

                notification = new NotificationCompat2.BigPictureStyle(noti)
                    .bigPicture(mostRecentNotification.mAttachmentBitmap)
                    // This sets the text for the expanded picture form:
                    .setSummaryText(mostRecentNotification.formatPictureMessage(context))
                    .build();
            } else {
                // Show a single notification -- big style with the text of the whole message
                notification = new NotificationCompat2.BigTextStyle(noti)
                    .bigText(mostRecentNotification.formatBigMessage(context))
                    .build();
            }
        } else {
            // We've got multiple messages
            if (uniqueThreadCount == 1) {
                // We've got multiple messages for the same thread.
                // Starting with the oldest new message, display the full text of each message.
                // Begin a line for each subsequent message.
                SpannableStringBuilder buf = new SpannableStringBuilder();
                NotificationInfo infos[] =
                        sNotificationSet.toArray(new NotificationInfo[sNotificationSet.size()]);
                int len = infos.length;
                for (int i = len - 1; i >= 0; i--) {
                    NotificationInfo info = infos[i];

                    buf.append(info.formatBigMessage(context));

                    if (i != 0) {
                        buf.append('\n');
                    }
                }

                noti.setContentText(context.getString(R.string.message_count_notification,
                        messageCount));

                // Show a single notification -- big style with the text of all the messages
                notification = new NotificationCompat2.BigTextStyle(noti)
                    .bigText(buf)
                    // Forcibly show the last line, with the app's smallIcon in it, if we 
                    // kicked the smallIcon out with an avatar bitmap
                    .setSummaryText((avatar == null) ? null : " ")
                    .build();
            } else {
                // Build a set of the most recent notification per threadId.
                HashSet<Long> uniqueThreads = new HashSet<Long>(sNotificationSet.size());
                ArrayList<NotificationInfo> mostRecentNotifPerThread =
                        new ArrayList<NotificationInfo>();
                Iterator<NotificationInfo> notifications = sNotificationSet.iterator();
                while (notifications.hasNext()) {
                    NotificationInfo notificationInfo = notifications.next();
                    if (!uniqueThreads.contains(notificationInfo.mThreadId)) {
                        uniqueThreads.add(notificationInfo.mThreadId);
                        mostRecentNotifPerThread.add(notificationInfo);
                    }
                }
                // When collapsed, show all the senders like this:
                //     Fred Flinstone, Barry Manilow, Pete...
                noti.setContentText(formatSenders(context, mostRecentNotifPerThread));
                NotificationCompat2.InboxStyle inboxStyle = new NotificationCompat2.InboxStyle(noti);

                // We have to set the summary text to non-empty so the content text doesn't show
                // up when expanded.
                inboxStyle.setSummaryText(" ");

                // At this point we've got multiple messages in multiple threads. We only
                // want to show the most recent message per thread, which are in
                // mostRecentNotifPerThread.
                int uniqueThreadMessageCount = mostRecentNotifPerThread.size();
                int maxMessages = Math.min(MAX_MESSAGES_TO_SHOW, uniqueThreadMessageCount);

                for (int i = 0; i < maxMessages; i++) {
                    NotificationInfo info = mostRecentNotifPerThread.get(i);
                    inboxStyle.addLine(info.formatInboxMessage(context));
                }
                notification = inboxStyle.build();
                if (DEBUG) {
                    Log.d(TAG, "updateNotification: multi messages," +
                            " showing inboxStyle notification");
                }
            }
        }

    	// TDH
    	Log.d("NotificationDebug", "Showing notification: " + notification);

        nm.notify(NOTIFICATION_ID, notification);
    }

    protected static CharSequence buildTickerMessage(
            Context context, String address, String subject, String body) {
        String displayAddress = Contact.get(address, true).getName();

        StringBuilder buf = new StringBuilder(
                displayAddress == null
                ? ""
                : displayAddress.replace('\n', ' ').replace('\r', ' '));
        buf.append(':').append(' ');

        int offset = buf.length();
        if (!TextUtils.isEmpty(subject)) {
            subject = subject.replace('\n', ' ').replace('\r', ' ');
            buf.append(subject);
            buf.append(' ');
        }

        if (!TextUtils.isEmpty(body)) {
            body = body.replace('\n', ' ').replace('\r', ' ');
            buf.append(body);
        }

        SpannableString spanText = new SpannableString(buf.toString());
        spanText.setSpan(new StyleSpan(Typeface.BOLD), 0, offset,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        return spanText;
    }

    private static String getMmsSubject(String sub, int charset) {
        return TextUtils.isEmpty(sub) ? ""
                : new EncodedStringValue(charset, PduPersister.getBytes(sub)).getString();
    }

    public static void notifyDownloadFailed(Context context, long threadId) {
        notifyFailed(context, true, threadId, false);
    }

    public static void notifySendFailed(Context context) {
        notifyFailed(context, false, 0, false);
    }

    public static void notifySendFailed(Context context, boolean noisy) {
        notifyFailed(context, false, 0, noisy);
    }

    private static void notifyFailed(Context context, boolean isDownload, long threadId,
                                     boolean noisy) {
        // TODO factor out common code for creating notifications
        boolean enabled = MessagingPreferenceActivity.getNotificationEnabled(context);
        if (!enabled) {
            return;
        }

        // Strategy:
        // a. If there is a single failure notification, tapping on the notification goes
        //    to the compose view.
        // b. If there are two failure it stays in the thread view. Selecting one undelivered
        //    thread will dismiss one undelivered notification but will still display the
        //    notification.If you select the 2nd undelivered one it will dismiss the notification.

        long[] msgThreadId = {0, 1};    // Dummy initial values, just to initialize the memory
        int totalFailedCount = getUndeliveredMessageCount(context, msgThreadId);
        if (totalFailedCount == 0 && !isDownload) {
            return;
        }
        // The getUndeliveredMessageCount method puts a non-zero value in msgThreadId[1] if all
        // failures are from the same thread.
        // If isDownload is true, we're dealing with 1 specific failure; therefore "all failed" are
        // indeed in the same thread since there's only 1.
        boolean allFailedInSameThread = (msgThreadId[1] != 0) || isDownload;

        Intent failedIntent;
        Notification notification = new Notification();
        String title;
        String description;
        if (totalFailedCount > 1) {
            description = context.getString(R.string.notification_failed_multiple,
                    Integer.toString(totalFailedCount));
            title = context.getString(R.string.notification_failed_multiple_title);
        } else {
            title = isDownload ?
                        context.getString(R.string.message_download_failed_title) :
                        context.getString(R.string.message_send_failed_title);

            description = context.getString(R.string.message_failed_body);
        }

        TaskStackBuilder taskStackBuilder = TaskStackBuilder.create(context);
        if (allFailedInSameThread) {
            failedIntent = new Intent(context, ComposeMessageActivity.class);
            if (isDownload) {
                // When isDownload is true, the valid threadId is passed into this function.
                failedIntent.putExtra("failed_download_flag", true);
            } else {
                threadId = msgThreadId[0];
                failedIntent.putExtra("undelivered_flag", true);
            }
            failedIntent.putExtra("thread_id", threadId);
            taskStackBuilder.addParentStack(ComposeMessageActivity.class);
        } else {
            failedIntent = new Intent(context, ConversationList.class);
        }
        taskStackBuilder.addNextIntent(failedIntent);

        notification.icon = R.drawable.stat_notify_sms_failed;

        notification.tickerText = title;

        notification.setLatestEventInfo(context, title, description,
                taskStackBuilder.getPendingIntent(0,  PendingIntent.FLAG_UPDATE_CURRENT));

        if (noisy) {
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
            boolean vibrate = sp.getBoolean(MessagingPreferenceActivity.NOTIFICATION_VIBRATE,
                    false /* don't vibrate by default */);
            if (vibrate) {
                notification.defaults |= Notification.DEFAULT_VIBRATE;
            }

            String ringtoneStr = sp.getString(MessagingPreferenceActivity.NOTIFICATION_RINGTONE,
                    null);
            notification.sound = TextUtils.isEmpty(ringtoneStr) ? null : Uri.parse(ringtoneStr);
        }

        NotificationManager notificationMgr = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (isDownload) {
            notificationMgr.notify(DOWNLOAD_FAILED_NOTIFICATION_ID, notification);
        } else {
            notificationMgr.notify(MESSAGE_FAILED_NOTIFICATION_ID, notification);
        }
    }

    /**
     * Query the DB and return the number of undelivered messages (total for both SMS and MMS)
     * @param context The context
     * @param threadIdResult A container to put the result in, according to the following rules:
     *  threadIdResult[0] contains the thread id of the first message.
     *  threadIdResult[1] is nonzero if the thread ids of all the messages are the same.
     *  You can pass in null for threadIdResult.
     *  You can pass in a threadIdResult of size 1 to avoid the comparison of each thread id.
     */
    private static int getUndeliveredMessageCount(Context context, long[] threadIdResult) {
        Cursor undeliveredCursor = SqliteWrapper.query(context, context.getContentResolver(),
                UNDELIVERED_URI, MMS_THREAD_ID_PROJECTION, "read=0", null, null);
        if (undeliveredCursor == null) {
            return 0;
        }
        int count = undeliveredCursor.getCount();
        try {
            if (threadIdResult != null && undeliveredCursor.moveToFirst()) {
                threadIdResult[0] = undeliveredCursor.getLong(0);

                if (threadIdResult.length >= 2) {
                    // Test to see if all the undelivered messages belong to the same thread.
                    long firstId = threadIdResult[0];
                    while (undeliveredCursor.moveToNext()) {
                        if (undeliveredCursor.getLong(0) != firstId) {
                            firstId = 0;
                            break;
                        }
                    }
                    threadIdResult[1] = firstId;    // non-zero if all ids are the same
                }
            }
        } finally {
            undeliveredCursor.close();
        }
        return count;
    }

    public static void nonBlockingUpdateSendFailedNotification(final Context context) {
        new AsyncTask<Void, Void, Integer>() {
            protected Integer doInBackground(Void... none) {
                return getUndeliveredMessageCount(context, null);
            }

            protected void onPostExecute(Integer result) {
                if (result < 1) {
                    cancelNotification(context, MESSAGE_FAILED_NOTIFICATION_ID);
                } else {
                    // rebuild and adjust the message count if necessary.
                    notifySendFailed(context);
                }
            }
        }.execute();
    }

    /**
     *  If all the undelivered messages belong to "threadId", cancel the notification.
     */
    public static void updateSendFailedNotificationForThread(Context context, long threadId) {
        long[] msgThreadId = {0, 0};
        if (getUndeliveredMessageCount(context, msgThreadId) > 0
                && msgThreadId[0] == threadId
                && msgThreadId[1] != 0) {
            cancelNotification(context, MESSAGE_FAILED_NOTIFICATION_ID);
        }
    }

    private static int getDownloadFailedMessageCount(Context context) {
        // Look for any messages in the MMS Inbox that are of the type
        // NOTIFICATION_IND (i.e. not already downloaded) and in the
        // permanent failure state.  If there are none, cancel any
        // failed download notification.
        Cursor c = SqliteWrapper.query(context, context.getContentResolver(),
                Mms.Inbox.CONTENT_URI, null,
                Mms.MESSAGE_TYPE + "=" +
                    String.valueOf(PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND) +
                " AND " + Mms.STATUS + "=" +
                    String.valueOf(DownloadManager.STATE_PERMANENT_FAILURE),
                null, null);
        if (c == null) {
            return 0;
        }
        int count = c.getCount();
        c.close();
        return count;
    }

    public static void updateDownloadFailedNotification(Context context) {
        if (getDownloadFailedMessageCount(context) < 1) {
            cancelNotification(context, DOWNLOAD_FAILED_NOTIFICATION_ID);
        }
    }

    public static boolean isFailedToDeliver(Intent intent) {
        return (intent != null) && intent.getBooleanExtra("undelivered_flag", false);
    }

    public static boolean isFailedToDownload(Intent intent) {
        return (intent != null) && intent.getBooleanExtra("failed_download_flag", false);
    }

    /**
     * Get the thread ID of the SMS message with the given URI
     * @param context The context
     * @param uri The URI of the SMS message
     * @return The thread ID, or THREAD_NONE if the URI contains no entries
     */
    public static long getSmsThreadId(Context context, Uri uri) {
        Cursor cursor = SqliteWrapper.query(
            context,
            context.getContentResolver(),
            uri,
            SMS_THREAD_ID_PROJECTION,
            null,
            null,
            null);

        if (cursor == null) {
            return THREAD_NONE;
        }

        try {
            if (cursor.moveToFirst()) {
                return cursor.getLong(cursor.getColumnIndex(Sms.THREAD_ID));
            } else {
                return THREAD_NONE;
            }
        } finally {
            cursor.close();
        }
    }

    /**
     * Get the thread ID of the MMS message with the given URI
     * @param context The context
     * @param uri The URI of the SMS message
     * @return The thread ID, or THREAD_NONE if the URI contains no entries
     */
    public static long getThreadId(Context context, Uri uri) {
        Cursor cursor = SqliteWrapper.query(
                context,
                context.getContentResolver(),
                uri,
                MMS_THREAD_ID_PROJECTION,
                null,
                null,
                null);

        if (cursor == null) {
            return THREAD_NONE;
        }

        try {
            if (cursor.moveToFirst()) {
                return cursor.getLong(cursor.getColumnIndex(Mms.THREAD_ID));
            } else {
                return THREAD_NONE;
            }
        } finally {
            cursor.close();
        }
    }
}
